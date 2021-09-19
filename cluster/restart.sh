#!/usr/bin/env bash

kubectl scale deployment flink-jobmanager --replicas=0
kubectl scale deployment flink-taskmanager --replicas=0

sleep 10

kubectl scale deployment flink-jobmanager --replicas=1
kubectl scale deployment flink-taskmanager --replicas=29

jobmanager_status=""
while [[ ${jobmanager_status} != "Running" ]]; do
    jobmanager_status=$(kubectl get pods | grep jobmanager | awk '{print $3}')
    echo "Jobmanager not running yet"
    sleep 2
done

taskmanager_status=""
while [[ ${taskmanager_status} != "Running" ]]; do
    taskmanager_status=$(kubectl get pods | grep taskmanager | awk 'NR==1 {print $3}')
    echo "Taskmanagers are not running yet"
    sleep 2
done
