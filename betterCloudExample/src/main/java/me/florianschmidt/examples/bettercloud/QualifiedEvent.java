package me.florianschmidt.examples.bettercloud;

public class QualifiedEvent {
	public CustomerEvent event;
	public ControlEvent controlEvent;
	public long sinkAt;
	public QualifiedEvent() {
	}

	public QualifiedEvent(CustomerEvent event, ControlEvent controlEvent, long sinkAt) {
		this.event = event;
		this.controlEvent = controlEvent;
		this.sinkAt = sinkAt;
	}
}
