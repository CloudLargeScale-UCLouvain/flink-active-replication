import json
import os
import sys
import pprint
import subprocess
import traceback
from datetime import datetime

import requests
import time


FLINK_SNAPSHOT_IMAGE = os.getenv("FLINK_IMAGE","grosinosky/flink:1.7.2-ar") #"gcr.io/florian-master-thesis-1337/flink:flink-1.7-SNAPSHOT"
FLINK_BASELINE_IMAGE = "grosinosky/flink:1.7.2"
JOB_NAME = os.getenv("JOB_NAME")
JOBMANAGER_PORT = os.getenv("JOBMANAGER_PORT", 31081) #8081
PROMETHEUS_PORT = os.getenv("PROMETHEUS_PORT",30090) #9090
FLINK_API_HOST = os.getenv("FLINK_API_HOST", "0.0.0.0")
PROMETHEUS_HOST = os.getenv("PROMETHEUS_HOST", "0.0.0.0")
JOB_JAR_FILE = os.getenv("JOB_JAR_FILE", "/Users/oleh/Code/flink/flink-active-replication-benchmark-master/target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar")
TEST_NAME = os.getenv("TEST_NAME", "test")
EXTRA_OPTS = os.getenv("EXTRA_OPTS")
NUM_SOURCES = os.getenv("NUM_SOURCES", 1)
GET_ALL_SINKS = os.getenv("GET_ALL_SINKS", "false")
XP_NAME = os.getenv("XP_NAME", "")
TIMESTAMP_JOB = float(os.getenv("TIMESTAMP"))
experiment_job_id = None
experiment_run_date = None


def noop():
    pass

def retrieve(job_name,
             job_parameters,
             test_fn=noop,
             test_name="",
             num_sources=1,
             jar="target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar",
             image=FLINK_SNAPSHOT_IMAGE):
    if image == FLINK_BASELINE_IMAGE:
        image_name = "1_7_2"
    else: 
        image_name = "1_7_SNAPSHOT"
    job_id = get_current_job_id().split("\n")[0]
    print("JOB_ID is ", job_id)    
    try:
        #
        if XP_NAME != "":
            run_date = XP_NAME
        else:
            run_date = f"{datetime.today().strftime('%Y-%m-%d-%H-%M-%S')}-{test_name}-{job_name}"            
        global experiment_run_date
        experiment_run_date = run_date

        measurement_path = f"../measurements/{run_date}"
        default_parameters = {
            "--state-backend": "rocksdb",
            "--checkpoint-frequency": "30",
            "--incremental-checkpointing": "false",
            "--bufferTimeout": "-2",
            "--latency-tracking-interval": "-1",
            "--idle-marks": "false",
            "--kafka.servers" : "flink-my-release:9092",
            "--zk.servers": "flink-zookeeper:2181"
        }
        jar_file = jar
        parameters = {**default_parameters, **job_parameters}


        prepare_measurement_dir(measurement_path)

        global experiment_job_id
        experiment_run_date = job_id

        try:
            cancel_job(job_id)
        except Exception as e:
            print("Error while cancelling the job with id: ", job_id)

        details = get_job_details(job_id)
        parameters["start_time"] = details["start-time"] / 1000
        parameters["end_time"] = details["end-time"] / 1000

        save_job_parameters(job_name, parameters, measurement_path)

        datagen_job_id = get_current_datagen_job_id()
        if datagen_job_id != "":
            try:
                cancel_job(datagen_job_id)
            except Exception as e:
                print("Error while cancelling the datagenjob with id: ", job_id)
            

        try:
            save_measurements(run_date, job_id, num_sources)
        except Exception as e:
            print("Error while saving measurements for job_id: ", job_id)
            print("Exception for saving measurements call is: ", e)

        save_job_graph(job_id, measurement_path)
        #cleanup(image)
    except Exception as e:
        #cleanup(image)
        traceback.print_tb(e.__traceback__)

    print("Finished running test for %s" % job_name)
    return run_date, job_id

def prepare_measurement_dir(measurement_path):
    print("Creating mesurement path %s" % measurement_path)
    subprocess.check_call(["mkdir", "-p", measurement_path])

