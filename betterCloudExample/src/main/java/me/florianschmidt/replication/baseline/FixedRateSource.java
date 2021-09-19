package me.florianschmidt.replication.baseline;

import org.apache.flink.streaming.api.functions.source.RichSourceFunction;

public abstract class FixedRateSource<T> extends RichSourceFunction<T> {

	public static final double NANOS_IN_SEC = 1000.0 * 1000.0 * 1000.0;

	private volatile boolean isRunning = true;

	private long pauseNanos;
	private long emitted;
	protected double targetRate;

	public FixedRateSource(long targetRate) {
		this.targetRate = targetRate;
		this.pauseNanos = (long) (NANOS_IN_SEC / (double) targetRate);
		System.out.println("Initialized with pause time " + pauseNanos);
	}

	@Override
	public void run(SourceContext<T> ctx) throws Exception {

		long lastUpdate = System.nanoTime();

		while (isRunning) {

			busyWait(pauseNanos);
			synchronized (ctx.getCheckpointLock()) {
				emitted++;
				doCollect(ctx);
			}


			if (emitted % (targetRate * 2) == 0) {
				long now = System.nanoTime();
				long nanoDuration = now - lastUpdate;
				double ratio = (targetRate / NANOS_IN_SEC) / ((double) emitted / nanoDuration);

				this.pauseNanos = (int) Math.ceil((double) this.pauseNanos / ratio);
				System.out.println("Updated pause to " + pauseNanos);
				// reset
				emitted = 0;
				lastUpdate = now;
			}
		}
	}

	private void busyWait(long pauseNanos) {
		long start = System.nanoTime();
		long end;
		do {
			end = System.nanoTime();
		} while (start + pauseNanos >= end);
	}

	@Override
	public void cancel() {
		this.isRunning = false;
	}

	public abstract void doCollect(SourceContext<T> ctx) throws Exception;
}
