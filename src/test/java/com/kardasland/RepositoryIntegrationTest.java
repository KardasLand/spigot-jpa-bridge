package com.kardasland;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import com.kardasland.domain.PlayerProfile;
import com.kardasland.domain.PlayerProfileRepository;
import com.kardasland.repository.RepositoryFactory;
import org.bukkit.Location;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RepositoryIntegrationTest {

	private ServerMock server;
	private MockPlugin plugin;
	private PlayerProfileRepository playerProfileRepository;

	@BeforeAll
	void setupServer() {
		server = MockBukkit.mock();
		plugin = MockBukkit.createMockPlugin("TestPlugin");
		server.addSimpleWorld("test-world");

		DatabaseConfig dbConfig = DatabaseConfig.builder()
			.type("sqlite")
			.filePath("target/test-db-" + UUID.randomUUID() + ".db")
			.showSql(false)
			.packagesToScan(List.of(PlayerProfile.class.getPackageName()))
			.build();

		DatabaseManager databaseManager = new DatabaseManager(plugin);
		databaseManager.initializeDataSource(dbConfig);

		RepositoryFactory factory = new RepositoryFactory(databaseManager.getEntityManagerFactory(), plugin);
		playerProfileRepository = factory.createRepository(PlayerProfileRepository.class);
	}

	@AfterAll
	void teardownServer() {
		MockBukkit.unmock();
	}

	@BeforeEach
	void cleanupDatabase() throws ExecutionException, InterruptedException {
		List<PlayerProfile> allProfiles = playerProfileRepository.findAll().get();
		for (PlayerProfile profile : allProfiles) {
			playerProfileRepository.delete(profile).get();
		}
	}

	@Test
	void testSaveAndFindById() throws ExecutionException, InterruptedException {
		UUID playerId = UUID.randomUUID();
		Location loc = new Location(server.getWorld("test-world"), 10, 20, 30);
		PlayerProfile profile = new PlayerProfile(playerId, "KardasLand", 10, loc);

		playerProfileRepository.save(profile).get();

		PlayerProfile foundProfile = playerProfileRepository.findById(playerId).get();

		assertNotNull(foundProfile);
		assertEquals("KardasLand", foundProfile.getUsername());
		assertEquals(10, foundProfile.getLevel());
		assertEquals(10, foundProfile.getLastLocation().getX());
	}

	@Test
	void testFindByCustomQuery() throws ExecutionException, InterruptedException {
		playerProfileRepository.save(new PlayerProfile(UUID.randomUUID(), "Notch", 100, null)).get();
		playerProfileRepository.save(new PlayerProfile(UUID.randomUUID(), "Jeb", 90, null)).get();

		PlayerProfile foundProfile = playerProfileRepository.findByUsername("Notch").get();

		assertNotNull(foundProfile);
		assertEquals(100, foundProfile.getLevel());
	}
}
