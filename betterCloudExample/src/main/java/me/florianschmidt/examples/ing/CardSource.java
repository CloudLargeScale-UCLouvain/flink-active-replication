package me.florianschmidt.examples.ing;

import java.io.Serializable;
import java.util.Iterator;

class CardSource implements Iterator<Card>, Serializable {

	@Override
	public boolean hasNext() {
		return true;
	}

	@Override
	public Card next() {
		return new Card(
				DataGenerator.randomId()
		);
	}
}