def save_job_parameters(job_name, parameters, measurement_path):
    print("Saving job parameters %s" % job_name)
    with open("%s/info.txt" % measurement_path, "w+") as f:
        f.write("job_name=%s\n" % job_name)
        for key, value in parameters.items():
            f.write("%s=%s\n" % (key, value))


def cancel_job(job_id):
    print("Canceling job %s" % job_id)
    requests.patch("http://{host}:{port}/jobs/{job_id}/".format(host=FLINK_API_HOST, job_id=job_id, port=JOBMANAGER_PORT), params={"mode": "cancel"})
    
    count=6
    while count >= 1:
        print("Waiting for the job to be canceled..")
        time.sleep(10)
        r = requests.get("http://{host}:{port}/jobs/overview/".format(host=FLINK_API_HOST, job_id=job_id, port=JOBMANAGER_PORT))
        for job in r.json()["jobs"]:
            print(job)
            if job["jid"] == job_id:
                if job["state"] == "CANCELED":
                    print("Job already canceled.")
                    count = -1
                    break
                if job["end-time"] != -1:
                    count = -1
        count = count -1
    if count == 0:
        print("Job could not be canceled")
        exit(-1)

def get_current_job_id():
    print("Running get_current_job_id")
    out = subprocess.check_output("set -e pipefail && curl -s {host}:{port}/jobs/overview | jq '.jobs[] | select((.name==\"Job\") or (.name==\"\") and (.state==\"RUNNING\")).jid' | tr -d '\"'".format(host=FLINK_API_HOST, port=JOBMANAGER_PORT),
                                   shell=True)
    print("Finished get_current_job_id is %s" % out.decode("UTF-8").strip())
    return out.decode("UTF-8").strip()

def get_current_datagen_job_id():
    print("Running get_current_datagen_job_id")
    out = subprocess.check_output("set -e pipefail && curl -s {host}:{port}/jobs/overview | jq '.jobs[] | select((.name==\"DataGenJob\") and (.state==\"RUNNING\")).jid' | tr -d '\"'".format(host=FLINK_API_HOST, port=JOBMANAGER_PORT),
                                   shell=True)
    print("Finished get_current_datagen_job_id is %s" % out.decode("UTF-8").strip())
    return out.decode("UTF-8").strip()    

def get_job_details(job_id):
    print("Get job details {}".format(job_id))
    r = requests.get("http://{host}:{port}/jobs/overview/".format(host=FLINK_API_HOST, job_id=job_id, port=JOBMANAGER_PORT))
    for job in r.json()["jobs"]:
        if job["jid"] == job_id:
            return job

