package me.florianschmidt.replication.baseline.jobs;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import me.florianschmidt.replication.baseline.functions.SaveLatencyToFileSink;
import me.florianschmidt.replication.baseline.functions.TransactionSource;
import me.florianschmidt.replication.baseline.model.Record;

import java.util.Random;

public class SkewedKeysJob {

	public static void main(String[] args) throws Exception {
		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args);

		env
				.addSource(new TransactionSource())
					.name("Transaction Generator")
					.uid("TransactionGenerator")
					.slotSharingGroup("sourcesAndSinks")
					.setParallelism(1)
				.map(new RichMapFunction<Record, Record>() {

					transient Random r = new Random();

					@Override
					public Record map(Record value) throws Exception {
						if (r == null) {
							r = new Random();
						}
						value.key = (r.nextDouble() < 0.66) ? "a" : "b";
						return value;
					}
				})
					.name("Add skewed key")
					.slotSharingGroup("sourcesAndSinks")
					.setParallelism(1)
				.keyBy((KeySelector<Record, String>) value -> value.key)
				.map((MapFunction<Record, Record>) value -> value)
					.name("Forwarding Map")
					.slotSharingGroup("maps")
					.setParallelism(2)
				.addSink(new SaveLatencyToFileSink())
					.setParallelism(1)
					.slotSharingGroup("sourcesAndSinks");

		env.execute();
	}
}
