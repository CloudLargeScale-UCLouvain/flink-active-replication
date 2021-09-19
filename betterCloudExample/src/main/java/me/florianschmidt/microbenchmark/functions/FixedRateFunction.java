package me.florianschmidt.microbenchmark.functions;

import com.google.common.collect.Lists;
import me.florianschmidt.examples.bettercloud.datagen.Cooperation;
import me.florianschmidt.examples.bettercloud.rulesengine.Rule;
import me.florianschmidt.replication.baseline.FixedRateSource;
import me.florianschmidt.replication.baseline.jobs.Record;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

public class FixedRateFunction extends FixedRateSource<Record> {
	private Integer count;
	private static final transient Random random = new Random();
	private String name;
	
    public FixedRateFunction(int rate, String name) {
        super(rate);
		this.name = name;
        this.count = 0;
    }

	@Override
	public void doCollect(SourceContext<Record> ctx) {

		Record r = new Record();
		r.key = "key-" + random.nextInt(10_000);
		r.id = this.name + "-elem-" + this.count;
		r.origin = name;
		r.createdAtNanos = System.nanoTime();
		r.createdAtMillis = System.currentTimeMillis();
		r.count = count;
		this.count++;
		ctx.collect(r);
    }
}

