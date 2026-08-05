package evola.composeapp.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import evola.shared.db.EvolaDatabase

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = NativeSqliteDriver(EvolaDatabase.Schema, "evola.db")
}
