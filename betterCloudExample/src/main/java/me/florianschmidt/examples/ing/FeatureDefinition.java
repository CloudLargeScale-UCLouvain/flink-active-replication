package me.florianschmidt.examples.ing;

import org.apache.flink.api.common.state.MapState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

class FeatureDefinition {
	private BiFunction<MapState<String, Double>, Map<String, Double>, Double> calculate;
	String featureName;
	List<String> requiredInput;

	public FeatureDefinition() {
	}

	public FeatureDefinition(String featureName, List<String> requiredInput, BiFunction<MapState<String, Double>, Map<String, Double>, Double> calculate) {
		this.featureName = featureName;
		this.requiredInput = requiredInput;
		this.calculate = calculate;
	}

	@Override
	public String toString() {
		return "FeatureDefinition{" +
				"name='" + featureName + '\'' +
				", requiredInput=" + requiredInput +
				'}';
	}

	public Map<String, Double> filterRequiredInput(Map<String, Double> payload) {
		Map<String, Double> requiredPayload = new HashMap<>();
		for (String input : requiredInput) {
			requiredPayload.put(input, payload.get(input));
		}

		return requiredPayload;
	}

	public Map<String, Double> filterRequiredInput(MapState<String, Double> state) throws Exception {
		Map<String, Double> requiredPayload = new HashMap<>();
		for (String input : requiredInput) {
			System.out.println(input);
			requiredPayload.put(input, state.get(input));
		}

		return requiredPayload;
	}

	public double calculateAndUpdate(MapState<String, Double> state, ArtificialKeyEvent event) throws Exception {
		return calculate.apply(state, event.payload);
	}
}
