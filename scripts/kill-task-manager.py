import json
import os
import sys
import pprint
import subprocess
import traceback
from datetime import datetime, timedelta
from minio import Minio
import requests
import time
import csv
import threading

JOBMANAGER_PORT = os.getenv("JOBMANAGER_PORT", 31081) #8081
FLINK_API_HOST = os.getenv("FLINK_API_HOST", "stream21")
TASK_NAME = os.getenv("TASK_NAME", "map-at-level-0")
KILL_DELAY = int(os.getenv("KILL_DELAY", 150))
REPLICA_INDEX = int(os.getenv("REPLICA_INDEX", 0))
OPERATOR_INDEX = int(os.getenv("OPERATOR_INDEX", 0))
BUCKET = os.getenv("BUCKET")
XP_NAME = os.getenv("XP_NAME")
WAIT_FOR_KILL = os.getenv("WAIT_FOR_KILL", True)
GRACE_PERIOD = int(os.getenv("GRACE_PERIOD", 30))
KAZOO_TARGET = os.getenv("KAZOO_TARGET", "")
# wait for one minute to let flink be initialized 
time.sleep(60)
start_time_timestamp_gen = None 
job_id = None
r = requests.get("http://{host}:{port}/jobs/overview".format(host=FLINK_API_HOST, port=JOBMANAGER_PORT))
result = r.json()
for job in result["jobs"]:
    if (job["name"] == "Job") or (job["name"] == ""):
        job_id = job["jid"]
        start_time_timestamp = job["start-time"]
    if (job["name"] == "DataGenJob"):
        start_time_timestamp_gen = job["start-time"]
if (job_id is not None):
    print("Found job:" + job_id)
else:
    print("Job not found")
    exit(-1)

task_id = None
r = requests.get("http://{host}:{port}/jobs/{job_id}".format(host=FLINK_API_HOST, port=JOBMANAGER_PORT, job_id=job_id))
result = r.json()
for task in result["vertices"]:
    if (task["name"] == TASK_NAME):
        task_id = task["id"]
        break
if (task_id is not None):
    print("Found task:" + task_id)
else:
    print("Task not found")
    exit(-2)

host = None
r = requests.get("http://{host}:{port}/jobs/{job_id}/vertices/{task_id}/subtasktimes".format(host=FLINK_API_HOST, port=JOBMANAGER_PORT, job_id=job_id, task_id=task_id))
result = r.json()
host = ""
subtask_id = ""
for subtask in result["subtasks"]:
    if KAZOO_TARGET == "": 
        if "replicaIndex" in subtask:
            # get only first replica index, first operator
            if subtask["replicaIndex"] == 0 and subtask["operatorIndex"] == 0:
                host = subtask["host"]
                subtask_id = subtask["subtask"]
                break
        else:
            # take first instead
            host = subtask["host"]
            subtask_id = subtask["subtask"]
            break
    else:
        if "operatorIndex" in subtask and subtask["operatorIndex"] == 0:
            # look for "took leadership" in the logs of this task manager
            
            # check using Loki disabled because of effects on latency
            #r = requests.get('http://admin:prom-operator@manager-grafana.manager:80/api/datasources/proxy/2/api/prom/query?direction=BACKWARD&limit=1000&regexp=&query={{pod=~"{}"}} |= "took leadership"&start=0'.format(subtask["host"]))
            # some results with "took leadership" in the logs means it's a leader
            #if len(r.json()["streams"]) > 0: # some logs mean it's the leader
            cmd = "kubectl logs {} |grep \"took leadership\"".format(subtask["host"].split(":")[0])
            exit_code = subprocess.call(
                cmd,
                shell=True
            )            
            print("Exit code {} -> {} ".format(subtask["host"].split(":")[0], exit_code))
            if exit_code == 0:
                if KAZOO_TARGET == "leader":
                    print("Found leader.")
                    host = subtask["host"]
                    subtask_id = subtask["subtask"]
                    break
                else:
                    print("Passing leader.")
            else: # other case it's a follower
                if KAZOO_TARGET == "follower":
                    print("Found follower.")
                    host = subtask["host"]
                    subtask_id = subtask["subtask"]
                    break                    
                else:
                    print("Passing follower.")

if host == "": # not found !
    print("Task manager not found..")
    exit(-4)


if host is not None:
    print("Found host:" + host)
else:
    print("Subtask not found")
    exit(-3)

start_time = datetime.fromtimestamp(start_time_timestamp / 1000)
print("Wait for {duration}s since {start_time} before triggering kill".format(
    duration=KILL_DELAY,
    start_time=start_time))
