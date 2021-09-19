package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.streaming.runtime.streamrecord.StreamElement;

import org.apache.flink.shaded.guava18.com.google.common.collect.Lists;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.CancelLeadershipException;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.curator.framework.state.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LeaderBasedReplication extends Chainable implements LeaderSelectorListener {

	private static final Logger LOG = LoggerFactory.getLogger(LeaderBasedReplication.class);
	private final int batchSize;

	private final BlockingQueue<Integer> nextCandidates;
	private final BlockingQueue<Enqueued>[] queues;
	private final BlockingQueue<Integer> nextChannels;

	private final OrderBroadcaster broadcaster;

	private final String owningTaskName;

	private volatile boolean hasFailed;
	private volatile boolean stopProcessor;
	private List<AutoCloseable> closables = new LinkedList<>();

	@SuppressWarnings("unchecked")
	public LeaderBasedReplication(
		int numProducers,
		OrderBroadcaster broadcaster,
		int batchSize
	) {
		this.broadcaster = broadcaster;
		this.batchSize = batchSize;

		this.queues = new BlockingQueue[numProducers];

		// TODO: Thesis - This has a capacity of Integer.MAX_VALUE and is not unbounded!
		this.nextChannels = new LinkedBlockingQueue<>();
		this.nextCandidates = new LinkedBlockingQueue<>();

		for (int i = 0; i < numProducers; i++) {
			this.queues[i] = new ArrayBlockingQueue<>(batchSize);
		}

		this.owningTaskName = Thread.currentThread().getName();
		((OrderBroadcasterImpl) this.broadcaster).setOwningThreadName(this.owningTaskName);

		this.hasFailed = false;
		this.stopProcessor = false;

		Thread processor = new Thread(this::processInput, "active-replication-" + owningTaskName);
		processor.start();
	}


	@Override
	public void accept(StreamElement element, int channel) throws Exception {
		checkFailure();
		LOG.info("At {}: Adding element to queue", owningTaskName);
		Enqueued e = new Enqueued(element.getCurrentTs(), channel, element);
		this.queues[channel].put(e);
		this.nextCandidates.put(channel);
		LOG.info("At {}: done adding element to queue", owningTaskName);
	}

	private void processInput() {
		hasFailed = false;
		while (!hasFailed || !stopProcessor) {
			try {
				// get next input
				int channel = nextChannels.take();
				Enqueued elem = queues[channel].take();
				if (hasNext()) {
					getNext().accept(elem.value, elem.channel);
				}
			} catch (Exception e) {
				LOG.error("Input processor failed with exception {}", e);
				hasFailed = true;
			}
		}
	}

	private void checkFailure() {
		if (this.hasFailed) {
			throw new RuntimeException("We have failed on the leader thread");
		}
	}

	public void acceptOrdering(List<Integer> nextBatch) throws Exception {
		checkFailure();
		LOG.info("At {}: Accepting ordering {}", owningTaskName, nextBatch);
		this.nextChannels.addAll(nextBatch);
	}

	@Override
	public void takeLeadership(CuratorFramework curatorFramework) throws Exception {
		LOG.info("At {}: I became the leader", owningTaskName);
		try {
			while (true) {
				List<Integer> next = new ArrayList<>(batchSize);

				boolean timeout = false;
				while (next.size() < batchSize && !timeout) {
					Integer n = this.nextCandidates.poll(1000, TimeUnit.MILLISECONDS);
					if (n != null) {
						next.add(n);
					} else {
						timeout = true;
					}
				}

				if (next.size() > 0) {
					LOG.info("At {}: Broadcasting {}", owningTaskName, next);
					broadcaster.broadcast(next);
					this.acceptOrdering(next);
				}
			}
		} catch (Throwable t) {
			LOG.warn("At {}: Lost leadership because of {}", owningTaskName, t);
			this.hasFailed = true;
			throw t;
		}
	}

	@Override
	public void stateChanged(CuratorFramework client, ConnectionState newState) {
		if (newState == ConnectionState.SUSPENDED || newState == ConnectionState.LOST) {
			LOG.error("At {}: State changed to {}", owningTaskName, newState);
			throw new CancelLeadershipException();
		}
	}

	@Override
	public void close() throws Exception {
		this.stopProcessor = true;
		for (AutoCloseable closable : this.closables) {
			closable.close();
		}
	}

	public void addCloseables(AutoCloseable... c) {
		this.closables.addAll(Lists.newArrayList(c));
	}
}
