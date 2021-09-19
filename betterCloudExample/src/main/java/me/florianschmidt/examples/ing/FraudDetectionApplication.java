package me.florianschmidt.examples.ing;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.RichCoFlatMapFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.util.Collector;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.apache.flink.streaming.api.TimeCharacteristic.IngestionTime;

public class FraudDetectionApplication {


	public static void main(String[] args) throws Exception {

		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setStreamTimeCharacteristic(IngestionTime);
		env.setParallelism(4);

		SourceFunction<SimpleTransaction> simpleTransactionSource
				= new ThrottlingSource(new SimpleTransactionSource(), 1);

		SingleOutputStreamOperator<UuidTransaction> uuidStream = env
				/*
				 * Read transactions from a source
				 * TODO: This might be changed to read from an actual source
				 * 		 instead of this very simplistic generator
				 */
				.addSource(simpleTransactionSource)
				/*
				 * Add a unique UUID to each transaction and transform the transaction
				 * from a class into a Map<String, Object> for use downstream. This also
				 * attaches the number of expected features (which are needed later when
				 * joining the calculated features back together)
				 */
				.map(new ToUUIDTransaction());

		SingleOutputStreamOperator<Feature> featureStream = uuidStream
				/*
				 * TODO: Clean this explanation up / make it completely right
				 * Emit an enriched ArtificalKeyEvent for each defined key
				 * in the manager (in this case "customerId" and "cardId")
				 * Each event equals a feature, where the information on how
				 * to handle this feature is included in the payload of the event
				 */
				.flatMap(new AddArtificalKeyAndFeatureDefinition())
				/*
				 * The artificial key in our example is either
				 * "customerId=alice" (or bob, ...) or
				 * "cardId=0" (or 1, ...)
				 */
				.keyBy(event -> event.artificalKey)
				/*
				 * For each artificial key we have some state of historical
				 * data that we can use to compute the wanted feature
				 */
				.map(new CalculateFeatureWithState());

		DataStream<ScoringResult> result = uuidStream
				.keyBy(transaction -> transaction.uuid)
				.connect(featureStream.keyBy(event -> event.uuid))
				/*
				 * Take both the original stream of transactions as well as the features
				 * calculated and join them into one enriched transaction.
				 */
				.flatMap(new TransactionFeatureJoiner())
				/*
				 * Use the EnrichedTransaction with all the features to score whether it is fraud
				 * or not
				 */
				.map(event -> new ScoringResult(event, FraudRules.scoreAdvancedFlyingCarpet(event)));


		result.print();

		env.execute("Flink Advanced-keyed Fraudulent Trnsaction");
	}

	private static class Manager {
		// TODO: In example keys could be composite, but here this should be enough for now
		private static String[] definedKeys() {
			return new String[]{"customerId", "cardId"};
		}

		public static FeatureDefinition featureForKey(String key) {
			if (key.equals("customerId")) {
				return new FeatureDefinition("utcLocation", Lists.newArrayList("UTC", "location"), (a, b) -> 1.0);
			} else if (key.equals("cardId")) {
				return new FeatureDefinition("averageAmount", Lists.newArrayList("amount"), (a, b) -> 1.0);
			} else {
				throw new RuntimeException("Invalid key " + key);
			}
		}
	}

	private static class CalculateFeatureWithState extends RichMapFunction<ArtificialKeyEvent, Feature> {

		private MapState<String, Double> state;

		@Override
		public void open(Configuration parameters) throws Exception {
			super.open(parameters);
			this.state = getRuntimeContext().getMapState(new MapStateDescriptor<String, Double>(
					"state",
					String.class,
					Double.class
			));
		}

		@Override
		public Feature map(ArtificialKeyEvent event) throws Exception {

			String key = event.artificalKey.split("=")[0];
			FeatureDefinition f = Manager.featureForKey(key);

			double value = f.calculateAndUpdate(state, event);

			return new Feature(
					event.uuid,
					event.featureName,
					value,
					event.expectedNumFeatures
			);
		}
	}

	private static class TransactionFeatureJoiner extends RichCoFlatMapFunction<UuidTransaction, Feature, EnrichedTransaction> {

		private ValueState<UuidTransaction> originalEvent;
		private MapState<String, Double> featureEvents;

		@Override
		public void open(Configuration parameters) throws Exception {
			super.open(parameters);

			this.originalEvent = getRuntimeContext().getState(new ValueStateDescriptor<>(
					"original-event",
					TypeInformation.of(UuidTransaction.class)
			));

			this.featureEvents = getRuntimeContext().getMapState(new MapStateDescriptor<>(
					"feature-events",
					TypeInformation.of(String.class),
					TypeInformation.of(Double.class)
			));
		}

		@Override
		public void flatMap1(UuidTransaction value, Collector<EnrichedTransaction> out) throws Exception {
			System.out.println("Incoming uuid trans: " + value.uuid);

			if (numberOfResultEvents() != value.expectedNumFeatures) {
				originalEvent.update(value);
				return;
			} else {
				EnrichedTransaction result = new EnrichedTransaction(
						value.uuid,
						value.payload,
						features()
				);

				out.collect(result);

				featureEvents.clear();
				originalEvent.clear();
			}
		}

		@Override
		public void flatMap2(Feature value, Collector<EnrichedTransaction> out) throws Exception {
			System.out.println("Incoming feature trans: " + value.uuid);
			featureEvents.put(value.name, value.value);
			System.out.println(featureEvents.keys());

			if (this.originalEvent.value() != null && (numberOfResultEvents() == value.expectedNumberOfFeatures)) {
				UuidTransaction original = this.originalEvent.value();
				EnrichedTransaction result = new EnrichedTransaction(
						original.uuid,
						original.payload,
						features()
				);
				out.collect(result);

				featureEvents.clear();
				this.originalEvent.clear();
			}
		}

		private Map<String, Double> features() throws Exception {
			return StreamSupport.stream(this.featureEvents.entries().spliterator(), false)
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		}

		private int numberOfResultEvents() throws Exception {
			// TODO: Save this as value state!
			return Iterables.size(featureEvents.keys());
		}
	}

	private static class AddArtificalKeyAndFeatureDefinition implements FlatMapFunction<UuidTransaction, ArtificialKeyEvent> {

		@Override
		public void flatMap(UuidTransaction transaction, Collector<ArtificialKeyEvent> collector) {
			for (String key : Manager.definedKeys()) {

				Object value = transaction.payload.get(key);
				String artificalKey = String.format("%s=%s", key, value.toString());

				FeatureDefinition featureToCalculate = Manager.featureForKey(key);
				String featureName = featureToCalculate.featureName;
				Map<String, Double> payload = featureToCalculate.filterRequiredInput(transaction.payload);


				collector.collect(new ArtificialKeyEvent(
						transaction.uuid,
						featureName,
						artificalKey,
						transaction.expectedNumFeatures,
						payload
				));
			}
		}
	}

	private static class ToUUIDTransaction implements MapFunction<SimpleTransaction, UuidTransaction> {
		@Override
		public UuidTransaction map(SimpleTransaction transaction) throws Exception {
			int expectedNumFeatures = Manager.definedKeys().length;
			return new UuidTransaction(UUID.randomUUID(), expectedNumFeatures, transaction.toMap());
		}
	}
}
