package me.florianschmidt.examples.ing;

import java.util.UUID;

class Feature {
	public UUID uuid;
	public int expectedNumberOfFeatures;

	public String name;
	public double value;

	public Feature() {
	}

	public Feature(UUID uuid, String name, double value, int expectedNumberOfFeatures) {
		this.uuid = uuid;
		this.name = name;
		this.value = value;
		this.expectedNumberOfFeatures = expectedNumberOfFeatures;
	}

	@Override
	public String toString() {
		return "Feature{" +
				"uuid=" + uuid +
				", name='" + name + '\'' +
				", value=" + value +
				", expectedNumberOfFeatures=" + expectedNumberOfFeatures +
				'}';
	}
}
