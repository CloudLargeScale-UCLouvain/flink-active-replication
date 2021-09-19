package me.florianschmidt.examples.bettercloud;

import com.google.common.collect.Lists;
import me.florianschmidt.examples.bettercloud.datagen.Cooperation;
import me.florianschmidt.examples.bettercloud.rulesengine.Rule;
import me.florianschmidt.replication.baseline.FixedRateSource;

import java.util.Random;

import static me.florianschmidt.examples.bettercloud.MockCustomerEventSource.TECH_COMPANIES;

public class MockControlEventSource extends FixedRateSource<ControlEvent> {

	private static final int NUM_CUSTOMERS = 250;
	private final Cooperation[] cooperations;
	private Random rnd;
	private long seed;


	public MockControlEventSource(int rate, long seed) {
		super(rate);

		this.seed = seed;

		if (seed == 0) {
			this.rnd = new Random();
		} else {
			this.rnd = new Random(0);
		}
		this.cooperations = new Cooperation[NUM_CUSTOMERS];

		for (int i = 0; i < NUM_CUSTOMERS; i++) {
			int numAvailableNames = TECH_COMPANIES.length;
			String name = TECH_COMPANIES[i % numAvailableNames];
			int suffix = Math.floorDiv(i, numAvailableNames);
			name = name + "-" + suffix;
			cooperations[i] = new Cooperation(name, 10_000);
		}
	}

	@Override
	public void doCollect(SourceContext<ControlEvent> ctx) {

		if (rnd.nextDouble() > 0.8) {
			ControlEvent c = new ControlEvent(
					System.currentTimeMillis(),
					"deadbeef-dead-beef-dead-beefdeadbeef",
					"PublicDocsShared",
					"PublicDocsShared",
					"Docs that are shared publicly",
					1,
					Lists.newArrayList(
							new Rule("$.actionType", "equals", "DocumentSharedEvent"),
							new Rule("$.audience", "equals", "public")
					)
			);

			ctx.collect(c);
		} else {
			ControlEvent c = new ControlEvent(
					System.currentTimeMillis(),
					cooperations[rnd.nextInt(cooperations.length)].getName(),
					"PublicDocsShared",
					"PublicDocsShared",
					"Docs that are shared publicly",
					1,
					Lists.newArrayList(
							new Rule("$.actionType", "equals", "DocumentSharedEvent"),
							new Rule("$.audience", "equals", "public")
					)
			);

			ctx.collect(c);
		}
	}
}
