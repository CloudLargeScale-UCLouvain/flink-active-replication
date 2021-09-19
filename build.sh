#!/usr/bin/env bash

echo "Cloning Flink"
make patch-flink
make compile-flink
make compile-benchmark

echo "Building Flink image"
make build-flink-image

echo "Build K8s cluster"
make install-helm
make build-cluster
make deploy-flink

echo "Make final measurements"
make measure
