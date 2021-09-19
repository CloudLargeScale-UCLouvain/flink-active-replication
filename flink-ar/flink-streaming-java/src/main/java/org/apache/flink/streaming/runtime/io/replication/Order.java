package org.apache.flink.streaming.runtime.io.replication;

import java.util.Arrays;
import java.util.Objects;

public class Order {
	public long created;
	public int[] order;

	public Order(long created, int[] order) {
		this.created = created;
		this.order = order;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Order order1 = (Order) o;
		return created == order1.created &&
			Arrays.equals(order, order1.order);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(created);
		result = 31 * result + Arrays.hashCode(order);
		return result;
	}
}
