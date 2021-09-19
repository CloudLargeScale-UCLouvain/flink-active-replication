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
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.operators.TwoInputStreamOperator;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.io.replication.BetterBiasAlgorithm;
import org.apache.flink.streaming.runtime.io.replication.BiasAlgorithm;
import org.apache.flink.streaming.runtime.io.replication.BiasAlgorithmMultithreaded;
import org.apache.flink.streaming.runtime.io.replication.Chainable;
import org.apache.flink.streaming.runtime.io.replication.Deduplication;
import org.apache.flink.streaming.runtime.io.replication.KafkaOrderBroadcaster;
import org.apache.flink.streaming.runtime.io.replication.KafkaReplication;
import org.apache.flink.streaming.runtime.io.replication.LiveRobinAlgorithm;
import org.apache.flink.streaming.runtime.io.replication.LogicalChannelMapper;
import org.apache.flink.streaming.runtime.io.replication.TwoInputStreamOperatorAdapter;
import org.apache.flink.streaming.runtime.io.replication.Utils;
import org.apache.flink.streaming.runtime.metrics.WatermarkGauge;
import org.apache.flink.streaming.runtime.streamrecord.StreamElement;
import org.apache.flink.streaming.runtime.streamrecord.StreamElementSerializer;
import org.apache.flink.streaming.runtime.streamstatus.StatusWatermarkValve;
import org.apache.flink.streaming.runtime.streamstatus.StreamStatus;
import org.apache.flink.streaming.runtime.streamstatus.StreamStatusMaintainer;
import org.apache.flink.streaming.runtime.tasks.TwoInputStreamTask;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.io.IOException;
import java.util.Collection;

/**
 * Input reader for {@link org.apache.flink.streaming.runtime.tasks.TwoInputStreamTask}.
 *
 * <p>This internally uses a {@link StatusWatermarkValve} to keep track of {@link Watermark} and
 * {@link StreamStatus} events, and forwards watermarks to event subscribers once the
 * {@link StatusWatermarkValve} determines the watermarks from all inputs has advanced, or changes
 * the task's {@link StreamStatus} once status change is toggled.
 *
 * <p>Forwarding elements, watermarks, or status status elements must be protected by synchronizing
 * on the given lock object. This ensures that we don't call methods on a
 * {@link TwoInputStreamOperator} concurrently with the timer callback or other things.
 *
 * @param <IN1> The type of the records that arrive on the first input
 * @param <IN2> The type of the records that arrive on the second input
 */
@Internal
public class StreamTwoInputProcessor<IN1, IN2> {

	private final RecordDeserializer<DeserializationDelegate<StreamElement>>[] recordDeserializers;

	private final DeserializationDelegate<StreamElement> deserializationDelegate1;
	private final DeserializationDelegate<StreamElement> deserializationDelegate2;
	private final TwoInputStreamTask<IN1, IN2, ?> checkpointedTask;
	private final TwoInputStreamOperator<IN1, IN2, ?> streamOperator;

	private RecordDeserializer<DeserializationDelegate<StreamElement>> currentRecordDeserializer;

	private final CheckpointBarrierHandler barrierHandler;

	private int currentChannel = -1;

	private final int numInputChannels1;

	private boolean isFinished;

	private final Chainable first;

	private CuratorFramework f;

