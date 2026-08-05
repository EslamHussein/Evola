package evola.composeapp.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import evola.shared.db.EvolaDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver =
        AndroidSqliteDriver(EvolaDatabase.Schema, context.applicationContext, "evola.db")
}

@Composable
actual fun rememberDatabaseDriverFactory(): DatabaseDriverFactory {
    val context = LocalContext.current
    return remember { DatabaseDriverFactory(context) }
}
