package me.florianschmidt.examples.ing;

import java.io.Serializable;
import java.util.Iterator;

class CustomerSource implements Iterator<Customer>, Serializable {

	// TODO: Fix this, numeric overflow
	private long previousUTC = System.currentTimeMillis() - 1000 * 3600 * 24 * 100; // 100 days ago

	@Override
	public boolean hasNext() {
		return true;
	}

	@Override
	public Customer next() {
		return new Customer(
				DataGenerator.randomName(),
				DataGenerator.randomId()
		);
	}

	// TODO: This does not seem to belong here...
	public long nextUTC() {
		previousUTC = DataGenerator.nextUTC(previousUTC);
		return previousUTC;
	}
}
