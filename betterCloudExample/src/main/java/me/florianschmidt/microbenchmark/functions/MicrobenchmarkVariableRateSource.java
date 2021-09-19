package me.florianschmidt.microbenchmark.functions;

import com.google.common.collect.Lists;
import org.apache.flink.streaming.api.checkpoint.ListCheckpointed;
import java.util.Random;
import java.util.List;
import me.florianschmidt.examples.bettercloud.datagen.Cooperation;
import me.florianschmidt.examples.bettercloud.rulesengine.Rule;
import me.florianschmidt.microbenchmark.functions.VariableRateSource;
import me.florianschmidt.replication.baseline.jobs.Record;


public class MicrobenchmarkVariableRateSource extends VariableRateSource<Record> implements ListCheckpointed<Record> {
	private Integer count;
	private static final transient Random random = new Random();
	private String name;

	private Record lastRecord = null;
	private Record restoredRecord = null;

    public MicrobenchmarkVariableRateSource(int rate, String name, String pattern) {
		super(rate, pattern);
		this.name = name;
        this.count = 0;

    }

	@Override
	public void doCollect(SourceContext<Record> ctx) {
		Record r = null;
		if (this.restoredRecord != null) {
			r = this.restoredRecord;
			this.restoredRecord = null;
		} 
		if (r == null) {
			r = new Record();
			r.key = "key-" + random.nextInt(10_000);
			r.id = String.format("%s-%010d", this.name, this.count);
			r.origin = name;
			r.createdAtNanos = System.nanoTime();
			r.createdAtMillis = System.currentTimeMillis();
			r.count = count;
		}
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
				this.count = restoredRecord.count;
				this.restoredRecord = restoredRecord;
			} else {
				throw new RuntimeException("No state");
			}
		}
	}		
}

