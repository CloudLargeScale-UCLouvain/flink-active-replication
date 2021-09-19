package me.florianschmidt.examples.ing;

import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.util.Iterator;

public class ThrottlingSource implements SourceFunction<SimpleTransaction> {

	private final long millis = 1000;

	private final Iterator<SimpleTransaction> iterator;
	private final int rate;

	private volatile boolean running = true;

	public ThrottlingSource(Iterator<SimpleTransaction> iterator, int rate) {
		this.iterator = iterator;
		this.rate = rate;
	}

	// TODO: Check this or even replace it with busy wait source
	@Override
	public void run(SourceContext<SimpleTransaction> ctx) throws Exception {
		while (running) {
			synchronized (ctx.getCheckpointLock()) {
				ctx.collect(iterator.next());
			}
			Thread.sleep(millis / rate);
		}
	}

	@Override
	public void cancel() {
		this.running = false;
	}
}
