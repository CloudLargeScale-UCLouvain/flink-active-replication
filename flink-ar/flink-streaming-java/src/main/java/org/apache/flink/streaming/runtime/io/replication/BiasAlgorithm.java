package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.streaming.runtime.streamrecord.StreamElement;
import org.apache.flink.util.Preconditions;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public final class BiasAlgorithm extends Chainable {

	private final Queue<Enqueued>[] queues;

	private BufferedWriter b;

	{
		try {
			b = new BufferedWriter(new FileWriter("/tmp/queue-length.csv"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private final long[] last;
	private final double[] bias;
	private final long[] elemsInEpoch;

	private final int numProducers;
	private long deliverCount = 0;

	@SuppressWarnings("unchecked")
	public BiasAlgorithm(int numProducers) {

		this.queues = new Queue[numProducers];

		this.last = new long[numProducers];
		this.bias = new double[numProducers];
		this.elemsInEpoch = new long[numProducers];

		this.numProducers = numProducers;

		for (int i = 0; i < numProducers; i++) {
			this.queues[i] = new LinkedList<>();
			this.last[i] = 0;
			this.bias[i] = 0;
			this.elemsInEpoch[i] = 0;
		}
	}

	@Override
	public void accept(StreamElement value, int channel) throws Exception {
		Preconditions.checkState(channel <= numProducers - 1, "Received message on channel %s, but max is %s", channel, numProducers - 1);

		long timestamp = value.getCurrentTs();
		addToQueue(channel, timestamp, value);

		while (true) {
			int next = nextTurn();
			if (queues[next].peek() == null) {
				// do nothing
				break;
			} else {
				Enqueued e = queues[next].poll();
				deliver(e);
			}
		}
	}

	public void newEpoch() throws Exception {
		int fastestChannel = idxMax(this.elemsInEpoch);
		long fastestRate = this.elemsInEpoch[fastestChannel];

		for (int i = 0; i < numProducers; i++) {
			long rate = this.elemsInEpoch[i];

			if (rate == 0) {
				rate = 1;
			}

			this.bias[i] = fastestRate / (double) rate;
			this.elemsInEpoch[i] = 0;
		}
	}

	private int idxMax(long[] array) {
		int idxMax = -1;
		long max = -1;
		for (int i = 0; i < array.length; i++) {
			if (max == -1 || array[i] > max) {
				idxMax = i;
				max = array[i];
			}
		}
		return idxMax;
	}

	private void addToQueue(int channel, long timestamp, StreamElement value) throws IOException {
		Enqueued e = new Enqueued(timestamp, channel, value);
		Queue<Enqueued> queue = this.queues[channel];

		b.write(channel + "," + queue.size() + "\n");

		queue.add(e);
	}

	private void deliver(Enqueued q) throws Exception {
		last[q.channel] = q.timestamp;

		if (this.hasNext()) {
			if (q.value.isEndOfEpochMarker()) {
				newEpoch();
			}
			this.getNext().accept(q.value, q.channel);
		}

		this.elemsInEpoch[q.channel]++;
	}

	private int nextTurn() {
		// early return to avoid NPE
		if (last.length == 1) {
			return 0;
		}

		double smallestTs = -1;
		int smallestTsChan = -1;

		for (int i = 0; i < last.length; i++) {

			double ts = last[i] + bias[i];

			if ((smallestTs == -1 && smallestTsChan == -1) || ts < smallestTs) {
				smallestTs = ts;
				smallestTsChan = i;
			}
		}

		return smallestTsChan;
	}

	// TODO: Thesis - This implementation is not determinstic yet!
	public void endOfStream() throws Exception {

		b.flush();

		List<Enqueued> next = new ArrayList<>();

		while (true) {
			for (Queue<Enqueued> q : queues) {
				if (q.peek() != null) {
					next.add(q.poll());
				}
			}

			if (next.size() == 0) {
				break;
			}

			next.sort(Comparator.comparingLong(o -> o.timestamp));
			for (Enqueued q : next) {
				deliver(q);
			}

			next.clear();
		}
	}


}
