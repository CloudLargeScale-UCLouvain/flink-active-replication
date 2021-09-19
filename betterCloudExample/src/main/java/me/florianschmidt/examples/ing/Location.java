package me.florianschmidt.examples.ing;

public class Location {

	public double latitude;

	public double longitude;

	public Location() {
	}

	public Location(double latitude, double longitude) {
		this.latitude = latitude;
		this.longitude = longitude;
	}

	@Override
	public String toString() {
		return "Location{" +
				"latitude=" + latitude +
				", longitude=" + longitude +
				'}';
	}
}
