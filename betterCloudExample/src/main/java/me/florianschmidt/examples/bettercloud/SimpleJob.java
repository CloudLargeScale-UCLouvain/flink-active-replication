package me.florianschmidt.examples.bettercloud;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.java.utils.ParameterTool;
import me.florianschmidt.replication.baseline.jobs.EnvBuilder;
import org.apache.flink.contrib.streaming.state.RocksDBStateBackend;
import org.apache.flink.runtime.state.filesystem.FsStateBackend;
import org.apache.flink.runtime.state.memory.MemoryStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.util.TernaryBoolean;

import java.util.Properties;

import static me.florianschmidt.examples.bettercloud.DataGeneratorJob.CONSUMER_EVENT_TOPIC;
import static me.florianschmidt.examples.bettercloud.DataGeneratorJob.CONTROL_EVENT_TOPIC;

@SuppressWarnings({"Convert2MethodRef", "Duplicates"})
public class SimpleJob {

	public static final String GLOBAL_CUSTOMER_ID = "deadbeef-dead-beef-dead-beefdeadbeef";
	private static final String GS_CHECKPOINT_PATH = "gs://florian-master-thesis-1337/flink-checkpoints";
	private static final String LOCAL_CP_PATH = "file:///temp/flink-cp/";

	public static void main(String[] args) throws Exception {
		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args);
//		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		// TODO: Rate to parallelism, how should such a job be scaled?

		env.setParallelism(1);
//		env.getConfig().setOrderingAlgorithm(ExecutionConfig.OrderingAlgorithm.LEADER_KAFKA);
//		env.getConfig().setOrderingAlgorithm(ExecutionConfig.OrderingAlgorithm.BETTER_BIAS);
//		env.getConfig().setKafkaServer("localhost:9092");
//		env.getConfig().setZkServer("localhost:2181");
//		env.getConfig().setOrderingAlgorithm(ExecutionConfig.OrderingAlgorithm.BETTER_BIAS);
//		env.getConfig().setKafkaServer("localhost:9092");
//		env.getConfig().setZkServer("localhost:2181");

//		TernaryBoolean useIncremental = TernaryBoolean.fromBoolean(true);
		env.setStateBackend(new RocksDBStateBackend(new FsStateBackend(GS_CHECKPOINT_PATH)));
//		env.setStateBackend(new MemoryStateBackend());
		env.enableCheckpointing(10_000, CheckpointingMode.EXACTLY_ONCE);

		// TODO: Setup kafka
		ParameterTool tool = ParameterTool.fromArgs(args);
		Properties p = new Properties();
		p.setProperty("bootstrap.servers", tool.get("kafka.servers"));
//		p.setProperty("bootstrap.servers", "localhost:9092");
		p.setProperty("group.id", "test");

		FlinkKafkaConsumer<Optional<CustomerEvent>> eventConsumer
				= new FlinkKafkaConsumer<>(CONSUMER_EVENT_TOPIC, new CustomerEventSchema(), p);
		eventConsumer.setStartFromLatest();

		FlinkKafkaConsumer<Optional<ControlEvent>> controlEventConsumer
				= new FlinkKafkaConsumer<>(CONTROL_EVENT_TOPIC, new ControlEventSchema(), p);
		eventConsumer.setStartFromLatest();

		/*
		 * Ingest a stream of Optional<CustomerEvent> and filter those
		 * where no value is present, for example because it could not be
		 * deserialized
		 */
		SingleOutputStreamOperator<CustomerEvent> eventStream = env
				.addSource(eventConsumer)
				.name("CustomerEventSource")
				.filter(customerEventOptional -> customerEventOptional.isPresent())
				.name("FilterInvalidCustomerEvents")
				.map(customerEventOptional -> customerEventOptional.get())
				.name("UnwrapOptionalCustomerEvent");

		/*
		 * Ingest a stream of Optional<ControlEvent> and filter those
		 * where no value is present, for example because it could not be
		 * deserialized.
		 * After this split the output in such a way that all control events
		 * with the customerId GLOBAL_CUSTOMER_ID are emitted as side output,
		 * whereas all others are just forwarded "regularly"
		 */
		SingleOutputStreamOperator<ControlEvent> perCustomerControlStream = env
				.addSource(controlEventConsumer)
				.name("ControlEventSource")
				.filter(controlEventOptional -> controlEventOptional.isPresent())
				.name("FilterInvalidControlEvents")
				.map(controlEventOptional -> controlEventOptional.get())
				.name("UnwrapOptionalControlEvent");

		/*
		 * Union the list of FilteredEvents that are created from the global and
		 * per customer control events and evaluate each events rules in the
		 * QualifierFunction.
		 * After this key the stream per customer and count the occurrences of
		 * each fired alert per customer and output the count to System.out
		 */
		perCustomerControlStream
				.connect(eventStream)
				.keyBy(controlEvent -> controlEvent.customerId, event -> event.customerId)
				.flatMap(new JoinControlAndCustomerEvents())
				.name("JoinControlAndCustomerEvents")
				.flatMap(new QualifierFunction())
				.name("Qualifier")
				.slotSharingGroup("Qualifier")
				.keyBy(e -> e.event.customerId)
				.flatMap(new CounterFunction())
				.name("Counter").slotSharingGroup("sources-and-sinks");
		env.execute("BetterCloud Flink Forward");
	}
}
