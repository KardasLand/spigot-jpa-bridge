package com.kardasland.repository;

import jakarta.persistence.EntityManagerFactory;
import lombok.AllArgsConstructor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Proxy;

@AllArgsConstructor
public class RepositoryFactory {
	private final EntityManagerFactory entityManagerFactory;
	private final JavaPlugin plugin;

	@SuppressWarnings("unchecked")
	public <T> T createRepository(Class<T> repositoryInterface) {
		return (T) Proxy.newProxyInstance(
			repositoryInterface.getClassLoader(),
			new Class[]{repositoryInterface},
			new RepositoryInvocationHandler(entityManagerFactory, plugin, repositoryInterface)
		);
	}
}
