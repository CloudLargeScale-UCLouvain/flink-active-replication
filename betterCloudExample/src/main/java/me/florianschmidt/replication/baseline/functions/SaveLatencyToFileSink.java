package me.florianschmidt.replication.baseline.functions;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import me.florianschmidt.replication.baseline.model.Record;

import java.io.BufferedWriter;
import java.io.FileWriter;

public class SaveLatencyToFileSink extends RichSinkFunction<Record> {

	private BufferedWriter writer;

	@Override
	public void open(Configuration parameters) throws Exception {
		FileWriter w = new FileWriter("/tmp/latencies.txt", true);
		this.writer = new BufferedWriter(w);
	}

	@Override
	public void close() throws Exception {
		this.writer.flush();
	}

	@Override
	public void invoke(Record record, Context context) throws Exception {
		// We also use 1000 * 1000 as factor in source
		long now = System.currentTimeMillis() * 1000 * 1000;

		long processingTimeLatency = now - record.ingestionTime;
		long eventTimeLatency = now - record.eventTime;

		this.writer.write(record.id + "," + now + "," + processingTimeLatency + "," + eventTimeLatency + "\n");
	}
}
