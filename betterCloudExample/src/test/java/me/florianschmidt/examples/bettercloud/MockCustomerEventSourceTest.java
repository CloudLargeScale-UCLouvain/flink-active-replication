package me.florianschmidt.examples.bettercloud;

import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.api.watermark.Watermark;

import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

public class MockCustomerEventSourceTest {
	@Test
	public void testDoCollect() throws Exception {
		int rate = 10000;

		MockCustomerEventSource source = new MockCustomerEventSource(rate);
		source.open(null);
		source.run(new MockSourceContext(rate, source));
		// source.doCollect(new MockSourceContext());
	}

	private class MockSourceContext implements SourceFunction.SourceContext<CustomerEvent> {

		private final int rate;
		private final SourceFunction source;

		private List<CustomerEvent> collected = new LinkedList<>();

		private MockSourceContext(int rate, SourceFunction source) {
			this.rate = rate;
			this.source = source;
		}

		@Override
		public void collect(CustomerEvent element) {
			collected.add(element);
			if (collected.size() > 20 * rate) {
				this.source.cancel();

				int total = collected.size();

				long canBeShared = collected.parallelStream().filter(customerEvent -> customerEvent.payload.contains("lunch-menu")).count();
				long cantBeShared = total - canBeShared;

				long areShared = collected.parallelStream().filter(customerEvent -> customerEvent.payload.contains("public")).count();
				long arentShared = total - areShared;

				long alert = collected.parallelStream().filter(customerEvent -> customerEvent.payload.contains("finances") && customerEvent.payload.contains("public")).count();
				long noAlert = total - alert;

				System.out.printf("Total: %d, canBeShared: %d, cantBeShared: %d, areShared: %d, arentShared: %d, alert: %d, noAlert %d %n", total, canBeShared, cantBeShared, areShared, arentShared, alert, noAlert);
			}

		}

		@Override
		public void collectWithTimestamp(CustomerEvent element, long timestamp) {
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
			// do nothing
		}
	}
}