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

import org.apache.flink.types.Record;

import org.junit.Assert;
import org.junit.Test;

public class ReplicationAwareSelectorTest {

	@Test
	public void selectFirstChannel() {
		// given 9 actual channels and replication factor 3
		int replicationFactor = 3;
		int actualNumChannels = 9;

		// when selecting the first logical channel
		MockChannelSelector mockSelector = new MockChannelSelector(new int[]{0});
		ReplicationAwareSelector<Record> selector = new ReplicationAwareSelector<>(mockSelector, replicationFactor);

		int[] selected = selector.selectChannels(null, actualNumChannels);

		// then the first three actual channels are selected
		Assert.assertArrayEquals(new int[]{0, 1, 2}, selected);
	}

	@Test
	public void selectSingleChannelThird() {
		// logical: 0, 1, 2
		// actual: 	0, 1, 2 | 3, 4, 5 | 6, 7, 8
		int replicationFactor = 3;
		int actualNumChannels = 9;

		MockChannelSelector mockSelector = new MockChannelSelector(new int[]{2});
		ReplicationAwareSelector<Record> selector = new ReplicationAwareSelector<>(mockSelector, replicationFactor);

		int[] selected = selector.selectChannels(null, actualNumChannels);

		Assert.assertArrayEquals(new int[]{6, 7, 8}, selected);
	}

	@Test
	public void selectMultipleChannelsConsecutive() {
		// logical: 0, 1, 2
		// actual: 	0, 1, 2 | 3, 4, 5 | 6, 7, 8
		int replicationFactor = 3;
		int actualNumChannels = 9;

		MockChannelSelector mockSelector = new MockChannelSelector(new int[]{0, 1});
		ReplicationAwareSelector<Record> selector = new ReplicationAwareSelector<>(mockSelector, replicationFactor);

		int[] selected = selector.selectChannels(null, actualNumChannels);

		Assert.assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5}, selected);
	}

	@Test
	public void selectMultipleChannelsWithGap() {
		// logical: 0, 1, 2
		// actual: 	0, 1, 2 | 3, 4, 5 | 6, 7, 8
		int replicationFactor = 3;
		int actualNumChannels = 9;

		MockChannelSelector mockSelector = new MockChannelSelector(new int[]{0, 2});
		ReplicationAwareSelector<Record> selector = new ReplicationAwareSelector<>(mockSelector, replicationFactor);

		int[] selected = selector.selectChannels(null, actualNumChannels);

		Assert.assertArrayEquals(new int[]{0, 1, 2, 6, 7, 8}, selected);
	}

	@Test
	public void selectChannelsReplicationFactorSingle() {
		// logical: 0, 1, 2
		// actual: 	0, 1, 2
		int replicationFactor = 1;
		int actualNumChannels = 3;

		MockChannelSelector mockSelector = new MockChannelSelector(new int[]{0});
		ReplicationAwareSelector<Record> selector = new ReplicationAwareSelector<>(mockSelector, replicationFactor);

		int[] selected = selector.selectChannels(null, actualNumChannels);

		Assert.assertArrayEquals(new int[]{0}, selected);
	}

	@Test
	public void selectChannelsReplicationFactorMultiple() {
		// logical: 0, 1, 2
		// actual: 	0, 1, 2
		int replicationFactor = 1;
		int actualNumChannels = 3;

		MockChannelSelector mockSelector = new MockChannelSelector(new int[]{0, 2});
		ReplicationAwareSelector<Record> selector = new ReplicationAwareSelector<>(mockSelector, replicationFactor);

		int[] selected = selector.selectChannels(null, actualNumChannels);

		Assert.assertArrayEquals(new int[]{0, 2}, selected);
	}

	private class MockChannelSelector implements ChannelSelector<Record> {

		private int[] selectedChannels;

		private MockChannelSelector(int[] selectedChannels) {
			this.selectedChannels = selectedChannels;
		}

		@Override
		public int[] selectChannels(Record record, int numChannels) {
			return selectedChannels;
		}
	}
}
