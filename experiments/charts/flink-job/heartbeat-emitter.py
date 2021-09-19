import asyncio
import time
import logging

@asyncio.coroutine
def periodic(period):

    def g_tick():
        t = loop.time()

        count = 0
        while True:
            count += 1
            yield max(t + count * period - loop.time(), 0)
    g = g_tick()
    global heartbeat_index
    heartbeat_index = 0
    while True:
        log.debug("Sending {}".format(heartbeat_index))
        try:
            future = producer.send('liverobin-heartbeats', bytes("{}".format(heartbeat_index), 'utf-8'))
            producer.flush()
            heartbeat_index = heartbeat_index + 1
        except:
            print("Error:", sys.exc_info()[0])
        
        #result = future.get(timeout=60)
        yield from asyncio.sleep(next(g))
if __name__ == "__main__":
    logging.basicConfig(format='%(asctime)s %(levelname)s: %(message)s', level=logging.INFO)
    log = logging.getLogger("heartbeat-emitter")
    log.setLevel(logging.INFO)
    ch = logging.StreamHandler()
    ch.setLevel(logging.INFO)
    log.addHandler(ch)
    import os

    period = int(os.getenv("PERIOD", 10)) / 1000
    kafka_server = os.getenv("KAFKA_SERVER", "flink-kafka.default:9092")
    duration = int(os.getenv("DURATION", 300))
    log.info("Launch heartbeat emitter with period {}, duration {} on server {}".format(period, duration, kafka_server))
    topic = "liverobin-heartbeats"    
    # remove topic for reset
    from kafka import KafkaAdminClient
    from kafka.errors import UnknownTopicOrPartitionError
    
    log.info("Create producer {}".format(kafka_server))
    from kafka import KafkaProducer
    producer = KafkaProducer(bootstrap_servers=[kafka_server], api_version=(2, 4, 0))
    global hearbeat_index 

    log.info("Topic {} suppression".format(topic))
    client = KafkaAdminClient(bootstrap_servers=kafka_server)
    try: 
        client.delete_topics([topic])
        log.info("Topic deleted")
    except UnknownTopicOrPartitionError:
        log.info("Topic unknown ")    




    log.info("Initialize async task")
    loop = asyncio.get_event_loop()
    task = loop.create_task(periodic(period))
    loop.call_later(duration, task.cancel)
    try:
        log.info("Launch async task")
        loop.run_until_complete(task)
    except asyncio.CancelledError:
        log.info("Task canceled.")
    log.info("Last hearbeat: {}".format(heartbeat_index))

    log.info("Topic {} suppression".format(topic))
    client = KafkaAdminClient(bootstrap_servers=kafka_server)
    try: 
        client.delete_topics([topic])
        log.info("Topic deleted")
    except UnknownTopicOrPartitionError:
        log.info("Topic unknown ")    
    
