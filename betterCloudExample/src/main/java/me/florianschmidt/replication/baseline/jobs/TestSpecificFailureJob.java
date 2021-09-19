package me.florianschmidt.replication.baseline.jobs;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

public class TestSpecificFailureJob {

	public static void main(String[] args) throws Exception {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.createRemoteEnvironment("localhost", 8081, "target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar");

		DataStreamSource<String> s1 = env.addSource(new SourceFunction<String>() {
			private volatile boolean isRunning = true;

			@Override
			public void run(SourceContext<String> ctx) throws Exception {
				while (isRunning) {
					synchronized (ctx.getCheckpointLock()) {
						ctx.collect("Hello");
						Thread.sleep(1000);
					}
				}
			}

			@Override
			public void cancel() {
				isRunning = false;
			}
		});

		DataStreamSource<String> s2 = env.addSource(new SourceFunction<String>() {
			private volatile boolean isRunning = true;

			@Override
			public void run(SourceContext<String> ctx) throws Exception {
				while (isRunning) {
					synchronized (ctx.getCheckpointLock()) {
						ctx.collect("Hello");
						Thread.sleep(1000);
					}
				}
			}

			@Override
			public void cancel() {
				isRunning = false;
			}
		});


		s1.union(s2)
				.map(new MapFunction<String, String>() {
					@Override
					public String map(String s) throws Exception {
						return s;
					}
				})
				.setParallelism(2)
				.addSink(new PrintSinkFunction<>())
				.disableChaining();

		env.execute("Test Job");
	}
}
