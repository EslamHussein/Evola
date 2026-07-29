package evola.integrations.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource

object DatabaseFactory {

    /** Connects Exposed to Postgres via HikariCP, applying pending Flyway migrations on boot. */
    fun connect(jdbcUrl: String, username: String, password: String): Database {
        val dataSource = createDataSource(jdbcUrl, username, password)
        migrate(dataSource)
        return Database.connect(dataSource)
    }

    private fun createDataSource(jdbcUrl: String, username: String, password: String): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                this.username = username
                this.password = password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 10
            },
        )

    private fun migrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
