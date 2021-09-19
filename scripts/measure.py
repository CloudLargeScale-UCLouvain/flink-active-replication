#!/usr/bin/env python3
import json
import os
import sys
import pprint
import subprocess
import traceback
from datetime import datetime

import requests
import time

FLINK_SNAPSHOT = os.getenv("FLINK_SNAPSHOT","/Users/oleh/Code/flink/flink/flink-dist/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT")#"~/dev/flink/flink-dist/target/flink-1.7-SNAPSHOT-bin/flink-1.7-SNAPSHOT"
# FLINK_1_7_1 = "~/dev/flink-1.7.1"
FLINK_DIR = FLINK_SNAPSHOT

FLINK_SNAPSHOT_IMAGE = os.getenv("FLINK_SNAPSHOT_IMAGE","grosinosky/flink:1.7.2-ar") #"gcr.io/florian-master-thesis-1337/flink:flink-1.7-SNAPSHOT"
FLINK_BASELINE_IMAGE = "grosinosky/flink:1.7.2"

JOBMANAGER_PORT = os.getenv("JOBMANAGER_PORT", 31081) #8081
PROMETHEUS_PORT = os.getenv("PROMETHEUS_PORT",30090) #9090
FLINK_API_HOST = os.getenv("FLINK_API_HOST", "0.0.0.0")
PROMETHEUS_HOST = os.getenv("PROMETHEUS_HOST", "0.0.0.0")
JOB_JAR_FILE = os.getenv("JOB_JAR_FILE", "/Users/oleh/Code/flink/flink-active-replication-benchmark-master/target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar")

experiment_job_id = None
experiment_run_date = None

def noop():
    pass


def run_bettercloud_test(image):
    # global JOB_JAR_FILE
    jar_file = "target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar"
    rebuild_flink_cluster(image)
    build_jar()

    datagen = "me.florianschmidt.examples.bettercloud.DataGeneratorJob"
    job = "me.florianschmidt.examples.bettercloud.Job"

    #subprocess.check_call("helm del --purge flink-thesis-kafka", shell=True)
    #subprocess.check_call("helm install --name flink-thesis-kafka bitnami/kafka", shell=True)

    print("Sleeping two minutes for kafka to be up and running")
    #time.sleep(120)

    run_job(job, jar_file, parameters={})
    run_job(datagen, jar_file, parameters={})
    print("Finished run_bettercloud_test")


def run_test(job_name,
             job_parameters,
             test_fn=noop,
             test_name="",
             num_sources=1,
             jar="target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar",
             image=FLINK_SNAPSHOT_IMAGE):#"gcr.io/florian-master-thesis-1337/flink:flink-1.7-SNAPSHOT"):

    print(f"\n#############################################################################\n"
          f"# test_name={test_name}\n"
          f"# job_name={job_name}\n"
          f"# job_parameters={pprint.pformat(job_parameters)}\n"
          f"# image={image}\n"
          f"###########################################################################\n")

    if image == FLINK_BASELINE_IMAGE:
        image_name = "1_7_2"
    else: 
        image_name = "1_7_SNAPSHOT"

    try:
        run_date = f"{datetime.today().strftime('%Y-%m-%d-%H-%M-%S')}-{test_name}-{image_name}"

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
            "--zk.servers": "flink-zookeeper:2181",
            "--fsStateBackend": "hdfs://hadoop-hdfs:8020/"
        }
        jar_file = jar
        parameters = {**default_parameters, **job_parameters}

        rebuild_flink_cluster(image)
        prepare_measurement_dir(measurement_path)
        save_job_parameters(job_name, parameters, measurement_path)
        build_jar()
        cancel_all_running_jobs()
        #if image_name == "1_7_2":
        #    job_id = run_job_via_api(job_name, jar_file, parameters).strip()
        #else:
        #    job_id = run_job(job_name, jar_file, parameters).strip()
        job_id = run_job(job_name, jar_file, parameters).strip()
        print("JOB_ID is ", job_id)

        global experiment_job_id
        experiment_run_date = job_id

        test_fn()

        try:
            cancel_job(job_id)
        except Exception as e:
            print("Error while cancelling the job with id: ", job_id)

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


