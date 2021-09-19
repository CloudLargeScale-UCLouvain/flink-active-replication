package me.florianschmidt.replication.baseline.jobs;
import java.io.Serializable;
public class Record implements Serializable {
	public long createdAtMillis;
	public long createdAtNanos;
	public long receivedAtMillis;
	public long receivedAtNanos;
	public String origin;
	public String key;
	public String id;
	public int count;

	public String toString() {
		return String.format("%d %s, %d, %d, %d, %d, %d\n", count, origin, createdAtMillis, createdAtNanos, receivedAtMillis, receivedAtNanos, receivedAtNanos - createdAtNanos);
	}

	public String getKey() {
		return key;
	}

	public Record() {
		// for pojo serializer
	}

	public Record(String id, String key, String origin, long createdAtMillis, long createdAtNanos, long receivedAtMillis, long receivedAtNanos, int count){
		this.id = id;
		this.key = key;
		this.origin = origin;
		this.createdAtMillis = createdAtMillis;
		this.receivedAtMillis = receivedAtMillis;
		this.createdAtNanos = createdAtNanos;
		this.receivedAtNanos = receivedAtNanos;
		this.count = count;
	}
}
