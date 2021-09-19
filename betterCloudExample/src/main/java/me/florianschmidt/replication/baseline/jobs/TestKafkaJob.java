package me.florianschmidt.replication.baseline.jobs;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;

import me.florianschmidt.replication.baseline.functions.BusyWaitRateFunction;
import me.florianschmidt.replication.baseline.functions.MeasuringSink;
import me.florianschmidt.replication.baseline.functions.Rates;

public class TestKafkaJob {

	public static void main(String[] args) throws Exception {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.createRemoteEnvironment("localhost", 8081, "target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar");

//		env.getConfig().setOrderingAlgorithm(ExecutionConfig.OrderingAlgorithm.LEADER_KAFKA);
//		env.getConfig().setKafkaServer("localhost:9092");
//		env.getConfig().setZkServer("localhost:2181");

		env.addSource(new BusyWaitRateFunction(Rates.constant(1000), "hi"))
				.setParallelism(1)
				.map(new MapFunction<Record, Record>() {
					@Override
					public Record map(Record value) throws Exception {
//						System.out.println(Thread.currentThread().getName() + " " + value);
						return value;
					}
				})
				.name("first-map")
				.setParallelism(2)
				.disableChaining()
				.map(new MapFunction<Record, Record>() {
					@Override
					public Record map(Record value) throws Exception {
//						System.out.println(Thread.currentThread().getName() + " " + value);
						return value;
					}
				})
				.name("second-map")
				.setParallelism(2)
				.addSink(new MeasuringSink("kafka-sink"));

		env.execute("KafkaTestJob");

	}
}
