package me.florianschmidt.examples.bettercloud.datagen;

class GSuiteLoginFailedEvent {
	public String ip;
	public String username;
	public String actionType = "GSuiteLoginFailedEvent";

	public GSuiteLoginFailedEvent(String ip, String username) {
		this.ip = ip;
		this.username = username;
	}
}
