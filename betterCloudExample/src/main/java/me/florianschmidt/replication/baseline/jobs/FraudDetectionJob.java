package me.florianschmidt.replication.baseline.jobs;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.co.RichCoFlatMapFunction;
import org.apache.flink.util.Collector;

import com.google.common.collect.Lists;
import me.florianschmidt.replication.baseline.GeoUtils;
import me.florianschmidt.replication.baseline.functions.TimeMeasureSink;
import me.florianschmidt.replication.baseline.functions.TransactionSource;
import me.florianschmidt.replication.baseline.model.Feature;
import me.florianschmidt.replication.baseline.model.FraudDetectionResult;
import me.florianschmidt.replication.baseline.model.Record;
import me.florianschmidt.replication.baseline.model.Transaction;

import java.util.List;
import java.util.Random;


public class FraudDetectionJob {

	public static void main(String[] args) throws Exception {

		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args);

		env
				.addSource(new TransactionSource())
				.name("Transaction Generator")
				.uid("TransactionGenerator")
//				.slotSharingGroup
				.keyBy(elem -> "common_key")
				.process(new KeyedProcessFunction<String, Record, Record>() {

					private transient MapState<Long, Record> allRecords;
					private transient Random r;

					@Override
					public void open(Configuration parameters) {
						allRecords = getRuntimeContext().getMapState(new MapStateDescriptor<>(
								"all-records",
								Long.class,
								Record.class
						));

						r = new Random(System.currentTimeMillis());
					}

					@Override
					public void processElement(
							Record value,
							Context ctx,
							Collector<Record> out
					) throws Exception {

						r.nextBytes(value.payload);
						allRecords.put(value.id, value);


						// do some arbitrary processing
						for (int i = 0; i < 5; i++) {
							if (matches(value.eventTime + i)) {
								System.out.print("");
							}
						}

						out.collect(value);
					}
				})
				// .disableChaining()
				.addSink(new TimeMeasureSink());

		env.execute("Fraud detection example");
	}

	private static boolean matches(long n) {
		boolean yes = true;

		for (int i = 1; i < 10; i++) {
			if (n % i == 0) {
				yes = true;
			} else if (n % i + 1 == 0) {
				yes = false;
			}
		}

		return yes;
	}

	public static class CalculateDifferenceFromMean extends RichMapFunction<Transaction, Feature> {

		private ListState<Transaction> lastTransactions;

		@Override
		public void open(Configuration parameters) throws Exception {
			ListStateDescriptor<Transaction> listStateDescriptor = new ListStateDescriptor<>(
					"last-n-transactions-per-card",
					Transaction.class
			);
			this.lastTransactions = getRuntimeContext().getListState(listStateDescriptor);
		}

		@Override
		public Feature map(Transaction transaction) throws Exception {
			List<Transaction> transactions = Lists.newArrayList(lastTransactions.get().iterator());
			double mean = mean(transactions);
			double difference = transaction.amount / mean;

			if (transactions.size() > 10) {
				transactions.remove(0);
			}

			transactions.add(transaction);

			lastTransactions.update(transactions);

			return new Feature("differenceFromMeanInPercent", /*difference*/0, transaction);
		}
	}

	public static double mean(List<Transaction> transactions) {
		double count = (double) transactions.size();
		double total = 0;

		for (Transaction transaction : transactions) {
			total += transaction.amount;
		}

		return total / count;
	}

	public static class CalculateSpeed extends RichMapFunction<Transaction, Feature> {

		private ValueState<Transaction> lastTransactionState;

		@Override
		public void open(Configuration parameters) {
			ValueStateDescriptor<Transaction> valueStateDescriptor = new ValueStateDescriptor<>(
					"last-transaction-per-customer-state",
					Transaction.class
			);

			this.lastTransactionState = getRuntimeContext().getState(valueStateDescriptor);
		}

		@Override
		public Feature map(Transaction transaction) throws Exception {
			Transaction lastTransaction = lastTransactionState.value();

			if (lastTransaction == null) {
				return new Feature("speed", 0.0, transaction);
			}

			double distance = GeoUtils.distance(lastTransaction.location, transaction.location);
			double time = GeoUtils.time(lastTransaction.created, transaction.created);
			double speed = distance / time;

			this.lastTransactionState.update(transaction);

			return new Feature("speed", speed, transaction);
		}
	}

	private static class FraudDetection implements MapFunction<Tuple2<Feature, Feature>, FraudDetectionResult> {

		@Override
		public FraudDetectionResult map(Tuple2<Feature, Feature> features) throws Exception {

			sanityCheck(features);

			boolean fraud = isFraud(features.f0, features.f1);
			return new FraudDetectionResult(fraud, features.f0.transaction);
		}

		private void sanityCheck(Tuple2<Feature, Feature> features) {
			if (!features.f0.transaction.uuid.equals(features.f1.transaction.uuid)) {
				throw new RuntimeException("Features for different transactions were combined");
			}
		}

		private boolean isFraud(Feature first, Feature second) {
			boolean tooFast = (first.value * 3.6 > 200);  // travelling with a speed higher than 200km/h
			boolean tooFarFromMean = (second.value > 10); // more than 10x withdrawel of the usual amount
			return tooFast || tooFarFromMean;
		}
	}

	private static class AddUuid implements MapFunction<Transaction, Transaction> {

		private int count = 0;
		@Override
		public Transaction map(Transaction transaction) throws Exception {
			String uuid = String.valueOf(count++);
			transaction.uuid = uuid;
			return transaction;
		}
	}

	private static class JoinFeatures extends RichCoFlatMapFunction<Feature, Feature, Tuple2<Feature, Feature>> {

		private ValueState<Feature> secondFeature;
		private ValueState<Feature> firstFeature;

		@Override
		public void open(Configuration parameters) throws Exception {
			this.firstFeature = getRuntimeContext().getState(new ValueStateDescriptor<>(
					"first-feature",
					Feature.class
			));

			this.secondFeature = getRuntimeContext().getState(new ValueStateDescriptor<>(
					"second-feature",
					Feature.class
			));
		}

		@Override
		public void flatMap1(
				Feature feature,
				Collector<Tuple2<Feature, Feature>> collector
		) throws Exception {
			if (secondFeature.value() != null) {
				collector.collect(Tuple2.of(feature, secondFeature.value()));
				secondFeature.update(null);
			} else {
				firstFeature.update(feature);
			}
		}

		@Override
		public void flatMap2(
				Feature feature,
				Collector<Tuple2<Feature, Feature>> collector
		) throws Exception {
			if (firstFeature.value() != null) {
				collector.collect(Tuple2.of(firstFeature.value(), feature));
				firstFeature.update(null);
			} else {
				secondFeature.update(feature);
			}
		}
	}
}
