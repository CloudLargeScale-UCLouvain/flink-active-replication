package me.florianschmidt.examples.bettercloud;

import org.apache.flink.api.java.utils.ParameterTool;
import me.florianschmidt.replication.baseline.jobs.EnvBuilder;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.memory.MemoryStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.apache.flink.util.TernaryBoolean;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static me.florianschmidt.examples.bettercloud.DataGeneratorJob.CONSUMER_EVENT_TOPIC;
import static me.florianschmidt.examples.bettercloud.DataGeneratorJob.CONTROL_EVENT_TOPIC;
import static me.florianschmidt.examples.bettercloud.DataGeneratorJob.RESULT_EVENT_TOPIC;
import java.util.UUID;

@SuppressWarnings("Convert2MethodRef")
public class Job {

	public static final String GLOBAL_CUSTOMER_ID = "deadbeef-dead-beef-dead-beefdeadbeef";
	private static final String GS_CHECKPOINT_PATH = "gs://florian-master-thesis-1337/flink-checkpoints";

	public static void main(String[] args) throws Exception {
//		StreamExecutionEnvironment env = StreamExecutionEnvironment.createRemoteEnvironment("localhost", 8081, "target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar");
		//StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args);

		// TODO: Rate to parallelism, how should such a job be scaled?

		env.setParallelism(1);
		//env.getConfig().setOrderingAlgorithm(ExecutionConfig.OrderingAlgorithm.LEADER_KAFKA);
//		env.getConfig().setOrderingAlgorithm(ExecutionConfig.OrderingAlgorithm.BETTER_BIAS);
//		env.getConfig().setKafkaServer("localhost:9092");
//		env.getConfig().setZkServer("localhost:2181");

//		TernaryBoolean useIncremental = TernaryBoolean.fromBoolean(true);
//		env.setStateBackend(new MemoryStateBackend());
//		env.enableCheckpointing(10_000, CheckpointingMode.EXACTLY_ONCE);

		// TODO: Setup kafka
		ParameterTool tool = ParameterTool.fromArgs(args);
		Properties p = new Properties();

		Boolean sharingGroup = tool.getBoolean("sharing-group", false);
		int parallelism = tool.getInt("map-parallelism", 1);
		String id = UUID.randomUUID().toString();
		Properties pInjector = new Properties();
		pInjector.setProperty("bootstrap.servers", tool.get("injector.kafka.servers"));
		pInjector.setProperty("group.id", "injector-customer-" + id);


		FlinkKafkaConsumer<Optional<CustomerEvent>> eventConsumer
				= new FlinkKafkaConsumer<>(CONSUMER_EVENT_TOPIC, new CustomerEventSchema(), pInjector);
		eventConsumer.setStartFromLatest();

		pInjector = new Properties();
		pInjector.setProperty("bootstrap.servers", tool.get("injector.kafka.servers"));
		pInjector.setProperty("group.id", "injector-control-" + id);
		FlinkKafkaConsumer<Optional<ControlEvent>> controlEventConsumer
				= new FlinkKafkaConsumer<>(CONTROL_EVENT_TOPIC, new ControlEventSchema(), pInjector);
		controlEventConsumer.setStartFromLatest();

		final OutputTag<ControlEvent> globalEventTag = new OutputTag<ControlEvent>("side-output") {
		};

		/*
		 * Ingest a stream of Optional<CustomerEvent> and filter those
		 * where no value is present, for example because it could not be
		 * deserialized
		 */
		SingleOutputStreamOperator<CustomerEvent> eventStream = env
				.addSource(eventConsumer)
				.name("CustomerEventSource")
				.slotSharingGroup(sharingGroup ? "source-event" : "default")
				.filter(customerEventOptional -> customerEventOptional.isPresent())
				.name("FilterInvalidCustomerEvents")
				.slotSharingGroup(sharingGroup ? "source-event-processing" : "default")
				.map(customerEventOptional -> customerEventOptional.get())
				.name("UnwrapOptionalCustomerEvent")
				.slotSharingGroup(sharingGroup ? "source-event-processing" : "default");

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
				.slotSharingGroup(sharingGroup ? "source-control" : "default")
				.filter(controlEventOptional -> controlEventOptional.isPresent())
				.name("FilterInvalidControlEvents")
				.slotSharingGroup(sharingGroup ? "source-control-processing" : "default")
				.map(controlEventOptional -> controlEventOptional.get())
				.name("UnwrapOptionalControlEvent")
				.slotSharingGroup(sharingGroup ? "source-control-processing" : "default")
				.process(new GlobalControlEventsToSideOutput(globalEventTag))
				.slotSharingGroup(sharingGroup ? "source-control-processing" : "default");

		/*
		 * The stream of ControlEvents where the customerId equals the GLOBAL_CUSTOMER_ID,
		 * and therefore should be evaluated for all customers
		 */
		DataStream<ControlEvent> globalControlStream = perCustomerControlStream.getSideOutput(globalEventTag);

		/*
		 * Connect the stream of non-global control events with the stream of customer events
		 * and key them by the customerId.
		 * This allows us to combine each CustomerEvent with a list of ControlEvents seen for
		 * that customer so far, which are forwarded as FilteredEvents
		 */
		SingleOutputStreamOperator<FilteredEvent> perCustomerFilteredEvents = perCustomerControlStream
				.connect(eventStream)
				.keyBy(controlEvent -> controlEvent.customerId, event -> event.customerId)
				.flatMap(new JoinControlAndCustomerEvents())
				.name("JoinControlAndCustomerEvents")
				.slotSharingGroup(sharingGroup ? "JoinControlAndCustomerEvents" : "default");

		MapStateDescriptor<String, ControlEvent> broadcastStateDescriptor
				= new MapStateDescriptor<>("global-control-events", Types.STRING, Types.POJO(ControlEvent.class));

		/*
		 * Connect the stream of CustomerEvents with a broadcasted stream of global
		 * ControlEvents. Persist all control events in the broadcast state and for
		 * each customer event forward it with a list of all global control events
		 * seen so far as FilteredEvents
		 */
		SingleOutputStreamOperator<FilteredEvent> globalFilteredEvents = eventStream
				.keyBy(event -> event.customerId)
				.connect(globalControlStream.broadcast(broadcastStateDescriptor))
				.process(new JoinGlobalControlAndCustomerEvents(broadcastStateDescriptor))
				.name("JoinGlobalControlAndCustomerEvents")
				.slotSharingGroup(sharingGroup ? "JoinGlobalControlAndCustomerEvents" : "default");

		/*
		 * Union the list of FilteredEvents that are created from the global and
		 * per customer control events and evaluate each events rules in the
		 * QualifierFunction.
		 * After this key the stream per customer and count the occurrences of
		 * each fired alert per customer and output the count to System.out
		 */
		globalFilteredEvents
				.union(perCustomerFilteredEvents)
//				perCustomerFilteredEvents
				.flatMap(new QualifierFunction())
				.setParallelism(parallelism)
				.name("Qualifier")
				.slotSharingGroup(sharingGroup ? "Qualifier" : "default")
				.keyBy(e -> e.event.customerId)
				.flatMap(new CounterFunction())
				.name("Counter")
				.slotSharingGroup(sharingGroup ? "Counter" : "default")
				.addSink(new FlinkKafkaProducer<>(RESULT_EVENT_TOPIC, new QualifiedEventSchema(), pInjector));
/*
				.addSink(new RichSinkFunction<QualifiedEvent>() {

					private BufferedWriter writer;

					@Override
					public void open(Configuration parameters) throws Exception {
						FileWriter w = new FileWriter("/tmp/latencies.txt", false);
						this.writer = new BufferedWriter(w);
					}

					@Override
					public void invoke(CustomerEvent value, Context context) throws Exception {
						if (this.writer == null) {
							this.writer = new BufferedWriter(new FileWriter("/tmp/latencies.txt", false));
						}

						this.writer.write(" " + (System.currentTimeMillis() - value.event.ingestedAt));
					}
				})
				.slotSharingGroup(sharingGroup ? "sink" : "default")
				;
*/
		env.execute("Job");
	}

