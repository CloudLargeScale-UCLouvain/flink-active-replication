# Running measurements

Please consult main page in case of any issues: [Issues Track](../README.md)

## Fresh start
Before running fresh experiment ensure that Flink cluster is fresh and Jobmanager and Taskmanagers do not contain any logs from previous runs.
You must have jq installed.
You can ensure it by running
```
../restart_cluster.sh
```

## Start measurements

Start measurements:
```
JOB_JAR_FILE=/home/ubuntu/betterCloud/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar  JOBMANAGER_PORT=30081  PROMETHEUS_PORT=9090  FLINK_SNAPSHOT=/home/ubuntu/flink-ar-jar/flink/flink/flink-dist/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT FLINK_SNAPSHOT_IMAGE=olehbodunov/flink-ar python3 measure.py
```

## Environmental variables

`JOB_JAR_FILE` location of JAR file to collect measurements from
`JOBMANAGER_PORT` Port of Flink Jobmanager
`PROMETHEUS_PORT` Prometheus port to collect results from
`FLINK_SNAPSHOT` Flink build to use (e.g. Active replication branch build)
`FLINK_SNAPSHOT_IMAGE` Flink image build from snapshot specified above

**!Note** Flink build is required to correctly run jobs if `active-replication` branch is used.
