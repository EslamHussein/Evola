package evola.composeapp.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.logs.LogSqliteDriver
import evola.shared.core.EvolaLog
import evola.shared.db.EvolaDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver = LogSqliteDriver(
        sqlDriver = AndroidSqliteDriver(EvolaDatabase.Schema, context.applicationContext, "evola.db"),
        logger = { message -> EvolaLog.d("sql", message) },
    )
}

@Composable
actual fun rememberDatabaseDriverFactory(): DatabaseDriverFactory {
    val context = LocalContext.current
    return remember { DatabaseDriverFactory(context) }
}
