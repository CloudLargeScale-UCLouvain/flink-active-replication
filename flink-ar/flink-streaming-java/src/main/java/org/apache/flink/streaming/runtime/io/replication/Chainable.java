package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.streaming.runtime.streamrecord.StreamElement;

public abstract class Chainable implements AutoCloseable {

	private Chainable next;

	public boolean hasNext() {
		return this.next != null;
	}

	public Chainable getNext() {
		return this.next;
	}

	public Chainable setNext(Chainable next) {
		this.next = next;
		return next;
	}

	public abstract void accept(StreamElement element, int channel) throws Exception;

	public void endOfStream() throws Exception {

	}

	public final void closeAll() throws Exception {
		if (this.hasNext()) {
			this.getNext().closeAll();
		}
		this.close();
	}

	@Override
	public void close() throws Exception{
	}
}