def save_measurements(run_date, job_id, num_sources):
    print("Running save_measurements")
    details = get_job_details(job_id)
    print(details)
    start_time = details["start-time"] / 1000
    end_time = details["end-time"] / 1000
    #subprocess.call(
    #    """kubectl get pods | grep jobmanager | awk '{print $1}' | xargs -I {} kubectl logs {} > ../measurements/%s/jm-logs.txt
    #       kubectl get pods | grep taskmanager | awk '{print $1}' | xargs -I {} sh -c 'kubectl logs $1 > ../measurements/$2/tm-$1-logs.txt' -- {} %s""" % (run_date, run_date),
    #    shell=True
    #)
    print("First command succeeded")
    sink_vertex = None
    sink_host = None
    if len(job_id.split("\n")) > 1:
        raise ValueError("Icorrectly retrieved job_id. Got: ", job_id.split("\n"))
        sys.exit(1)
    try:
        cmd2 = "curl -s http://{host}:{port}/jobs/{job_id}/ | jq -r -c '.vertices[] | select(.name | . and contains(\"Sink\")) | .id'".format(host=FLINK_API_HOST, port=JOBMANAGER_PORT, job_id=job_id)
        print("Running command: ", cmd2)
        sys.stdout.flush()
        sink_vertex = subprocess.check_output(
            cmd2,
            shell=True
        ).decode("UTF-8").strip()

        print("Second command succeeded")
    except Exception as e:
        print("ERROR! Second command failed: ",e)
        print("ERROR! Command was: ", cmd2)
    if sink_vertex is None:
        print("ERROR! Command failed: ", cmd2)
    sys.stdout.flush()

    try:
        print("Now running cmd3 ...")
        print("With vertices: ", sink_vertex)

        #cmd3 = "curl -s http://{host}:{port}/jobs/{job_id}/vertices/{sink_vertex}/ | jq -r -c '.subtasks[0].host' | awk -F':' '{print $1}'" % {
        #    'host': FLINK_API_HOST,
        #    'port': JOBMANAGER_PORT,
        #    'job_id': job_id, 'sink_vertex': sink_vertex}
        cmd3 = f"curl -s http://{FLINK_API_HOST}:{JOBMANAGER_PORT}/jobs/{job_id}/vertices/{sink_vertex}/ | jq -r -c '.subtasks[0].host' | awk -F':' '{{print $1}}'"

        print("Running command: ", cmd3)
        sink_host = subprocess.check_output(
            cmd3,
            shell=True
        ).decode("UTF-8").strip()
        print("Third command succeeded")
    except Exception as e:
        print("ERROR! Third command failed: ",e)
        print("ERROR! Command was: ", cmd3)
    if sink_host is None:
        print("ERROR! previous command failed: ", cmd3)
    sys.stdout.flush()

    if GET_ALL_SINKS == "true":
        # workaround for kill task manager with checkpoint: read start_time registered in calling bash, and remove 60 seconds
        start_time = TIMESTAMP_JOB - 60
        sink_host = None
    try:
        if sink_host is None:
            collect_pods = "kubectl get po -l  app=flink,component=taskmanager | awk '{print $1}' | awk 'NR>1'"
            hosts = subprocess.check_output(
                collect_pods,
                shell=True
            ).decode("UTF-8").strip().split("\n")
            for sink_host in hosts:
                cmd5 = "kubectl cp %s:/tmp/sink.csv ../measurements/%s/sink-%s.csv" % (sink_host, run_date, sink_host)
                print("Running now: ", cmd5)
                subprocess.call(
                    cmd5,
                    shell=True
                )
                cmd6 = "kubectl cp %s:/tmp/latencies.csv ../measurements/%s/latencies-%s.csv" % (sink_host, run_date, sink_host)
                print("Running now: ", cmd6)
                subprocess.call(
                    cmd6,
                    shell=True
                )
        else: # if previous Flink API call didn't fail
            cmd5 = "kubectl cp %s:/tmp/sink.csv ../measurements/%s/sink.csv" % (sink_host, run_date)
            print("Running now: ", cmd5)
            subprocess.call(
                cmd5,
                shell=True
            )
        print("Fifth command succeeded")
    except Exception as e:
        print("ERROR! Fifth command failed: ",e)
        print("ERROR! Command was: ", cmd5)
    sys.stdout.flush()

    time.sleep(5)
    path = os.path.abspath("../measurements/%s/{}" % run_date)
    
    #sum(container_cpu_usage_seconds_total{namespace="default",pod=~"flink.*",pod!~"flink-minio.*", container!=""})
    
    #export_prometheus_generic("export cpu duration", "container_cpu_user_seconds_total{namespace=\"default\", pod=~\"flink.*\", container!=\"\", container!=\"POD\", container!~\".*minio.*\"}", path.format("cpu-seconds.csv"), start_time, end_time)
    #export_prometheus_generic("export cpu duration", "sum(container_cpu_usage_seconds_total{namespace=\"default\",pod=~\"flink.*\",pod!~\"flink-minio.*\", container!=\"\"})", path.format("cpu-seconds-sum.csv"), start_time, end_time)
    export_prometheus_generic("export cpu duration", "irate(container_cpu_user_seconds_total{namespace=\"default\", pod=~\"flink.*\", container!=\"\", container!=\"POD\", container!~\".*minio.*\"}[45s])", path.format("cpu-seconds-irate.csv"), start_time, end_time)
    export_prometheus_generic("export cpu duration", "irate(container_network_transmit_bytes_total{namespace=\"default\", pod=~\"flink.*\", interface=\"eth0\", container!~\".*minio.*\"}[45s])", path.format("network-transmit-irate.csv"), start_time, end_time)

    export_prometheus_generic("export_cpu/heap", "flink_taskmanager_Status_JVM_CPU_Load", path.format("cpu-load.csv"), start_time, end_time)
    export_prometheus_generic("export_cpu/heap", "flink_taskmanager_Status_JVM_Memory_Heap_Used", path.format("heap-used.csv"), start_time, end_time)
    export_prometheus_generic("export_cpu/heap", "flink_taskmanager_Status_JVM_Memory_Heap_Max", path.format("heap-max.csv"), start_time, end_time)
    export_prometheus_generic("export_cpu/heap", "flink_taskmanager_Status_JVM_Memory_Heap_Committed", path.format("heap-committed.csv"), start_time, end_time)
    print("Exported first part of data")
    sys.stdout.flush()

    export_prometheus_generic("export_num_bytes", "flink_taskmanager_job_task_numBytesInRemote", path.format("num-bytes-in.csv"), start_time, end_time)
    export_prometheus_generic("export_num_bytes", "flink_taskmanager_job_task_numBytesInRemotePerSecond", path.format("num-bytes-in-per-second.csv"), start_time, end_time)
    export_prometheus_generic("export_num_bytes", "flink_taskmanager_job_task_numBytesOut", path.format("num-bytes-out.csv"), start_time, end_time)
    export_prometheus_generic("export_num_bytes", "flink_taskmanager_job_task_numBytesOutPerSecond", path.format("num-bytes-out-per-second.csv"), start_time, end_time)
    print("Exported second part of data")
    sys.stdout.flush()

    export_prometheus_generic("num_records", "flink_taskmanager_job_task_operator_numRecordsIn", path.format("records-in.csv"), start_time, end_time)
    export_prometheus_generic("num_records", "flink_taskmanager_job_task_operator_numRecordsInPerSecond", path.format("records-in-per-second.csv"), start_time, end_time)
    export_prometheus_generic("num_records", "flink_taskmanager_job_task_operator_numRecordsOut", path.format("records-out.csv"), start_time, end_time)
    export_prometheus_generic("num_records", "flink_taskmanager_job_task_operator_numRecordsOutPerSecond", path.format("records-out-per-second.csv"), start_time, end_time)
    print("Exported third part of data")
    sys.stdout.flush()


    # implemented, but not used
    export_prometheus_generic("export_time", "flink_taskmanager_job_task_operator_time_to_alert", path.format("time-to-alert.csv"), start_time, end_time)
    export_prometheus_generic("export_time", "flink_taskmanager_job_task_operator_time_to_rule_effective", path.format("time-to-rule-effective.csv"), start_time, end_time)

    export_files_from_sources(job_id, num_sources, run_date)
    print("Finished save_measurements")
    sys.stdout.flush()