	@SuppressWarnings("unchecked")
	public StreamTwoInputProcessor(
		Collection<InputGate> inputGates1,
		Collection<InputGate> inputGates2,
		TypeSerializer<IN1> inputSerializer1,
		TypeSerializer<IN2> inputSerializer2,
		TwoInputStreamTask<IN1, IN2, ?> checkpointedTask,
		CheckpointingMode checkpointMode,
		Object lock,
		IOManager ioManager,
		Configuration taskManagerConfig,
		StreamStatusMaintainer streamStatusMaintainer,
		TwoInputStreamOperator<IN1, IN2, ?> streamOperator,
		TaskIOMetricGroup metrics,
		WatermarkGauge input1WatermarkGauge,
		WatermarkGauge input2WatermarkGauge,
		ExecutionConfig executionConfig,
		String replicaGroup
	) throws IOException {

		this.checkpointedTask = checkpointedTask;
		this.streamOperator = streamOperator;

		final InputGate inputGate = InputGateUtil.createInputGate(inputGates1, inputGates2);

		this.barrierHandler = InputProcessorUtil.createCheckpointBarrierHandler(
			checkpointedTask, checkpointMode, ioManager, inputGate, taskManagerConfig);

		StreamElementSerializer<IN1> ser1 = new StreamElementSerializer<>(inputSerializer1);
		this.deserializationDelegate1 = new NonReusingDeserializationDelegate<>(ser1);

		StreamElementSerializer<IN2> ser2 = new StreamElementSerializer<>(inputSerializer2);
		this.deserializationDelegate2 = new NonReusingDeserializationDelegate<>(ser2);

		// Initialize one deserializer per input channel
		this.recordDeserializers = new SpillingAdaptiveSpanningRecordDeserializer[inputGate.getNumberOfInputChannels()];

		for (int i = 0; i < recordDeserializers.length; i++) {
			recordDeserializers[i] = new SpillingAdaptiveSpanningRecordDeserializer<>(
				ioManager.getSpillingDirectoriesPaths());
		}

		int numInputChannels1 = 0;
		for (InputGate gate: inputGates1) {
			numInputChannels1 += gate.getNumberOfInputChannels();
		}

		this.numInputChannels1 = numInputChannels1;

		metrics.gauge("checkpointAlignmentTime", barrierHandler::getAlignmentDurationNanos);

		int numChannels = Utils.numLogicalChannels(inputGate.getUpstreamReplicationFactor());

		int[] upstreamReplicationFactor = inputGate.getUpstreamReplicationFactor();

		// build up processing chain
		this.first = buildProcessingStack(
			inputGate,
			inputGates1,
			inputGates2,
			input1WatermarkGauge,
			input2WatermarkGauge,
			executionConfig,
			upstreamReplicationFactor,
			streamStatusMaintainer,
			lock,
			replicaGroup,
			metrics
		);
	}

	public Chainable buildProcessingStack(
		InputGate combinedGate,
		Collection<InputGate> gate1,
		Collection<InputGate> gate2,
		WatermarkGauge gauge1,
		WatermarkGauge gauge2,
		ExecutionConfig executionConfig,
		int[] upstreamReplicationFactor,
		StreamStatusMaintainer maintainer,
		Object checkpointLock,
		String replicaGroup,
		TaskIOMetricGroup metrics
	) {

		int numLogicalChannels = Utils.numLogicalChannels(upstreamReplicationFactor);

		Chainable mapper = new LogicalChannelMapper(upstreamReplicationFactor);
		Chainable dedup = new Deduplication(numLogicalChannels);
		Chainable adapter = createTwoInputStreamAdapter(combinedGate, gate1, gate2, gauge1, gauge2, maintainer, checkpointLock);
		Chainable outTSUpdater = new StreamInputProcessor.OutTsUpdater(checkpointedTask);

		Chainable merger;

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
				merger = new StreamInputProcessor.NoOrder();
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

	public Chainable createTwoInputStreamAdapter(
		InputGate combinedGate,
		Collection<InputGate> inputGate1,
		Collection<InputGate> inputGate2,
		WatermarkGauge gauge1,
		WatermarkGauge gauge2,
		StreamStatusMaintainer maintainer,
		Object lock
	) {
		return new TwoInputStreamOperatorAdapter<>(
			this.streamOperator,
			combinedGate,
			inputGate1,
			inputGate2,
			gauge1,
			gauge2,
			maintainer,
			lock
		);

	}

	public boolean processInput() throws Exception {
		if (isFinished) {
			return false;
		}

		while (true) {
			if (currentRecordDeserializer != null) {
				DeserializationResult result;
				boolean isFirstInput = currentChannel < numInputChannels1;

				if (isFirstInput) {
					result = currentRecordDeserializer.getNextRecord(deserializationDelegate1);
				} else {
					result = currentRecordDeserializer.getNextRecord(deserializationDelegate2);
				}

				if (result.isBufferConsumed()) {
					currentRecordDeserializer.getCurrentBuffer().recycleBuffer();
					currentRecordDeserializer = null;
				}

				if (result.isFullRecord()) {

					StreamElement element = (isFirstInput)
						? deserializationDelegate1.getInstance()
						: deserializationDelegate2.getInstance();

					this.first.accept(element, currentChannel);

					if (element.isRecord()) {
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

		// cleanup the barrier handler resources
		barrierHandler.cleanup();
	}
}
