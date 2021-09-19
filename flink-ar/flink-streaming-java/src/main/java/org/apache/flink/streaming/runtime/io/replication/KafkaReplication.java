package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.runtime.metrics.groups.TaskIOMetricGroup;
import org.apache.flink.streaming.runtime.streamrecord.StreamElement;

import org.apache.flink.shaded.guava18.com.google.common.collect.Lists;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.CancelLeadershipException;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class KafkaReplication extends Chainable implements LeaderSelectorListener {

	private static final Logger LOG = LoggerFactory.getLogger(KafkaReplication.class);
	private final int batchSize;

	private final BlockingQueue<Integer> nextCandidates;
	private final BlockingQueue<Enqueued>[] queues;
	private final long kafkaTimeout;

	private final OrderBroadcaster broadcaster;

	private final String owningTaskName;
	private final KafkaConsumer<String, Order> consumer;
	private final TaskIOMetricGroup metrics;

	private volatile boolean hasFailed;
	private volatile boolean stopProcessor;
	private List<AutoCloseable> closables = new LinkedList<>();

	private LeaderSelector leaderSelector;

	@SuppressWarnings("unchecked")
	public KafkaReplication(
		int numProducers,
		OrderBroadcaster broadcaster,
		int batchSize,
		long kafkaTimeout,
		String topic,
		CuratorFramework f,
		String kafkaServer,
		TaskIOMetricGroup metrics
	) {

		this.broadcaster = broadcaster;
		this.batchSize = batchSize;
		this.kafkaTimeout = kafkaTimeout;

		this.metrics = metrics;

		this.hasFailed = false;
		this.stopProcessor = false;

		this.owningTaskName = Thread.currentThread().getName();

		this.nextCandidates = new LinkedBlockingQueue<>();

		this.queues = new BlockingQueue[numProducers];
		for (int i = 0; i < numProducers; i++) {
			this.queues[i] = new LinkedBlockingQueue<>();
		}

		Properties props = new Properties();

		props.put("bootstrap.servers", kafkaServer);
		props.put("group.id", UUID.randomUUID().toString());
		props.put("enable.auto.commit", "true");
		props.put("auto.commit.interval.ms", "1000");
		props.put("auto.offset.reset", "earliest");
		props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer", "org.apache.flink.streaming.runtime.io.replication.OrderSerializer");
		props.put("batchSize", batchSize);

		ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();


		Thread.currentThread().setContextClassLoader(null);
		this.consumer = new KafkaConsumer<>(props);
		this.consumer.subscribe(Lists.newArrayList(topic));
		
		LOG.info("Using kafka ordering with timeout {} and batchsize {}", kafkaTimeout, batchSize);

		LOG.info("Setup kafka consumer to read from topic {}", topic);

		LeaderSelector leaderSelector = new LeaderSelector(f, "/flink/" + topic, this);
		leaderSelector.autoRequeue();
		leaderSelector.start();
		this.leaderSelector = leaderSelector;
		this.closables.add(leaderSelector);

		Thread processor = new Thread(this::pollOrderings, "active-replication-" + owningTaskName);
		processor.setContextClassLoader(originClassLoader);
		Thread.currentThread().setContextClassLoader(originClassLoader);
		processor.start();
	}


	@SuppressWarnings({"Duplicates"})
	@Override
	public void accept(StreamElement element, int channel) throws Exception {
		checkFailure();

		LOG.trace("Adding element to queue. Current queue length at channel {}: {}", channel, queues[channel].size());

		Enqueued e = new Enqueued(element.getCurrentTs(), channel, element);
		this.queues[channel].put(e);
		this.nextCandidates.put(channel);

		LOG.trace("Successfully added element to queue");
	}

	private void pollOrderings() {
		hasFailed = false;
		while (!hasFailed || !stopProcessor) {

			ConsumerRecords<String, Order> records = consumer.poll(Duration.ofMillis(100));

			LOG.trace("Got {} records", records.count());
			for (ConsumerRecord<String, Order> r : records) {

				LOG.trace("Processing order from kafka {} with latency {}ms", Arrays.toString(r.value().order), System.currentTimeMillis() - r.value().created);

				for (int chan : r.value().order) {

					try {
						Enqueued elem = queues[chan].take();
						// remove corresponding next candidate if not the leader
						if (!this.leaderSelector.hasLeadership()) {
							this.nextCandidates.take();
						}
						if (hasNext()) {
							this.getNext().accept(elem.value, elem.channel);
						}
					} catch (Exception e) {
						LOG.error("Input processor failed with exception {}", e);
						hasFailed = true;
					}
				}

				LOG.trace("Delivered all events for received order {}", Arrays.toString(r.value().order));
			}
		}
	}

	private void checkFailure() {
		if (this.hasFailed) {
			throw new RuntimeException("We have failed on the leader thread");
		}
	}

	@Override
	public void takeLeadership(CuratorFramework curatorFramework) throws Exception {
		LOG.info("took leadership");

		String previousName = Thread.currentThread().getName();
		Thread.currentThread().setName("leader-for-" + owningTaskName);
		try {
			broadcastOrder();
		} catch (Throwable t) {
			LOG.warn("Lost leadership because of {}", t);
			this.hasFailed = true;
			Thread.currentThread().setName(previousName);
			throw t;
		}
	}

	@SuppressWarnings({"Duplicates"})
	private void broadcastOrder() throws InterruptedException, java.util.concurrent.ExecutionException {
		while (true) {

			List<Integer> next = new ArrayList<>(batchSize);
			this.nextCandidates.drainTo(next, batchSize);

			if (next.size() > 0) {
				long start = System.nanoTime();
				broadcaster.broadcast(next).get();
				long duration = System.nanoTime() - start;
				LOG.trace("Broadcast took {} nanoseconds for {} elements, still {} elements", duration, next.size(), nextCandidates.size());

			} else {
				//LOG.trace("No next candidates to broadcast");
				Thread.sleep(1);
			}
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
