package org.apache.flink.streaming.runtime.io.replication;

import org.junit.Assert;
import org.junit.Test;

public class StatisticsTest {

	@Test
	public void testEpochDuration() {
		Statistics s = new Statistics();

		s.onEpochEnded(1, 0, 10);
		s.onEpochEnded(2, 0, 15);

		Assert.assertEquals(5, s.epochDuration(2, 0));
	}

	@Test
	public void testEpochDurationTwoChannels() {
		Statistics s = new Statistics();

		s.onEpochEnded(1, 0, 10);
		s.onEpochEnded(1, 1, 0);
		s.onEpochEnded(2, 0, 15);
		s.onEpochEnded(2, 1, 3);
		Assert.assertEquals(5, s.epochDuration(2, 0));
		Assert.assertEquals(3, s.epochDuration(2, 1));
	}

	@Test
	public void testEpochDurationPreservesHistory() {
		Statistics s = new Statistics();

		s.onEpochEnded(1, 0, 10);
		s.onEpochEnded(2, 0, 15);

		Assert.assertEquals(5, s.epochDuration(2, 0));

		s.onEpochEnded(3, 0, 20);
		s.onEpochEnded(4, 0, 22);

		Assert.assertEquals(5, s.epochDuration(3, 0));
		Assert.assertEquals(2, s.epochDuration(4, 0));
	}

	@Test(expected = RuntimeException.class)
	public void testEpochDurationThrowsForEpochsThatHaveNotEndedYet() {
		Statistics s = new Statistics();

		s.onEpochEnded(1, 0, 10);

		// should throw
		s.epochDuration(2, 0);
	}

	@Test(expected = RuntimeException.class)
	public void testEpochCantEndTwice() {
		Statistics s = new Statistics();
		s.onEpochEnded(1, 0, 0);

		// should throw
		s.onEpochEnded(1, 0, 0);
	}

	@Test
	public void testCount() {
		Statistics s = new Statistics();

		s.onEventReceived(0, 1);
		s.onEventReceived(0, 1);
		s.onEventReceived(0, 1);
		s.onEventReceived(0, 1);

		Assert.assertEquals(4, s.elementsInEpoch(0, 1));
	}

	@Test
	public void testCountTwoChannels() {
		Statistics s = new Statistics();

		s.onEventReceived(0, 1);
		s.onEventReceived(0, 2);
		s.onEventReceived(0, 1);
		s.onEventReceived(0, 2);
		s.onEventReceived(0, 1);
		s.onEventReceived(0, 2);
		s.onEventReceived(0, 1);

		Assert.assertEquals(4, s.elementsInEpoch(0, 1));
		Assert.assertEquals(3, s.elementsInEpoch(0, 2));
	}

	@Test
	public void testCountPreservesHistory() {
		Statistics s = new Statistics();

		s.onEventReceived(0, 1);
		s.onEventReceived(0, 1);
		s.onEventReceived(0, 1);
		s.onEventReceived(0, 1);

		Assert.assertEquals(4, s.elementsInEpoch(0, 1));

		s.onEventReceived(1, 1);
		s.onEventReceived(1, 1);
		s.onEventReceived(1, 1);

		Assert.assertEquals(4, s.elementsInEpoch(0, 1));
		Assert.assertEquals(3, s.elementsInEpoch(1, 1));
	}

	@Test
	public void bias() {
		Statistics s = new Statistics();

		int L = 100;
		double p1 = 0.5;
		double p2 = 0.01;

		int j = 1;
		int t = 0;
		double res = s.bias(p1, p2, t, j, L);
		Assert.assertEquals(114.315, res, 0.001);

		t = 50;
		res = s.bias(p1, p2, t, j, L);
		Assert.assertEquals(161.391, res, 0.001);

		t = 99;
		res = s.bias(p1, p2, t, j, L);
		Assert.assertEquals(458.211, res, 0.001);

		t = 100;
		res = s.bias(p1, p2, t, j, L);
		Assert.assertEquals(458.211, res, 0.001);

		t = 50;
		j = 2;
		res = s.bias(p1, p2, t, j, L);
		Assert.assertEquals(458.211, res, 0.001);
	}
}
