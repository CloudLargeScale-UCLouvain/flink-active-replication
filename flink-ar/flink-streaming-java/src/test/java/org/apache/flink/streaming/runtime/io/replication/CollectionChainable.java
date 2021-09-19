package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.runtime.streamrecord.StreamElement;

import java.util.LinkedList;
import java.util.List;


public class CollectionChainable extends Chainable {

	private List<Tuple2<StreamElement, Integer>> elements = new LinkedList<>();

	@Override
	public void accept(StreamElement element, int channel) throws Exception {
		this.elements.add(Tuple2.of(element, channel));
	}

	public List<Tuple2<StreamElement, Integer>> getElements() {
		return elements;
	}

	public void clear() {
		this.elements.clear();
	}
}
