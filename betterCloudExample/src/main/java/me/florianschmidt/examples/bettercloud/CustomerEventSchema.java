package me.florianschmidt.examples.bettercloud;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class CustomerEventSchema implements DeserializationSchema<Optional<CustomerEvent>>, SerializationSchema<CustomerEvent> {

	private ObjectMapper om = new ObjectMapper();

	@Override
	public Optional<CustomerEvent> deserialize(byte[] message) {
		try {
			CustomerEvent customerEvent = om.readValue(message, CustomerEvent.class);
			long now = System.currentTimeMillis();
			if (customerEvent.receivedAt == 0) {
				customerEvent.receivedAt = now;
			}				
			return Optional.of(customerEvent);
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	@Override
	public boolean isEndOfStream(Optional<CustomerEvent> nextElement) {
		return false;
	}

	@Override
	public TypeInformation<Optional<CustomerEvent>> getProducedType() {
		return TypeInformation.of(new TypeHint<Optional<CustomerEvent>>() {
		});
	}

	@Override
	public byte[] serialize(CustomerEvent customerEvent) {
		try {
			return om.writeValueAsBytes(customerEvent);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
