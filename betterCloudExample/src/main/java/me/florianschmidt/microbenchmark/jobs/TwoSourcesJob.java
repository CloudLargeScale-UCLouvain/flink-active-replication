package me.florianschmidt.microbenchmark.jobs;

import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import me.florianschmidt.replication.baseline.functions.BusyWaitRateFunction;
import me.florianschmidt.replication.baseline.functions.MeasuringSink;
import me.florianschmidt.replication.baseline.functions.NoopMapFunction;
import me.florianschmidt.replication.baseline.functions.Rates;
import me.florianschmidt.replication.baseline.jobs.EnvBuilder;
import me.florianschmidt.replication.baseline.jobs.Record;

import me.florianschmidt.microbenchmark.functions.MicrobenchmarkVariableRateSource;

public class TwoSourcesJob {

	public static void main(String[] args) throws Exception {
		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args);
		env.setParallelism(1);
		ParameterTool tool = ParameterTool.fromArgs(args);
		Boolean sharingGroup = tool.getBoolean("sharing-group", false);	

		int rate1 = tool.getInt("rate");
		int rate2 = tool.getInt("rate2");
		String pattern1 = tool.get("pattern1", "");
		String pattern2 = tool.get("pattern2", "");

		System.out.printf("Using rates %d (pattern:%s) and %d (pattern:%s) %n", rate1, pattern1, rate2, pattern2);
		DataStream<Record> first = env
				.addSource(new MicrobenchmarkVariableRateSource(rate1, "source-1.csv", pattern1))
				.name("source-1")
				.slotSharingGroup("measuring");
		DataStream<Record> second = env
				.addSource(new MicrobenchmarkVariableRateSource(rate2, "source-2.csv", pattern2))
				.name("source-2")
				.slotSharingGroup("measuring");

		first.union(second)
				.map(new NoopMapFunction<>())
				.slotSharingGroup(sharingGroup ? "maps" : "default")
				.name("noop-map-function")
				.addSink(new MeasuringSink("sink.csv"))
				.slotSharingGroup("measuring");

		env.execute("");
	}
}

