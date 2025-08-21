package com.kardasland.repository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface CrudRepository<T, ID> {
	CompletableFuture<T> findById(ID id);
	CompletableFuture<Void> save(T entity);
	CompletableFuture<Void> delete(T entity);
	CompletableFuture<List<T>> findAll();
}
