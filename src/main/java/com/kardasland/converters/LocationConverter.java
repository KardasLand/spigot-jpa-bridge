package com.kardasland.converters;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LocationConverter implements AttributeConverter<Location, String> {

	private static final String SEPARATOR = ";";

	@Override
	public String convertToDatabaseColumn(Location location) {
		if (location == null) {
			return null;
		}
		if (location.getWorld() == null) {
			throw new IllegalArgumentException("Location must have a valid world.");
		}
		// Format: worldName;x;y;z;yaw;pitch
		return location.getWorld().getName() + SEPARATOR +
			location.getX() + SEPARATOR +
			location.getY() + SEPARATOR +
			location.getZ() + SEPARATOR +
			location.getYaw() + SEPARATOR +
			location.getPitch();
	}

	@Override
	public Location convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isEmpty()) {
			return null;
		}
		String[] parts = dbData.split(SEPARATOR);
		if (parts.length != 6) {
			throw new IllegalArgumentException("Invalid location format in database: " + dbData);
		}
		return new Location(
			Bukkit.getWorld(parts[0]),
			Double.parseDouble(parts[1]),
			Double.parseDouble(parts[2]),
			Double.parseDouble(parts[3]),
			Float.parseFloat(parts[4]),
			Float.parseFloat(parts[5])
		);
	}
}
