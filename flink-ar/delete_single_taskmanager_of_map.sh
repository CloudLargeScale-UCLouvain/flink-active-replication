#!/usr/bin/env bash

# Get the ID of the running job (there can only be one!)
jobId=$(curl -s localhost:8081/jobs/ | jq -r ".jobs[0].id")

# Get the vertexID of the one running the map task
vertexId=$(curl -s localhost:8081/jobs/${jobId}/ | jq -c -r '.vertices[] | select(.name | . and contains("Map")) | .id')

# Find out which host runs the first parallel subtask of this vertex
portOfHost=$(curl -s localhost:8081/jobs/${jobId}/vertices/${vertexId}/ | jq -c -r ".subtasks[0].host" | awk -F ':' '{print $2}')

# Kill that host by checking with process listens on that port and kill that process
echo "Killing a taskmanager that is listening on port ${portOfHost}"
lsof -nP -i4TCP:${portOfHost} | grep LISTEN | awk '{print $2}' | xargs kill -9
