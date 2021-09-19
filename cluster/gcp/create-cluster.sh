#!/usr/bin/env bash
gcloud container clusters create flink-testing \
    --machine-type=n1-highmem-2 \
    --num-nodes=5 \
    --max-nodes=5 \
   --region=us-central1 \

