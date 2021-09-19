#!/bin/bash
kubectl create ns manager
#Helm3
helm delete --namespace manager manager
#Helm2
helm delete manager --purge

./reset-pv.sh
kubectl delete crd --all
cd ../chart
helm dep update
#Helm3
helm install manager --namespace manager --set hostAddress=10-0-0-33.nip.io .
#Helm2
helm install --name manager --namespace manager --set hostAddress=10-0-0-33.nip.io .