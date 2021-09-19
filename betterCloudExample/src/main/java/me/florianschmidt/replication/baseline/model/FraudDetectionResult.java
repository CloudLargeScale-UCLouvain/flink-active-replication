package me.florianschmidt.replication.baseline.model;

public class FraudDetectionResult {
	public final boolean isFraud;
	public final Transaction transaction;

	public FraudDetectionResult(boolean isFraud, Transaction transaction) {
		this.isFraud = isFraud;
		this.transaction = transaction;
	}

	@Override
	public String toString() {
		return "FraudDetectionResult{" +
				"isFraud=" + isFraud +
				", transaction=" + transaction +
				'}';
	}
}
