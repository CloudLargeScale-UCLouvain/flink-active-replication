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

package org.apache.flink.runtime.executiongraph.failover;

import org.apache.flink.runtime.executiongraph.Execution;
import org.apache.flink.runtime.executiongraph.ExecutionGraph;
import org.apache.flink.runtime.executiongraph.ExecutionJobVertex;
import org.apache.flink.runtime.jobgraph.JobVertexID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReplicatedFailoverStrategy extends FailoverStrategy {

	private static final Logger logger = LoggerFactory.getLogger(ReplicatedFailoverStrategy.class);
	private final ExecutionGraph executionGraph;

	private Map<JobVertexID, boolean[]> failureTracker;


	public ReplicatedFailoverStrategy(ExecutionGraph executionGraph) {
		this.executionGraph = executionGraph;

		// TODO: Or parallelism * replication factor, also make this a map with key = executionjobvertex
		this.failureTracker = new HashMap<>();
		// TODO: The execution graph is not yet finished here, we have to think about this differently...

		for (ExecutionJobVertex jv : executionGraph.getVerticesTopologically()) {
			this.failureTracker.put(jv.getJobVertexId(), new boolean[jv.getParallelism()]);
		}
	}

	@Override
	public void onTaskFailure(Execution taskExecution, Throwable cause) {

		boolean[] replicaStatus = failureTracker.get(taskExecution.getVertex().getJobvertexId());

		int replicationFactor = taskExecution
			.getVertex()
			.getJobVertex()
			.getReplicationFactor();

		int parallelism = taskExecution
			.getVertex()
			.getJobVertex()
			.getParallelism();

		int subtaskIdx = taskExecution
			.getVertex()
			.getParallelSubtaskIndex();

		int beginReplicas = Math.floorDiv(subtaskIdx, parallelism) * replicationFactor;
		int endReplicas = beginReplicas + replicationFactor;

		boolean[] replicas = Arrays.copyOfRange(replicaStatus, beginReplicas, endReplicas);

		int numFailed = 0;
		for (boolean hasFailedPreviously : replicas) {
			if (hasFailedPreviously) {
				numFailed++;
			}
		}

		if (numFailed + 1 >= replicationFactor) {
			logger.info("Not enough replicas up and running");
			executionGraph.failGlobal(cause);
		} else {
			logger.info("Tolerating failure of replica");
			replicas[subtaskIdx % replicationFactor] = true;
		}
	}

	@Override
	public void notifyNewVertices(List<ExecutionJobVertex> newJobVerticesTopological) {
		for (ExecutionJobVertex ejv : newJobVerticesTopological) {
			boolean[] allFalse = new boolean[ejv.getParallelism()];
			Arrays.fill(allFalse, false);
			this.failureTracker.put(ejv.getJobVertexId(), allFalse);
		}
	}

	@Override
	public String getStrategyName() {
		return null;
	}
}
