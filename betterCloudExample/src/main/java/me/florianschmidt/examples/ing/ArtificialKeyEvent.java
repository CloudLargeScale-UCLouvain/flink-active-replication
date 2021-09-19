package me.florianschmidt.examples.ing;

import java.util.Map;
import java.util.UUID;

class ArtificialKeyEvent {

	public UUID uuid;
	public String featureName;
	public String artificalKey;
	public int expectedNumFeatures;
	public Map<String, Double> payload;

	public ArtificialKeyEvent() {
	}

	public ArtificialKeyEvent(
			UUID uuid,
			String featureName,
			String artificalKey,
			int expectedNumFeatures,
			Map<String, Double> payload
	) {

		this.uuid = uuid;
		this.featureName = featureName;
		this.artificalKey = artificalKey;
		this.expectedNumFeatures = expectedNumFeatures;
		this.payload = payload;
	}

	@Override
	public String toString() {
		return "ArtificialKeyEvent{" +
				"uuid=" + uuid +
				", name='" + featureName + '\'' +
				", keyValue=" + artificalKey +
				", expectedNumFeatures=" + expectedNumFeatures +
				", payload=" + payload +
				'}';
	}
}
