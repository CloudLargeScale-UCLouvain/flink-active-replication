package org.apache.flink.runtime.taskmanager;

// TODO: Which package should this live in?

/**
 * Some helpers.
 */
public class ReplicationUtils {
	public static int calculateOperatorIndexForSubtask(int subtaskIndex, int replicationFactor) {
		return Math.floorDiv(subtaskIndex, replicationFactor);
	}
}
