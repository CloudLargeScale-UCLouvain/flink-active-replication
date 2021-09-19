# Flink active replication

Source code of KaZoo and LiveRobin algorithms for Flink 1.7, and experimentation scripts from the SRDS'21 paper: Active replication for latency-sensitive stream processing in Apache Flink. If you want to use this code, please cite the paper.

## Abstract

Stream processing frameworks allow processing massive amounts of data shortly after it is produced, and enable a fast reaction to events in scenarios such as datacenter monitoring, smart transportation, or telecommunication networks.
Many scenarios depend on the fast and reliable processing of incoming data, requiring low end-to-end latencies from the ingest of a new event to the corresponding output.
The occurrence of faults jeopardizes these guarantees: Currently-leading high-availability solutions for stream processing such as Spark Streaming or Apache Flink's implement passive replication through snapshotting, requiring a stop-the-world operation to recover from a failure.
Active replication, while incurring higher deployment costs, can overcome these limitations and allow to mask the impact of faults and match stringent end-to-end latency requirements.
We present the design, implementation, and evaluation of active replication in the popular Apache Flink platform.
Our study explores two alternative designs, a leader-based approach leveraging external services (Kafka and ZooKeeper) and a leaderless implementation leveraging a novel deterministic merging algorithm.
Our evaluation using a series of microbenchmarks and a SaaS cloud monitoring scenario on a 37-server cluster show that the actively-replicated Flink can fully mask the impact of faults on end-to-end latency.

## Repository tree

- [flink/](flink/): source code of Flink 1.7
- [flink-ar/](flink-ar/): source code of modified Flink 1.7
- [docker/](docker/): needed files for Flink Docker image generation
- [betterCloudExample/](betterCloudExample/): source code of the test jobs
- [scripts/](scripts/): various Python scripts used for experimentation (job stop, results retrieval, ...)
- [experiments/](experiments/)
  - [charts/](experiments/charts/): Helm charts
    - [flink/](experiments/charts/flink): Flink Helm chart
    - [flink-job/](experiments/charts/flink-job): Flink test jobs injector Helm charts
  - [docker/](experiments/charts/): Docker image used for Python scripts execution (`grosinosky/python-mc:0.0.3`)
  - [scripts/](experiments/scripts/): experiment notebooks
- [cluster/](cluster/)
  - [dresden/](cluster/dresden/): values for sample cluster (experimentations SRDS paper)
  - [kind/](cluster/kind/): values for Kind test
- [ManagerKubernetesExperimentation/](ManagerKubernetesExperimentation/): chart for installation of required modules for experimentation orchestration, and results storage (Jupyter)

## Build the code

Please first clone the regular Flink 1.7-SNAPSHOT version from [this repository](https://github.com/guillaumerosinosky/flink/tree/master) in the `flink` directory in the root of the repository. This version, build with the following scripts (`compile-flink` target), is used for comparison with our modified version (`compile-flink-ar` target).

```bash
make compile-flink
make compile-flink-ar
```

Detailed technical description of the modified source code of Flink (LiveRobin and KaZoo implementations) is currently in the process of writing and will be made available later. Please take contact with us if you need information on this subject, or check [this repository](https://github.com/guillaumerosinosky/flink/tree/active-replication) to have access to the git log history.

### Benchmark

```bash
make compile-benchmark
```

### Containers

```bash
make build-flink-image
make build-flink-ar-image
```

## Deployment on Kubernetes

Load tests can be done on the [unmodified version of Flink](flink/) or our [modified version](flink-ar/). We propose Helm charts for the deployment of the different tested modules and the execution of the load testing scenarios.

We propose two deployment scenarios: one with [Kind](https://kind.sigs.k8s.io/) (tested with version v0.7.0) for local tests, and an example of deployment on an on-premises cluster.

The deployment scripts are based on Jupyter for tests orchestration and Minio as the results storage server. Both need a local hostPath persistentVolume (please be aware scripts cannot be used simply on cloud-managed storage).

### Prerequisites

Docker images should have been built and pushed on [DockerHub](https://hub.docker.com/). Please change the user (default: `grosinosky`) by yours in build scripts (`build-docker-baseline.sh` and `build-docker-ar.sh`) and Helm charts.  Once it has been done, you can launch the following:

```bash
make init
```

### Deployment with Kind

Kind runs a local Kubernetes cluster using Docker containers to emulate each node. We propose scripts to deploy our support scripts to test the general behaviour.

The user should prepare a directory for the source code directory (this repository) and one for the results storage, with 777 rights. Both should be set in the Kind configuration file, in the `extraMount` section of the [cluster/kind/kind-flink.yaml](cluster/kind/kind-flink.yaml#L25) file.

```bash
./init.sh
```

### Deployment on an on-premises cluster

We assume that the user has a running on-premises Kubernetes cluster with a sufficient number of nodes. To reproduce our results, we assume the following nodes' labels:

- manager (1 node, __preferably with SSD__)
- prometheus (1 node)
- injector (2 nodes)
- zkh (3 nodes)
- job manager (1 node)
- taskmanager (27 nodes)

The labeling of the nodes is made in the [cluster/dresden/label-nodes.sh](cluster/dresden/label-nodes.sh).

We assume the `manager` node is used for operations and results storage. This repository should be cloned there, and a measurements directory should be prepared. Persistent volumes directories should be modified accordingly: Directories of the [persistent volumes](cluster/dresden/pv) should be also set to yours, the `pv-git` and `pv-jupyter` persistent volumes targeting this repository, and `pv-minio` a directory where the experiment data will be put. All directories should be writeable with `777` rights.  
We also assume Local path provisioner is installed, with the storage class name "local-path".

The manager chart uses a specific [values file](cluster/dresden/manager.yaml) where it is possible to enable Prometheus and Loki. Be aware though that they incur a significant overhead.

Once everything is set, [cluster/dresden/install.sh](cluster/dresden/install.sh) can be launched, it will install Jupyter and Minio.

### Access to Jupyter and Grafana

The access to Jupyter is not enabled by default from outside the cluster. Please port-forward (or use any prefered external access method such as NodePort or Ingress). 

```bash
kubectl port-forward deployments/manager-jupyter 8888:8888 -n manager --address 0.0.0.0 &
```

With Prometheus/Loki activated, you can access Grafana by forwarding the port 3000, and launch a browser on `http://localhost:3000`. Credentials for Grafana are `admin` with password `prom-operator` (default for Prometheus Operator).

```bash
kubectl port-forward services/manager-grafana 3000:80 -n manager --address 0.0.0.0
```

## Experiments

A version of the Jupyter notebooks used for tests and experimentation is available in [experiments/scripts](experiments/scripts), reachable from Jupyter. The notebooks will be precisely documented in the near future.

Note: Experiments based on the Bettercloud workload use Kafka for the source and sinks. Kafka should be installed using the following chart version (launched from the root of the repository):

```bash
helm install injector bitnami/kafka -f experiments/charts/values-kafka.yaml --version="11.7.2" 
```
