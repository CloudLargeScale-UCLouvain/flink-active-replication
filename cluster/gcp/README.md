# Create a new cluster on Google Kubernetes Engine
Create a new kubernetes cluster on GCP according to https://cloud.google.com/kubernetes-engine/docs/quickstart

**Create cluster**

`gcloud container clusters create flink-testing`

**Configure jkubectl**

`gcloud container clusters get-credentials flink-testing`

**Show the nodes in cluster**

`kubectl get nodes` shows all nodes in cluster

`gcloud container clusters describe flink-testing` gets cluster description

# Deploy Flink
## Jobmanager
`kubectl create -f jobmanager-deployment.yaml`

## Taskmanagers
`kubectl create -f taskmanager-deployment.yaml`

## Show the deployments
`kubectl get deployments`

## Access Flink UI
localhost:8001/api/v1/proxy/namespaces/default/services/flink-jobmanager:8081/

Make me cluster admin
kubectl create clusterrolebinding florian-cluster-admin-binding --clusterrole=cluster-admin --user=florian.schmidt.1994@gmail.com