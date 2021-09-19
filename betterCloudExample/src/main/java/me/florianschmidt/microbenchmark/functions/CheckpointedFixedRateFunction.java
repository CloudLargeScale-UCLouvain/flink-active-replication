package me.florianschmidt.microbenchmark.functions;

import com.google.common.collect.Lists;
import java.util.Random;
import java.util.List;

import org.apache.flink.streaming.api.checkpoint.ListCheckpointed;

import me.florianschmidt.examples.bettercloud.datagen.Cooperation;
import me.florianschmidt.examples.bettercloud.rulesengine.Rule;
import me.florianschmidt.replication.baseline.FixedRateSource;
import me.florianschmidt.replication.baseline.jobs.Record;

public class CheckpointedFixedRateFunction extends FixedRateSource<Record> implements ListCheckpointed<Record> {
	private Integer count;
	private static final transient Random random = new Random();
	private String name;
	private Record lastRecord = null;

    public CheckpointedFixedRateFunction(int rate, String name) {
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
		r.count = this.count;
		synchronized (ctx.getCheckpointLock()) {
			this.lastRecord = r;
			ctx.collect(r);
			this.count++;
		}

    }
	@Override
	public List<Record> snapshotState(long checkpointId, long timestamp) {
		if (this.lastRecord == null) {
			return Lists.newArrayList();
		} else {
			return Lists.newArrayList(this.lastRecord);
		}
	}	
	@Override
	public void restoreState(List<Record> state) {
		if (state.size() > 0) {
			Record restoredRecord = state.get(0);
			if (restoredRecord != null) {
				this.lastRecord = null;
				this.count = this.lastRecord.count;
			} else {
				throw new RuntimeException("No state");
			}
		}
	}	
}

