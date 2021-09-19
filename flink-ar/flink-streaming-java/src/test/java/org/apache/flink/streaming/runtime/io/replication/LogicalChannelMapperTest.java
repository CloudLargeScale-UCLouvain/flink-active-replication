package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.streaming.runtime.streamrecord.StreamElement;

import org.junit.Assert;
import org.junit.Test;

public class LogicalChannelMapperTest {

	@Test
	public void acceptWithDifferentReplicationFactors() throws Exception {
		LogicalChannelMapper m = new LogicalChannelMapper(new int[]{2, 1, 3});

		m.setNext(expect(0));
		m.accept(new StreamElement() {
		}, 0);

		m.setNext(expect(0));
		m.accept(new StreamElement() {
		}, 1);

		m.setNext(expect(1));
		m.accept(new StreamElement() {
		}, 2);

		m.setNext(expect(2));
		m.accept(new StreamElement() {
		}, 3);

		m.setNext(expect(2));
		m.accept(new StreamElement() {
		}, 4);

		m.setNext(expect(2));
		m.accept(new StreamElement() {
		}, 5);
	}

	@Test
	public void acceptWithIdenticalReplicationFactors() throws Exception {
		LogicalChannelMapper m = new LogicalChannelMapper(new int[]{2, 2});

		m.setNext(expect(0));
		m.accept(new StreamElement() {
		}, 0);

		m.setNext(expect(0));
		m.accept(new StreamElement() {
		}, 1);

		m.setNext(expect(1));
		m.accept(new StreamElement() {
		}, 2);

		m.setNext(expect(1));
		m.accept(new StreamElement() {
		}, 3);
	}

	@Test(expected = RuntimeException.class)
	public void acceptWithOutOfRangeChannel() throws Exception {
		LogicalChannelMapper m = new LogicalChannelMapper(new int[]{1});

		m.accept(new StreamElement() {
		}, 1);
	}

	private Chainable expect(int expected) {
		return new Chainable() {
			@Override
			public void accept(StreamElement element, int channel) throws Exception {
				Assert.assertEquals(expected, channel);
			}
		};
	}
}
