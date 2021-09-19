package org.apache.flink.streaming.runtime.io.replication;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

import java.nio.ByteBuffer;
import java.util.Map;

public class OrderSerializer implements Serializer<Order>, Deserializer<Order> {


	@Override
	public void configure(Map<String, ?> configs, boolean isKey) {

	}

	@Override
	public Order deserialize(String topic, byte[] data) {
		ByteBuffer b = ByteBuffer.wrap(data);

		long created = b.getLong();
		int size = b.getInt();
		int[] order = new int[size];
		for (int i = 0; i < size; i++) {
			order[i] = b.getInt();
		}

		return new Order(created, order);
	}

	@Override
	public byte[] serialize(String topic, Order data) {
		ByteBuffer b = ByteBuffer.allocate(Long.BYTES + Integer.BYTES + data.order.length * Integer.BYTES);

		b.putLong(data.created);
		b.putInt(data.order.length);

		for (int i = 0; i < data.order.length; i++) {
			b.putInt(data.order[i]);
		}

		return b.array();
	}

	@Override
	public void close() {

	}
}
