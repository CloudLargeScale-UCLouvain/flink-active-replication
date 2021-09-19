package me.florianschmidt.replication.baseline.model;

import java.io.Serializable;

public class Record implements Serializable {
	public long ingestionTime;
	public long eventTime;
	public long id;
	public boolean duplicate;

	public byte[] payload = new byte[100];
	public String key;

	public Record() {
		// for pojo serializer
	}

	public Record(long id, long eventTime, long ingestionTime, boolean duplicate) {
		this.id = id;
		this.eventTime = eventTime;
		this.ingestionTime = ingestionTime;
		this.duplicate = duplicate;
	}
}
