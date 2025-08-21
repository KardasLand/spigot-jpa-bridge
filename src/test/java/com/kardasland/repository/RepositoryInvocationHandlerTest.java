package com.kardasland.repository;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestEntity {}

interface TestRepository extends CrudRepository<TestEntity, Long> {}

class RepositoryInvocationHandlerTest {

	private String invokeBuildJpql(String methodName) throws Exception {
		RepositoryInvocationHandler handler = new RepositoryInvocationHandler(null, null, TestRepository.class);

		Method method = RepositoryInvocationHandler.class.getDeclaredMethod("buildJpqlFromMethodName", String.class, String.class);
		method.setAccessible(true);

		String baseSelect = "SELECT e FROM TestEntity e";
		return (String) method.invoke(handler, methodName, baseSelect);
	}

	@Test
	void testBuildJpqlForFindByField() throws Exception {
		String jpql = invokeBuildJpql("findByUsername");
		assertEquals("SELECT e FROM TestEntity e WHERE e.username = :arg0", jpql);
	}

	@Test
	void testBuildJpqlForFindByMultipleFieldsWithAnd() throws Exception {
		String jpql = invokeBuildJpql("findByUsernameAndLevel");
		assertEquals("SELECT e FROM TestEntity e WHERE e.username = :arg0 AND e.level = :arg1", jpql);
	}

	@Test
	void testBuildJpqlWithOrderBy() throws Exception {
		String jpql = invokeBuildJpql("findAllByOrderByScoreDesc");
		assertEquals("SELECT e FROM TestEntity e ORDER BY e.score DESC", jpql);
	}

	@Test
	void testBuildJpqlWithFindByAndOrderBy() throws Exception {
		String jpql = invokeBuildJpql("findByLevelOrderByScoreAsc");
		assertEquals("SELECT e FROM TestEntity e WHERE e.level = :arg0 ORDER BY e.score ASC", jpql);
	}

	@Test
	void testBuildJpqlWithNoCriteria() throws Exception {
		String jpql = invokeBuildJpql("findAll");
		assertEquals("SELECT e FROM TestEntity e", jpql);
	}
}
