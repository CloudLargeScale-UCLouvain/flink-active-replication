package me.florianschmidt.examples.ing;

class Card {
	public int id;

	public Card(int id) {
		this.id = id;
	}

	public Card() {
	}

	@Override
	public String toString() {
		return "Card{" +
				"id=" + id +
				'}';
	}
}
