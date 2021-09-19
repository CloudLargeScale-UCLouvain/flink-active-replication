/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.runtime.io;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.event.AbstractEvent;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.io.disk.iomanager.IOManager;
import org.apache.flink.runtime.io.network.api.EndOfPartitionEvent;
import org.apache.flink.runtime.io.network.api.serialization.RecordDeserializer;
import org.apache.flink.runtime.io.network.api.serialization.RecordDeserializer.DeserializationResult;
import org.apache.flink.runtime.io.network.api.serialization.SpillingAdaptiveSpanningRecordDeserializer;
import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.runtime.io.network.partition.consumer.BufferOrEvent;
import org.apache.flink.runtime.io.network.partition.consumer.InputGate;
import org.apache.flink.runtime.metrics.groups.TaskIOMetricGroup;
import org.apache.flink.runtime.plugable.DeserializationDelegate;
import org.apache.flink.runtime.plugable.NonReusingDeserializationDelegate;
import org.apache.flink.runtime.rpc.RpcService;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.operators.OneInputStreamOperator;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.io.replication.BetterBiasAlgorithm;
import org.apache.flink.streaming.runtime.io.replication.BiasAlgorithm;
import org.apache.flink.streaming.runtime.io.replication.BiasAlgorithmMultithreaded;
import org.apache.flink.streaming.runtime.io.replication.Chainable;
import org.apache.flink.streaming.runtime.io.replication.Deduplication;
import org.apache.flink.streaming.runtime.io.replication.KafkaOrderBroadcaster;
import org.apache.flink.streaming.runtime.io.replication.KafkaReplication;
import org.apache.flink.streaming.runtime.io.replication.LeaderBasedReplication;
import org.apache.flink.streaming.runtime.io.replication.LiveRobinAlgorithm;
import org.apache.flink.streaming.runtime.io.replication.LogicalChannelMapper;
import org.apache.flink.streaming.runtime.io.replication.OneInputStreamOperatorAdapter;
import org.apache.flink.streaming.runtime.io.replication.Utils;
import org.apache.flink.streaming.runtime.metrics.WatermarkGauge;
import org.apache.flink.streaming.runtime.streamrecord.StreamElement;
import org.apache.flink.streaming.runtime.streamrecord.StreamElementSerializer;
import org.apache.flink.streaming.runtime.streamstatus.StatusWatermarkValve;
import org.apache.flink.streaming.runtime.streamstatus.StreamStatus;
import org.apache.flink.streaming.runtime.streamstatus.StreamStatusMaintainer;
import org.apache.flink.streaming.runtime.tasks.StreamTask;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Input reader for {@link org.apache.flink.streaming.runtime.tasks.OneInputStreamTask}.
 *
 * <p>This internally uses a {@link StatusWatermarkValve} to keep track of {@link Watermark} and
 * {@link StreamStatus} events, and forwards them to event subscribers once the
 * {@link StatusWatermarkValve} determines the {@link Watermark} from all inputs has advanced, or
 * that a {@link StreamStatus} needs to be propagated downstream to denote a status change.
 *
 * <p>Forwarding elements, watermarks, or status status elements must be protected by synchronizing
 * on the given lock object. This ensures that we don't call methods on a
 * {@link OneInputStreamOperator} concurrently with the timer callback or other things.
 *
 * @param <IN> The type of the record that can be read with this record reader.
 */
@Internal
public class StreamInputProcessor<IN> {

	private static final Logger LOG = LoggerFactory.getLogger(StreamInputProcessor.class);

	private final RecordDeserializer<DeserializationDelegate<StreamElement>>[] recordDeserializers;

	private final Chainable first;
	private final StreamTask<?, ?> checkpointedTask;

	private RecordDeserializer<DeserializationDelegate<StreamElement>> currentRecordDeserializer;

	private final DeserializationDelegate<StreamElement> deserializationDelegate;

	private final CheckpointBarrierHandler barrierHandler;

	/**
	 * The channel from which a buffer came, tracked so that we can appropriately map
	 * the watermarks and watermark statuses to channel indexes of the valve.
	 */
	private int currentChannel = -1;

	private final OneInputStreamOperator<IN, ?> streamOperator;

	// ---------------- Metrics ------------------

