package me.florianschmidt.replication.baseline.functions;

import java.util.Random;

public class Rates {

	public static Rate triangle(int maximum) {

		return tick -> {
			int max = maximum - 1;
			int iteration = Math.floorDiv(tick, max);

			if (iteration % 2 == 0) {
				return (tick - iteration * max) + 1;
			} else {
				return (max - (tick - iteration * max)) + 1;
			}
		};
	}

	public static Rate hiccup(int rate) {
		return tick -> {
			if (tick >= 15 && tick < 25) {
				return 0;
			} else {
				return rate;
			}
		};
	}

	public static Rate random(int max) {
		Random r = new Random();
		return tick -> r.nextInt(max);
	}

	public static Rate constant(int rate) {
		return tick -> rate;
	}

	public static Rate constantBounded(int rate, int until) {
		return tick -> {
			if (tick > until) {
				return 0;
			} else {
				return rate;
			}
		};
	}

	public static Rate linear(int gradient) {
		return tick -> (tick * gradient);
	}

	public static Rate linear(int gradient, int offset) {
		return tick -> {
			return (gradient * tick) + offset;
		};
	}

	public static Rate sin(int amplitude) {
		return tick -> (int) (amplitude + Math.ceil((amplitude * Math.sin(tick / Math.PI))));
	}
}
