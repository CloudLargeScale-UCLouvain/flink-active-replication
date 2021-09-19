package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.runtime.streamrecord.StreamElement;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DeduplicationTest {

	@SuppressWarnings("unchecked")
	@Test
	public void accept() throws Exception {

		CollectingChainable c = new CollectingChainable();
		Deduplication d = new Deduplication(3);
		d.setNext(c);

		// only the first should be in the results
		d.accept(getElementWithDedup(0), 0);
		d.accept(getElementWithDedup(0), 0);
		d.accept(getElementWithDedup(0), 0);

		// only the first should be in the results
		d.accept(getElementWithDedup(2), 0);
		d.accept(getElementWithDedup(2), 0);
		d.accept(getElementWithDedup(2), 0);

		// none of those should be in the results
		d.accept(getElementWithDedup(1), 0);
		d.accept(getElementWithDedup(1), 0);
		d.accept(getElementWithDedup(1), 0);

		// all of those should be in the results
		d.accept(getElementWithDedup(1), 1);
		d.accept(getElementWithDedup(2), 1);
		d.accept(getElementWithDedup(3), 1);

		// only the first should be in the results
		d.accept(getElementWithDedup(3), 2);
		d.accept(getElementWithDedup(2), 2);
		d.accept(getElementWithDedup(1), 2);

		c.expect(
			Tuple2.of(0L, 0),
			Tuple2.of(2L, 0),

			Tuple2.of(1L, 1),
			Tuple2.of(2L, 1),
			Tuple2.of(3L, 1),

			Tuple2.of(3L, 2)
		);

	}

	private StreamElement getElementWithDedup(long ts) {
		StreamElement e = new StreamElement() {
		};
		e.setDeduplicationTimestamp(ts);
		return e;
	}

	private class CollectingChainable extends Chainable {

		private List<Tuple2<Long, Integer>> elements = new ArrayList<>();

		@Override
		public void accept(StreamElement element, int channel) throws Exception {
			this.elements.add(Tuple2.of(element.getDeduplicationTimestamp(), channel));
		}

		public void expect(Tuple2<Long, Integer>... expected) {
			Assert.assertEquals(Lists.newArrayList(expected), elements);
		}
	}
}
