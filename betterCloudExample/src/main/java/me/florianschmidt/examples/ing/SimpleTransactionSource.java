package me.florianschmidt.examples.ing;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Random;

public class SimpleTransactionSource implements Iterator<SimpleTransaction>, Serializable {

	private final CustomerSource customerSource;
	private final CardSource cardSource;
	private final Random rnd;

	public SimpleTransactionSource() {
		customerSource = new CustomerSource();
		cardSource = new CardSource();
		rnd = new Random();
	}

	@Override
	public boolean hasNext() {
		return true;
	}

	@Override
	public SimpleTransaction next() {
		return new SimpleTransaction(
				customerSource.next().id,
				cardSource.next().id,
				DataGenerator.randomLocation(),
				customerSource.nextUTC(),
				rnd.nextDouble()
		);
	}

}
