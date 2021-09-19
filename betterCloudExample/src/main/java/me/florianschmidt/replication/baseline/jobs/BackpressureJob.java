package me.florianschmidt.replication.baseline.jobs;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import me.florianschmidt.replication.baseline.functions.BusyWaitRateFunction;
import me.florianschmidt.replication.baseline.functions.MeasuringSink;
import me.florianschmidt.replication.baseline.functions.Rates;

public class BackpressureJob {

	public static void main(String[] args) throws Exception {

		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args);
		env.setParallelism(1);
		ParameterTool tool = ParameterTool.fromArgs(args);

		int rate1 = tool.getInt("rate-1");
		int rate2 = tool.getInt("rate-2");
		int waitMs = tool.getInt("wait");

		DataStream<Record> first = env
				.addSource(new BusyWaitRateFunction(Rates.constant(rate1), "source-1.csv"))
				.name("source-1")
				.slotSharingGroup("measuring");

		DataStream<Record> second = env
				.addSource(new BusyWaitRateFunction(Rates.constant(rate2), "source-2.csv"))
				.name("source-2")
				.slotSharingGroup("measuring");

		first.union(second)
				.map(new BusyWaitMap(waitMs))
				.slotSharingGroup("maps")
				.name("noop-map-function")
				.addSink(new MeasuringSink("sink.csv"))
				.slotSharingGroup("measuring");

		env.execute("Backpressure Job");
	}

	public static class BusyWaitMap implements MapFunction<Record, Record> {

		private final long waitMs;

		public BusyWaitMap(long waitMs) {
			this.waitMs = waitMs;
		}

		@Override
		public Record map(Record value) throws Exception {
			// sleep 30ms
			long start = System.nanoTime();
			long end;
			do {
				end = System.nanoTime();
			} while (start + (1_000_000 * waitMs) >= end);
			return value;
		}
	}
}
