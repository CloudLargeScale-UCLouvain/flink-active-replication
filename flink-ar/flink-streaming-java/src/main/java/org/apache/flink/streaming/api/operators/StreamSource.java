/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.api.operators;

import org.apache.flink.annotation.Internal;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.MetricOptions;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.streamrecord.EndOfEpochMarker;
import org.apache.flink.streaming.runtime.streamrecord.LatencyMarker;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.streaming.runtime.streamstatus.StreamStatusMaintainer;
import org.apache.flink.streaming.runtime.tasks.ProcessingTimeCallback;
import org.apache.flink.streaming.runtime.tasks.ProcessingTimeService;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
/**
 * {@link StreamOperator} for streaming sources.
 *
 * @param <OUT> Type of the output elements
 * @param <SRC> Type of the source function of this stream source operator
 */
@Internal
public class StreamSource<OUT, SRC extends SourceFunction<OUT>>
		extends AbstractUdfStreamOperator<OUT, SRC> implements StreamOperator<OUT> {

	private static final long serialVersionUID = 1L;

	private transient SourceFunction.SourceContext<OUT> ctx;

	private transient volatile boolean canceledOrStopped = false;

	public StreamSource(SRC sourceFunction) {
		super(sourceFunction);

		this.chainingStrategy = ChainingStrategy.HEAD;
	}

	public void run(final Object lockingObject, final StreamStatusMaintainer streamStatusMaintainer) throws Exception {
		run(lockingObject, streamStatusMaintainer, output);
	}

	public void run(final Object lockingObject,
			final StreamStatusMaintainer streamStatusMaintainer,
			final Output<StreamRecord<OUT>> collector) throws Exception {

		final TimeCharacteristic timeCharacteristic = getOperatorConfig().getTimeCharacteristic();

		final Configuration configuration = this.getContainingTask().getEnvironment().getTaskManagerInfo().getConfiguration();
		final long latencyTrackingInterval = getExecutionConfig().isLatencyTrackingConfigured()
			? getExecutionConfig().getLatencyTrackingInterval()
			: configuration.getLong(MetricOptions.LATENCY_INTERVAL);

		LatencyMarksEmitter<OUT> latencyEmitter = null;
		if (latencyTrackingInterval > 0) {
			latencyEmitter = new LatencyMarksEmitter<>(
				getProcessingTimeService(),
				collector,
				latencyTrackingInterval,
				this.getOperatorID(),
				getRuntimeContext().getIndexOfThisSubtask());
		}

		IdleMarksEmitter<OUT> idleMarksEmitter = null;
		if (getExecutionConfig().isIdleMarksEnabled()) {
			LOG.info(String.format("Starting bounded delay emitter with interval %dms", getExecutionConfig().getIdleMarksInterval()));
			idleMarksEmitter = new IdleMarksEmitter<>(
				collector,
				getExecutionConfig().getIdleMarksInterval(),
				lockingObject
			);
		}
		
		LiveRobinKafkaHeartbeatEmitter<OUT> liveRobinKafkaHeartbeatEmitter = null;
		LiveRobinTimerHeartbeatEmitter<OUT> liveRobinTimerHeartbeatEmitter = null;
		if (getExecutionConfig().isLiveRobinEnabled()) {
			if (getExecutionConfig().getKafkaServer() != "") {
				LOG.info(String.format("Starting live robin Kafka heartbeat emitter on Kafka server %s ", getExecutionConfig().getKafkaServer()));
				liveRobinKafkaHeartbeatEmitter = new LiveRobinKafkaHeartbeatEmitter<>(
					collector,
					getExecutionConfig().getKafkaServer(),
					"liverobin-heartbeats",
					lockingObject);
			} else {
				LOG.info(String.format("Starting live robin heartbeat emitter with interval %dms", 100));
				liveRobinTimerHeartbeatEmitter = new LiveRobinTimerHeartbeatEmitter<>(
					collector,
					100,
					lockingObject
				);
			}
		}


		final long watermarkInterval = getRuntimeContext().getExecutionConfig().getAutoWatermarkInterval();

		this.ctx = StreamSourceContexts.getSourceContext(
			timeCharacteristic,
			getProcessingTimeService(),
			lockingObject,
			streamStatusMaintainer,
			collector,
			watermarkInterval,
			-1);

		try {
			userFunction.run(ctx);

			// if we get here, then the user function either exited after being done (finite source)
			// or the function was canceled or stopped. For the finite source case, we should emit
			// a final watermark that indicates that we reached the end of event-time
			if (!isCanceledOrStopped()) {
				ctx.emitWatermark(Watermark.MAX_WATERMARK);
			}
		} finally {
			// make sure that the context is closed in any case
			ctx.close();
			if (latencyEmitter != null) {
				latencyEmitter.close();
			}
			if (idleMarksEmitter != null) {
				idleMarksEmitter.close();
			}
			if (liveRobinTimerHeartbeatEmitter != null) {
				liveRobinTimerHeartbeatEmitter.close();
			}

			if (liveRobinKafkaHeartbeatEmitter != null) {
				liveRobinKafkaHeartbeatEmitter.close();
			}
		}
	}

	public void cancel() {
		// important: marking the source as stopped has to happen before the function is stopped.
		// the flag that tracks this status is volatile, so the memory model also guarantees
		// the happens-before relationship
		markCanceledOrStopped();
		userFunction.cancel();

		// the context may not be initialized if the source was never running.
		if (ctx != null) {
			ctx.close();
		}
	}

	/**
	 * Marks this source as canceled or stopped.
	 *
	 * <p>This indicates that any exit of the {@link #run(Object, StreamStatusMaintainer, Output)} method
	 * cannot be interpreted as the result of a finite source.
	 */
	protected void markCanceledOrStopped() {
		this.canceledOrStopped = true;
	}

	/**
	 * Checks whether the source has been canceled or stopped.
	 * @return True, if the source is canceled or stopped, false is not.
	 */
	protected boolean isCanceledOrStopped() {
		return canceledOrStopped;
	}

	private static class LiveRobinKafkaHeartbeatEmitter<OUT> {
		private final KafkaConsumer<String, String> consumer;
		Thread processor;
		public LiveRobinKafkaHeartbeatEmitter(final Output<StreamRecord<OUT>> output,
		final String kafkaServer,
		final String topic,
		final Object lock
		) {
			Properties props = new Properties();
			String uuid = UUID.randomUUID().toString();

			props.put("bootstrap.servers", kafkaServer);
			props.put("group.id", uuid);
			props.put("enable.auto.commit", "true");
			props.put("auto.commit.interval.ms", "1000");
			props.put("auto.offset.reset", "earliest");
			props.put("key.deserializer", StringDeserializer.class.getName());
			props.put("value.deserializer", StringDeserializer.class.getName());
			props.put("batchSize", 500);
			props.put("max.poll.records", 1);
			
			ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(null);
			consumer = new KafkaConsumer<String, String>(props);
			consumer.subscribe(Arrays.asList(topic));

			processor = new Thread("heartbeat-processor-" + uuid) {
				public void run() {
					boolean running = true;
					while (running) {
						ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
						
						for (ConsumerRecord<String, String> r : records) {
							LOG.trace("Processing {} ", r.value());
							synchronized (lock) {
								EndOfEpochMarker marker = new EndOfEpochMarker();
		
								marker.setPreviousTimestamp(0);
								marker.setCurrentTimestamp(0);
								marker.setEpoch(new Long(r.value()));
								output.emitBoundedDelayMarker(marker);					
							}
						}
					}
				}
			};
			processor.setContextClassLoader(originClassLoader);
			Thread.currentThread().setContextClassLoader(originClassLoader);			
			processor.start();
		}

		public void close() {
			this.processor.interrupt();
		}		
	}

	private static class LiveRobinTimerHeartbeatEmitter<OUT> {

		private final ScheduledFuture<?> liveRobinHeartbeatTimer;
		private long currentEpoch = 0;

		public LiveRobinTimerHeartbeatEmitter(final Output<StreamRecord<OUT>> output,
		final long boundedIdlenessInterval,
		final Object lock
		) {
			this.liveRobinHeartbeatTimer = Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
				try {
					synchronized (lock) {
						EndOfEpochMarker marker = new EndOfEpochMarker();

						marker.setPreviousTimestamp(0);
						marker.setCurrentTimestamp(0);
						marker.setEpoch(currentEpoch);
						output.emitBoundedDelayMarker(marker);
						currentEpoch++;
					}
				} catch (Throwable t) {
					LOG.warn("Error while emitting bounded delay marker. ", t);
				}
			}, 0L, boundedIdlenessInterval, TimeUnit.MILLISECONDS);
		}

		public void close() {
			this.liveRobinHeartbeatTimer.cancel(true);
		}			
		
	}

	private static class IdleMarksEmitter<OUT> {

		private final ScheduledFuture<?> idleMarksTimer;

		public IdleMarksEmitter(
			final Output<StreamRecord<OUT>> output,
			final long boundedIdlenessInterval,
			final Object lock
		) {

			this.idleMarksTimer = Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
				try {
					synchronized (lock) {
						EndOfEpochMarker marker = new EndOfEpochMarker();

						marker.setPreviousTimestamp(Long.MAX_VALUE);
						marker.setCurrentTimestamp(Long.MAX_VALUE);

						output.emitBoundedDelayMarker(marker);
					}
				} catch (Throwable t) {
					LOG.warn("Error while emitting bounded delay marker. ", t);
				}
			}, 0L, boundedIdlenessInterval, TimeUnit.MILLISECONDS);
		}

		public void close() {
			this.idleMarksTimer.cancel(true);
		}
	}

	private static class LatencyMarksEmitter<OUT> {
		private final ScheduledFuture<?> latencyMarkTimer;

		public LatencyMarksEmitter(
				final ProcessingTimeService processingTimeService,
				final Output<StreamRecord<OUT>> output,
				long latencyTrackingInterval,
				final OperatorID operatorId,
				final int subtaskIndex) {

			latencyMarkTimer = processingTimeService.scheduleAtFixedRate(
				new ProcessingTimeCallback() {
					@Override
					public void onProcessingTime(long timestamp) throws Exception {
						try {
							// ProcessingTimeService callbacks are executed under the checkpointing lock
							output.emitLatencyMarker(new LatencyMarker(processingTimeService.getCurrentProcessingTime(), operatorId, subtaskIndex));
						} catch (Throwable t) {
							// we catch the Throwables here so that we don't trigger the processing
							// timer services async exception handler
							LOG.warn("Error while emitting latency marker.", t);
						}
					}
				},
				0L,
				latencyTrackingInterval);
		}

		public void close() {
			latencyMarkTimer.cancel(true);
		}
	}
}
