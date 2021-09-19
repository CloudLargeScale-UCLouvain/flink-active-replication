package me.florianschmidt.examples.ing;

public class ScoringResult implements TransactionEvent {
	public TransactionEvent event;
	public boolean score;

	public ScoringResult(TransactionEvent event, boolean score) {
		this.event = event;
		this.score = score;
	}

	public ScoringResult() {
	}

	@Override
	public String toString() {
		return "ScoringResult{" +
				"event=" + event +
				", score=" + score +
				'}';
	}
}
