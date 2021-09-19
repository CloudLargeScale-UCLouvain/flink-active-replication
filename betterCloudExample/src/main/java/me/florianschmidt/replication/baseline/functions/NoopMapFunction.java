package me.florianschmidt.replication.baseline.functions;

import org.apache.flink.api.common.functions.MapFunction;

public class NoopMapFunction<T> implements MapFunction<T, T> {

	@Override
	public T map(T value) throws Exception {
		return value;
	}
}
