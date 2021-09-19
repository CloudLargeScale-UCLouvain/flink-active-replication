package me.florianschmidt.microbenchmark.functions;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;

import me.florianschmidt.replication.baseline.jobs.Record;

import java.io.IOException;

public class RecordSchema
		implements DeserializationSchema<Record>, SerializationSchema<Record> {

	private ObjectMapper om = new ObjectMapper();

	@Override
	public Record deserialize(byte[] message) throws IOException {
		Record r = om.readValue(message, Record.class);
		return r;
	}

	@Override
	public boolean isEndOfStream(Record nextElement) {
		return false;
	}

	@Override
	public TypeInformation<Record> getProducedType() {
		return TypeInformation.of(new TypeHint<Record>() {
		});
	}

	@Override
	public byte[] serialize(Record element) {
		try {
			return om.writeValueAsBytes(element);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Cannot serialize Record to bytes", e);
		}
	}
}
