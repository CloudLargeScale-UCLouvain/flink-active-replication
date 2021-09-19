package me.florianschmidt.examples.bettercloud.datagen;

import java.io.Serializable;
import java.util.Random;

public class Cooperation implements Serializable {

	private String[] employees;
	private String name;

	private final Random r;

	public Cooperation(String name, int numUsers) {
		this.employees = new String[numUsers];
		this.name = name;

		r = new Random();

		for (int i = 0; i < numUsers; i++) {
			this.employees[i] = String.format("%s@%s.com", "hello", this.name.toLowerCase());
		}
	}

	public String randomEmployee() {
		return this.employees[r.nextInt(this.employees.length)];
	}

	public String getName() {
		return name;
	}
}
