package me.florianschmidt.replication.baseline.model;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

import me.florianschmidt.examples.ing.Location;

public class Transaction {

	@JsonProperty("CustomerID")
	public int customerId;

	@JsonProperty("CardID")
	public int cardId;

	@JsonProperty("Location")
	public Location location;

	public double amount;

	@JsonProperty("CreatedAt")
	public long created;

	@JsonProperty("ID")
	public String uuid;

	// public final byte[] payload = new byte[100];


	public Transaction() {
	}

	public Transaction(
			int customerId,
			int cardId,
			Location location,
			long utcTimestamp,
			double amount
	) {
		this.customerId = customerId;
		this.cardId = cardId;
		this.location = location;
		this.amount = amount;
		this.created = System.currentTimeMillis();
	}

	@Override
	public String toString() {
		return "Transaction{" +
				"customerId=" + customerId +
				", cardId=" + cardId +
				", location=" + location +
				", amount=" + amount +
				'}';
	}
}
