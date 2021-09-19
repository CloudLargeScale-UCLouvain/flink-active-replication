package org.apache.flink.streaming.runtime.io.replication;

public class MonotonicWallclockTime {

	private static boolean isFirstTime = true;
	private static long last = 0;
	private static long startMilliAsNano;
	private static long startNano;

	public static synchronized long getNanos() {
		if (isFirstTime) {
			startMilliAsNano = System.currentTimeMillis() * 1_000_000;
			startNano = System.nanoTime();
			isFirstTime = false;
		}

		long nanos = startMilliAsNano + (System.nanoTime() - startNano);
		if (nanos <= last) {
			last = last + 1;
			return last;
		} else {
			last = nanos;
			return nanos;
		}
	}
}
