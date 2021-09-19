package org.apache.flink.streaming.runtime.io.replication;

import org.apache.flink.api.common.time.Time;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.runtime.rpc.RpcService;
import org.apache.flink.runtime.taskexecutor.TaskExecutorGateway;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonRootName;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.CancelLeadershipException;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.x.discovery.ServiceCache;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.UriSpec;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;
import org.apache.curator.x.discovery.details.ServiceCacheListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class OrderBroadcasterImpl implements ServiceCacheListener, OrderBroadcaster {

	private static final Logger LOG = LoggerFactory.getLogger(OrderBroadcasterImpl.class);

	private final ServiceInstance<InstanceDetails> thisInstance;
	private final ServiceDiscovery<InstanceDetails> discovery;
	private final RpcService rpcService;
	private final CuratorFramework client;
	private final ServiceCache<InstanceDetails> serviceCache;

	private volatile Map<ServiceInstance<InstanceDetails>, TaskExecutorGateway> gateways = new HashMap<>();
	private String owningThreadName;

	public OrderBroadcasterImpl(
		CuratorFramework curatorFramework,
		RpcService rpcService,
		ExecutionAttemptID executionAttempt,
		String replicaGroupId
	) throws Exception {

		this.rpcService = rpcService;
		this.client = curatorFramework;
		this.client.start();

		// TODO: Thesis - Replicas in a group need to have a unique group i
		thisInstance = ServiceInstance.<InstanceDetails>builder()
			.name(replicaGroupId)
			.payload(new InstanceDetails(executionAttempt))
			.address(this.rpcService.getAddress())
			.port(this.rpcService.getPort())
			.uriSpec(new UriSpec("akka.tcp://flink@{address}:{port}/user/taskmanager_0"))
			.build();

		JsonInstanceSerializer<InstanceDetails> serializer = new JsonInstanceSerializer<>(InstanceDetails.class);
		this.discovery = ServiceDiscoveryBuilder
			.builder(InstanceDetails.class)
			.client(client)
			.basePath("/flink/discovery/" + replicaGroupId)
			.serializer(serializer)
			.thisInstance(thisInstance)
			.build();

		this.discovery.start();

		this.serviceCache = discovery.serviceCacheBuilder()
			.name(replicaGroupId)
			.executorService(Executors.newFixedThreadPool(2))
			.build();
		this.serviceCache.start();
		this.serviceCache.addListener(this);

		this.discovery.queryForInstances(replicaGroupId).forEach(instance -> {
			if (!instance.getId().equals(this.thisInstance.getId())) {
				this.gateways.put(instance, createGatewayForInstance(instance));
			}
		});
	}

	public void setOwningThreadName(String name) {
		this.owningThreadName = name;
	}

	@Override
	public void cacheChanged() {
		LOG.info("At {}: Updating instances cache", owningThreadName);
		Map<ServiceInstance<InstanceDetails>, TaskExecutorGateway> newMap = new HashMap<>();
		for (ServiceInstance<InstanceDetails> instance : this.serviceCache.getInstances()) {
//			if (!instance.getId().equals(this.thisInstance.getId())) {
//				LOG.info("Ignore self");
//				continue;
//			}
			TaskExecutorGateway gateway = this.gateways.get(instance);
			if (gateway == null) {
				gateway = createGatewayForInstance(instance);
			}
			newMap.put(instance, gateway);
			this.gateways = newMap;
		}
	}

	private TaskExecutorGateway createGatewayForInstance(ServiceInstance<InstanceDetails> instance) {
		try {
			return this.rpcService.connect(instance.buildUriSpec(), TaskExecutorGateway.class).get();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public CompletableFuture<Acknowledge> broadcast(List<Integer> nextBatch) throws ExecutionException, InterruptedException {
		LOG.info("At {}: Broadcasting next batch {}", owningThreadName, nextBatch);
		for (Map.Entry<ServiceInstance<InstanceDetails>, TaskExecutorGateway> serviceInstance : this.gateways.entrySet()) {
			if (serviceInstance.getKey().getId().equals(thisInstance.getId())) {
				LOG.info("At {}: Not broadcasting to instance that is self {}", owningThreadName, thisInstance.getId());
				continue;
			}
			TaskExecutorGateway instanceGateway = serviceInstance.getValue();
			ServiceInstance<InstanceDetails> s = serviceInstance.getKey();
			LOG.info("At {}: Broadcasting to instance {}", owningThreadName, serviceInstance.getKey().getId());
			proposeNextOrdering(nextBatch, instanceGateway, s).join();
			LOG.info("At {}: Broadcasting was successful on our side", owningThreadName);
		}

		return CompletableFuture.completedFuture(Acknowledge.get());
		// TODO: Thesis – Wait for all to finish and see that majority has received all value
	}

	private CompletableFuture<Acknowledge> proposeNextOrdering(
		List<Integer> nextBatch,
		TaskExecutorGateway instanceGateway,
		ServiceInstance<InstanceDetails> s
	) {
		ExecutionAttemptID executionAttemptID = s.getPayload().calculateExecutionAttemptID();
		return instanceGateway.triggerAcceptInputOrdering(executionAttemptID, nextBatch, Time.milliseconds(100));
	}

	@Override
	public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
		LOG.info("At {}: Our state changed to {}", connectionState);
	}

	@JsonRootName("details")
	public static class InstanceDetails {

		public long lowerPart;
		public long upperPart;

		public InstanceDetails() {
		}

		public InstanceDetails(ExecutionAttemptID executionAttempt) {
			this.lowerPart = executionAttempt.getLowerPart();
			this.upperPart = executionAttempt.getUpperPart();
		}

		public ExecutionAttemptID calculateExecutionAttemptID() {
			return new ExecutionAttemptID(lowerPart, upperPart);
		}
	}

	public void close() throws Exception {
		if (this.client != null) {
			this.client.close();
		}

		if (this.discovery != null) {
			this.discovery.close();
		}

		if (this.serviceCache != null) {
			this.serviceCache.close();
		}
	}
}
