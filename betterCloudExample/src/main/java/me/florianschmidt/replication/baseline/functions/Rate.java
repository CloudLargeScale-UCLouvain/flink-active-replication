package me.florianschmidt.replication.baseline.functions;

import java.io.Serializable;

public interface Rate extends Serializable {
	int update(int tick);
}
