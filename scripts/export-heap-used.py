#!/usr/bin/env python3

import requests
from pprint import pprint

r = requests.get(url="http://localhost:9090/api/v1/query", params={"query": "flink_taskmanager_Status_JVM_Memory_Heap_Used[30m]"})

res = r.json()

print("name,instance,node_name,tm_id,timestamp,value")

for metricRes in res["data"]["result"]:
    name = metricRes["metric"]["name"]
    instance = metricRes["metric"]["instance"]
    node_name = metricRes["metric"]["node_name"]
    tm_id = metricRes["metric"]["tm_id"]

    for dp in metricRes["values"]:
        print("%s,%s,%s,%s,%s,%s" % (name, instance, node_name, tm_id, dp[0], dp[1]))
