package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.SimpleCounter;
import org.apache.flink.runtime.io.network.partition.consumer.InputGate;
import org.apache.flink.runtime.metrics.groups.OperatorMetricGroup;
import org.apache.flink.streaming.api.operators.TwoInputStreamOperator;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.metrics.WatermarkGauge;
import org.apache.flink.streaming.runtime.streamrecord.StreamElement;
import org.apache.flink.streaming.runtime.streamstatus.StatusWatermarkValve;
import org.apache.flink.streaming.runtime.streamstatus.StreamStatus;
import org.apache.flink.streaming.runtime.streamstatus.StreamStatusMaintainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static org.apache.flink.util.Preconditions.checkNotNull;

public class TwoInputStreamOperatorAdapter<IN1, IN2> extends Chainable {

	private static final Logger LOG = LoggerFactory.getLogger(TwoInputStreamOperatorAdapter.class);

	private final TwoInputStreamOperator<IN1, IN2, ?> streamOperator;
	private final StreamStatusMaintainer streamStatusMaintainer;

	private final StatusWatermarkValve statusWatermarkValve1;
	private final StatusWatermarkValve statusWatermarkValve2;

	private final WatermarkGauge input1WatermarkGauge;
	private final WatermarkGauge input2WatermarkGauge;

	private final int numInputChannels1;
	private final int numInputChannels2;

	private final Object lock;

	private final Counter recordsIn;

	private StreamStatus firstStatus;
	private StreamStatus secondStatus;


	public TwoInputStreamOperatorAdapter(
		TwoInputStreamOperator<IN1, IN2, ?> streamOperator,
		InputGate combinedGate,
		Collection<InputGate> inputGates1,
		Collection<InputGate> inputGates2,
		WatermarkGauge input1WatermarkGauge,
		WatermarkGauge input2WatermarkGauge,
		StreamStatusMaintainer maintainer,
		Object lock
	) {

		// TODO: THIS DOES NOT RESPECT REPLICATION YET, FIX IT!
		this.numInputChannels1 = inputGates1.stream()
			.mapToInt(gate -> Utils.numLogicalChannels(gate.getUpstreamReplicationFactor()))
			.sum();

		this.numInputChannels2 = inputGates2.stream()
			.mapToInt(gate -> Utils.numLogicalChannels(gate.getUpstreamReplicationFactor()))
			.sum();

		// sanity check
		if (numInputChannels1 + numInputChannels2 != Utils.numLogicalChannels(combinedGate.getUpstreamReplicationFactor())) {
			throw new RuntimeException("We did something wrong in the calculation");
		}

		this.streamOperator = streamOperator;
		this.statusWatermarkValve1 =
			new StatusWatermarkValve(numInputChannels1, new ForwardingValveOutputHandler1(streamOperator, lock));
		this.statusWatermarkValve2 =
			new StatusWatermarkValve(numInputChannels2, new ForwardingValveOutputHandler2(streamOperator, lock));

		this.lock = lock;
		this.recordsIn = initializeCounter();

		this.input1WatermarkGauge = input1WatermarkGauge;
		this.input2WatermarkGauge = input2WatermarkGauge;

		this.firstStatus = StreamStatus.ACTIVE;
		this.secondStatus = StreamStatus.ACTIVE;

		this.streamStatusMaintainer = maintainer;
	}

	private Counter initializeCounter() {
		try {
			return ((OperatorMetricGroup) streamOperator.getMetricGroup())
				.getIOMetricGroup()
				.getNumRecordsInCounter();
		} catch (Exception e) {
			LOG.warn("An exception occurred during the metrics setup.", e);
			return new SimpleCounter();
		}
	}

