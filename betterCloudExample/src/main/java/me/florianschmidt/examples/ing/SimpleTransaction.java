package me.florianschmidt.examples.ing;

import java.util.HashMap;
import java.util.Map;

public class SimpleTransaction implements TransactionEvent {

	public int customerId;
	public int cardId;
	public Location location;
	public long UTC;
	public double amount;

	public SimpleTransaction(int customerId, int cardId, Location location, long UTC, double amount) {
		this.customerId = customerId;
		this.cardId = cardId;
		this.location = location;
		this.UTC = UTC;
		this.amount = amount;
	}

	public SimpleTransaction() {
	}

	@Override
	public String toString() {
		return "SimpleTransaction{" +
				"customerId='" + customerId + '\'' +
				", id=" + cardId +
				", location=" + location +
				", UTC=" + UTC +
				", amount=" + amount +
				'}';
	}

	public Map<String, Double> toMap() {

		Map<String, Double> m = new HashMap<>();
		m.put("customerId", (double)customerId);
		m.put("cardId", (double) cardId);
		m.put("latitude", location.latitude);
		m.put("longitude", location.longitude);
		m.put("UTC", (double) UTC);
		m.put("amount", amount);

		return m;
	}
}
