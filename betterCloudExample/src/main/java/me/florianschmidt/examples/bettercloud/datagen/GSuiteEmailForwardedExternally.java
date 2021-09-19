package me.florianschmidt.examples.bettercloud.datagen;

class GSuiteEmailForwardedExternally {

	public String from;
	public String to;
	public String actionType = "GSuiteEmailForwardedExternally";
	public String provider = "GSuite";

	public GSuiteEmailForwardedExternally(String from, String to) {
		this.from = from;
		this.to = to;
	}

}