def cleanup(image):
    return "WARN! Do not need to run cleanup righ now"
    subprocess.check_call(
        "sed -i -c \"s|%s|DOCKER_IMAGE_NAME|g\" ../cluster/gcp/jobmanager-deployment.yaml" % image,
        shell=True
    )
    subprocess.check_call(
        "sed -i -c \"s|%s|DOCKER_IMAGE_NAME|g\" ../cluster/gcp/taskmanager-deployment.yaml" % image,
        shell=True
    )

    #pkill -9 -f "port-forward"
    #pkill -9 -f "kubectl proxy"


def prepare_measurement_dir(measurement_path):
    print("Creating mesurement path %s" % measurement_path)
    subprocess.check_call(["mkdir", "-p", measurement_path])


def rebuild_flink_cluster(image):
    
    print("Clean logs and old job artifacts")
    subprocess.call("kubectl scale deployment flink-jobmanager --replicas=0", shell=True)
    subprocess.call("kubectl scale deployment flink-taskmanager --replicas=0", shell=True)
    time.sleep(5)
    subprocess.call("kubectl scale deployment flink-jobmanager --replicas=1", shell=True)
    subprocess.call("kubectl scale deployment flink-taskmanager --replicas=30", shell=True)
    time.sleep(60)
    return 
    # subprocess.call("killall kubectl", shell=True)
    # # subprocess.call("kubectl get pods | awk '{print $1}' | xargs kubectl delete pod", shell=True)
    # subprocess.call("kubectl delete -f ../cluster/gcp/", shell=True)
    # subprocess.call(
    #     "kubectl create clusterrolebinding florian-cluster-admin-binding --clusterrole=cluster-admin --user=florian@data-artisans.com",
    #     shell=True)
    #
    # # subprocess.call("helm del --purge flink-thesis-kafka", shell=True)
    # # subprocess.check_call("helm install --name flink-thesis-kafka bitnami/kafka", shell=True)
    #
    # # print("Sleeping a minute for kafka to be up and running")
    # # time.sleep(60)
    #
    # subprocess.check_call(
    #     "sed -i -c \"s|DOCKER_IMAGE_NAME|%s|g\" ../cluster/gcp/jobmanager-deployment.yaml" % image,
    #     shell=True)
    # subprocess.check_call(
    #     "sed -i -c \"s|DOCKER_IMAGE_NAME|%s|g\" ../cluster/gcp/taskmanager-deployment.yaml" % image,
    #     shell=True)
    #
    # subprocess.check_call("kubectl create -f ../cluster/gcp", shell=True)
    #
    # subprocess.call("""
    #     while [[ ${jobmanager_status} != "Running" ]]; do
    #         jobmanager_status=$(kubectl get pods | grep jobmanager | awk '{print $3}')
    #         echo "Not running yet"
    #         sleep 1
    #         done
    # """, shell=True)

    # subprocess.call(
    #     "kubectl get pods | grep jobmanager | awk '{print $1}' | xargs -I {} kubectl port-forward {} 8081 &",
    #     shell=True)
    # subprocess.call("kubectl get pods | grep grafana | awk '{print $1}' | xargs -I {} kubectl port-forward {} 3000 &",
    #                 shell=True)
    # subprocess.call("kubectl proxy &", shell=True)
    print("Funished checking Kubernetes cluster")


def save_job_parameters(job_name, parameters, measurement_path):
    print("Saving job parameters %s" % job_name)
    with open("%s/info.txt" % measurement_path, "w+") as f:
        f.write("job_name=%s\n" % job_name)
        for key, value in parameters.items():
            f.write("%s=%s\n" % (key, value))

def run_job(job_name, jar_file, parameters):
    param_string = ""
    for key, value in parameters.items():
        param_string = param_string + " " + key + " " + value
    #run_job_command = "%s/bin/flink run -c %s -d %s %s" % (FLINK_SNAPSHOT, job_name, JOB_JAR_FILE, param_string)
    #TODO job manager address (check with Oleh)
    run_job_command = "%s/bin/flink run -m %s:%s -c %s -d %s %s" % (FLINK_SNAPSHOT, FLINK_API_HOST, JOBMANAGER_PORT, job_name, JOB_JAR_FILE, param_string)
    print("Running job with %s" % run_job_command)
    subprocess.call(run_job_command, shell=True)

    return get_current_job_id().split("\n")[0]

