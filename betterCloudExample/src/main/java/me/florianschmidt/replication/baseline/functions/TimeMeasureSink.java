package me.florianschmidt.replication.baseline.functions;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.streaming.api.checkpoint.ListCheckpointed;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.google.common.collect.Lists;
import me.florianschmidt.replication.baseline.model.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class TimeMeasureSink extends RichSinkFunction<Record> implements ListCheckpointed<Record> {

	private Histogram eventTimeLatency;
	private Histogram eventTimeDuplicateLatency;
	private Histogram processingTimeLatency;

	private Logger logger = LoggerFactory.getLogger(TimeMeasureSink.class);
	private Record lastRecord;

	@Override
	public void open(Configuration parameters) throws Exception {

		SlidingTimeWindowReservoir reservoir = new SlidingTimeWindowReservoir(10, TimeUnit.SECONDS);
		com.codahale.metrics.Histogram dropwizardHistogram =
				new com.codahale.metrics.Histogram(reservoir);

		this.eventTimeLatency = getRuntimeContext().getMetricGroup().histogram(
				"event_time_latency",
				new DropwizardHistogramWrapper(dropwizardHistogram)
		);

		SlidingTimeWindowReservoir reservoir1 = new SlidingTimeWindowReservoir(10, TimeUnit.SECONDS);
		com.codahale.metrics.Histogram dropwizardHistogram1 =
				new com.codahale.metrics.Histogram(reservoir1);

		this.eventTimeDuplicateLatency = getRuntimeContext().getMetricGroup().histogram(
				"event_time_duplicate_latency",
				new DropwizardHistogramWrapper(dropwizardHistogram1)
		);

		SlidingTimeWindowReservoir reservoir2 = new SlidingTimeWindowReservoir(10, TimeUnit.SECONDS);
		com.codahale.metrics.Histogram dropwizardHistogram2 =
				new com.codahale.metrics.Histogram(reservoir2);

		this.processingTimeLatency = getRuntimeContext().getMetricGroup().histogram(
				"processing_time_latency",
				new DropwizardHistogramWrapper(dropwizardHistogram2)
		);

	}

	@Override
	public void invoke(Record record, Context context) throws Exception {

		if (lastRecord == null) {
			this.lastRecord = record;
			return;
		}

		long now = System.currentTimeMillis() * 1000 * 1000;
		long processingTimeLatency = now - record.ingestionTime;

		long eventTimeLatency = now - record.eventTime;

		if (record.duplicate) {
			this.eventTimeDuplicateLatency.update(eventTimeLatency);
		} else {
			this.eventTimeLatency.update(eventTimeLatency);
		}

		this.processingTimeLatency.update(processingTimeLatency);

		this.lastRecord = record;
	}

	@Override
	public List<Record> snapshotState(long checkpointId, long timestamp) {
		return Lists.newArrayList(this.lastRecord);
	}

	@Override
	public void restoreState(List<Record> state) {

		Record restoredRecord = state.get(0);
		if (restoredRecord != null) {
			this.lastRecord = restoredRecord;
			logger.info("Restoring sink record to {}", restoredRecord.id);
		} else {
			throw new RuntimeException("No state");
		}
	}
}
