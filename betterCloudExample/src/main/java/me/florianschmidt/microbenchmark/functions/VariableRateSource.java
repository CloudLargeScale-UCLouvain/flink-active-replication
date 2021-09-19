package me.florianschmidt.microbenchmark.functions;
import java.util.Random;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.apache.flink.api.java.utils.ParameterTool;

public abstract class VariableRateSource<T> extends RichSourceFunction<T> {

	public static final double NANOS_IN_SEC = 1000.0 * 1000.0 * 1000.0;

	private volatile boolean isRunning = true;

	private long pauseNanos;
	private long emitted;
	private String pattern;
	protected double targetRate;
	private Map<Long, Float> mapPattern;
	private List<Long> changeMoments;
	private long nanoCreation;
	private int indexMoment;

	public VariableRateSource(long initialRate, String pattern) {
		this.targetRate = initialRate;
		this.pattern = pattern;
		this.pauseNanos = (long) (NANOS_IN_SEC / (double) targetRate);
		System.out.println("Initialized with pause time " + pauseNanos);
	}

	@Override
	public void run(SourceContext<T> ctx) throws Exception {
        nanoCreation = System.nanoTime();
        
		List<String> elements;
        this.mapPattern = new HashMap<Long,Float>();
        this.changeMoments = new ArrayList<Long>();
		System.out.println("Pattern:" + pattern);
		if (pattern.equals("__NO_VALUE_KEY")) {
			elements = new ArrayList<String>();
		} else {
			elements = Arrays.asList(pattern.split(","));

			System.out.println(elements);
			for (String element : elements) {
				System.out.println(element);
				long moment = this.nanoCreation + (long) (Float.parseFloat(element.split(":")[0]) * 1e9);
				mapPattern.put(moment, Float.parseFloat(element.split(":")[1]));
				changeMoments.add(moment);
			}
			Collections.sort(changeMoments, new Comparator<Long>() {
				@Override
				public int compare(Long o1, Long o2) {
					return Long.compare(o1, o2);
				}
			});
		}		
		this.indexMoment = 0;

		long lastUpdate = System.nanoTime();
		boolean change = false;
		while (isRunning) {

			busyWait(pauseNanos);
			synchronized (ctx.getCheckpointLock()) {
				emitted++;
				doCollect(ctx);
				if (this.indexMoment < changeMoments.size()) {
					if (System.nanoTime() >= this.changeMoments.get(this.indexMoment).longValue()) {
						this.targetRate = mapPattern.get(this.changeMoments.get(this.indexMoment));
						System.out.println("Change to rate:" + this.targetRate);
						this.pauseNanos = (long) (NANOS_IN_SEC / (double) targetRate);
						System.out.println("Initialized with pause time " + pauseNanos);
						change = true;
						this.indexMoment++;
					}
				}				
			}


			if ((emitted % (targetRate * 2) == 0) || (change == true)) {
				change = false;
				long now = System.nanoTime();
				long nanoDuration = now - lastUpdate;
				double ratio = (targetRate / NANOS_IN_SEC) / ((double) emitted / nanoDuration);

				this.pauseNanos = (int) Math.ceil((double) this.pauseNanos / ratio);
				System.out.println("Updated pause to " + pauseNanos);
				// reset
				emitted = 0;
				lastUpdate = now;
			}
		}
	}

	private void busyWait(long pauseNanos) {
		long start = System.nanoTime();
		long end;
		do {
			end = System.nanoTime();
		} while (start + pauseNanos >= end);
	}

	@Override
	public void cancel() {
		this.isRunning = false;
	}

	public abstract void doCollect(SourceContext<T> ctx) throws Exception;
}
