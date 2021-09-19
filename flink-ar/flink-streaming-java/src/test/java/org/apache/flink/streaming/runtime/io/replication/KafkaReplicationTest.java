package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.streaming.runtime.streamrecord.StreamElement;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.Test;

import java.util.Random;

public class KafkaReplicationTest {

	private Random r = new Random();

	@Test
	public void test() throws Exception {

		TestingServer s = new TestingServer();

		CuratorFramework f = CuratorFrameworkFactory.newClient(s.getConnectString(), new ExponentialBackoffRetry(10_000, 3));

		String topic = "topic-for-operator-" + r.nextInt(1000);
		KafkaOrderBroadcaster b = new KafkaOrderBroadcaster(topic, "localhost:9092");

		KafkaReplication r = new KafkaReplication(2, b, 1, 100, topic, f, "localhost:9092", metrics);
		KafkaReplication r2 = new KafkaReplication(2, b, 1, 100, topic, f, "localhost:9092", metrics);

		r.setNext(new Chainable() {
			@Override
			public void accept(StreamElement element, int channel) throws Exception {
				System.out.println("r1: Delivered " + element + " at channel " + channel);
			}
		});

		r2.setNext(new Chainable() {
			@Override
			public void accept(StreamElement element, int channel) throws Exception {
				System.out.println("r2: Delivered " + element + " at channel " + channel);
			}
		});

		r.accept(new StreamRecord<>("hello"), 0);
		r.accept(new StreamRecord<>("world"), 1);
		r2.accept(new StreamRecord<>("hello"), 0);
		r2.accept(new StreamRecord<>("world"), 1);

		r.takeLeadership(null);
	}

}