# Rename to run_job if run agains official Flink
def run_job_via_api(job_name, jar_file, parameters):
    global JOB_JAR_FILE, FLINK_API_HOST, JOBMANAGER_PORT
    print("Running job %s" % job_name)
    param_string = ""
    #for key, value in parameters.items():
#        param_string = param_string + " " + key + " " + value
    for key, value in parameters.items():
        param_string = param_string + key + "=" + value + ","
    param_string = param_string[:-1] # strip comma, just to be safe

    run_job_command = "%s/bin/flink run -c %s -d ../%s %s" % (FLINK_SNAPSHOT, job_name, jar_file, param_string)
    print("Running job with %s" % run_job_command)
    print("Comma separated params are: %s" % param_string)
    #subprocess.call(run_job_command, shell=True)
    # TODO submit job, run job via REST API
    submit_job_string = "curl -X POST -H \"Expect:\" -F \"jarfile=@{path_to_flink_job}\" http://{hostname}:{port}/jars/upload".format(path_to_flink_job=JOB_JAR_FILE, hostname=FLINK_API_HOST, port=JOBMANAGER_PORT)
    print("Curl req shoud be: ", submit_job_string )
    sys.stdout.flush()
    out = subprocess.check_output(
            submit_job_string,
        shell=True).decode(sys.stdout.encoding)
    print("Job ID is: ", json.loads(out)['filename'])
    # run job
    jar_id = json.loads(out)['filename'].split("/")[-1]

    url = "http://{hostname}:{port}/jars/{jar_id}/run".format(hostname=FLINK_API_HOST, port=JOBMANAGER_PORT, jar_id=jar_id)
    params = {
        "entry-class": job_name,
        #"parallelism": , # String value that specifies the fully qualified name of the entry point class.
        "programArg": param_string, # Comma-separated list of program arguments.
    }
    print("Submitting job via:",url)
    print("with params ", params)
    resp = requests.post(url, params=params, data="")
    print("Run job request returned ", json.loads(resp.text))

    return get_current_job_id()


def cancel_all_running_jobs(): #!WARN! It fill fail the whole workflow if the cluster is no jobs in cluster
    # TODO : make it work every time
    return
    print("Canceling all jobs")
    subprocess.call(
        "%s/bin/flink list | grep RUNNING | awk '{print $4}' | xargs %s/bin/flink cancel" % (
            FLINK_SNAPSHOT, FLINK_SNAPSHOT),
        shell=True)


def cancel_job(job_id):
    print("Canceling job %s" % job_id)
    requests.patch("http://{host}:{port}/jobs/{job_id}/".format(host=FLINK_API_HOST, job_id=job_id, port=JOBMANAGER_PORT), params={"mode": "cancel"})


def build_jar():
    #subprocess.check_call("cd .. && mvn clean install -Drat.ignoreErrors=true -Dmaven.test.skip -Dcheckstyle.skip -Dhadoop.version=2.8.0 -Pinclude-hadoop", shell=True)
    print("Finished building jar")


def get_current_job_id():
    print("Running get_current_job_id")
    out = subprocess.check_output("set -e pipefail && curl -s {host}:{port}/jobs/ | jq '.jobs[].id' | tr -d '\"'".format(host=FLINK_API_HOST, port=JOBMANAGER_PORT),
                                   shell=True)
    print("Finished get_current_job_id is %s" % out.decode("UTF-8").strip())

    return out.decode("UTF-8").strip()


