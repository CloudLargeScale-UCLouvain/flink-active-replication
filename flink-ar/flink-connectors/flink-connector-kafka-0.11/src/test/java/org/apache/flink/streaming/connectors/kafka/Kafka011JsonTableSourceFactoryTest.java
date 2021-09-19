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

package org.apache.flink.streaming.connectors.kafka;

import static org.apache.flink.table.descriptors.KafkaValidator.CONNECTOR_VERSION_VALUE_011;

/**
 * Tests for legacy Kafka011JsonTableSourceFactory.
 *
 * @deprecated Ensures backwards compatibility with Flink 1.5. Can be removed once we
 *             drop support for format-specific table sources.
 */
@Deprecated
public class Kafka011JsonTableSourceFactoryTest extends KafkaJsonTableSourceFactoryTestBase {

	@Override
	protected String version() {
		return CONNECTOR_VERSION_VALUE_011;
	}

	@Override
	protected KafkaJsonTableSource.Builder builder() {
		return Kafka011JsonTableSource.builder();
	}
}
