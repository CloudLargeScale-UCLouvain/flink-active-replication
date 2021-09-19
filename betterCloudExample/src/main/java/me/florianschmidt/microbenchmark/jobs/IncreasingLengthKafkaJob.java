package me.florianschmidt.microbenchmark.jobs;

import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import java.util.UUID;
import java.util.Properties;

import me.florianschmidt.replication.baseline.functions.BusyWaitRateFunction;
import me.florianschmidt.replication.baseline.functions.MeasuringSink;
import me.florianschmidt.replication.baseline.functions.NoopMapFunction;
import me.florianschmidt.replication.baseline.functions.Rates;
import me.florianschmidt.microbenchmark.functions.RecordSchema;

import me.florianschmidt.replication.baseline.jobs.EnvBuilder;
import me.florianschmidt.replication.baseline.jobs.Record;

public class IncreasingLengthKafkaJob {
    public static final String TOPIC_EVENTS1 = "events";

	public static void main(String[] args) throws Exception {
		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args);
		env.setParallelism(1);
		ParameterTool tool = ParameterTool.fromArgs(args);
		int rate = tool.getInt("rate", 1000);
		int parallelism = tool.getInt("map-parallelism", 1);
		int numberOfStages = tool.getInt("length", 1);
		Boolean sharingGroup = tool.getBoolean("sharing-group", false);	

		String id = UUID.randomUUID().toString();
		Properties pInjector = new Properties();
		pInjector.setProperty("bootstrap.servers", tool.get("injector.kafka.servers"));
		pInjector.setProperty("group.id", "injector-customer-" + id);

		System.out.println("Using rate " + rate);

		FlinkKafkaConsumer<Record> eventsSource
				= new FlinkKafkaConsumer<>(TOPIC_EVENTS1, new RecordSchema(), pInjector);
		eventsSource.setStartFromLatest();

		DataStream<Record> stream = env.addSource(eventsSource)
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
