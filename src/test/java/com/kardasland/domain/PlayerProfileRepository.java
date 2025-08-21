package com.kardasland.domain;

import com.kardasland.repository.CrudRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerProfileRepository extends CrudRepository<PlayerProfile, UUID> {
	CompletableFuture<PlayerProfile> findByUsername(String username);
	CompletableFuture<List<PlayerProfile>> findByLevelOrderByUsernameAsc(int level);
}