	private boolean isFinished;

	private LeaderBasedReplication inputOrdering;
	private CuratorFramework f;

	@SuppressWarnings("unchecked")
	public StreamInputProcessor(
		InputGate[] inputGates,
		TypeSerializer<IN> inputSerializer,
		StreamTask<?, ?> checkpointedTask,
		CheckpointingMode checkpointMode,
		Object checkpointLock,
		IOManager ioManager,
		Configuration taskManagerConfig,
		StreamStatusMaintainer streamStatusMaintainer,
		OneInputStreamOperator<IN, ?> streamOperator,
		TaskIOMetricGroup metrics,
		WatermarkGauge watermarkGauge,
		RpcService rpcService,
		ExecutionAttemptID executionAttempt,
		String replicaGroup,
		ExecutionConfig executionConfig
	) throws Exception {
		this.checkpointedTask = checkpointedTask;

		checkNotNull(checkpointLock);

		InputGate inputGate = InputGateUtil.createInputGate(inputGates);

		this.barrierHandler = InputProcessorUtil.createCheckpointBarrierHandler(
			checkpointedTask, checkpointMode, ioManager, inputGate, taskManagerConfig);

		StreamElementSerializer<IN> ser = new StreamElementSerializer<>(inputSerializer);
		this.deserializationDelegate = new NonReusingDeserializationDelegate<>(ser);

		// Initialize one deserializer per input channel
		this.recordDeserializers = new SpillingAdaptiveSpanningRecordDeserializer[inputGate.getNumberOfInputChannels()];

		for (int i = 0; i < recordDeserializers.length; i++) {
			recordDeserializers[i] = new SpillingAdaptiveSpanningRecordDeserializer<>(
				ioManager.getSpillingDirectoriesPaths());
		}

		this.streamOperator = checkNotNull(streamOperator);

		metrics.gauge("checkpointAlignmentTime", barrierHandler::getAlignmentDurationNanos);

		this.first = buildProcessingStack(
			executionConfig,
			inputGate.getUpstreamReplicationFactor(),
			watermarkGauge,
			streamStatusMaintainer,
			checkpointLock,
			rpcService,
			replicaGroup,
			executionAttempt,
			metrics
		);
	}

	public Chainable buildProcessingStack(
		ExecutionConfig executionConfig,
		int[] upstreamReplicationFactor,
		WatermarkGauge gauge,
		StreamStatusMaintainer maintainer,
		Object checkpointLock,
		RpcService rpcService,
		String replicaGroup,
		ExecutionAttemptID executionAttempt,
		TaskIOMetricGroup metrics
	) throws Exception {

		int numLogicalChannels = Utils.numLogicalChannels(upstreamReplicationFactor);

		Chainable mapper = new LogicalChannelMapper(upstreamReplicationFactor);
		Chainable dedup = new Deduplication(numLogicalChannels);
		Chainable adapter = createOneInputStreamAdapter(gauge, maintainer, checkpointLock, numLogicalChannels);
		Chainable outTSUpdater = new OutTsUpdater(checkpointedTask);

		Chainable merger;

		LOG.info("{} Using algorithm for ordering {}", Thread.currentThread().getName(), executionConfig.getOrderingAlgorithm());
		switch (executionConfig.getOrderingAlgorithm()) {
			case BIAS:
				merger = new BiasAlgorithm(numLogicalChannels);
				break;
			case BIAS_THREADED:
				merger = new BiasAlgorithmMultithreaded(numLogicalChannels);
				break;
			case BETTER_BIAS:
				merger = new BetterBiasAlgorithm(numLogicalChannels);
				break;
			case LIVE_ROBIN:
				merger = new LiveRobinAlgorithm(numLogicalChannels);
				break;
			case NO_ORDERING:
				merger = new NoOrder();
				break;
			case LEADER_KAFKA:
				String topic = replicaGroup;
				int kafkaBatchSize = executionConfig.getKafkaBatchSize();
				String kafkaServer = executionConfig.getKafkaServer();

				KafkaOrderBroadcaster broadcaster = new KafkaOrderBroadcaster(topic, kafkaServer);
				f = CuratorFrameworkFactory.newClient(executionConfig.getZkServer(), 
					100, // session timeout
					100, // connection timeout
					new ExponentialBackoffRetry(100, 0));
				f.start();

				merger = new KafkaReplication(numLogicalChannels, broadcaster, kafkaBatchSize, executionConfig.getKafkaTimeout(), topic, f, kafkaServer, metrics);

				break;
			default:
				throw new RuntimeException("Unsupported algorithm " + executionConfig.getOrderingAlgorithm());
		}

		mapper.setNext(dedup)
			.setNext(merger)
			.setNext(outTSUpdater)
			.setNext(adapter);

		return mapper;
	}

