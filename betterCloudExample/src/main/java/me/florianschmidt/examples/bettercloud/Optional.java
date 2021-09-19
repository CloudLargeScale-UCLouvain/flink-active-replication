package me.florianschmidt.examples.bettercloud;

public class Optional<T> {

	public T value;

	public Optional() {

	}

	public Optional(T value) {
		this.value = value;
	}

	public boolean isPresent() {
		return value != null;
	}

	public T get() {
		return value;
	}

	public static <T> Optional<T> empty() {
		return new Optional<>();
	}

	public static <T> Optional<T> of(T value) {
		return new Optional<>(value);
	}
}
