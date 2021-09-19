package me.florianschmidt.examples.bettercloud;

import java.util.List;

public class FilteredEvent {
	public CustomerEvent event;
	public List<ControlEvent> controls;

	public FilteredEvent() {
	}

	public FilteredEvent(CustomerEvent event, List<ControlEvent> controls) {
		this.event = event;
		this.controls = controls;
	}
	@Override
	public String toString() {
		return this.event.toString() + "," + this.controls.toString();
	}	
}
