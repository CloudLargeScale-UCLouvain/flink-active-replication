package org.apache.flink.runtime.taskmanager;

import org.junit.Assert;
import org.junit.Test;

public class ReplicationUtilsTest {

	@Test
	public void testCalculateOperatorIndexForSubtask() {
		int replicationFactor = 2;

		int res = ReplicationUtils.calculateOperatorIndexForSubtask(0, replicationFactor);
		Assert.assertEquals(0, res);

		res = ReplicationUtils.calculateOperatorIndexForSubtask(1, replicationFactor);
		Assert.assertEquals(0, res);

		res = ReplicationUtils.calculateOperatorIndexForSubtask(2, replicationFactor);
		Assert.assertEquals(1, res);

		res = ReplicationUtils.calculateOperatorIndexForSubtask(3, replicationFactor);
		Assert.assertEquals(1, res);

		res = ReplicationUtils.calculateOperatorIndexForSubtask(4, replicationFactor);
		Assert.assertEquals(2, res);

		res = ReplicationUtils.calculateOperatorIndexForSubtask(5, replicationFactor);
		Assert.assertEquals(2, res);
	}

	@Test
	public void testCalculateOperatorIndexForSubtaskNoReplication() {
		int replicationFactor = 1;

		int res = ReplicationUtils.calculateOperatorIndexForSubtask(0, replicationFactor);
		Assert.assertEquals(0, res);

		res = ReplicationUtils.calculateOperatorIndexForSubtask(1, replicationFactor);
		Assert.assertEquals(1, res);

		res = ReplicationUtils.calculateOperatorIndexForSubtask(2, replicationFactor);
		Assert.assertEquals(2, res);

		res = ReplicationUtils.calculateOperatorIndexForSubtask(3, replicationFactor);
		Assert.assertEquals(3, res);

		res = ReplicationUtils.calculateOperatorIndexForSubtask(4, replicationFactor);
		Assert.assertEquals(4, res);

		res = ReplicationUtils.calculateOperatorIndexForSubtask(5, replicationFactor);
		Assert.assertEquals(5, res);
	}

}