def export_files_from_sources(job_id, num_sources, run_date):
    for i in range(1, num_sources + 1):
        r = requests.get("http://{host}:{port}/jobs/{job_id}/".format(host=FLINK_API_HOST,
                                                                        job_id=job_id,
                                                                        port=JOBMANAGER_PORT
                                                                ))
        response = r.json()

        print("export_files_from_sources for source %s returned %s: " % (i, r.status_code))
        vertex_ids = []
        source_name = "source-%d" % i
        for vertex in response["vertices"]:
            if source_name in vertex["name"]:
                vertex_ids.append(vertex["id"])

        hosts = []

        for vertex_id in vertex_ids:
            api_call = "http://{host}:{port}/jobs/{job_id}/vertices/{vertex_id}/".format(host=FLINK_API_HOST,
                                                                                                      job_id=job_id,
                                                                                                      vertex_id=vertex_id,
                                                                                                      port=JOBMANAGER_PORT
                                                                                               )

            print("DEBUG! Collecting vertices from flink via call: ", api_call)
            r = requests.get(api_call)
            response = r.json()
            for subtask in response["subtasks"]:
                hosts.append(subtask["host"].split(":")[0])

            print("DEBUG! Collecting vertices returned: ", r.status_code)

        print("DEBUG! Found %s for %s" % (hosts, source_name))

        for host in hosts:
            subprocess.check_call(
                "kubectl cp %s:/tmp/%s.csv ../measurements/%s/%s.csv" % (host, source_name, run_date, source_name),
                shell=True
            )