def save_measurements(run_date, job_id, num_sources):
    print("Running save_measurements")
    subprocess.call(
        """kubectl get pods | grep jobmanager | awk '{print $1}' | xargs -I {} kubectl logs {} > ../measurements/%s/jm-logs.txt
           kubectl get pods | grep taskmanager | awk '{print $1}' | xargs -I {} sh -c 'kubectl logs $1 > ../measurements/$2/tm-$1-logs.txt' -- {} %s""" % (run_date, run_date),
        shell=True
    )
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

    try:
        sink_host = sink_host.strip().replace(" ", "")
        print("Running command four. Sink host is %s \n" % sink_host)
        collect_pods = "kubectl get po -l  app=flink,component=taskmanager | awk '{print $1}' | awk 'NR>1'"

        # ensure that value is not empty
        if (sink_host is None) or not sink_host or sink_host == "(unassigned)":
            hosts = subprocess.check_output(
                collect_pods,
                shell=True
            ).decode("UTF-8").strip().split("\n")
            print("Have collected hosts: ", hosts)

            for new_sink_host in hosts:
                cmd4 = "kubectl exec %s -- bash -c 'ls -lah /tmp/sink.csv' | awk '{print $5}'" % new_sink_host
                print("Executing: ", cmd4)

                file_size = subprocess.check_output(cmd4, shell=True)

                if file_size is not None and 'M' in str(file_size):
                    # file not empty, pod that contains data with size >= 1M
                    print(f"Downloading log file from sink host {new_sink_host} with size {file_size}")
                    # Save proper Pod name to refer to it later
                    sink_host = new_sink_host
        else:
            cmd4 = "kubectl exec %s -- bash -c 'ls -lah /tmp/sink.csv' | awk '{print $5}'" % sink_host
            print("Running now: ", cmd4)
            file_size = subprocess.check_output(cmd4, shell=True)

            print(f"Downloading log file from sink host {sink_host} with size {file_size}")

        print("Fourth command succeeded")

    except Exception as e:
        print("ERROR! Fourth command failed: ",e)
        print("ERROR! Command was: ", cmd4)
    sys.stdout.flush()

    try:

        if sink_host is None:
            collect_pods = "kubectl get po -l  app=flink,component=taskmanager | awk '{print $1}' | awk 'NR>1'"
            hosts = subprocess.check_output(
                collect_pods,
                shell=True
            ).decode("UTF-8").strip().split("\n")
            for sink_host in hosts:
                cmd5 = "kubectl cp %s:/tmp/sink.csv ../measurements/%s/sink.csv" % (sink_host, run_date)
                print("Running now: ", cmd5)
                subprocess.call(
                    cmd5,
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

    # subprocess.call(
    #     "kubectl get pods | grep prometheus | awk '{print $1}' | xargs -I {} kubectl port-forward {} 9090 &",
    #     shell=True
    # )

    time.sleep(5)
    path = os.path.abspath("../measurements/%s/{}" % run_date)
    
    export_prometheus_generic("export_cpu/heap", "flink_taskmanager_Status_JVM_CPU_Load[120m]", path.format("cpu-load.csv"))
    export_prometheus_generic("export_cpu/heap", "flink_taskmanager_Status_JVM_Memory_Heap_Used[120m]", path.format("heap-used.csv"))
    export_prometheus_generic("export_cpu/heap", "flink_taskmanager_Status_JVM_Memory_Heap_Max[120m]", path.format("heap-max.csv"))
    export_prometheus_generic("export_cpu/heap", "flink_taskmanager_Status_JVM_Memory_Heap_Committed[120m]", path.format("heap-committed.csv"))
    print("Exported first part of data")
    sys.stdout.flush()

    export_prometheus_generic("export_num_bytes", "flink_taskmanager_job_task_numBytesInRemote[120m]", path.format("num-bytes-in.csv"))
    export_prometheus_generic("export_num_bytes", "flink_taskmanager_job_task_numBytesInRemotePerSecond[120m]", path.format("num-bytes-in-per-second.csv"))
    export_prometheus_generic("export_num_bytes", "flink_taskmanager_job_task_numBytesOut[120m]", path.format("num-bytes-out.csv"))
    export_prometheus_generic("export_num_bytes", "flink_taskmanager_job_task_numBytesOutPerSecond[120m]", path.format("num-bytes-out-per-second.csv"))
    print("Exported second part of data")
    sys.stdout.flush()

    export_prometheus_generic("num_records", "flink_taskmanager_job_task_operator_numRecordsIn[120m]", path.format("records-in.csv"))
    export_prometheus_generic("num_records", "flink_taskmanager_job_task_operator_numRecordsInPerSecond[120m]", path.format("records-in-per-second.csv"))
    export_prometheus_generic("num_records", "flink_taskmanager_job_task_operator_numRecordsOut[120m]", path.format("records-out.csv"))
    export_prometheus_generic("num_records", "flink_taskmanager_job_task_operator_numRecordsOutPerSecond[120m]", path.format("records-out-per-second.csv"))
    print("Exported third part of data")
    sys.stdout.flush()


    # implemented, but not used
    export_prometheus_generic("export_time", "flink_taskmanager_job_task_operator_time_to_alert[120m]", path.format("time-to-alert.csv"))
    export_prometheus_generic("export_time", "flink_taskmanager_job_task_operator_time_to_rule_effective[120m]", path.format("time-to-rule-effective.csv"))

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

def export_prometheus_generic(type_metric, metric, to_file):
    print("Export metric {} to {} - ".format(metric, to_file), end = '')
    r = requests.get(url="http://{host}:{port}/api/v1/query".format(host=PROMETHEUS_HOST, port=PROMETHEUS_PORT),
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


def main():
    pass

if __name__ == "__main__":
    main()
#################################################################
# Run a job where both the operators that follow each other can vary                     #
#################################################################
for length in range(7, 8):
#     for algorithm in ["BETTER_BIAS", "LEADER_KAFKA"]:
     for algorithm in ["VANILLA"]:
         run_test(
             "me.florianschmidt.replication.baseline.jobs.IncreasingLengthJob",
             {
                 "--rate": "5000",
                 "--map-parallelism": str(2),
                 "--length": str(length),
                 "--algorithm": algorithm,
                 #"--idle-marks": "true",
                 "--checkpointing" : "true",
                 "--state-backend": "rocksdb",
                 "--kafka.servers" : "flink-kafka:9092",
                 "--zk.servers": "flink-zookeeper:2181",
                 "--fsStateBackend": "hdfs://flink-hdfs:8020/flink-checkpoints"
             },
             lambda: time.sleep(60),
             test_name=f"increasing-length-{algorithm}-{length}",
             num_sources=1,
             jar="target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar",
             image=FLINK_SNAPSHOT_IMAGE
         )

#################################################################
# Run a job where both the parallelism as well as the number of #
# operators that follow each other can vary                     #
#################################################################
#for length in range(1, 8):
#     for algorithm in ["BETTER_BIAS", "LEADER_KAFKA"]:
#         run_test(
#             "me.florianschmidt.replication.baseline.jobs.IncreasingLengthJob",
#             {
#                 "--rate": "5000",
#                 "--map-parallelism": str(length),
#                 "--length": str(1),
#                 "--algorithm": algorithm,
#                 "--idle-marks": "true"
#             },
#             lambda: time.sleep(60),
#             test_name=f"increasing-parallelism-{algorithm}-{length}",
#             num_sources=1,
#             image=FLINK_SNAPSHOT_IMAGE
#         )

#################################################################
# Increasing length test with multiple iterations               #
#################################################################
# parallelism = 2
# for algorithm in ["vanilla"]:
#     for length in range(1, 4):
#         for iteration in range(0, 4):
#             run_test(
#                 "me.florianschmidt.replication.baseline.jobs.IncreasingLengthJob",
#                 {
#                     "--rate": "5000",
#                     "--map-parallelism": str(parallelism),
#                     "--length": str(length),
#                     "--algorithm": algorithm,
#                     "--idle-marks": "true"
#                 },
#                 lambda: time.sleep(60),
#                 test_name=f"algorithm-{algorithm}-parallelism-{parallelism}-length-{length}-iteration-{iteration}",
#                 num_sources=1,
#                 image=FLINK_1_7_1_IMAGE
#             )


#################################################################
# Run a job with two inputs that have a different input rate (done) #
#################################################################
# for first in [1, 10, 100]:
#    for second in [1, 10, 100, 1000, 10000, 30000, 50000]:
#        for algorithm in ["LEADER_KAFKA", "BETTER_BIAS"]:
#            run_test(
#                "me.florianschmidt.replication.baseline.jobs.ConstantButSkewedInputRateJob",
#                {
#                    "--rate-1": str(first),
#                    "--rate-2": str(second),
#                    "--algorithm": algorithm,
#                    "--idle-marks": str(True).lower()
#                },
#                lambda: time.sleep(60),
#                test_name=f"{algorithm}-rate-1-{str(first)}-rate-2-{str(second)}-idle-marks-{str(True).lower()}",
#                num_sources=2
#            )

#################################################################
# Different idle marks timeouts                                 #
#################################################################
first = 1
second = 100
algorithm = "BETTER_BIAS"

# for pause in [750]:
#     run_test(
#         "me.florianschmidt.replication.baseline.jobs.ConstantButSkewedInputRateJob",
#         {
#             "--rate-1": str(first),
#             "--rate-2": str(second),
#             "--algorithm": algorithm,
#             "--idle-marks-interval": str(pause)
#         },
#         lambda: time.sleep(40),
#         test_name=f"varying-idle-marks-pause-{pause}",
#         num_sources=2
#     )

# #################################################################
# # Run a job with identical input rates where one of the two     #
# # sources sends nothing after a while for a couple of seconds   #
# #################################################################
# for idle_marks in [True, False]:
#     first = 10000
#     second = 10000
#     run_test(
#         "me.florianschmidt.replication.baseline.jobs.HiccupJob",
#         {
#             "--rate-1": str(first),
#             "--rate-2": str(second),
#             "--algorithm": "BETTER_BIAS",
#             "--idle-marks": str(idle_marks).lower()
#         },
#         lambda: time.sleep(60),
#         test_name="hiccup-rate-1-%s-rate-2-%s-idle-marks-%s" % (str(first), str(second), str(idle_marks)),
#         num_sources=2
#     )
#
# #################################################################
# # Run a job with random input rates on one of the two channels  #
# #################################################################
# for algorithm in ["LEADER_KAFKA"]:
#    for idle_marks in [True]:
#        first = 1000    # constant
#        second = 1000   # constant
#        timeout = 100
#        batch_size = 1000
#        run_test(
#            "me.florianschmidt.replication.baseline.jobs.ConstantButSkewedInputRateJob",
#            {
#                "--rate-1": str(first),
#                "--rate-2": str(second),
#                "--algorithm": algorithm,
#                "--idle-marks": str(idle_marks).lower(),
#                "--kafka-timeout": str(timeout),
#                "--kafka-batch-size": str(batch_size)
#            },
#            lambda: time.sleep(30),
#            test_name=f"random-rate-1-{str(first)}-rate-2-max-{str(second)}-idle-marks-{str(idle_marks)}-algorithm-{str(algorithm)}-timeout-{str(timeout)}-batch-{str(batch_size)}",
#            num_sources=2,
#        )
#
# #################################################################
# # Run a job where the operation takes some time for processing  #
# # so that we have backpressure                                  #
# #################################################################
# first = 10000
# second = 10000
# wait = 20  # wait 20ms per element
# run_test(
#     "me.florianschmidt.replication.baseline.jobs.BackpressureJob",
#     {
#         "--rate-1": str(first),
#         "--rate-2": str(second),
#         "--wait": str(wait),
#         "--algorithm": "BETTER_BIAS",
#         "--idle-marks": "true"
#     },
#     lambda: time.sleep(60),
#     test_name="backpressure-rate-1-%s-rate-2-%s-wait-%s-idle-marks-true" % (str(first), str(second), str(wait)),
#     num_sources=2
# )

#################################################################
# Run a job the operation emits more than one output for a      #
# single input so that correct downstream                       #
# timestamp usage is tested                                     #
#################################################################
# run_date, job_id = run_test(
#      "me.florianschmidt.replication.baseline.jobs.FlatMapJob",
#      {
#          "--rate": "1000",
#          "--algorithm": "BETTER_BIAS"
#      },
#      lambda: time.sleep(60),
#      test_name="flatmap-rate-1000",
#      num_sources=1
# )


#run_date = "timemerge-passive"
# job_id = "294fd49dd1c1ecd8be042ec2abe8e3bf"
#num_sources = 2
#measurement_path = f"../measurements/{run_date}"
#save_measurements(run_date, job_id, num_sources)
#save_job_graph(job_id, measurement_path)

# rebuild_flink_cluster(FLINK_SNAPSHOT_IMAGE)
#run_bettercloud_test(FLINK_SNAPSHOT_IMAGE)

# Curl request to submit and run specific job
"""
curl -X POST -H "Expect:" -F "jarfile=@/Users/oleh/Code/flink/flink-active-replication-benchmark-master/target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar" http://localhost:30081/jars/upload


curl  -X POST -H "Content-Type: application/json" --data '
{
"entryClass": "me.florianschmidt.replication.baseline.jobs.FlatMapJob",
"programArgs": " --state-backend=rocksdb, --checkpoint-frequency=30, --incremental-checkpointing=false, --bufferTimeout=-2, --latency-tracking-interval=-1, --idle-marks=false, --rate=1000, --algorithm=BETTER_BIAS"
}
' http://localhost:30081/jars/2e2f6fc5-d09d-47ef-bf23-d3ab747096c3_flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar/run
"""
