package me.florianschmidt.examples.bettercloud;

import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.configuration.Configuration;

import java.io.BufferedWriter;
import java.io.FileWriter;

import me.florianschmidt.replication.baseline.jobs.EnvBuilder;
import me.florianschmidt.replication.baseline.jobs.Record;

import java.util.Properties;

// TODO: Timings and thresholds
// - every time
// - after over threshold
// if this occurs more than X times for a single Y (e.g. user) during a window of X, Y, Z

public class DataGeneratorJob {

	public static final String CONTROL_EVENT_TOPIC = "control-events";
	public static String CONSUMER_EVENT_TOPIC = "consumer-events";
	public static final String RESULT_EVENT_TOPIC = "result-events";

	public static void main(String[] args) throws Exception {
		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args, false);

		ParameterTool tool = ParameterTool.fromArgs(args);
		int consumerEventRate = tool.getInt("consumer-event-rate", 5_000);
		int controlEventRate = tool.getInt("control-event-rate", 5);

		int seed = tool.getInt("seed", 0);

		Properties p = new Properties();
		Boolean sharingGroup = tool.getBoolean("sharing-group", false);

		Properties pInjector = new Properties();
		pInjector.setProperty("bootstrap.servers", tool.get("injector.kafka.servers"));
		pInjector.setProperty("group.id", "injector");

		env.addSource(new MockCustomerEventSource(consumerEventRate, seed))
				.name("CustomerEventGenerator")
				.slotSharingGroup(sharingGroup ? "source-customer-generator" : "default")
				.addSink(new FlinkKafkaProducer<>(CONSUMER_EVENT_TOPIC, new CustomerEventSchema(), pInjector))
				.slotSharingGroup(sharingGroup ? "sink-customer-generator" : "default")
				.name("CustomerEventKafkaSink");

		env.addSource(new MockControlEventSource(controlEventRate, seed))
				.name("ControlEventGenerator")
				.slotSharingGroup(sharingGroup ? "source-control-generator" : "default")
				.addSink(new FlinkKafkaProducer<>(CONTROL_EVENT_TOPIC, new ControlEventSchema(), pInjector))
				.slotSharingGroup(sharingGroup ? "sink-control-generator" : "default")
				.name("ControlEventKafkaSink");

		FlinkKafkaConsumer<QualifiedEvent> resultEventConsumer
				= new FlinkKafkaConsumer<>(RESULT_EVENT_TOPIC, new QualifiedEventSchema(), pInjector);
		env.addSource(resultEventConsumer)
			.slotSharingGroup(sharingGroup ? "source-control-generator" : "default")
			.addSink(new RichSinkFunction<QualifiedEvent>() {

					private BufferedWriter writer;

					@Override
					public void open(Configuration parameters) throws Exception {
						FileWriter w = new FileWriter("/tmp/latencies.csv", false);
						this.writer = new BufferedWriter(w);
					}

					@Override
					public void invoke(QualifiedEvent value, Context context) throws Exception {
						if (this.writer == null) {
							this.writer = new BufferedWriter(new FileWriter("/tmp/latencies.txt", false));
						}
						long now = System.currentTimeMillis();
						this.writer.write(value.event.ingestedAt + "," + value.event.receivedAt + "," + value.sinkAt + "," + now + "," + (now - value.event.ingestedAt) + "," + (value.sinkAt - value.event.receivedAt) +  String.format("%n"));
						this.writer.flush();
					}
				})
			.slotSharingGroup(sharingGroup ? "sink-control-generator" : "default");

		env.execute("DataGenJob");
	}

}
