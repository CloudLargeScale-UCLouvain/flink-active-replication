package me.florianschmidt.examples.bettercloud;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class ControlEventSchema
		implements DeserializationSchema<Optional<ControlEvent>>, SerializationSchema<ControlEvent> {

	private ObjectMapper om = new ObjectMapper();

	@Override
	public Optional<ControlEvent> deserialize(byte[] message) throws IOException {
		try {
			ControlEvent ce = om.readValue(message, ControlEvent.class);
			return Optional.of(ce);
		} catch (JsonProcessingException e) {
			return Optional.empty();
		}
	}

	@Override
	public boolean isEndOfStream(Optional<ControlEvent> nextElement) {
		return false;
	}

	@Override
	public TypeInformation<Optional<ControlEvent>> getProducedType() {
		return TypeInformation.of(new TypeHint<Optional<ControlEvent>>() {
		});
	}

	@Override
	public byte[] serialize(ControlEvent element) {
		try {
			return om.writeValueAsBytes(element);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Cannot serialize ControlEvent to bytes", e);
		}
	}
}
