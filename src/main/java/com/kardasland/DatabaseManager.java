package com.kardasland;

import com.kardasland.converters.LocationConverter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.PersistenceUnitTransactionType;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import javax.sql.DataSource;
import java.net.URL;
import java.security.CodeSource;
import java.util.*;
import java.util.stream.Collectors;

public class DatabaseManager {

	private final JavaPlugin plugin;
	@Getter
	private EntityManagerFactory entityManagerFactory;

	public DatabaseManager(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void initializeDataSource(DatabaseConfig config) {
		HikariDataSource dataSource = getHikariDataSource(config);

		Properties properties = new Properties();
		properties.put("hibernate.dialect", getDialect(config.getType()));
		properties.put("hibernate.hbm2ddl.auto", "update");
		properties.put("hibernate.show_sql", String.valueOf(config.isShowSql()));
		properties.put("hibernate.format_sql", "true");

		Reflections reflections = instantiateReflections(config);

		Set<Class<?>> entityClasses = reflections.getTypesAnnotatedWith(Entity.class);
		plugin.getLogger().info("Found entities: " + entityClasses.stream().map(Class::getSimpleName).collect(Collectors.joining(", ")));

		this.entityManagerFactory = new HibernatePersistenceProvider()
			.createContainerEntityManagerFactory(
				createPersistenceUnitInfo(plugin.getName(), entityClasses, dataSource),
				properties
			);
	}

	private Reflections instantiateReflections(DatabaseConfig config) {
		ClassLoader pluginCl = plugin.getClass().getClassLoader();
		ClassLoader ctxCl = Thread.currentThread().getContextClassLoader();
		List<ClassLoader> loaders = Arrays.asList(pluginCl, ctxCl);
		Set<URL> urls = new LinkedHashSet<>(ClasspathHelper.forClassLoader(loaders.toArray(ClassLoader[]::new)));
		if (config.getPackagesToScan() != null) {
			for (String pkg : config.getPackagesToScan()) {
				urls.addAll(ClasspathHelper.forPackage(pkg, loaders.toArray(ClassLoader[]::new)));
			}
		}
		CodeSource cs = plugin.getClass().getProtectionDomain().getCodeSource();
		if (cs != null && cs.getLocation() != null) {
			urls.add(cs.getLocation());
		}
		ConfigurationBuilder cfg = new ConfigurationBuilder()
			.addClassLoaders(loaders.toArray(ClassLoader[]::new))
			.setUrls(urls)
			.setScanners(Scanners.TypesAnnotated);
		return new Reflections(cfg);
	}

	private HikariDataSource getHikariDataSource(DatabaseConfig config) {
		HikariConfig hikariConfig = new HikariConfig();
		hikariConfig.setPoolName(plugin.getName() + "-Hikari");
		switch (config.getType().toLowerCase()) {
			case "sqlite":
				hikariConfig.setJdbcUrl("jdbc:sqlite:" + config.getFilePath());
				break;
			case "mysql":
			case "postgresql":
				hikariConfig.setJdbcUrl(
					"jdbc:" + config.getType() + "://" +
						config.getHost() + ":" +
						config.getPort() + "/" +
						config.getName() + "?autoReconnect=true&useSSL=false"
				);
				hikariConfig.setUsername(config.getUser());
				hikariConfig.setPassword(config.getPassword());
				break;
			default:
				throw new IllegalArgumentException("Unsupported database type in DatabaseConfig: " + config.getType());
		}
		return new HikariDataSource(hikariConfig);
	}

	private String getDialect(String dbType) {
		return switch (dbType.toLowerCase()) {
			case "mysql" -> "org.hibernate.dialect.MySQL8Dialect";
			case "postgresql" -> "org.hibernate.dialect.PostgreSQLDialect";
			case "sqlite" -> "org.hibernate.community.dialect.SQLiteDialect";
			default -> throw new IllegalArgumentException("Unsupported database type: " + dbType);
		};
	}

	private PersistenceUnitInfo createPersistenceUnitInfo(String persistenceUnitName, Set<Class<?>> entityClasses, DataSource dataSource) {
		return new PersistenceUnitInfo() {
			@Override
			public String getPersistenceUnitName() { return persistenceUnitName; }

			@Override
			public String getPersistenceProviderClassName() { return HibernatePersistenceProvider.class.getName(); }

			@Override
			public PersistenceUnitTransactionType getTransactionType() {
				return null;
			}
			@Override
			public DataSource getJtaDataSource() { return null; }

			@Override
			public DataSource getNonJtaDataSource() { return dataSource; }

			@Override
			public List<String> getMappingFileNames() { return Collections.emptyList(); }

			@Override
			public List<URL> getJarFileUrls() { return Collections.emptyList(); }

			@Override
			public URL getPersistenceUnitRootUrl() { return null; }

			@Override
			public List<String> getManagedClassNames() {
				List<String> classNames = new ArrayList<>();
				// Manually add our custom converter so Hibernate finds it
				classNames.add(LocationConverter.class.getName());
				entityClasses.forEach(c -> classNames.add(c.getName()));
				return classNames;
			}

			@Override
			public boolean excludeUnlistedClasses() { return false; }

			@Override
			public SharedCacheMode getSharedCacheMode() { return SharedCacheMode.UNSPECIFIED; }

			@Override
			public ValidationMode getValidationMode() { return ValidationMode.AUTO; }

			@Override
			public Properties getProperties() { return new Properties(); }

			@Override
			public String getPersistenceXMLSchemaVersion() { return "2.2"; }

			@Override
			public ClassLoader getClassLoader() { return plugin.getClass().getClassLoader(); }

			@Override
			public void addTransformer(ClassTransformer transformer) { }

			@Override
			public ClassLoader getNewTempClassLoader() { return null; }

			@Override
			public String getScopeAnnotationName() {
				return null;
			}

			@Override
			public List<String> getQualifierAnnotationNames() {
				return List.of();
			}

		};
	}
}
