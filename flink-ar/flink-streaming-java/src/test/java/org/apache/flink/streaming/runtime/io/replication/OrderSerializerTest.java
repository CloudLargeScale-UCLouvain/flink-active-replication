package org.apache.flink.streaming.runtime.io.replication;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class OrderSerializerTest {


	@Test
	public void test() {
		Order o1 = new Order(System.currentTimeMillis(), new int[]{1, 2, 3});
		OrderSerializer serde = new OrderSerializer();
		serde.configure(new HashMap<String, Integer>() {{
			put("batchSize", 3);
		}}, false);

		Assert.assertEquals(o1, serde.deserialize("topic", serde.serialize("topic", o1)));
	}
}
