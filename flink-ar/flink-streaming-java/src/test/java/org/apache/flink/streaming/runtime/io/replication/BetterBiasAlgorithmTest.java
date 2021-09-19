package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.runtime.streamrecord.EndOfEpochMarker;
import org.apache.flink.streaming.runtime.streamrecord.StreamElement;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;

public class BetterBiasAlgorithmTest {

	@Test
	public void acceptSingleChannelNoEpoch() throws Exception {
		BetterBiasAlgorithm b = new BetterBiasAlgorithm(2);
		CollectionChainable collector = new CollectionChainable();
		b.setNext(collector);

		b.accept(createStreamElement(1, 0), 0);
		b.accept(createStreamElement(2, 1), 0);
		b.accept(createStreamElement(3, 2), 0);
		b.accept(createStreamElement(4, 3), 0);
		b.accept(createStreamElement(5, 4), 0);

		// list should be empty
		Assert.assertEquals(Lists.newArrayList(), collector.getElements());
	}

	@Test
	public void acceptTwoChannelsNoEpoch() throws Exception {
		BetterBiasAlgorithm b = new BetterBiasAlgorithm(2);
		CollectionChainable collector = new CollectionChainable();
		b.setNext(collector);

		StreamElement s0 = createStreamElement(1, 0);
		b.accept(s0, 0);
		StreamElement s1 = createStreamElement(1, 0);
		b.accept(s1, 1);

		StreamElement s2 = createStreamElement(2, 1);
		b.accept(s2, 0);

		StreamElement s3 = createStreamElement(2, 1);
		b.accept(s3, 1);

		// list should be empty
		Assert.assertEquals(Lists.newArrayList(
			Tuple2.of(s0, 0),
			Tuple2.of(s1, 1),
			Tuple2.of(s2, 0),
			Tuple2.of(s3, 1)
		), collector.getElements());
	}

	@Test
	public void acceptOneChannelEpochOnBothChannels() throws Exception {
		BetterBiasAlgorithm b = new BetterBiasAlgorithm(2);
		CollectionChainable collector = new CollectionChainable();
		b.setNext(collector);

		StreamElement s0 = createStreamElement(1, 0);
		b.accept(s0, 0);
		StreamElement s1 = createStreamElement(2, 1);
		b.accept(s1, 0);

		StreamElement e1 = createEndOfEpoch(0, 0);
		b.accept(e1, 0); // end of epoch with ts 0 and epoch 0 on channel 0
		StreamElement e2 = createEndOfEpoch(0, 0);
		b.accept(e2, 1); // end of epoch with ts 0 and epoch 0 on channel 1

		// list should be empty
		Assert.assertEquals(Lists.newArrayList(
			Tuple2.of(s0, 0),
			Tuple2.of(s1, 0),
			Tuple2.of(e1, 0),
			Tuple2.of(e2, 1)
		), collector.getElements());
	}

	@Test
	public void acceptTwoChannelsEpochOnBothChannels() throws Exception {
		BetterBiasAlgorithm b = new BetterBiasAlgorithm(2);
		CollectionChainable collector = new CollectionChainable();
		b.setNext(collector);

		StreamElement s_0 = createStreamElement(1, 0);
		b.accept(s_0, 0);
		Assert.assertEquals(Lists.newArrayList(), collector.getElements());

		StreamElement s_1 = createStreamElement(1, 0);
		b.accept(s_1, 1);

		Assert.assertEquals(Lists.newArrayList(
			Tuple2.of(s_0, 0),
			Tuple2.of(s_1, 1)
		), collector.getElements());
		collector.clear();

		StreamElement s_2 = createStreamElement(2, 1);
		b.accept(s_2, 0);
		Assert.assertEquals(Lists.newArrayList(
		), collector.getElements());
		collector.clear();

		StreamElement s_3 = createStreamElement(3, 2);
		b.accept(s_3, 0);
		Assert.assertEquals(Lists.newArrayList(
		), collector.getElements());
		collector.clear();

		StreamElement s_4 = createStreamElement(3, 1);
		b.accept(s_4, 1);

		Assert.assertEquals(Lists.newArrayList(
			Tuple2.of(s_2, 0),
			Tuple2.of(s_4, 1),
			Tuple2.of(s_3, 0)
		), collector.getElements());
		collector.clear();

		StreamElement s_5 = createStreamElement(4, 3);
		b.accept(s_5, 0);
		Assert.assertEquals(Lists.newArrayList(
		), collector.getElements());

		StreamElement s_6 = createStreamElement(5, 4);
		b.accept(s_6, 0);
		Assert.assertEquals(Lists.newArrayList(
		), collector.getElements());

		StreamElement s_7 = createStreamElement(5, 3);
		b.accept(s_7, 1);
		Assert.assertEquals(Lists.newArrayList(
			Tuple2.of(s_5, 0),
			Tuple2.of(s_7, 1),
			Tuple2.of(s_6, 0)
		), collector.getElements());
		collector.clear();

		StreamElement s_8 = createStreamElement(7, 5);
		b.accept(s_8, 1);
		Assert.assertEquals(Lists.newArrayList(
		), collector.getElements());

		// flush
		StreamElement e_1 = createEndOfEpoch(0, 0);
		b.accept(e_1, 0); // end of epoch with ts 0 and epoch 0 on channel 0
		StreamElement e_2 = createEndOfEpoch(0, 0);
		b.accept(e_2, 1); // end of epoch with ts 0 and epoch 0 on channel 1

		// list should be empty
		Assert.assertEquals(Lists.newArrayList(
			Tuple2.of(s_8, 1),
			Tuple2.of(e_1, 0),
			Tuple2.of(e_2, 1)
		), collector.getElements());
	}

