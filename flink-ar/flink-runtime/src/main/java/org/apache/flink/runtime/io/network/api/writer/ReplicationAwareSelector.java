/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.io.network.api.writer;

import org.apache.flink.core.io.IOReadableWritable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

class ReplicationAwareSelector<T extends IOReadableWritable> implements ChannelSelector<T>, Serializable {

	private final ChannelSelector<T> wrapped;
	private final int replicationFactor;
	private Logger logger = LoggerFactory.getLogger(ReplicationAwareSelector.class);

	public ReplicationAwareSelector(ChannelSelector<T> channelSelector, int replicationFactor) {
		this.wrapped = channelSelector;
		this.replicationFactor = replicationFactor;
	}

	@Override
	public int[] selectChannels(T record, int numChannels) {

		// how many channels would exist if replication was 1 (e.g. "off")
		int virtualNumChannels = numChannels / replicationFactor;

		// check which channels we would send to if replication was 1
		int[] virtualSelected = wrapped.selectChannels(record, virtualNumChannels);

		int[] actual = new int[virtualSelected.length * replicationFactor];

		if (replicationFactor == 1) {
			return virtualSelected;
		}

		// map the virtual channels with replication 1 to the actual ones
		// with the replication that we are using
		for (int i = 0; i < virtualSelected.length; i++) {
			for (int j = 0; j < replicationFactor; j++) {
				int idxOffset = i * replicationFactor;
				actual[idxOffset + j] = (virtualSelected[i] * replicationFactor) + j;
			}
		}

		logger.trace("Mapping channels {} to replicated channels {}", virtualSelected, actual);
		return actual;
	}
}
