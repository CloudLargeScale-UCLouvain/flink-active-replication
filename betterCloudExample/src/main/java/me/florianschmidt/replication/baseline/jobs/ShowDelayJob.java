package me.florianschmidt.replication.baseline.jobs;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import me.florianschmidt.replication.baseline.functions.BusyWaitRateFunction;
import me.florianschmidt.replication.baseline.functions.MeasuringSink;
import me.florianschmidt.replication.baseline.functions.Rates;

public class ShowDelayJob {

	public static void main(String[] args) throws Exception {

		StreamExecutionEnvironment env = StreamExecutionEnvironment.createRemoteEnvironment("localhost", 8081, "target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar");
//		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

//		env.getConfig().setOrderingAlgorithm(ExecutionConfig.OrderingAlgorithm.BIAS);
//		env.getConfig().enableIdleMarks();

		SingleOutputStreamOperator<Record> source1 = env.addSource(new BusyWaitRateFunction(Rates.constant(1000), "source1.csv"))
				.name("100 records / s");

		SingleOutputStreamOperator<Record> source2 = env.addSource(new BusyWaitRateFunction(Rates.constant(1000), "source2.csv"))
				.name("100 records / s");

		source1.union(source2)
//				.map(new MapFunction<Record, Record>() {
//					@Override
//					public Record map(Record s) throws Exception {
//						return s;
//					}
//				})
//				.setParallelism(1)
//				.disableChaining()
				.addSink(new MeasuringSink("sink.csv")).setParallelism(1);

		env.execute("Test delays");
	}

}