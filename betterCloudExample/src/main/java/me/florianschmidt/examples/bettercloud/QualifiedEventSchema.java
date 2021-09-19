package me.florianschmidt.examples.bettercloud;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class QualifiedEventSchema implements DeserializationSchema<QualifiedEvent>, SerializationSchema<QualifiedEvent> {

	private ObjectMapper om = new ObjectMapper();

	@Override
	public QualifiedEvent deserialize(byte[] message) throws IOException {
		QualifiedEvent qualifiedEvent = null;
        try {        
            qualifiedEvent = om.readValue(message, QualifiedEvent.class);
        } catch (JsonProcessingException e) {

        }            
        return qualifiedEvent;
	}

	@Override
	public boolean isEndOfStream(QualifiedEvent nextElement) {
		return false;
	}

	@Override
	public TypeInformation<QualifiedEvent> getProducedType() {
		return TypeInformation.of(new TypeHint<QualifiedEvent>() {
		});
	}

	@Override
	public byte[] serialize(QualifiedEvent customerEvent) {
		try {
			return om.writeValueAsBytes(customerEvent);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
