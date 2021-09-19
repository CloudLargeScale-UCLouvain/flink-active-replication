package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.runtime.messages.Acknowledge;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public interface OrderBroadcaster {
	CompletableFuture<Acknowledge> broadcast(List<Integer> nextOrder) throws ExecutionException, InterruptedException;
}
