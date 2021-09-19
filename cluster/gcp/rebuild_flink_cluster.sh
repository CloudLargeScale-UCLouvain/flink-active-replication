#!/usr/bin/env bash

function step {
    echo ""
    echo -e "----------------------------------------------------------------------------\r------------------ $1 "
    echo ""
}

step "Kill kubectl (proxies, port-forwarding)"
set +e
killall kubectl

step "Delete all pods"
kubectl get pods | awk '{print $1}' | xargs kubectl delete pod

step "Remove previous cluster"
kubectl delete -f cluster/gcp/

step "Create admin clusterrolebinding"
kubectl create clusterrolebinding florian-cluster-admin-binding --clusterrole=cluster-admin --user=florian@data-artisans.com

set -e

step "Patch .yaml files with docker image name ${DOCKER_IMAGE_NAME}"
sed -i -c "s|DOCKER_IMAGE_NAME|${DOCKER_IMAGE_NAME}|g" cluster/gcp/jobmanager-deployment.yaml
sed -i -c "s|DOCKER_IMAGE_NAME|${DOCKER_IMAGE_NAME}|g" cluster/gcp/taskmanager-deployment.yaml

step "Create new cluster"
kubectl create -f cluster/gcp

step "Wait for flink jobmanager to be running"
jobmanager_status=""
while [[ ${jobmanager_status} != "Running" ]]; do
    jobmanager_status=$(kubectl get pods | grep jobmanager | awk '{print $3}')
    echo "Not running yet"
    sleep 1
done

step "Start port-forwarding for jobmanager 8081"
kubectl get pods | grep jobmanager | awk '{print $1}' | xargs -I {} kubectl port-forward {} 8081 &

step "Start port-forwarding graphana on 3000"
kubectl get pods | grep grafana | awk '{print $1}' | xargs -I {} kubectl port-forward {} 3000 &

step "Run kubectl proxy"
kubectl proxy &