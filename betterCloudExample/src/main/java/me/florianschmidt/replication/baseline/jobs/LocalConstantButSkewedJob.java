package me.florianschmidt.replication.baseline.jobs;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import me.florianschmidt.replication.baseline.functions.BusyWaitRateFunction;
import me.florianschmidt.replication.baseline.functions.MeasuringSink;
import me.florianschmidt.replication.baseline.functions.NoopMapFunction;
import me.florianschmidt.replication.baseline.functions.Rates;

public class LocalConstantButSkewedJob {

	public static void main(String[] args) throws Exception {
		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args, true);
		env.setParallelism(1);

//		env.getConfig().setZkServer("localhost:2181");
//		env.getConfig().setKafkaServer("localhost:9092");
//		env.getConfig().setOrderingAlgorithm(ExecutionConfig.OrderingAlgorithm.LEADER_KAFKA);
//		env.getConfig().setKafkaBatchSize(1000);
//
		ParameterTool tool = ParameterTool.fromArgs(args);

		int rate1 = tool.getInt("rate-1");
		int rate2 = tool.getInt("rate-2");

		DataStream<Record> first = env
				.addSource(new BusyWaitRateFunction(Rates.constant(rate1), "source-1.csv"))
				.name("source-1")
				.slotSharingGroup("measuring");
		DataStream<Record> second = env
				.addSource(new BusyWaitRateFunction(Rates.constant(rate2), "source-2.csv"))
				.name("source-2")
				.slotSharingGroup("measuring");

		first.union(second)
				.map(new NoopMapFunction<>())
				.slotSharingGroup("maps")
				.name("noop-map-function")
				.addSink(new MeasuringSink("sink.csv"))
				.slotSharingGroup("measuring");

		env.execute("");
	}
}
