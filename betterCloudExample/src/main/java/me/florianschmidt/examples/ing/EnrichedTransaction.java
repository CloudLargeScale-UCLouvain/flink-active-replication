package me.florianschmidt.examples.ing;

import java.util.Map;
import java.util.UUID;

class EnrichedTransaction implements TransactionEvent {
	UUID uuid;
	Map<String, Double> payload;
	Map<String, Double> state;

	public EnrichedTransaction() {
	}

	public EnrichedTransaction(
			UUID uuid,
			Map<String, Double> payload,
			Map<String, Double> state
	) {
		this.uuid = uuid;
		this.payload = payload;
		this.state = state;
	}

	@Override
	public String toString() {
		return "EnrichedTransaction{" +
				"uuid=" + uuid +
				", payload=" + payload +
				", state=" + state +
				'}';
	}
}
