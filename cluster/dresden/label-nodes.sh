#!/bin/bash
for i in {11..50} 
do
    echo "Unlabeling node stream$i"
    kubectl label nodes stream$i tier-
done

kubectl label nodes stream22 tier=prometheus
kubectl label nodes stream12 tier=manager
#kubectl label nodes stream13 tier=injector
kubectl label nodes stream14 tier=injector
kubectl label nodes stream15 tier=injector
kubectl label nodes stream16 tier=zkh
kubectl label nodes stream17 tier=zkh
kubectl label nodes stream18 tier=zkh

kubectl label nodes stream21 tier=jobmanager
for i in {23..50} 
do
    echo "Labeling task manager node stream$i"
    kubectl label nodes stream$i tier=taskmanager
done


