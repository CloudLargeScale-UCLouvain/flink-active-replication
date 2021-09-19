package me.florianschmidt.examples.bettercloud;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeutils.base.StringSerializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.contrib.streaming.state.RocksDBStateBackend;
import org.apache.flink.runtime.state.filesystem.FsStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;

import me.florianschmidt.replication.baseline.functions.MeasuringSink;
import me.florianschmidt.replication.baseline.jobs.EnvBuilder;
import me.florianschmidt.replication.baseline.jobs.Record;
import me.florianschmidt.replication.baseline.jobs.TestRecoveryTimeDataGen;

import java.util.Properties;

public class TestRecoveryTimeJob {

	public static void main(String[] args) throws Exception {

		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args, true);
		env.setParallelism(1);

//		env.setStateBackend(new MemoryStateBackend());
//		env.enableCheckpointing(100, CheckpointingMode.EXACTLY_ONCE);

		String FIRST_TOPIC = "first-topic";
		String SECOND_TOPIC = "second-topic";

		Properties p = new Properties();
		p.setProperty("bootstrap.servers", "flink-thesis-kafka.default.svc.cluster.local:9092");
		p.setProperty("group.id", "recoverygroup");

//		env.enableCheckpointing(100, CheckpointingMode.EXACTLY_ONCE);
//		env.setStateBackend(new RocksDBStateBackend(new FsStateBackend("gs://florian-master-thesis-1337/checkpoints")));

//		env.getConfig().setOrderingAlgorithm(ExecutionConfig.OrderingAlgorithm.LEADER_KAFKA);

		FlinkKafkaConsumer<Record> c1 = new FlinkKafkaConsumer<>(FIRST_TOPIC, new TestRecoveryTimeDataGen.RecordSchema(), p);
		c1.setStartFromLatest();
		DataStream<Record> first = env.addSource(c1).slotSharingGroup("sources-and-sinks");

		FlinkKafkaConsumer<Record> c2 = new FlinkKafkaConsumer<>(SECOND_TOPIC, new TestRecoveryTimeDataGen.RecordSchema(), p);
		c2.setStartFromLatest();
		DataStream<Record> second = env.addSource(c2).slotSharingGroup("sources-and-sinks");

		first.union(second)
				.keyBy(record -> record.getKey())
				.map(new RichMapFunction<Record, Record>() {

					private MapState<String, String> state;

					@Override
					public void open(Configuration parameters) throws Exception {
						this.state = getRuntimeContext().getMapState(new MapStateDescriptor<>("some-state", StringSerializer.INSTANCE, StringSerializer.INSTANCE));
					}

					@Override
					public Record map(Record value) throws Exception {
						this.state.put(value.origin, String.valueOf(value.id));
						return value;
					}
				}).setParallelism(2)
				.slotSharingGroup("maps")
				.addSink(new MeasuringSink("sink.csv"))
				.slotSharingGroup("sources-and-sinks");

		env.execute("Recover me");
	}
}
