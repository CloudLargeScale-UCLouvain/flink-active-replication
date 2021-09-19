package me.florianschmidt.examples.bettercloud;

import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper;
import org.apache.flink.metrics.Counter;
import org.apache.flink.util.Collector;

import com.codahale.metrics.SlidingTimeWindowReservoir;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class CounterFunction extends RichFlatMapFunction<QualifiedEvent, QualifiedEvent> {

	private MapState<String, Integer> counts;
	private DropwizardHistogramWrapper processingTimeLatency;
	private Counter numAlertsFired;
	private BufferedWriter writer;

	@Override
	public void open(Configuration parameters) throws IOException {
		MapStateDescriptor<String, Integer> descriptor = new MapStateDescriptor<>(
				"counts",
				String.class,
				Integer.class
		);

		this.processingTimeLatency = getRuntimeContext().getMetricGroup().histogram(
				"time-to-alert",
				new DropwizardHistogramWrapper(new com.codahale.metrics.Histogram(new SlidingTimeWindowReservoir(10, TimeUnit.SECONDS)))
		);

		this.numAlertsFired = getRuntimeContext().getMetricGroup().counter("alertsFired");

		this.counts = getRuntimeContext().getMapState(descriptor);

		FileWriter w = new FileWriter("/tmp/latencies.txt", false);
		this.writer = new BufferedWriter(w);
	}

	@Override
	public void flatMap(QualifiedEvent value, Collector<QualifiedEvent> out) throws Exception {
		String key = String.format("customer-%s-alert-%s", value.event.customerId, value.controlEvent.alertId);

		int previousCount = (counts.get(key) != null) ? counts.get(key) : 0;

		counts.put(key, previousCount + 1);
		System.out.printf("Alert '%s' fired for customer '%s' for the %s time%n", value.controlEvent.alertName, value.event.customerId, counts.get(key));

		this.numAlertsFired.inc();
		this.processingTimeLatency.update(System.currentTimeMillis() - value.event.ingestedAt);
		long now = System.currentTimeMillis();
		/*
		if (this.writer == null) {
			this.writer = new BufferedWriter(new FileWriter("/tmp/latencies.txt", false));
		}

		this.writer.write(value.event.ingestedAt + "," + now + "," + (now - value.event.ingestedAt) + String.format("%n"));
		*/
		value.sinkAt = now;
		out.collect(value);
	}
}
