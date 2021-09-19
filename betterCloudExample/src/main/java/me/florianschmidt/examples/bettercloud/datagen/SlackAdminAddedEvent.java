package me.florianschmidt.examples.bettercloud.datagen;

class SlackAdminAddedEvent {
	public String username;
	public String email;
	public String addedBy;
	public String actionType = "SlackAdminAddedEvent";

	public SlackAdminAddedEvent(String username, String email, String addedBy) {
		this.username = username;
		this.email = email;
		this.addedBy = addedBy;
	}
}
