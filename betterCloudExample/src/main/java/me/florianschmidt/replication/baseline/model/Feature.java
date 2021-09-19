package me.florianschmidt.replication.baseline.model;

public class Feature {
	public double value;
	public String name;
	public Transaction transaction;

	// TODO: PojoSerializer, maybe use something else
	public Feature() {

	}

	public Feature(String name, double value, Transaction transaction) {
		this.value = value;
		this.name = name;
		this.transaction = transaction;
	}
}
