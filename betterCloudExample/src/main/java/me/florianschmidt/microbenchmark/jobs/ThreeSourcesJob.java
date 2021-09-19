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

public class ThreeSourcesJob {

	public static void main(String[] args) throws Exception {
		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args);
		env.setParallelism(1);
		ParameterTool tool = ParameterTool.fromArgs(args);
		Boolean sharingGroup = tool.getBoolean("sharing-group", false);	

		int numberOfStages = tool.getInt("length", 1);				
		int parallelism = tool.getInt("map-parallelism", 1);

		int rate1 = tool.getInt("rate");
		int rate2 = tool.getInt("rate2");
		String pattern1 = tool.get("pattern1", "");
		String pattern2 = tool.get("pattern2", "");
		String pattern3 = tool.get("pattern3", pattern2);

		System.out.printf("Using rates %d (pattern:%s) and %d (pattern:%s) %n", rate1, pattern1, rate2, pattern2);
		DataStream<Record> first = env
				.addSource(new MicrobenchmarkVariableRateSource(rate1, "A", pattern1))
				.name("source-1")
				.slotSharingGroup("measuring");
		DataStream<Record> second = env
				.addSource(new MicrobenchmarkVariableRateSource(rate2, "B", pattern2))
				.name("source-2")
				.slotSharingGroup("measuring");

		DataStream<Record> third = env
				.addSource(new MicrobenchmarkVariableRateSource(rate2, "C", pattern3))
				.name("source-3")
				.slotSharingGroup("measuring");


		DataStream <Record> unionTask = first.union(second, third)
				.map(new NoopMapFunction<>())
				.slotSharingGroup(sharingGroup ? "maps" : "default")
				.name("noop-map-function");
		
		for (int i = 0; i < numberOfStages; i++) {
			unionTask = unionTask
					.rebalance()
					.map(new NoopMapFunction<>())
					.setParallelism(parallelism)
					.name("map-at-level-" + i)			
					.slotSharingGroup(sharingGroup ? "map-at-level-" + i : "default")
					.disableChaining();
		}					
		unionTask.addSink(new MeasuringSink("sink.csv"))
			 .slotSharingGroup("measuring");

		env.execute("ThreeSourcesJob");
	}
}