	private static class GlobalControlEventsToSideOutput extends ProcessFunction<ControlEvent, ControlEvent> {
		private final OutputTag<ControlEvent> globalEventTag;

		public GlobalControlEventsToSideOutput(OutputTag<ControlEvent> globalEventTag) {
			this.globalEventTag = globalEventTag;
		}

		@Override
		public void processElement(ControlEvent event, Context ctx, Collector<ControlEvent> out) {
			if (event.customerId.equals(GLOBAL_CUSTOMER_ID)) {
				ctx.output(globalEventTag, event);
			} else {
				out.collect(event);
			}
		}
	}

	private static class JoinGlobalControlAndCustomerEvents extends KeyedBroadcastProcessFunction<String, CustomerEvent, ControlEvent, FilteredEvent> {

		private final MapStateDescriptor<String, ControlEvent> broadcastStateDescriptor;

		public JoinGlobalControlAndCustomerEvents(MapStateDescriptor<String, ControlEvent> broadcastStateDescriptor) {
			this.broadcastStateDescriptor = broadcastStateDescriptor;
		}

		@Override
		public void processElement(
				CustomerEvent value,
				ReadOnlyContext ctx,
				Collector<FilteredEvent> out
		) throws Exception {

			List<ControlEvent> allControlEvents = StreamSupport.stream(ctx.getBroadcastState(broadcastStateDescriptor).immutableEntries().spliterator(), false)
					.map((Map.Entry<String, ControlEvent> e) -> e.getValue())
					.collect(Collectors.toList());

			out.collect(new FilteredEvent(value, allControlEvents));
		}

		@Override
		public void processBroadcastElement(
				ControlEvent value,
				Context ctx,
				Collector<FilteredEvent> out
		) throws Exception {
			ctx.getBroadcastState(broadcastStateDescriptor).put(value.alertId, value);
		}
	}
}
