package me.florianschmidt.microbenchmark.jobs;

import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import java.util.Properties;

import me.florianschmidt.replication.baseline.jobs.EnvBuilder;
import me.florianschmidt.replication.baseline.jobs.Record;
import me.florianschmidt.replication.baseline.functions.BusyWaitRateFunction;
import me.florianschmidt.replication.baseline.functions.Rates;

import me.florianschmidt.microbenchmark.functions.RecordSchema;
import me.florianschmidt.microbenchmark.functions.MicrobenchmarkVariableRateSource;


public class GeneratorConstantRateJob {
    public static final String TOPIC_EVENTS1 = "events";
    public static final String TOPIC_EVENTS2 = "events2";

	public static void main(String[] args) throws Exception {
		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args, false);

		ParameterTool tool = ParameterTool.fromArgs(args);
		int rate1 = tool.getInt("consumer-event-rate", 5_000);
		int rate2 = tool.getInt("control-event-rate", 0);
		String pattern1 = tool.get("pattern1", "");
		String pattern2 = tool.get("pattern2", "");

		Properties p = new Properties();
		Boolean sharingGroup = tool.getBoolean("sharing-group", false);

		Properties pInjector = new Properties();
		pInjector.setProperty("bootstrap.servers", tool.get("injector.kafka.servers"));
		pInjector.setProperty("group.id", "injector");

		//env.addSource(new BusyWaitRateFunction(Rates.constant(rate1), "source-1.csv"))
		env.addSource(new MicrobenchmarkVariableRateSource(rate1, "source-1.csv", pattern1))
				.name("Events1")
				.slotSharingGroup(sharingGroup ? "source-generator" : "default")
				.addSink(new FlinkKafkaProducer<>(TOPIC_EVENTS1, new RecordSchema(), pInjector))
				.slotSharingGroup(sharingGroup ? "sink-generator" : "default")
				.name("Events1KafkaSink");

        if (rate2 != 0) {
//            env.addSource(new BusyWaitRateFunction(Rates.constant(rate1), "source-2.csv"))
            env.addSource(new MicrobenchmarkVariableRateSource(rate2, "source-2.csv", pattern2))
                    .name("Events2")
                    .slotSharingGroup(sharingGroup ? "source-generator" : "default")
                    .addSink(new FlinkKafkaProducer<>(TOPIC_EVENTS2, new RecordSchema(), pInjector))
                    .slotSharingGroup(sharingGroup ? "sink-generator" : "default")
                    .name("Events2KafkaSink");
        }
		env.execute("DataGenJob");
    }
   
}