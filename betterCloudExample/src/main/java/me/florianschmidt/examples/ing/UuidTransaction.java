package me.florianschmidt.examples.ing;

import java.util.Map;
import java.util.UUID;

public class UuidTransaction {
	public UUID uuid;
	public int expectedNumFeatures;
	public Map<String, Double> payload;

	public UuidTransaction(UUID uuid, int expectedNumFeatures, Map<String, Double> payload) {
		this.uuid = uuid;
		this.expectedNumFeatures = expectedNumFeatures;
		this.payload = payload;
	}

	public UuidTransaction() {
	}

	@Override
	public String toString() {
		return "UuidTransaction{" +
				"uuid=" + uuid +
				", expectedNumFeatures=" + expectedNumFeatures +
				", payload=" + payload +
				'}';
	}
}
