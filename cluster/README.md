# Running Flink on the Kubernetes cluster

Active replication feature is only supported for now, if being run from flink binary, via `./bin/flink` command.

The full path is the following: `/flink-dist/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT/bin`

# Requirements
## Storage

Install [Local Path Provisioner](https://github.com/rancher/local-path-provisioner). It permits automated PV provision.
```
kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml
```
Storage class "local-path" is used in the local deployments.
You should create /opt/local-path-provisioner directories on each node you want the charts to install, and `chmod` it to 777.

## Configuration

Ensure that flink config file is properly configured at:
`/flink-dist/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT/`

The following parameters need to be specified:

```
jobmanager.rpc.address: localhost  # or remote hostname

jobmanager.rpc.port:  6123 #port on remote cluster should be accessible, this can be achieved by using NodePort 30123

rest.port: 8081 # e.g. 30081 specify port of flink REST API on remote cluster
```

Changing other parameters is optional.


# Installing Kafka on Kubernetes

## Prerequisites

1. Ensure Helm package manager is installed.

You can install it with:
```
$ curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3
$ chmod 700 get_helm.sh
$ ./get_helm.sh
```
More details can be found here: [Official Helm website](https://helm.sh/docs/intro/install/)

2. Ensure Helm can install packages in default namespace.
Allow only necessary permissions for Helm by creating correct ServiceAccount and ClusterRoleBinding for Helm, via:
```
kubectl apply -f ./local/tiller.yaml
```

3. Create Persistent Volumes for Charts

```
kubectl create -f ./local/volumes/kafka/
kubectl create -f ./local/volumes/zookeeper
kubectl create -f ./local/volumes/prometheus
```

4. Install Kafka , Zookeeper and Prometheus using latest helm version:
```
helm repo add bitnami https://charts.bitnami.com/bitnami

helm install my-release  bitnami/kafka     --set zookeeper.volumePermissions.enabled=false --set zookeeper.persistence.enabled=false --set volumePermissions.enabled=true --set persistence.enabled=false
```

**!Recommended**: or with running Zookeeper and Kafka separately:

```
helm install zookeeper -f ./zookeeper/values.yaml bitnami/zookeeper --set replicaCount=1 --set auth.enabled=false  --set allowAnonymousLogin=true --set zookeeper.volumePermissions.enabled=false
```
Save zookeeper endpoint. By default it will be `zookeeper.default.svc.cluster.local`, and use it later for starting up Kafka brokers

```
helm install my-release -f ./kafka/values.yaml bitnami/kafka  --set volumePermissions.enabled=true --set zookeeper.enabled=false --set replicaCount=3 --set externalZookeeper.servers=zookeeper.default.svc.cluster.local --set livenessProbe.enabled=false,readinessProbe.enabled=false
```

**!Note** If other than default Kafka DNS name or port is used - BetterCloud example should be recompiled with setting correct Kafka DNS in Kafka property `bootstrap.servers`.

Default is: `my-release-kafka.default.svc.cluster.local:9092`


5. Install Prometheus

```
helm install my-prometheus stable/prometheus
```

After Prometheus has been installed, create port forwarding for prometheus API (this can be changed by adding additional service with NodePort).

`kubectl --namespace default port-forward $(kubectl get pods --namespace default -l "app=prometheus,component=server" -o jsonpath="{.items[0].metadata.name}") 9090`


6. When all necessary pods are in running state - scale Flink cluster by deploying task and jobmanagers.

```
kubectl apply -f ./local/flink
```

### Optional deployments:

7. Deploy data generator:
```
kubectl apply -f ./local/generator
```

8. Install Grafana Dashboard:
```
kubectl apply -f ./local/grafana
```


# Installing Flink on GCP

To install Flink cluster on GCP, please follow instructions in `gcp` folder:
[How to run Flink on GCP](./gcp)
