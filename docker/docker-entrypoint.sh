#!/bin/sh

###############################################################################
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
# limitations under the License.
###############################################################################

# If unspecified, the hostname of the container is taken as the JobManager address
JOB_MANAGER_RPC_ADDRESS=${JOB_MANAGER_RPC_ADDRESS:-$(hostname -f)}

drop_privs_cmd() {
    if [ $(id -u) != 0 ]; then
        # Don't need to drop privs if EUID != 0
        return
    elif [ -x /sbin/su-exec ]; then
        # Alpine
        echo su-exec flink
    else
        # Others
        echo gosu flink
    fi
}

cp ${FLINK_HOME}/conf/flink-conf-readonly.yaml ${FLINK_HOME}/conf/flink-conf.yaml

if [ "$1" = "help" ]; then
    echo "Usage: $(basename "$0") (jobmanager|taskmanager|help)"
    exit 0
elif [ "$1" = "jobmanager" ]; then
    shift 1
    echo "Starting Job Manager"
    sed -i -e "s/jobmanager.rpc.address: localhost/jobmanager.rpc.address: ${JOB_MANAGER_RPC_ADDRESS}/g" "$FLINK_HOME/conf/flink-conf.yaml"
    echo "blob.server.port: 6124" >> "$FLINK_HOME/conf/flink-conf.yaml"
    echo "query.server.port: 6125" >> "$FLINK_HOME/conf/flink-conf.yaml"

    echo "config file: " && grep '^[^\n#]' "$FLINK_HOME/conf/flink-conf.yaml"
    exec $(drop_privs_cmd) "$FLINK_EXEC_PATH/bin/jobmanager.sh" start-foreground "$@"
elif [ "$1" = "taskmanager" ]; then
    TASK_MANAGER_NUMBER_OF_TASK_SLOTS=${TASK_MANAGER_NUMBER_OF_TASK_SLOTS:-$(grep -c ^processor /proc/cpuinfo)}

    sed -i -e "s/jobmanager.rpc.address: localhost/jobmanager.rpc.address: ${JOB_MANAGER_RPC_ADDRESS}/g" "$FLINK_HOME/conf/flink-conf.yaml"
    sed -i -e "s/taskmanager.numberOfTaskSlots: 1/taskmanager.numberOfTaskSlots: $TASK_MANAGER_NUMBER_OF_TASK_SLOTS/g" "$FLINK_HOME/conf/flink-conf.yaml"
    echo "blob.server.port: 6124" >> "$FLINK_HOME/conf/flink-conf.yaml"
    echo "query.server.port: 6125" >> "$FLINK_HOME/conf/flink-conf.yaml"

    echo "Starting Task Manager"
    echo "config file: " && grep '^[^\n#]' "$FLINK_HOME/conf/flink-conf.yaml"
    exec $(drop_privs_cmd) "$FLINK_EXEC_PATH/bin/taskmanager.sh" start-foreground
elif [ "$1" = "job" ]; then
    #--state-backend rocksdb --checkpoint-frequency 30 --incremental-checkpointing false --bufferTimeout -2 --latency-tracking-interval -1 --idle-marks true --rate 5000 --map-parallelism 2 --length 1
    echo "Launch job $JOB_NAME (jar: $JAR_FILE) on ${FLINK_API_HOST}:${JOBMANAGER_PORT} with $EXTRA_OPTS "
    $FLINK_HOME/bin/flink run -m ${FLINK_API_HOST}:${JOBMANAGER_PORT} -c ${JOB_NAME} -d ${JAR_FILE:-/opt/flink/betterCloudExample/target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar} ${EXTRA_OPTS}
    exit 0
fi

exec "$@"