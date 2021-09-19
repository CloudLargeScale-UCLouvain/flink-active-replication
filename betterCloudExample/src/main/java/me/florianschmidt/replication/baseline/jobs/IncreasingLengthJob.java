package me.florianschmidt.replication.baseline.jobs;

import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import me.florianschmidt.replication.baseline.functions.BusyWaitRateFunction;
import me.florianschmidt.replication.baseline.functions.MeasuringSink;
import me.florianschmidt.replication.baseline.functions.NoopMapFunction;
import me.florianschmidt.replication.baseline.functions.Rates;

public class IncreasingLengthJob {

	public static void main(String[] args) throws Exception {
		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args);
		env.setParallelism(1);
		ParameterTool tool = ParameterTool.fromArgs(args);
		int rate = tool.getInt("rate", 1000);
		int parallelism = tool.getInt("map-parallelism", 1);
		int numberOfStages = tool.getInt("length", 1);
		Boolean sharingGroup = tool.getBoolean("sharing-group", false);	
		
		System.out.println("Using rate " + rate);
		DataStream<Record> stream = env.addSource(new BusyWaitRateFunction(Rates.constant(rate), "source-1.csv"))
				.slotSharingGroup("default")
				.name("source-1");

		for (int i = 0; i < numberOfStages; i++) {
			stream = stream
					.rebalance()
					.map(new NoopMapFunction<>())
					.setParallelism(parallelism)
					.name("map-at-level-" + i)
					.slotSharingGroup(sharingGroup ? "map-at-level-" + i : "default")
					.disableChaining();
		}

		stream
				.addSink(new MeasuringSink("sink.csv"))
				.slotSharingGroup("default");

		env.execute("Job");
	}
}
