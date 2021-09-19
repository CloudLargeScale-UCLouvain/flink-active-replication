package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.streaming.runtime.streamrecord.StreamElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class LiveRobinAlgorithm extends Chainable {
	private static final Logger LOG = LoggerFactory.getLogger(LiveRobinAlgorithm.class);
	
    private long highestHeartbeat = 0;
    private long currentHeartbeat = 0;
    private long[] heartbeatAtChannel;
    private int currentQueuePolling;
    private int numProducers;

    private Map<Integer, LinkedBlockingQueue<StreamElement>> messages;

    public LiveRobinAlgorithm(int numProducers) {
        this.heartbeatAtChannel = new long[numProducers];
        for (int i = 0; i < numProducers; i++) {
            this.heartbeatAtChannel[i] = 0;
        }
        this.currentQueuePolling = 0;
        this.numProducers = numProducers;
		this.messages = new HashMap<>();
        for (int i = 0; i < numProducers; i++) {
            this.messages.put(i, new LinkedBlockingQueue<StreamElement>());
        }
    }

	@Override
	public void accept(StreamElement element, int channel) throws Exception {
        messages.get(channel).add(element);
        processingLoop();
    }

    public void processingLoop() throws Exception {
        boolean processing = true;
        while (processing) {
            StreamElement nextElement = messages.get(this.currentQueuePolling).poll();
            if (nextElement == null) {
                processing = false;
                break;
            }
            
            if (nextElement.isEndOfEpochMarker()) { // read HB
                LOG.trace("Heartbeat received,{},{},{}", this.currentHeartbeat, this.currentQueuePolling, nextElement.asEndOfEpochMarker().toString());
                if (nextElement.getEpoch() > highestHeartbeat) {  // first?
                    if (this.hasNext()) { // propagate
                        //LOG.debug("Heartbeat propagation (epoch {})", this.currentHeartbeat);
                        LOG.trace("Heartbeat propagation,{},{},{}", this.currentHeartbeat, this.currentQueuePolling, nextElement.asEndOfEpochMarker().toString());
                        this.getNext().accept(nextElement, this.currentQueuePolling);
                        highestHeartbeat = nextElement.getEpoch();
                    }
                } 
                heartbeatAtChannel[this.currentQueuePolling] = nextElement.getEpoch();
                long lastProducers = this.numProducers;
                for (int i = numProducers - 1; i > 0; i--) {
                    int channel = (this.currentQueuePolling + i) % numProducers;
                    if (heartbeatAtChannel[channel] >= this.currentHeartbeat) {
                        lastProducers--;
                    } else {
                        this.currentQueuePolling = channel;
                    }
                }
                if (lastProducers == 1) {
                    LOG.debug("All heartbeats {} processed, increase current heartbeat to {}", this.currentHeartbeat, this.currentHeartbeat + 1);
                    this.currentHeartbeat++;
                }

            } else {
                if (this.hasNext()) {
                    LOG.trace("Element propagation,{},{},{}", this.currentHeartbeat, this.currentQueuePolling, nextElement.asRecord().toString());
                    this.getNext().accept(nextElement, this.currentQueuePolling);
                }
            }
        }            
    }

}