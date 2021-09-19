package me.florianschmidt.examples.ing;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Random;
import java.util.UUID;

public class DataGenerator {

	private static final int[] NAMES = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
	private static final int[] CARD_IDS = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};

	private static final Random rnd = new Random();
	private static final ObjectMapper om = new ObjectMapper();

	public static int randomName() {
		return DataGenerator.NAMES[rnd.nextInt(DataGenerator.NAMES.length)];
	}

	public static int randomId() {
		return DataGenerator.CARD_IDS[rnd.nextInt(DataGenerator.CARD_IDS.length)];
	}

	public static Location randomLocation() {
		double latitude = (rnd.nextDouble() * -180.0) + 90.0;
		double longitude = (rnd.nextDouble() * -360.0) + 180.0;

		return new Location(latitude, longitude);
	}

	public static String randomCustomerId(int upperBound) {
		return String.format("customer-%d", rnd.nextInt(upperBound));
	}

	public static long nextUTC(long previous) {
		int maxDelay = 1000 * 3600; // 1 hour
		return previous + rnd.nextInt(maxDelay);
	}

	public static String randomUUID() {
		return UUID.randomUUID().toString();
	}

	// TODO: Make supported provider a very large percentage use the same and only sometimes a different one
}
