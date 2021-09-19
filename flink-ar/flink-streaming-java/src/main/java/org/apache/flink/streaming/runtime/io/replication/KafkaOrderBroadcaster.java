package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.runtime.messages.Acknowledge;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.lang.ClassLoader;

public class KafkaOrderBroadcaster implements OrderBroadcaster {

	private static final Logger LOG = LoggerFactory.getLogger(KafkaOrderBroadcaster.class);

	private final KafkaProducer<String, Order> producer;
	private final String topic;

	public KafkaOrderBroadcaster(String topic, String kafkaServer) {

		ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();

		this.topic = topic;
		Properties props = new Properties();
		props.put("bootstrap.servers", kafkaServer);
		props.put("acks", "all");
		props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		props.put("value.serializer", "org.apache.flink.streaming.runtime.io.replication.OrderSerializer");
		props.put("auto.create.topics.enable", "true");
//		props.put("linger.ms", "10");

		Thread.currentThread().setContextClassLoader(null);
		this.producer = new KafkaProducer<>(props);
		Thread.currentThread().setContextClassLoader(originClassLoader);
		LOG.info("Setup kafka producer to write to topic {}", topic);
	}

	@Override
	public CompletableFuture<Acknowledge> broadcast(List<Integer> nextOrder) throws ExecutionException, InterruptedException {

		int[] nexts = new int[nextOrder.size()];

		for (int i = 0; i < nextOrder.size(); i++) {
			nexts[i] = nextOrder.get(i);
		}

		LOG.trace("Broadcasting order {} to topic {}", nexts, topic);
		ProducerRecord<String, Order> record = new ProducerRecord<>(
			topic,
			"some-key",
			new Order(System.currentTimeMillis(), nexts)
		);

		this.producer.send(record).get();

		LOG.trace("Successfully broadcasted order {} to topic {}", nexts, topic);

		return CompletableFuture.completedFuture(Acknowledge.get());
	}
}
