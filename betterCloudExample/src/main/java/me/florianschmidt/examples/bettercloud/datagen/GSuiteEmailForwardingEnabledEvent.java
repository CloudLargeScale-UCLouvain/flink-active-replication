package me.florianschmidt.examples.bettercloud.datagen;

class GSuiteEmailForwardingEnabledEvent {
	public String to;
	public String username;
	public String provider = "GSuite";
	public String actionType = "GSuiteEmailForwardingEnabledEvent";

	public GSuiteEmailForwardingEnabledEvent(String to, String username) {
		this.to = to;
		this.username = username;
	}

}
