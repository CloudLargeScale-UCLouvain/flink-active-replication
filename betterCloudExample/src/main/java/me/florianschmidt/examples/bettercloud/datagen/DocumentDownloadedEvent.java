package me.florianschmidt.examples.bettercloud.datagen;

public class DocumentDownloadedEvent {

	public String title;
	public String path;
	public String provider;
	public String actionType = "DocumentDownloadedEvent";

	public DocumentDownloadedEvent(String title, String path, String provider) {
		this.title = title;
		this.path = path;
		this.provider = provider;
	}
}

