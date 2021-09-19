package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.streaming.runtime.streamrecord.StreamElement;

public class LogicalChannelMapper extends Chainable {

	private final int[] upstreamReplicationFactor;

	public LogicalChannelMapper(int[] upstreamReplicationFactor) {
		this.upstreamReplicationFactor = upstreamReplicationFactor;
	}

	public void accept(StreamElement element, int actualChannel) throws Exception {
		int logicalChannel = Utils.logicalChannel(actualChannel, upstreamReplicationFactor);
		if (this.hasNext()) {
			this.getNext().accept(element, logicalChannel);
		}
	}
}
