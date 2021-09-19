package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.streaming.runtime.streamrecord.StreamElement;

import java.util.Arrays;

public class Deduplication extends Chainable {

	private long[] latestDeduplicationTimestamp;

	public Deduplication(int numChannels) {
		latestDeduplicationTimestamp = new long[numChannels];
		Arrays.fill(latestDeduplicationTimestamp, -1);
	}

	@Override
	public void accept(StreamElement element, int channel) throws Exception {
		if (!this.isDuplicate(element, channel)) {
			if (this.hasNext()) {
				this.getNext().accept(element, channel);
			}
		}
	}

	private boolean isDuplicate(StreamElement elem, int channel) {

		long deduplicationTimestamp = elem.getDeduplicationTimestamp();

		if (deduplicationTimestamp == -1) {
			throw new RuntimeException("Deduplication timestamp was -1. Expected value >= 0");
		}

		boolean isDuplicate = deduplicationTimestamp <= this.latestDeduplicationTimestamp[channel];

		if (isDuplicate) {
			return true;
		} else {
			this.latestDeduplicationTimestamp[channel] = elem.getDeduplicationTimestamp();
			return false;
		}
	}
}
