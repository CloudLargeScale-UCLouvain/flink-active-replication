#!/usr/bin/env zsh
function get_unused_port() {
  (netstat -atn | awk '{printf "%s\n%s\n", $4, $4}' | grep -oE '[0-9]*$'; seq 32768 61000) | sort -n | uniq -u | head -n 1
}

export FLINK_DIR=~/dev/flink/flink-dist/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT
jps | grep 'TaskManager*\|Standalone' | awk '{print $1}' | xargs kill -9

cd ~/dev/flink-fault-tolerance-baseline/kafka/ && docker-compose down && sleep 2 && docker-compose up -d && sleep 2 && cd -

rm -f $FLINK_DIR/log/*

FLINK_ENV_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$(get_unused_port)" $FLINK_DIR/bin/jobmanager.sh start
sleep 5
FLINK_ENV_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$(get_unused_port)" $FLINK_DIR/bin/taskmanager.sh start
sleep 5
tail -n 10000 -f $FLINK_DIR/log/flink-florianschmidt-taskexecutor-*log
