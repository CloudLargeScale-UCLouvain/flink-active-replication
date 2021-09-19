package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.util.Preconditions;

import java.util.HashMap;
import java.util.Map;

class Statistics {

	private Map<Long, Map<Integer, Long>> count = new HashMap<>();
	private Map<Long, Map<Integer, Long>> deliveryCount = new HashMap<>();
	private Map<Long, Map<Integer, Long>> end = new HashMap<>();

	public void onEventReceived(long epoch, int channel) {
		this.count.computeIfAbsent(epoch, key -> new HashMap<>());

		if (this.count.get(epoch).get(channel) == null) {
			this.count.get(epoch).put(channel, 1L);
		} else {
			this.count.get(epoch).compute(channel, (key, value) -> value + 1);
		}
	}

	public void onEventDelivered(long epoch, int channel) {
		this.deliveryCount.computeIfAbsent(epoch, key -> new HashMap<>());

		if (this.deliveryCount.get(epoch).get(channel) == null) {
			this.deliveryCount.get(epoch).put(channel, 1L);
		} else {
			this.deliveryCount.get(epoch).compute(channel, (key, value) -> value + 1);
		}
	}

	public void onEpochEnded(long epoch, int channel, long endTs) {
		this.end.computeIfAbsent(epoch, key -> new HashMap<>());

		// sanity check
		if (this.end.get(epoch).get(channel) != null) {
			throw new RuntimeException("End of Epoch already observed");
		}

		this.end.get(epoch).put(channel, endTs);
	}

	public long elementsInEpoch(long epoch, int channel) {
		return count.get(epoch).get(channel);
	}

	public long epochDuration(long epoch, int channel) {
		long last = this.end.get(epoch).get(channel);
		long prev = this.end.get(epoch - 1).get(channel);

		return last - prev;
	}

	/*
	 * From "Efficient Atomic Broadcast Using Deterministic Merge", Aguilera et. al, 2000
	 * See Figure 5
	 *
	 * https://dl.acm.org/citation.cfm?id=343620
	 */
	public double bias(double p1, double p2, long t, int j, long L) {

		Preconditions.checkArgument((j == 1 || j == 2), "j must be 1 or 2. Actual %s", j);
		Preconditions.checkArgument(p1 >= p2, "p1 >= p2 must always be true");


		if (t <= L - 1 && j == 1) {
			double dividend = Math.log(p1) + Math.log(1 - Math.pow(1 - p2, (L - t))) - Math.log(1 - Math.pow((1 - p1), (L - t)));
			double divisor = Math.log(1 - p2);
			return dividend / divisor;
		} else {
			return Math.log(p2) / Math.log(1 - p2);
		}
	}

	public long relativeTs(long ts, long currentEpoch, int channel) {
		return ts - this.end.get(currentEpoch - 1).get(channel);
	}
}
