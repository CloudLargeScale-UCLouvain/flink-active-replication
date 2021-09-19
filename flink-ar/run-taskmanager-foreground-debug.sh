#!/usr/bin/env bash

function get_unused_port() {
  (netstat -atn | awk '{printf "%s\n%s\n", $4, $4}' | grep -oE '[0-9]*$'; seq 32768 61000) | sort -n | uniq -u | head -n 1
}

FLINK_DIST="/Users/florianschmidt/dev/flink/flink-dist/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT"

# Stop all previously running taskmanagers


# Clean up those logs
if [[ $1 == "taskmanager" ]]; then
    while [[ $(${FLINK_DIST}/bin/${1}.sh stop) != *"No taskexecutor daemon to stop on"* ]]; do
        echo "Stopping another ${1}"
    done
elif [[ $1 == "jobmanager" ]]; then
    while [[ $(${FLINK_DIST}/bin/${1}.sh stop) != *"No"* ]]; do
        echo "Stopping another ${1}"
    done
fi

# Clean up those logs
if [[ $1 == "taskmanager" ]]; then
    rm ${FLINK_DIST}/log/*taskexecutor*
elif [[ $1 == "jobmanager" ]]; then
    rm ${FLINK_DIST}/log/*standalone*
fi

# Clean up those logs
if [[ $1 == "taskmanager" ]]; then
    sleep 1
    # Run another taskmanager with debugger enabled
    FLINK_ENV_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${2}" ${FLINK_DIST}/bin/${1}.sh start
    sleep 1
    FLINK_ENV_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$(get_unused_port)" ${FLINK_DIST}/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT/bin/taskmanager.sh start
    sleep 1
    FLINK_ENV_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$(get_unused_port)" ${FLINK_DIST}/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT/bin/taskmanager.sh start
    sleep 1
    FLINK_ENV_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$(get_unused_port)" ${FLINK_DIST}/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT/bin/taskmanager.sh start
    sleep 1
    FLINK_ENV_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$(get_unused_port)" ${FLINK_DIST}/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT/bin/taskmanager.sh start
    sleep 1
    FLINK_ENV_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$(get_unused_port)" ${FLINK_DIST}/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT/bin/taskmanager.sh start
    sleep 1
    FLINK_ENV_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$(get_unused_port)" ${FLINK_DIST}/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT/bin/taskmanager.sh start
elif [[ $1 == "jobmanager" ]]; then
    FLINK_ENV_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${2}" ${FLINK_DIST}/bin/${1}.sh start &
fi

# Sleep so that the debugger port is open and can accept a connection
# A little dirty, I know 🙈
sleep 5
