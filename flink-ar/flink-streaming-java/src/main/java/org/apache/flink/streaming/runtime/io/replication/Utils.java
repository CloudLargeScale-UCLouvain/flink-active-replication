package org.apache.flink.streaming.runtime.io.replication;

import java.util.Arrays;

public class Utils {

	public static int logicalChannel(int actualChannel, int[] replicationFactor) {
		int replicationFactorOffset = 0;
		for (int i = 0; i < replicationFactor.length; i++) {
			if (actualChannel < replicationFactorOffset + replicationFactor[i]) {
				return i;
			}
			replicationFactorOffset += replicationFactor[i];
		}

		throw new RuntimeException(
			String.format(
				"Actual channel %d cannot be be mapped to logical channel with replication factor %s. This indicates that something went wrong in the active replication stack.",
				actualChannel,
				Arrays.toString(replicationFactor)
			)
		);
	}

	public static int numLogicalChannels(int[] replicationFactor) {
		// TODO: Implement and test me!
		return replicationFactor.length;
	}
}
