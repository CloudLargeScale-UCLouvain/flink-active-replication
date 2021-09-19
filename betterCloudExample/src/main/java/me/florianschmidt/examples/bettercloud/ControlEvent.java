package me.florianschmidt.examples.bettercloud;

import me.florianschmidt.examples.bettercloud.rulesengine.Rule;

import java.util.List;

public class ControlEvent {

	public List<Rule> rules;
	public String customerId;
	public String alertId;
	public String alertName;
	public String alertDescription;
	public int threshold;
	public String bootstrapCustomerId;
	public long createdAt;

	public ControlEvent() {
	}

	public ControlEvent(
			long createdAt,
			String customerId,
			String alertId,
			String alertName,
			String alertDescription,
			int threshold,
			List<Rule> rules
	) {
		this.createdAt = createdAt;
		this.customerId = customerId;
		this.alertId = alertId;
		this.alertName = alertName;
		this.alertDescription = alertDescription;
		this.threshold = threshold;
		this.rules = rules;
	}
	@Override
	public String toString() {
		return String.format("ControlEvent{{%d,%s,%s,%s,%d}}", createdAt, customerId, alertId, alertDescription, threshold);
	}
}
