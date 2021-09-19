package org.apache.flink.runtime.state;

import org.junit.Test;

public class KeyGroupRangeAssignmentTest {

	// --- tests with no replication ---
	@Test
	public void assignKeyToParallelOperator() {
		int parallelOperatorIndex = KeyGroupRangeAssignment.assignKeyToParallelOperator("hello", 10, 5);
	}

	@Test
	public void assignToKeyGroup() {
		int keyGroupIndex = KeyGroupRangeAssignment.assignToKeyGroup("hello", 10);
	}

	@Test
	public void computeKeyGroupForKeyHash() {
		int keyGroupIndex = KeyGroupRangeAssignment.computeKeyGroupForKeyHash(123456789, 10);
	}

	@Test
	public void computeKeyGroupRangeForOperatorIndex() {
		KeyGroupRange range = KeyGroupRangeAssignment.computeKeyGroupRangeForOperatorIndex(10, 5, 3);
	}

	@Test
	public void computeOperatorIndexForKeyGroup() {
		KeyGroupRange r = new KeyGroupRange(0, 2);
		int idx = KeyGroupRangeAssignment.computeOperatorIndexForKeyGroup(10, 5, r.getKeyGroupId(1));
	}

	@Test
	public void computeDefaultMaxParallelism() {
		int defaultMaxParallelism = KeyGroupRangeAssignment.computeDefaultMaxParallelism(2);
	}

	@Test
	public void checkParallelismPreconditions() {
		KeyGroupRangeAssignment.checkParallelismPreconditions(10);
	}

	// --- tests with replicationreplicated_
	@Test
	public void replicated_assignKeyToParallelOperator() {
		int instance = KeyGroupRangeAssignment.assignKeyToParallelOperator("hello", 10, 5);
	}

	@Test
	public void replicated_assignToKeyGroup() {
	}

	@Test
	public void replicated_computeKeyGroupForKeyHash() {
	}

	@Test
	public void replicated_computeKeyGroupRangeForOperatorIndex() {
	}

	@Test
	public void replicated_computeOperatorIndexForKeyGroup() {
	}

	@Test
	public void replicated_computeDefaultMaxParallelism() {
	}

	@Test
	public void replicated_checkParallelismPreconditions() {
	}
}
