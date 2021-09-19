package me.florianschmidt.replication.baseline.functions;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import me.florianschmidt.replication.baseline.jobs.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.concurrent.TimeUnit;

public class MeasuringSink extends RichSinkFunction<Record> {

	private static final Logger LOG = LoggerFactory.getLogger(MeasuringSink.class);

	private final String fileName;

	private BufferedWriter writer;
	private DropwizardHistogramWrapper latency;

	public MeasuringSink(String fileName) {
		this.fileName = fileName;
	}

	@Override
	public void open(Configuration parameters) throws Exception {
		FileWriter w = new FileWriter("/tmp/" + fileName, true);
		this.writer = new BufferedWriter(w);
		this.latency = getRuntimeContext().getMetricGroup().histogram("latency", new DropwizardHistogramWrapper(new Histogram(new SlidingTimeWindowReservoir(1, TimeUnit.SECONDS))));
	}

	@Override
	public void close() throws Exception {
		this.writer.flush();
	}

	@Override
	public void invoke(Record value, Context context) throws Exception {

		LOG.info("received event");

		if (this.writer == null) {
			this.writer = new BufferedWriter(new FileWriter("/tmp/" + fileName, true));
		}

		value.receivedAtNanos = System.nanoTime();
		value.receivedAtMillis = System.currentTimeMillis();

		this.writer.write(value.toString());
		System.out.println(value.id);
		latency.update(value.receivedAtMillis - value.createdAtMillis);
	}
}