def export_prometheus_generic(type_metric, metric, to_file, start_time, end_time):
    print("Export metric {} to {} - ".format(metric, to_file), end = '')
    r = requests.get(url="http://{host}:{port}/api/v1/query_range?start={start}&end={end}&step={step}".format(host=PROMETHEUS_HOST, port=PROMETHEUS_PORT, start=start_time, end=end_time, step="5s"),
                     params={"query": metric})
    res = r.json()
    # issue on name not existing in results : replaced by __name__
    # issue on node_name not existing in results : replaced by kubernetes_pod_name.
    try: 
        print("{} elements".format(len(res['data']['result'])))
        with open(to_file, "w+") as f:
            if type_metric == "export_time":
                # export_time_to_alert, flink_taskmanager_job_task_operator_time_to_alert[120m]
                # export_time_to_rule_effective, flink_taskmanager_job_task_operator_time_to_rule_effective[120m]
                f.write("name,quantile,timestamp,value\n")

                for metricRes in res["data"]["result"]:
                    name = metricRes["metric"]["kubernetes_pod_name"]
                    quantile = metricRes["metric"]["quantile"]

                    for dp in metricRes["values"]:
                        f.write(f"{name},{quantile},{dp[0]},{dp[1]}\n")
            elif type_metric == "num_records":
                # export_num_records_out_per_second, flink_taskmanager_job_task_operator_numRecordsOutPerSecond[120m]
                # export_num_records_out, flink_taskmanager_job_task_operator_numRecordsOut[120m]
                # export_num_records_in, flink_taskmanager_job_task_operator_numRecordsIn[120m]
                # export_num_records_in_per_second, flink_taskmanager_job_task_operator_numRecordsInPerSecond[120m]
                f.write("name,instance,node_name,tm_id,operator_id,subtask_index,task_id,task_name,jobid,timestamp,value\n")

                for metricRes in res["data"]["result"]:

                    name = metricRes["metric"]["kubernetes_pod_name"]
                    instance = metricRes["metric"]["instance"]
                    node_name = metricRes["metric"]["node_name"]
                    #node_name = metricRes["metric"]["kubernetes_pod_name"]

                    tm_id = metricRes["metric"]["tm_id"]
                    operator_id = metricRes["metric"]["operator_id"]
                    subtask_index = metricRes["metric"]["subtask_index"]
                    task_id = metricRes["metric"]["task_id"]
                    task_name = metricRes["metric"]["task_name"]
                    job_id = metricRes["metric"]["job_id"]

                    for dp in metricRes["values"]:
                        f.write(
                            f"{name},{instance},{node_name},{tm_id},{operator_id},{subtask_index},{task_id},{task_name},{job_id},{dp[0]}, {dp[1]}\n")
            elif type_metric == "export_num_bytes":
                # export_num_bytes_out_per_second, flink_taskmanager_job_task_numBytesOutPerSecond[120m]
                # export_num_bytes_out, flink_taskmanager_job_task_numBytesOut[120m]
                # export_num_bytes_in, flink_taskmanager_job_task_numBytesInRemote[120m]
                # export_num_bytes_in_per_second, flink_taskmanager_job_task_numBytesInRemotePerSecond[120m]
                f.write("name,instance,node_name,tm_id,timestamp,value\n")

                for metricRes in res["data"]["result"]:

                    name = metricRes["metric"]["kubernetes_pod_name"]
                    instance = metricRes["metric"]["instance"]
                    node_name = metricRes["metric"]["node_name"]
                    #node_name = metricRes["metric"]["kubernetes_pod_name"]
                    tm_id = metricRes["metric"]["tm_id"]
                    # operator_id = metricRes["metric"]["operator_id"]
                    # subtask_index = metricRes["metric"]["subtask_index"]
                    # task_id = metricRes["metric"]["task_id"]
                    # task_name = metricRes["metric"]["task_name"]
                    # job_id = metricRes["metric"]["job_id"]

                    for dp in metricRes["values"]:
                        f.write(f"{name},{instance},{node_name},{tm_id},{dp[0]},{dp[1]}\n")
            elif type_metric == "export_cpu/heap":
                # export_taskmanager_cpu_load, flink_taskmanager_Status_JVM_CPU_Load[120m]
                # export_heap_used, flink_taskmanager_Status_JVM_Memory_Heap_Used[120m]
                # export_heap_max, flink_taskmanager_Status_JVM_Memory_Heap_Max[120m]
                # export_heap_committed, flink_taskmanager_Status_JVM_Memory_Heap_Committed[120m]
                f.write("name,instance,node_name,tm_id,timestamp,value\n")

                for metricRes in res["data"]["result"]:
                    name = metricRes["metric"]["kubernetes_pod_name"]
                    instance = metricRes["metric"]["instance"]
                    node_name = metricRes["metric"]["node_name"]
                    #node_name = metricRes["metric"]["kubernetes_pod_name"]
                    tm_id = metricRes["metric"]["tm_id"]

                    for dp in metricRes["values"]:
                        f.write("%s,%s,%s,%s,%s,%s\n" % (name, instance, node_name, tm_id, dp[0], dp[1]))
            elif type_metric == "export cpu duration":
                f.write("name,instance,node_name,timestamp,value\n")

                for metricRes in res["data"]["result"]:
                    name = metricRes["metric"]["pod"]
                    instance = metricRes["metric"]["instance"]
                    node_name = metricRes["metric"]["node"]
                    #node_name = metricRes["metric"]["kubernetes_pod_name"]
                    #tm_id = metricRes["metric"]["tm_id"]

                    for dp in metricRes["values"]:
                        f.write("%s,%s,%s,%s,%s\n" % (name, instance, node_name, dp[0], dp[1]))                
    except Exception as e:
        print("ERROR! failed with: ", e)        

