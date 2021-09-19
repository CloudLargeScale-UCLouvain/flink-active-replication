package me.florianschmidt.replication.baseline.jobs;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;

import me.florianschmidt.replication.baseline.functions.BusyWaitRateFunction;
import me.florianschmidt.replication.baseline.functions.Rates;

import java.io.IOException;
import java.util.Properties;

public class TestRecoveryTimeDataGen {

	public static void main(String[] args) throws Exception {
		StreamExecutionEnvironment env = EnvBuilder.createEnvFromArgs(args, true);

		String FIRST_TOPIC = "first-topic";
		String SECOND_TOPIC = "second-topic";

		Properties p = new Properties();
		p.setProperty("bootstrap.servers", "flink-thesis-kafka.default.svc.cluster.local:9092");

		env.addSource(new BusyWaitRateFunction(Rates.constant(500), "source-1.csv"))
				.addSink(new FlinkKafkaProducer<>(FIRST_TOPIC, new RecordSchema(), p));

		env.addSource(new BusyWaitRateFunction(Rates.constant(500), "source-2.csv"))
				.addSink(new FlinkKafkaProducer<>(SECOND_TOPIC, new RecordSchema(), p));

		env.execute("DataGen");
	}

	public static class RecordSchema implements DeserializationSchema<Record>, SerializationSchema<Record> {

		private ObjectMapper om = new ObjectMapper();

		@Override
		public Record deserialize(byte[] message) throws IOException {
			return om.readValue(message, Record.class);
		}

		@Override
		public boolean isEndOfStream(Record nextElement) {
			return false;
		}

		@Override
		public byte[] serialize(Record element) {
			try {
				return om.writeValueAsBytes(element);
			} catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public TypeInformation<Record> getProducedType() {
			return TypeInformation.of(new TypeHint<Record>() {
			});
		}
	}
}
