package evola.composeapp.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.logs.LogSqliteDriver
import evola.shared.core.EvolaLog
import evola.shared.db.EvolaDatabase

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = LogSqliteDriver(
        sqlDriver = NativeSqliteDriver(EvolaDatabase.Schema, "evola.db"),
        logger = { message -> EvolaLog.d("sql", message) },
    )
}

@Composable
actual fun rememberDatabaseDriverFactory(): DatabaseDriverFactory = remember { DatabaseDriverFactory() }