	@Test
	public void acceptTwoChannelsTwoEpochsOnBothChannels() throws Exception {
		BetterBiasAlgorithm b = new BetterBiasAlgorithm(2);
		CollectionChainable collector = new CollectionChainable();
		b.setNext(collector);

		StreamElement s_0 = createStreamElement(1, 0);
		b.accept(s_0, 0);
		Assert.assertEquals(Lists.newArrayList(), collector.getElements());

		StreamElement s_1 = createStreamElement(1, 0);
		b.accept(s_1, 1);

		Assert.assertEquals(Lists.newArrayList(
			Tuple2.of(s_0, 0),
			Tuple2.of(s_1, 1)
		), collector.getElements());
		collector.clear();

		StreamElement s_2 = createStreamElement(2, 1);
		b.accept(s_2, 0);
		Assert.assertEquals(Lists.newArrayList(
		), collector.getElements());
		collector.clear();

		StreamElement s_3 = createStreamElement(3, 2);
		b.accept(s_3, 0);
		Assert.assertEquals(Lists.newArrayList(
		), collector.getElements());
		collector.clear();

		StreamElement s_4 = createStreamElement(3, 1);
		b.accept(s_4, 1);

		Assert.assertEquals(Lists.newArrayList(
			Tuple2.of(s_2, 0),
			Tuple2.of(s_4, 1),
			Tuple2.of(s_3, 0)
		), collector.getElements());
		collector.clear();

		StreamElement s_5 = createStreamElement(4, 3);
		b.accept(s_5, 0);
		Assert.assertEquals(Lists.newArrayList(
		), collector.getElements());

		StreamElement s_6 = createStreamElement(5, 4);
		b.accept(s_6, 0);
		Assert.assertEquals(Lists.newArrayList(
		), collector.getElements());

		StreamElement s_7 = createStreamElement(5, 3);
		b.accept(s_7, 1);
		Assert.assertEquals(Lists.newArrayList(
			Tuple2.of(s_5, 0),
			Tuple2.of(s_7, 1),
			Tuple2.of(s_6, 0)
		), collector.getElements());
		collector.clear();

		StreamElement s_8 = createStreamElement(7, 5);
		b.accept(s_8, 1);
		Assert.assertEquals(Lists.newArrayList(
		), collector.getElements());

		// flush
		StreamElement e_1 = createEndOfEpoch(0, 0);
		b.accept(e_1, 0); // end of epoch with ts 0 and epoch 0 on channel 0
		StreamElement e_2 = createEndOfEpoch(0, 0);
		b.accept(e_2, 1); // end of epoch with ts 0 and epoch 0 on channel 1

		// list should be empty
		Assert.assertEquals(Lists.newArrayList(
			Tuple2.of(s_8, 1),
			Tuple2.of(e_1, 0),
			Tuple2.of(e_2, 1)
		), collector.getElements());
		collector.clear();

		StreamElement s_9 = createStreamElement(6, 5); b.accept(s_9, 0);
		StreamElement s_10 = createStreamElement(8, 7); b.accept(s_10, 1);

		Assert.assertEquals(Lists.newArrayList(
			Tuple2.of(s_9, 0),
			Tuple2.of(s_10, 1)
		), collector.getElements());
		collector.clear();
	}

	@Test
	public void acceptTestBias() throws Exception {
		BetterBiasAlgorithm b = new BetterBiasAlgorithm(2);
		CollectionChainable collector = new CollectionChainable();
		b.setNext(collector);

		b.accept(createEndOfEpoch(-1, 0), 0);
		b.accept(createEndOfEpoch(-1, 0), 1);

		b.accept(createEndOfEpoch(-1, 1), 1);
		b.accept(createEndOfEpoch(-1, 2), 1);
		b.accept(createEndOfEpoch(-1, 3), 1);

		b.accept(createEndOfEpoch(-1, 1), 0);

		b.accept(createEndOfEpoch(-1, 2), 0);
	}

	@Test
	public void acceptEmptyEpoch() throws Exception {
		BetterBiasAlgorithm b = new BetterBiasAlgorithm(2);
		CollectionChainable collector = new CollectionChainable();
		b.setNext(collector);

		b.accept(createEndOfEpoch(0, 0), 0);
		b.accept(createEndOfEpoch(0, 0), 1);

		b.accept(createStreamElement(10, 0), 0);

		b.accept(createEndOfEpoch(12, 1), 0);
		b.accept(createEndOfEpoch(12, 1), 1);
	}

	private StreamElement createStreamElement(long curr, long prev) {
		StreamElement element = new StreamElement() {
			@Override
			public String toString() {
				return "(curr: " + curr + ", prev: " + prev + ")";
			}
		};
		element.setCurrentTimestamp(curr);
		element.setPreviousTimestamp(prev);
		return element;
	}

	private StreamElement createEndOfEpoch(long timestamp, long epoch) {
		EndOfEpochMarker m = new EndOfEpochMarker();
		m.setEpoch(epoch);
		m.setCurrentTimestamp(Long.MAX_VALUE);
		m.setPreviousTimestamp(Long.MAX_VALUE);
		return m;
	}

}
