package me.florianschmidt.replication.baseline.jobs;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

import me.florianschmidt.replication.baseline.functions.BusyWaitRateFunction;
import me.florianschmidt.replication.baseline.functions.MeasuringSink;
import me.florianschmidt.replication.baseline.functions.Rates;

@SuppressWarnings("Duplicates")
public class FlatMapJob {

	public static void main(String[] args) throws Exception {

		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args);
		env.setParallelism(1);
		ParameterTool tool = ParameterTool.fromArgs(args);

		int rate = tool.getInt("rate", 1000);

		System.out.println("Using rate " + rate);
		DataStream<Record> stream = env.addSource(new BusyWaitRateFunction(Rates.constant(rate), "source-1.csv"))
				.slotSharingGroup("measuring")
				.name("source-1");

		stream
				.flatMap(new FlatMapFunction<Record, Record>() {
					@Override
					public void flatMap(Record value, Collector<Record> out) {
						for (int i = 0; i < 10; i++) {
							out.collect(value);
						}
					}
				})
				.slotSharingGroup("maps")
				.name("flatmap-function")
				.setParallelism(2)
				.addSink(new MeasuringSink("sink.csv"))
				.slotSharingGroup("measuring");

		env.execute("Flatmap Job");
	}
}
