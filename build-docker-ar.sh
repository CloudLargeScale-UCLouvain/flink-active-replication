#!/usr/bin/env bash
version=1.7.2-ar
tag="grosinosky"
echo "Building docker image with tag:  ${tag}/flink:${version}"

docker build --network=host -t "${tag}/flink:${version}" .
docker push "${tag}/flink:${version}"
