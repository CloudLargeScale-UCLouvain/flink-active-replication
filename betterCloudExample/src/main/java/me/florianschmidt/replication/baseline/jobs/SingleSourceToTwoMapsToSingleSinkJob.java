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
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import me.florianschmidt.replication.baseline.functions.TransactionSource;
import me.florianschmidt.replication.baseline.functions.SaveLatencyToFileSink;
import me.florianschmidt.replication.baseline.model.Record;


@SuppressWarnings("Duplicates")
public class SingleSourceToTwoMapsToSingleSinkJob {

	public static void main(String[] args) throws Exception {
		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args);

		env
				.addSource(new TransactionSource())
					.name("Transaction Generator")
					.uid("TransactionGenerator")
					.slotSharingGroup("sourcesAndSinks")
					.setParallelism(1)
					.rebalance()
				.map(new MapFunction<Record, Record>() {
					@Override
					public Record map(Record value) throws Exception {
						return value;
					}
				})
					.name("Forwarding Map")
					.uid("ForwardingMap")
					.setParallelism(1)
					.slotSharingGroup("maps")
					.rebalance()
				.addSink(new SaveLatencyToFileSink())
					.setParallelism(1)
					.slotSharingGroup("sourcesAndSinks");


		env.execute("");
	}
}
