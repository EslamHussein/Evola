package evola.composeapp.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.logs.LogSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import evola.shared.core.EvolaLog
import evola.shared.core.SqlLoggingGate
import evola.shared.db.EvolaDatabase

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = LogSqliteDriver(
        sqlDriver = NativeSqliteDriver(
            EvolaDatabase.Schema,
            "evola.db",
            // See the matching comment in DatabaseDriverFactory.android.kt - foreign keys are off
            // by default per-connection in SQLite; without this the schema's ON DELETE CASCADE is a
            // no-op and deletes would leave orphaned rows.
            onConfiguration = { config: DatabaseConfiguration ->
                config.copy(extendedConfig = DatabaseConfiguration.Extended(foreignKeyConstraints = true))
            },
        ),
        logger = { message -> if (!SqlLoggingGate.suppressed) EvolaLog.d("sql", message) },
    )
}

@Composable
actual fun rememberDatabaseDriverFactory(): DatabaseDriverFactory = remember { DatabaseDriverFactory() }
