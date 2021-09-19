package me.florianschmidt.examples.bettercloud.datagen;

public class DocumentSharedEvent {

	public String provider;
	public String title;
	public String audience;
	public String type;
	public String actionType = "DocumentSharedEvent";

	public DocumentSharedEvent(String provider, String title, String audience, String type) {
		this.provider = provider;
		this.title = title;
		this.audience = audience;
		this.type = type;
	}
}
