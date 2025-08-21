package com.kardasland;

import com.kardasland.annotation.InjectRepository;
import com.kardasland.repository.RepositoryFactory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.util.logging.Level;

public class SpigotJPA {

	/**
	 * Initializes SpigotJPA with a default SQLite database.
	 * The database file will be created at /plugins/<pluginname>/database.db
	 * @param plugin Your plugin instance.
	 */
	public static void initialize(JavaPlugin plugin) {
		File dbFile = new File(plugin.getDataFolder(), "database.db");
		DatabaseConfig defaultConfig = DatabaseConfig.builder()
			.type("sqlite")
			.filePath(dbFile.getAbsolutePath())
			.build();
		initialize(plugin, defaultConfig);
	}

	/**
	 * Initializes SpigotJPA with a custom database configuration.
	 * @param plugin Your plugin instance.
	 * @param dbConfig The database configuration object.
	 */
	public static void initialize(JavaPlugin plugin, DatabaseConfig dbConfig) {
		if (!plugin.getDataFolder().exists()) {
			if (!plugin.getDataFolder().mkdirs()) {
				plugin.getLogger().log(Level.SEVERE, "Failed to create data folder: " + plugin.getDataFolder().getAbsolutePath());
				throw new RuntimeException("Could not create data folder for plugin: " + plugin.getName());
			}
		}

		DatabaseManager dbManager = new DatabaseManager(plugin);
		dbManager.initializeDataSource(dbConfig);

		RepositoryFactory repoFactory = new RepositoryFactory(dbManager.getEntityManagerFactory(), plugin);

		injectRepositories(plugin, repoFactory);

		plugin.getLogger().info("SpigotJPA has been initialized successfully.");
	}

	private static void injectRepositories(Object target, RepositoryFactory factory) {
		for (Field field : target.getClass().getDeclaredFields()) {
			if (field.isAnnotationPresent(InjectRepository.class)) {
				try {
					field.setAccessible(true);
					Object repoInstance = factory.createRepository(field.getType());
					field.set(target, repoInstance);
				} catch (Exception e) {
					JavaPlugin.getProvidingPlugin(target.getClass()).getLogger()
						.log(Level.SEVERE, "Failed to inject repository into " + field.getName(), e);
				}
			}
		}
	}
}
