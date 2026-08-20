package evola.composeapp.core.database

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.logs.LogSqliteDriver
import evola.shared.core.analytics.EvolaLog
import evola.shared.core.common.SqlLoggingGate
import evola.shared.db.EvolaDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver = LogSqliteDriver(
        sqlDriver = AndroidSqliteDriver(
            EvolaDatabase.Schema,
            context.applicationContext,
            "evola.db",
            // SQLite has foreign keys off by default per-connection - without this, the schema's
            // ON DELETE CASCADE (materials -> lessons -> vocabulary/grammar) silently does nothing
            // and deleting a material/lesson would leave every child row orphaned.
            callback = object : AndroidSqliteDriver.Callback(EvolaDatabase.Schema) {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
        ),
        logger = { message -> if (!SqlLoggingGate.suppressed) EvolaLog.d("sql", message) },
    )
}

@Composable
actual fun rememberDatabaseDriverFactory(): DatabaseDriverFactory {
    val context = LocalContext.current
    return remember { DatabaseDriverFactory(context) }
}