def save_job_graph(job_id, measurement_path):
    global PROMETHEUS_HOST
    print("Running save_job_graph with job_id: \n", job_id)
    print("#####")

    job_id = job_id.split('\n')[0]
    req_str = "http://{host}:{port}/jobs/{job_id}/".format(host=FLINK_API_HOST,
                                                                  job_id=job_id,
                                                                  port=JOBMANAGER_PORT)
    print("Checking save_job_graph request: ", req_str)
    r = requests.get(req_str)
    print("save_job_graph returned: ", r.status_code)

    if int(r.status_code) >= 400:
            print("Error! Save_job_graph failed with code: ", r.status_code)
            return

    job_info = r.json()

    vertices = {}
    for vertex in job_info["vertices"]:
        r = requests.get("http://{host}:{port}/jobs/{job_id}/vertices/{v_id}".format(host=FLINK_API_HOST,
                                                                                   job_id=job_id,
                                                                                   v_id=vertex["id"],
                                                                           port=JOBMANAGER_PORT))


        print("[save_job_graph] Details request returned", r.status_code)
        # This adds data from response, this will be used for plotting later
        # But this is very strange way to collect the data
        # TODO maybe refactor this

        vertex["details"] = r.json()
        print(vertex)
        vertices[vertex['id']] = r.json()

    with open("%s/job.json" % measurement_path, "w") as f:
        json.dump(job_info, f)

    with open('%s/result-vertices.json' % measurement_path, 'w') as fp:
        json.dump(vertices, fp)


    print("Finished save_job_graph")


print(EXTRA_OPTS)
i=iter(EXTRA_OPTS.split(' '))   
extra_opts_map = map(" ".join,zip(i,i))    
extra_opts = dict(x.split(" ") for x in list(extra_opts_map))
retrieve(
    JOB_NAME, 
    extra_opts,
    lambda: time.sleep(60),
    test_name= TEST_NAME,
    num_sources=1,
    jar=JOB_JAR_FILE,
    image=FLINK_SNAPSHOT_IMAGE 
    )
