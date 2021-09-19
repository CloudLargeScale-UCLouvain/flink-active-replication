#!/usr/bin/env bash
version=1.7.2-baseline
tag="grosinosky"
echo "Building docker image with tag:  ${tag}/flink:${version}"

docker build --network=host -f Dockerfile.baseline  -t "${tag}/flink:${version}" .
docker push "${tag}/flink:${version}"
