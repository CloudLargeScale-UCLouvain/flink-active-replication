package me.florianschmidt.examples.bettercloud.rulesengine;

public class Rule {

	public String path;
	public String check;
	public String value;

	public Rule(String path, String check, String value) {
		this.path = path;
		this.check = check;
		this.value = value;
	}

	public Rule() {
	}

	public Boolean evaluate(String actualValue) {
		if (check.equals("equals")) {
			return value.equals(actualValue);
		} else {
			throw new RuntimeException("Unsupported check " + check);
		}
	}
}
