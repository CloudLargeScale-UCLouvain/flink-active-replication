# Better Cloud use-case

Better Cloud is a company that uses Flink in production.

The topology they use is the following:
![topology](./images/topology.png)

Topology is used for benchmarking with load tests described in [Measurements script](../scripts/measure.py)

## Build steps

### Requirements:

1. Ensure that build correct `flink active-replication` branch is build.

2. Compile all necessary jars using Maven:

```
mvn clean install \
	 -Drat.ignoreErrors=true \
	 -Dmaven.test.skip \
	 -Dcheckstyle.skip \
	 -Dhadoop.version=2.8.0 \
	 -Pinclude-hadoop -f pom.xml
```

3. Export Jars from `target` directory to environmental variables,
and correct Flink active-replication build:
```
export JOB_JAR_FILE="{$PWD}/target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar"
export FLINK_SNAPSHOT={$flink-dir}/flink/flink-dist/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT
```
or note the absolute paths, to be passed to [measurements scripts](../scripts/measure.py#L13) 
