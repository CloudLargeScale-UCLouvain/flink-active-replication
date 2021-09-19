package me.florianschmidt.examples.bettercloud;

public class CustomerEvent {

	public String customerId;
	public String payload;
	public long ingestedAt;
	public long receivedAt;

	public CustomerEvent(long ingestedAt, String customerId, String payload, long receivedAt) {
		this.ingestedAt = ingestedAt;
		this.customerId = customerId;
		this.payload = payload;
		this.receivedAt = receivedAt;
	}

	public CustomerEvent() {
	}

	@Override
	public String toString() {
		return "CustomerEvent{" +
				"customerId='" + customerId + '\'' +
				", payload='" + payload + '\'' +
				", ingestedAt=" + ingestedAt +
				", receivedAt=" + receivedAt +
				'}';
	}
}

