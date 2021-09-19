package me.florianschmidt.examples.ing;

public class Customer {
	int id;
	int cardId;

	public Customer(int id, int cardId) {
		this.id = id;
		this.cardId = cardId;
	}

	public Customer() {
	}

	@Override
	public String toString() {
		return "Customer{" +
				"id='" + id + '\'' +
				", cardId=" + cardId +
				'}';
	}
}
