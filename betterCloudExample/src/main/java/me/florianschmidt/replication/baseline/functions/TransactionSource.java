package me.florianschmidt.replication.baseline.functions;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.checkpoint.ListCheckpointed;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;

import com.google.common.collect.Lists;
import me.florianschmidt.replication.baseline.Config;
import me.florianschmidt.replication.baseline.model.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.List;

public class TransactionSource extends RichParallelSourceFunction<Record> implements ListCheckpointed<Record> {

	private volatile boolean cancelled = false;

	private transient BufferedReader reader;
	private transient BufferedWriter writer;

	private Record lastRecord;
	private Logger logger = LoggerFactory.getLogger(TransactionSource.class);

	@Override
	public void open(Configuration parameters) throws Exception {
		Socket socket = new Socket(Config.GENERATOR_HOST, Config.GENERATOR_PORT);

		this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		// keep this here, we might need the info how to do this later
		// String jobID = getRuntimeContext().getMetricGroup().getAllVariables().get("<job_id>");
	}

	@Override
	public void run(SourceContext<Record> sourceContext) throws Exception {

		int j = 0;
		while (!cancelled) {

			long offset = (this.lastRecord == null) ? -1 : this.lastRecord.id;

			this.writer.write((offset + 1) + "\n");
			this.writer.flush();

			for (int i = 0; i < 100; i++) {

				String idxStr = this.reader.readLine();
				String timestampStr = this.reader.readLine();
				String linesInFileStr = this.reader.readLine();

				Long linesInFile = Long.parseLong(linesInFileStr);
				Long eventTime = Long.parseLong(timestampStr);
				Long id = Long.parseLong(idxStr);

				boolean duplicate = linesInFile > id + 1;

				long ingestionTime = System.currentTimeMillis() * 1000 * 1000;
				Record record = new Record(id, eventTime, ingestionTime, duplicate);

				synchronized (sourceContext.getCheckpointLock()) {
					sourceContext.collect(record);
					this.lastRecord = record;
					j++;
				}
			}
		}
	}

	@Override
	public void cancel() {
		this.cancelled = true;
		try {
			this.reader.close();
			this.writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public List<Record> snapshotState(long checkpointId, long timestamp) {
		if (this.lastRecord == null) {
			return Lists.newArrayList();
		} else {
			return Lists.newArrayList(this.lastRecord);
		}
	}

	@Override
	public void restoreState(List<Record> state) {

		Record restoredRecord = state.get(0);
		if (restoredRecord != null) {
			this.lastRecord = restoredRecord;
			logger.info("Restoring source record to {}", restoredRecord.id);
		} else {
			throw new RuntimeException("No state");
		}
	}
}
