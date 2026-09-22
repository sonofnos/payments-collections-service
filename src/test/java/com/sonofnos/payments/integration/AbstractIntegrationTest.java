package com.sonofnos.payments.integration;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Deliberately does NOT use JUnit 5's {@code @Testcontainers}/{@code @Container}
 * lifecycle management here: with multiple integration test classes in the
 * same Maven Surefire run, that per-class beforeAll/afterAll lifecycle was
 * observed to start a fresh Postgres/Redis pair per test class and tear the
 * previous pair down mid-suite, leaving other classes' Hikari/Lettuce
 * connections pointing at containers (and ports) that no longer existed -
 * connection-refused and command-timeout failures that had nothing to do
 * with the code under test. Starting both containers exactly once, eagerly,
 * in a static initializer (the documented Testcontainers "singleton
 * container" pattern) means they live for the whole JVM/Surefire fork and
 * are only reaped by Ryuk on exit.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestAccountClientConfig.class)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final RedisContainer REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("payments")
                .withUsername("payments")
                .withPassword("payments");
        POSTGRES.start();

        REDIS = new RedisContainer(DockerImageName.parse("redis:7-alpine"));
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getFirstMappedPort());
    }
}
