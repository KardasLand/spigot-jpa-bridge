package com.kardasland.repository;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class RepositoryInvocationHandler implements InvocationHandler {

	private final EntityManagerFactory emf;
	private final JavaPlugin plugin;
	private final Class<?> entityType;
	private static final Pattern TOP_N_PATTERN = Pattern.compile("findTop(\\d+)");

	public RepositoryInvocationHandler(EntityManagerFactory emf, JavaPlugin plugin, Class<?> repositoryInterface) {
		this.emf = emf;
		this.plugin = plugin;
		this.entityType = (Class<?>) ((ParameterizedType) repositoryInterface.getGenericInterfaces()[0]).getActualTypeArguments()[0];
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) {
		String methodName = method.getName();
		switch (methodName) {
			case "equals": return proxy == args[0];
			case "hashCode": return System.identityHashCode(proxy);
			case "toString": return "Proxy for repository: " + method.getDeclaringClass().getSimpleName();
		}

		// --- Standard CRUD methods ---
		if (methodName.equals("findById") || methodName.equals("save") || methodName.equals("delete") || methodName.equals("findAll")) {
			return handleCrudMethod(methodName, args);
		}

		// --- Dynamic Query Derivation ---
		return handleDerivedQuery(method, args);
	}

	private CompletableFuture<?> handleDerivedQuery(Method method, Object[] args) {
		return CompletableFuture.supplyAsync(() -> {
			EntityManager em = emf.createEntityManager();
			try {
				String methodName = method.getName();
				Matcher topNMatcher = TOP_N_PATTERN.matcher(methodName);
				int limit = 0;
				if (topNMatcher.find()) {
					limit = Integer.parseInt(topNMatcher.group(1));
				}

				String baseSelect = "SELECT e FROM " + entityType.getSimpleName() + " e";
				String jpql = buildJpqlFromMethodName(methodName, baseSelect);
				TypedQuery<?> query = em.createQuery(jpql, entityType);

				if (args != null) {
					for (int i = 0; i < args.length; i++) {
						query.setParameter("arg" + i, args[i]);
					}
				}
				if (limit > 0) {
					query.setMaxResults(limit);
				}

				try {
					if (List.class.isAssignableFrom(getGenericReturnType(method))) {
						return query.getResultList();
					} else if (Optional.class.isAssignableFrom(getGenericReturnType(method))) {
						List<?> results = query.getResultList();
						return Optional.ofNullable(results.isEmpty() ? null : results.getFirst());
					} else {
						return query.getSingleResult();
					}
				} catch (NoResultException e) {
					plugin.getLogger().info("Query returned no results, " + e.getMessage());
					return method.getReturnType().equals(java.util.Optional.class) ? Optional.empty() : null;
				}
			} catch (Exception e) {
				plugin.getLogger().severe("Error executing derived query: " + e.getMessage());
				e.printStackTrace();
				throw e;
			} finally {
				if (em.isOpen()) {
					em.close();
				}
			}
		}, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
	}

	private String buildJpqlFromMethodName(String methodName, String baseSelect) {
		StringBuilder jpql = new StringBuilder(baseSelect);

		// Find By / FindAllBy
		int byIndex = methodName.indexOf("By");
		int orderByIndex = methodName.indexOf("OrderBy");

		if (byIndex != -1) {
			String criteriaString = (orderByIndex != -1)
				? methodName.substring(byIndex + 2, orderByIndex)
				: methodName.substring(byIndex + 2);

			if (!criteriaString.isEmpty()) {
				jpql.append(" WHERE ");
				String[] conditions = criteriaString.split("And|Or");
				int argIndex = 0;
				for (int i = 0; i < conditions.length; i++) {
					String property = toCamelCase(conditions[i]);
					jpql.append("e.").append(property).append(" = :arg").append(argIndex++);
					if (i < conditions.length - 1) {
						if (criteriaString.contains("Or")) {
							jpql.append(" OR ");
						} else {
							jpql.append(" AND ");
						}
					}
				}
			}
		}

		// Order By
		if (orderByIndex != -1) {
			String orderString = methodName.substring(orderByIndex + "OrderBy".length());
			String propertyName = toCamelCase(orderString.replaceAll("Desc|Asc", ""));
			String direction = orderString.endsWith("Desc") ? "DESC" : "ASC";
			jpql.append(" ORDER BY e.").append(propertyName).append(" ").append(direction);
		}

		return jpql.toString();
	}

	private String toCamelCase(String input) {
		if (input.isEmpty()) return input;
		return Character.toLowerCase(input.charAt(0)) + input.substring(1);
	}

	private Class<?> getGenericReturnType(Method method) {
		java.lang.reflect.Type returnType = method.getGenericReturnType();
		if (returnType instanceof ParameterizedType paramType) {
			java.lang.reflect.Type[] typeArgs = paramType.getActualTypeArguments();
			if (typeArgs.length == 1) {
				java.lang.reflect.Type argType = typeArgs[0];
				if (argType instanceof Class) {
					return (Class<?>) argType;
				} else if (argType instanceof ParameterizedType) {
					return (Class<?>) ((ParameterizedType) argType).getRawType();
				}
			}
		}
		return Object.class;
	}

	private CompletableFuture<?> handleCrudMethod(String methodName, Object[] args) {
		return CompletableFuture.supplyAsync(() -> {
			EntityManager em = emf.createEntityManager();
			try {
				switch (methodName) {
					case "findById": return em.find(this.entityType, args[0]);
					case "save":
						em.getTransaction().begin();
						em.merge(args[0]);
						em.flush();
						em.getTransaction().commit();
						return null;
					case "delete":
						em.getTransaction().begin();
						Object entityToDelete = em.contains(args[0]) ? args[0] : em.merge(args[0]);
						em.remove(entityToDelete);
						em.getTransaction().commit();
						return null;
					case "findAll":
						String jpql = "SELECT e FROM " + entityType.getSimpleName() + " e";
						TypedQuery<?> findAllQuery = em.createQuery(jpql, entityType);
						return findAllQuery.getResultList();
				}
			}finally {
				if (em.isOpen()) {
					em.close();
				}
			}
			return null;
		}, (runnable) -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
	}
}
