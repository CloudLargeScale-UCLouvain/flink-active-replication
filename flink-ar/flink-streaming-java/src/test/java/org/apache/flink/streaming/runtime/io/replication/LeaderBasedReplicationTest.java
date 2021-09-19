package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.streaming.runtime.streamrecord.StreamElement;

import com.google.common.collect.Lists;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LeaderBasedReplicationTest {

	@Test
	public void accept() throws Exception {
		TestingServer server = new TestingServer();
		CuratorFramework curator = CuratorFrameworkFactory.newClient(server.getConnectString(), new ExponentialBackoffRetry(1000, 3));
		String latchPath = "/testing/latchpath";

		MockBroadcaster b = new MockBroadcaster();
		LeaderBasedReplication r1 = new LeaderBasedReplication(2, b, 3);

		LeaderSelector selector = new LeaderSelector(curator, latchPath, r1);

		server.start();
		curator.start();
		selector.start();

		r1.accept(newElement(), 0);
		r1.accept(newElement(), 0);
		r1.accept(newElement(), 0);

		Thread.sleep(10_000);

		Assert.assertEquals(Lists.newArrayList(0, 0, 0), b.toBeBroadcasted);
	}

	@Test
	public void testWithBroadcasting() throws Exception {
		TestingServer server = new TestingServer();
		CuratorFramework curator = CuratorFrameworkFactory.newClient(server.getConnectString(), new ExponentialBackoffRetry(1000, 3));
		String latchPath = "/testing/latchpath";

		MockBroadcaster b1 = new MockBroadcaster();
		MockBroadcaster b2 = new MockBroadcaster();
		MockBroadcaster b3 = new MockBroadcaster();
		LeaderBasedReplication r1 = new LeaderBasedReplication(2, b1, 3);
		LeaderBasedReplication r2 = new LeaderBasedReplication(2, b2, 3);
		LeaderBasedReplication r3 = new LeaderBasedReplication(2, b3, 3);

		b1.addBroadcastTargets(r2, r3);
		b2.addBroadcastTargets(r1, r3);
		b3.addBroadcastTargets(r1, r2);

		server.start();
		curator.start();
		LeaderSelector selector1 = new LeaderSelector(curator, latchPath, r1);
		LeaderSelector selector2 = new LeaderSelector(curator, latchPath, r2);
		LeaderSelector selector3 = new LeaderSelector(curator, latchPath, r3);
		selector1.start();
		selector2.start();
		selector3.start();

		CollectingChainable r1collector = new CollectingChainable();
		r1.setNext(r1collector);

		CollectingChainable r2collector = new CollectingChainable();
		r2.setNext(r2collector);

		CollectingChainable r3collector = new CollectingChainable();
		r3.setNext(r3collector);

		r1.accept(newElement(), 0);
		r1.accept(newElement(), 1);
		r1.accept(newElement(), 0);

		r2.accept(newElement(), 0);
		r2.accept(newElement(), 1);
		r2.accept(newElement(), 0);

		r3.accept(newElement(), 0);
		r3.accept(newElement(), 1);
		r3.accept(newElement(), 0);

		Thread.sleep(1000);

		Assert.assertEquals(Lists.newArrayList(0, 1, 0), r1collector.channelOrder);
		Assert.assertEquals(Lists.newArrayList(0, 1, 0), r2collector.channelOrder);
		Assert.assertEquals(Lists.newArrayList(0, 1, 0), r3collector.channelOrder);

		r1.accept(newElement(), 0);
		r1.accept(newElement(), 0);
		r1.accept(newElement(), 0);
	}

	private StreamElement newElement() {
		return new StreamElement() {
		};
	}

	private class MockBroadcaster implements OrderBroadcaster {

		private List<Integer> toBeBroadcasted = new LinkedList<>();
		private List<LeaderBasedReplication> targets = new LinkedList<>();

		@Override
		public CompletableFuture<Acknowledge> broadcast(List<Integer> nextOrder) {
			this.toBeBroadcasted.addAll(nextOrder);
			for (LeaderBasedReplication t : this.targets) {
				try {
					t.acceptOrdering(nextOrder);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			return CompletableFuture.completedFuture(Acknowledge.get());
		}

		public void addBroadcastTargets(LeaderBasedReplication... replicas) {
			this.targets.addAll(Lists.newArrayList(replicas));
		}
	}

	private class CollectingChainable extends Chainable {

		private List<Integer> channelOrder = new LinkedList<>();

		@Override
		public void accept(StreamElement element, int channel) throws Exception {
			System.out.println("Adding " + channel);
			this.channelOrder.add(channel);
		}
	}
}
