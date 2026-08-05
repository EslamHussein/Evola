package evola.composeapp.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import evola.shared.db.EvolaDatabase

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = NativeSqliteDriver(EvolaDatabase.Schema, "evola.db")
}

@Composable
actual fun rememberDatabaseDriverFactory(): DatabaseDriverFactory = remember { DatabaseDriverFactory() }
