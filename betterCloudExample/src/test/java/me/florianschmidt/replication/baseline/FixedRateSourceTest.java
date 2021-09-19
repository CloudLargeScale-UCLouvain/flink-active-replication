package me.florianschmidt.replication.baseline;

import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.api.watermark.Watermark;

import org.junit.Test;

public class FixedRateSourceTest {

	private class MeasuringContext<T> implements SourceFunction.SourceContext<T> {

		private final int rate;
		private FixedRateSource s;
		private long count = 0;
		private long total = 0;
		private long before = System.nanoTime();

		private MeasuringContext(FixedRateSource s, int rate) {
			this.s = s;
			this.rate = rate;
		}

		@Override
		public void collect(T element) {
			count++;
			total++;

			if (total > (rate * 10)) {
				s.cancel();
			}

			if (count % rate == 0) {
				long after = System.nanoTime();
				System.out.println(String.format("%d elements in %d milliseconds", count, after - before));

				// reset
				count = 0;
				before = after;
			}

		}

		@Override
		public void collectWithTimestamp(T element, long timestamp) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void emitWatermark(Watermark mark) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void markAsTemporarilyIdle() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public Object getCheckpointLock() {
			return new Object();
		}

		@Override
		public void close() {

		}
	}

	@Test
	public void run() throws Exception {
		int targetRate = 1_000_000;
//		FixedRateSource s = new FixedRateSource(targetRate);
//		s.run(new MeasuringContext<>(s, targetRate));
	}
}