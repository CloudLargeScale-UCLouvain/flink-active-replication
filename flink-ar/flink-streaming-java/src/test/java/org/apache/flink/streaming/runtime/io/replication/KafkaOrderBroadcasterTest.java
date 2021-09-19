package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.runtime.messages.Acknowledge;

import org.apache.flink.shaded.guava18.com.google.common.collect.Lists;

import org.junit.Test;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class KafkaOrderBroadcasterTest {

	Random r = new Random();

	@Test
	public void broadcast() throws ExecutionException, InterruptedException {
//		KafkaOrderBroadcaster b = new KafkaOrderBroadcaster("topic-for-operator" + r.nextInt(1000));
//		CompletableFuture<Acknowledge> f = b.broadcast(Lists.newArrayList(9, 4, 2, 3, 6, 5));
//		f.get();
	}
}