kill_time = start_time + timedelta(seconds=KILL_DELAY)
if datetime.now() < kill_time:
    print("Waiting for {}".format(kill_time-datetime.now()), end="",flush=True)
    while datetime.now() < kill_time:
        print(".", end="",flush=True)
        time.sleep(1)
    print("`\nDone.")

print("Deleting pod {host}".format(host=host))
kill_date = datetime.now().timestamp()
kill_pod_command = "kubectl delete pod/{host} --grace-period={grace_period}".format(host=host, grace_period=GRACE_PERIOD)
print("Launch command: {}".format(kill_pod_command))
subprocess.Popen(kill_pod_command, shell=True)



# if WAIT_FOR_KILL, check status to find the last moment
checking_job_state = True
while checking_job_state:
    r = requests.get("http://{host}:{port}/jobs/{job_id}/vertices/{task_id}".format(host=FLINK_API_HOST, port=JOBMANAGER_PORT, job_id=job_id, task_id=task_id))
    result = r.json()
    for subtask in result["subtasks"]:
        if subtask["subtask"] == subtask_id:
            if subtask["status"] != "RUNNING":
                now = datetime.now()
                failing_time_timestamp = now.timestamp()
                print("Failing time for task: {} - {}".format(now, failing_time_timestamp))
                checking_job_state = False
                break                    
    time.sleep(0.1)

checking_job_state = True
while checking_job_state:
    r = requests.get("http://{host}:{port}/jobs/{job_id}/vertices/{task_id}".format(host=FLINK_API_HOST, port=JOBMANAGER_PORT, job_id=job_id, task_id=task_id))
    result = r.json()
    for subtask in result["subtasks"]:
        if subtask["subtask"] == subtask_id:
            if subtask["status"] != "FAILING":
                now = datetime.now()
                end_time_timestamp = now.timestamp()
                print("End time for task: {} - {}".format(now, end_time_timestamp))
                checking_job_state = False
                break                    
    time.sleep(0.1)


# if WAIT_FOR_KILL != WAIT_FOR_KILL:
#     checking_job_state = True
#     while checking_job_state:
#         r = requests.get("http://{host}:{port}/jobs/overview".format(host=FLINK_API_HOST, port=JOBMANAGER_PORT))
#         result = r.json()
#         job_present = False
#         for job in result["jobs"]:
#             if job["jid"] == job_id:
#                 job_present = True
#                 failing_time_timestamp = datetime.now().timestamp()
#                 print("{} Job {}, state {} ".format(datetime.now(), job["jid"], job["state"]))
#                 if job["state"] != "RUNNING":
#                     checking_job_state = False 
#         if job_present == False:
#             checking_job_state = False 
#         time.sleep(0.1)

#     checking_job_state=True
#     while checking_job_state:
#         r = requests.get("http://{host}:{port}/jobs/overview".format(host=FLINK_API_HOST, port=JOBMANAGER_PORT))
#         result = r.json()
#         job_present = False
#         for job in result["jobs"]:
#             if job["jid"] == job_id:
#                 job_present = True
#                 end_time_timestamp = datetime.now().timestamp()
#                 print("{} Job {}, state {} ".format(datetime.now(), job["jid"], job["state"]))
#                 if job["state"] != "FAILING":
#                     checking_job_state = False 
#         if job_present == False:
#             checking_job_state = False 
#         time.sleep(0.1)

# save timestamps in map
map_results = {}
map_results["job_id"] = job_id
map_results["start_time_initial"] = start_time_timestamp / 1000
map_results["planned_kill_date"] = kill_time.timestamp()
map_results["kill_date"] = kill_date
map_results["end_time_initial"] = end_time_timestamp 
map_results["failing_time_initial"] = failing_time_timestamp 
map_results["kazoo_target"] = KAZOO_TARGET
if start_time_timestamp_gen is not None:
    map_results["start_time_gen"] = start_time_timestamp_gen / 1000

# save kill-result file with results map
minio = Minio('flink-minio.default:9000',
              access_key='AKIAIOSFODNN7EXAMPLE',
              secret_key='wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
              secure=False)
bucket_list = [b.name for b in minio.list_buckets()]
if BUCKET not in bucket_list:
    minio.make_bucket(BUCKET)
with open('kill-result.csv', 'w') as f:
    for key in map_results.keys():
        f.write("%s,%s\n"%(key,map_results[key]))
file_stat = os.stat('kill-result.csv')
with open('kill-result.csv', 'rb') as data:
    minio.put_object(BUCKET, XP_NAME + "/kill-result.csv", data, file_stat.st_size, 'text/plain')