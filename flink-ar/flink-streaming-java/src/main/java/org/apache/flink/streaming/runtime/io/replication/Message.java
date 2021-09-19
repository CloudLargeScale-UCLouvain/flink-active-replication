package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.streaming.runtime.streamrecord.StreamElement;

public class Message {
	private final StreamElement element;
	private final long elemPreviousTs;
	private final long elemCurrentTs;

	public Message(StreamElement element, long elemPreviousTs, long elemCurrentTs) {
		this.element = element;
		this.elemPreviousTs = elemPreviousTs;
		this.elemCurrentTs = elemCurrentTs;
	}

	public StreamElement getElement() {
		return element;
	}

	public long getElemPreviousTs() {
		return elemPreviousTs;
	}

	public long getElemCurrentTs() {
		return elemCurrentTs;
	}
}
