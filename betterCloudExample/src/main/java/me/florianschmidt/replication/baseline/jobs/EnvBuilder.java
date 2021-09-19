package me.florianschmidt.replication.baseline.jobs;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.contrib.streaming.state.RocksDBStateBackend;
import org.apache.flink.runtime.state.StateBackend;
import org.apache.flink.runtime.state.filesystem.FsStateBackend;
import org.apache.flink.runtime.state.memory.MemoryStateBackend;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.TernaryBoolean;
import org.apache.flink.streaming.api.environment.CheckpointConfig;

public class EnvBuilder {

	private static final String GS_CHECKPOINT_PATH = "gs://flink-master-thesis/flink-checkpoints";
	private static final String FS_CHECKPOINT_PATH = "file:///tmp/flink-docker";

	public static StreamExecutionEnvironment createEnvFromArgs(String[] args) {
		return createEnvFromArgs(args, false);
	}

	public static StreamExecutionEnvironment createEnvFromArgs(String[] args, boolean remote) {
		StreamExecutionEnvironment env;
		if (remote) {
			env = StreamExecutionEnvironment.createRemoteEnvironment("localhost", 8081, "target/flink-fault-tolerance-baseline-1.0-SNAPSHOT.jar");
		} else {
			env = StreamExecutionEnvironment.getExecutionEnvironment();
		}


		ParameterTool tool = ParameterTool.fromArgs(args);
		String fsStateBackend = tool.get("fsStateBackend", FS_CHECKPOINT_PATH);
		String algorithm = tool.get("algorithm", "VANILLA");
		System.out.println(algorithm);
		if (!algorithm.startsWith("VANILLA")) {
			ExecutionConfig.OrderingAlgorithm a = ExecutionConfig.OrderingAlgorithm.valueOf(algorithm);
			env.getConfig().setReplicationFactor(tool.getInt("replicationFactor", 2));
			System.out.println(String.format("Replication factor %d", tool.getInt("replicationFactor", 2)));
			env.getConfig().setOrderingAlgorithm(a);
			System.out.println("Using algorithm " + a);
			if(tool.getBoolean("idle-marks", false)) {
				env.getConfig().enableIdleMarks();
				env.getConfig().setIdleMarksInterval(tool.getLong("idle-marks.interval", 200));
			}
			if(tool.getBoolean("liverobin-marks", false)) {
				env.getConfig().enableLiveRobin();
			}			
			System.out.println(String.format("Idle markers %b with latency %d", env.getConfig().isIdleMarksEnabled(), env.getConfig().getIdleMarksInterval()));
			env.getConfig().setKafkaServer(tool.get("kafka.servers"));
			env.getConfig().setZkServer(tool.get("zk.servers"));
			env.getConfig().setKafkaBatchSize(tool.getInt("kafka-batch-size", 1000));
			env.getConfig().setKafkaTimeout(tool.getInt("kafka-timeout", 200));
		}		




		// use checkpointing
		boolean enableCheckpointing = tool.getBoolean("checkpointing", false);

		if (enableCheckpointing) {
			// checkpoint-frequency (in seconds)
			long checkpointingFrequency = tool.getLong("checkpoint-frequency", 30);
			env.enableCheckpointing(checkpointingFrequency * 1000);

			// state-backend
			String stateBackend = tool.get("state-backend", "rocksdb");
			
			boolean externalizedCheckpoints = tool.getBoolean("externalized-checkpoints", true);
			if (externalizedCheckpoints) {
				env.getCheckpointConfig().enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
			}

			// incremental-checkpointing
			boolean incrementalCheckpointing = tool.getBoolean("incremental-checkpointing", false);

			StateBackend s;

			switch (stateBackend) {
				case "rocksdb":
					TernaryBoolean useIncremental = TernaryBoolean.fromBoolean(incrementalCheckpointing);
					s = new RocksDBStateBackend(new FsStateBackend(fsStateBackend), useIncremental);
					break;
				case "file":
					s = new FsStateBackend(fsStateBackend);
					break;
				case "memory":
					s = new MemoryStateBackend();
					break;
				default:
					throw new RuntimeException("Expected stateBackend to be memory/file/rocksdb, actual: " + stateBackend);
			}

			env.setStateBackend(s);
		}

		// buffer-timeout
		long bufferTimeout = tool.getLong("buffer-timeout", -2);
		if (bufferTimeout > -2) { // dont set any value and use default
			env.setBufferTimeout(bufferTimeout);
		}

		// disable latency tracking
		long interval = tool.getLong("latency-tracking-interval", -1);
		env.getConfig().setLatencyTrackingInterval(interval);
		return env;
	}
}
