#!/usr/bin/env bash
RUN_DATE=$1
kubectl get pods | grep jobmanager | awk '{print $1}' | xargs -I {} kubectl logs {} > ../measurements/${RUN_DATE}/jm-logs.txt
kubectl get pods | grep taskmanager | awk '{print $1}' | xargs -I {} sh -c 'kubectl logs $1 > ../measurements/$2/tm-$1-logs.txt' -- {} ${RUN_DATE}

job_id=$(curl -s localhost:8081/jobs/ | jq '.jobs[].id' | tr -d '"')
sink_vertex_id=$(curl -s localhost:8081/jobs/${job_id}/ | jq -r -c '.vertices[] | select(.name | . and contains("Sink")) | .id')
sink_host=$(curl -s localhost:8081/jobs/${job_id}/vertices/${sink_vertex_id}/ | jq -r -c '.subtasks[0].host' | awk -F":" '{print $1}')
file_size=$(kubectl exec -it ${sink_host} -- bash -c 'ls -lah /tmp/latencies.txt' | awk '{print $5}')
echo "File size is ${file_size}"
kubectl cp ${sink_host}:/tmp/latencies.txt ../measurements/${RUN_DATE}/latencies.txt

kubectl get pods |grep prometheus | awk '{print $1}' | xargs -I {} kubectl port-forward {} 9090 &

sleep 5

./export-cpu-load.py > ../measurements/${RUN_DATE}/cpu.csv
./export-heap-max.py > ../measurements/${RUN_DATE}/heap-map.csv
./export-heap-used.py > ../measurements/${RUN_DATE}/heap-used.csv
./export-heap-commited.py > ../measurements/${RUN_DATE}/heap-committed.csv