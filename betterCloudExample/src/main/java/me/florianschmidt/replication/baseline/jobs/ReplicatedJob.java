package me.florianschmidt.replication.baseline.jobs;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.api.functions.windowing.RichWindowFunction;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.Window;
import org.apache.flink.util.Collector;

public class ReplicatedJob {
	public static void main(String[] args) {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		DataStream<String> strings = env.addSource(new SourceFunction<String>() {

			private transient boolean running = true;

			@Override
			public void run(SourceContext<String> ctx) throws Exception {
				while (running) {
					synchronized (ctx.getCheckpointLock()) {
						ctx.collect("An element");
					}
				}
			}

			@Override
			public void cancel() {
				this.running = false;
			}
		});


		DataStream<String> splittedWords = strings.flatMap(new FlatMapFunction<String, String>() {
			@Override
			public void flatMap(String value, Collector<String> out) throws Exception {
				String[] words = value.split(" ");
				for (String word : words) {
					out.collect(word);
				}
			}
		});

		KeyedStream keyedWords = splittedWords.keyBy(word -> word);

		keyedWords.timeWindow(Time.seconds(10))
				.apply(new RichWindowFunction() {
					@Override
					public void apply(
							Object o, Window window, Iterable input, Collector out
					) throws Exception {

					}
				});
	}
}
