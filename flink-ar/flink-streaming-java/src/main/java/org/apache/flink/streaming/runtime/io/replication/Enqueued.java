package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.streaming.runtime.streamrecord.StreamElement;

public class Enqueued {

	public StreamElement value;
	public final long timestamp;
	public final int channel;

	public Enqueued(long timestamp, int channel, StreamElement value) {
		this.channel = channel;
		this.timestamp = timestamp;
		this.value = value;
	}
}
