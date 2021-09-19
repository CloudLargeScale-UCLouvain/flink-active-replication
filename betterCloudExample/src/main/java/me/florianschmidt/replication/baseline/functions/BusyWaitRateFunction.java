package me.florianschmidt.replication.baseline.functions;

import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.api.checkpoint.ListCheckpointed;
import me.florianschmidt.replication.baseline.jobs.Record;
import java.util.List;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.common.collect.Lists;
public class BusyWaitRateFunction implements SourceFunction<Record>, ListCheckpointed<Record> {

	private static final int MILLI_TO_NANO = 1_000_000;
	private static final transient Random random = new Random();

	private volatile boolean isRunning = true;

	private final String name;
	private Record lastRecord;
	private Record restoredRecord;
	private final Rate rate;
	private final AtomicInteger pause;
	private final AtomicInteger count;

	private transient BufferedWriter br;
	
	private volatile Exception e;

	public BusyWaitRateFunction(Rate rate, String name) throws IOException {
		this.rate = rate;
		this.pause = new AtomicInteger(rateToPause(1));
		this.count = new AtomicInteger(0);
		this.name = name;
		this.lastRecord = null;
		this.br = new BufferedWriter(new FileWriter("/tmp/" + name));
		this.restoredRecord = null;
	}

	@Override
	public void run(SourceContext<Record> ctx) throws Exception {

		Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable() {

			int tick = 1;
			int count = 0;

			@Override
			public void run() {
				try {
					System.out.println("Running");
					int after = rateToPause(BusyWaitRateFunction.this.rate.update(tick++));
					BusyWaitRateFunction.this.pause.set(after);
					count = BusyWaitRateFunction.this.count.get();
					System.out.println("Elements per tick: " + count);
					BusyWaitRateFunction.this.count.set(0);
				} catch (Exception e) {
					BusyWaitRateFunction.this.e = e;
				}
			}

		}, 1, 1, TimeUnit.SECONDS);


		while (isRunning) {
			if (e != null) {
				throw e;
			}

			if (this.pause.get() != -1) {
				doWork(ctx);
				this.count.getAndIncrement();
				busyWait(this.pause.get());
			} else {
				busyWait(MILLI_TO_NANO);
			}
		}
	}

	@Override
	public void cancel() {
		isRunning = false;
		try {
			this.br.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void doWork(SourceContext<Record> ctx) throws IOException {

		if (br == null) {
			br = new BufferedWriter(new FileWriter("/tmp/" + name));
		}

		Record r = null;
		if (this.restoredRecord != null) {
			restoredRecord = null;
			r = this.restoredRecord;
		} 
		
		if (r == null) {
			int c = BusyWaitRateFunction.this.count.get();
			r = new Record();
			r.key = "key-" + random.nextInt(10_000);
			r.id = this.name + "-elem-" + c;
			r.origin = name;
			r.createdAtNanos = System.nanoTime();
			r.createdAtMillis = System.currentTimeMillis();
			r.count = c;
		}

		synchronized (ctx.getCheckpointLock()) {
			ctx.collect(r);
			this.lastRecord = r;			
		}

		this.br.write(r.toString());
	}

	private int rateToPause(int elementsPerSecond) {

		if (elementsPerSecond == 0) {
			return -1;
		}

		double elementsPerMilli = (double) elementsPerSecond / (1_000_000_000);
		return (int) Math.ceil(1 / elementsPerMilli);
	}

	private void busyWait(long pauseNanos) {
		long start = System.nanoTime();
		long end;
		do {
			end = System.nanoTime();
		} while (start + pauseNanos >= end);
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
				this.restoredRecord = restoredRecord;
				BusyWaitRateFunction.this.count.set(this.restoredRecord.count);
				//logger.info("Restoring source record to {}", restoredRecord.id);
			} else {
				throw new RuntimeException("No state");
			}
		}
	}	
}