	static class NoOrder extends Chainable {
		@Override
		public void accept(StreamElement element, int channel) throws Exception {
			if (this.hasNext()) {
				this.getNext().accept(element, channel);
			}
		}
	}

	public static class OutTsUpdater extends Chainable {

		private final StreamTask task;
		Map<Integer, Long> sentTs;

		OutTsUpdater(StreamTask task) {
			this.task = task;
			sentTs = new HashMap<>();
		}


		@Override
		public void accept(StreamElement element, int channel) throws Exception {

			if (!element.isEndOfEpochMarker()) {
				long ts = element.getCurrentTs();
				sentTs.put(channel, ts);
				long max = Collections.max(sentTs.values());
				task.setOutTs(max);
			}

			if (this.hasNext()) {
				this.getNext().accept(element, channel);
			}
		}
	}

	private OneInputStreamOperatorAdapter createOneInputStreamAdapter(
		WatermarkGauge gauge,
		StreamStatusMaintainer maintainer,
		Object checkpointLock,
		int numLogicalChannels
	) {
		return new OneInputStreamOperatorAdapter(
			this.streamOperator,
			gauge,
			maintainer,
			numLogicalChannels,
			checkpointLock
		);
	}

	public boolean processInput() throws Exception {

		if (isFinished) {
			return false;
		}

		// do this until we processed on full record or reached the end
		while (true) {

			if (currentRecordDeserializer != null) {
				DeserializationResult result = currentRecordDeserializer.getNextRecord(deserializationDelegate);

				if (result.isBufferConsumed()) {
					currentRecordDeserializer.getCurrentBuffer().recycleBuffer();
					currentRecordDeserializer = null;
				}

				if (result.isFullRecord()) {
					StreamElement element = deserializationDelegate.getInstance();
					boolean isRecord = element.isRecord();

					this.first.accept(element, currentChannel);

					if (isRecord) {
						return true;
					} else {
						continue;
					}
				}
			}

			final BufferOrEvent bufferOrEvent = barrierHandler.getNextNonBlocked();
			if (bufferOrEvent != null) {
				if (bufferOrEvent.isBuffer()) {
					currentChannel = bufferOrEvent.getChannelIndex();
					currentRecordDeserializer = recordDeserializers[currentChannel];
					currentRecordDeserializer.setNextBuffer(bufferOrEvent.getBuffer());
				} else {
					// Event received
					final AbstractEvent event = bufferOrEvent.getEvent();
					if (event.getClass() != EndOfPartitionEvent.class) {
						throw new IOException("Unexpected event: " + event);
					}
				}
			} else {
				isFinished = true;
				if (!barrierHandler.isEmpty()) {
					throw new IllegalStateException("Trailing data in checkpoint barrier handler.");
				}
				this.first.endOfStream();
				return false;
			}
		}
	}

	public void cleanup() throws Exception {
		// clear the buffers first. this part should not ever fail
		for (RecordDeserializer<?> deserializer : recordDeserializers) {
			Buffer buffer = deserializer.getCurrentBuffer();
			if (buffer != null && !buffer.isRecycled()) {
				buffer.recycleBuffer();
			}
			deserializer.clear();
		}

		if (f != null) {
			f.close();
		}

		// cleanup the barrier handler resources
		barrierHandler.cleanup();
		this.first.closeAll();
	}

	public void triggerAcceptInputOrdering(List<Integer> newBatch) throws Exception {
		if (this.inputOrdering != null) {
			this.inputOrdering.acceptOrdering(newBatch);
		} else {
			throw new RuntimeException("Current configuration does not support broadcasting of input ordering!");
		}

	}
}