	@Override
	public void accept(StreamElement element, int currentChannel) throws Exception {

		boolean isFirstInput = currentChannel < numInputChannels1;

		StatusWatermarkValve valve = (isFirstInput)
			? statusWatermarkValve1
			: statusWatermarkValve2;

		int chan = (isFirstInput)
			? currentChannel
			: currentChannel - numInputChannels1;

		if (element.isWatermark()) {
			valve.inputWatermark(element.asWatermark(), chan);
		} else if (element.isStreamStatus()) {
			valve.inputStreamStatus(element.asStreamStatus(), chan);
		} else if (element.isLatencyMarker()) {
			if (isFirstInput) {
				synchronized (lock) {
					streamOperator.processLatencyMarker1(element.asLatencyMarker());
				}
			} else {
				synchronized (lock) {
					streamOperator.processLatencyMarker2(element.asLatencyMarker());
				}
			}
		} else if (element.isEndOfEpochMarker()) {
			synchronized (lock) {
				streamOperator.processBoundedDelayMarker(element.asEndOfEpochMarker());
			}
		} else if (element.isRecord()) {

			recordsIn.inc();

			if (isFirstInput) {
				synchronized (lock) {
					streamOperator.setKeyContextElement1(element.asRecord());
					streamOperator.processElement1(element.asRecord());
				}
			} else {
				synchronized (lock) {
					streamOperator.setKeyContextElement2(element.asRecord());
					streamOperator.processElement2(element.asRecord());
				}
			}
		} else {
			throw new RuntimeException("Unsupported element type " + element);
		}
	}

	private class ForwardingValveOutputHandler1 implements StatusWatermarkValve.ValveOutputHandler {
		private final TwoInputStreamOperator<IN1, IN2, ?> operator;
		private final Object lock;

		private ForwardingValveOutputHandler1(final TwoInputStreamOperator<IN1, IN2, ?> operator, final Object lock) {
			this.operator = checkNotNull(operator);
			this.lock = checkNotNull(lock);
		}

		@Override
		public void handleWatermark(Watermark watermark) {
			try {
				synchronized (lock) {
					input1WatermarkGauge.setCurrentWatermark(watermark.getTimestamp());
					operator.processWatermark1(watermark);
				}
			} catch (Exception e) {
				throw new RuntimeException("Exception occurred while processing valve output watermark: ", e);
			}
		}

		@Override
		public void handleStreamStatus(StreamStatus streamStatus) {
			try {
				synchronized (lock) {
					firstStatus = streamStatus;

					// check if we need to toggle the task's stream status
					if (!streamStatus.equals(streamStatusMaintainer.getStreamStatus())) {
						if (streamStatus.isActive()) {
							// we're no longer idle if at least one input has become active
							streamStatusMaintainer.toggleStreamStatus(StreamStatus.ACTIVE);
						} else if (secondStatus.isIdle()) {
							// we're idle once both inputs are idle
							streamStatusMaintainer.toggleStreamStatus(StreamStatus.IDLE);
						}
					}
				}
			} catch (Exception e) {
				throw new RuntimeException("Exception occurred while processing valve output stream status: ", e);
			}
		}
	}

	private class ForwardingValveOutputHandler2 implements StatusWatermarkValve.ValveOutputHandler {
		private final TwoInputStreamOperator<IN1, IN2, ?> operator;
		private final Object lock;

		private ForwardingValveOutputHandler2(final TwoInputStreamOperator<IN1, IN2, ?> operator, final Object lock) {
			this.operator = checkNotNull(operator);
			this.lock = checkNotNull(lock);
		}

		@Override
		public void handleWatermark(Watermark watermark) {
			try {
				synchronized (lock) {
					input2WatermarkGauge.setCurrentWatermark(watermark.getTimestamp());
					operator.processWatermark2(watermark);
				}
			} catch (Exception e) {
				throw new RuntimeException("Exception occurred while processing valve output watermark: ", e);
			}
		}

		@Override
		public void handleStreamStatus(StreamStatus streamStatus) {
			try {
				synchronized (lock) {
					secondStatus = streamStatus;

					// check if we need to toggle the task's stream status
					if (!streamStatus.equals(streamStatusMaintainer.getStreamStatus())) {
						if (streamStatus.isActive()) {
							// we're no longer idle if at least one input has become active
							streamStatusMaintainer.toggleStreamStatus(StreamStatus.ACTIVE);
						} else if (firstStatus.isIdle()) {
							// we're idle once both inputs are idle
							streamStatusMaintainer.toggleStreamStatus(StreamStatus.IDLE);
						}
					}
				}
			} catch (Exception e) {
				throw new RuntimeException("Exception occurred while processing valve output stream status: ", e);
			}
		}
	}
}
