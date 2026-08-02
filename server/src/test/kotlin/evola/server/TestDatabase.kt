package evola.server

import evola.integrations.persistence.DatabaseFactory
import org.jetbrains.exposed.sql.Database

/**
 * A dedicated `evola_test` database, migrated via the same [DatabaseFactory] production code
 * uses (so tests exercise the real Flyway migrations, not a hand-rolled schema). Points at the
 * project's local dev Postgres by default (docker/docker-compose.yml) with its own database name
 * so test runs never touch real dev data; override via env vars for CI or a different host.
 * Individual tests truncate their own tables in @BeforeEach.
 */
object TestDatabase {
    val database: Database by lazy {
        DatabaseFactory.connect(
            jdbcUrl = System.getenv("TEST_DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/evola_test",
            username = System.getenv("TEST_DATABASE_USER") ?: "evola",
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "evola",
        )
    }
}
