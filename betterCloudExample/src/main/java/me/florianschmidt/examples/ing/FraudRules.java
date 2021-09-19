package me.florianschmidt.examples.ing;

public class FraudRules {

	public static boolean scoreAdvancedFlyingCarpet(EnrichedTransaction input) {

		double utcLocation = (double) input.state.get("utcLocation");
		double avgAmount = (double) input.state.get("averageAmount");
		double amount = (double) input.payload.get("amount");

		return utcLocation > 1000.0 && (avgAmount - amount) > 2 * avgAmount;
	}

}
