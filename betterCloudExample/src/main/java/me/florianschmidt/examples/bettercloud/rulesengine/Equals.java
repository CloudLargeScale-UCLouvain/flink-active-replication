package me.florianschmidt.examples.bettercloud.rulesengine;

import java.util.Objects;

public class Equals implements Check {
	@Override
	public Boolean apply(String o, String o2) {
		return Objects.equals(o.toLowerCase(), o2.toLowerCase());
	}
}
