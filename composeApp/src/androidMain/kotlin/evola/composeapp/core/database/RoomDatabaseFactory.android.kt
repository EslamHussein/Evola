package evola.composeapp.core.database

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import evola.database.DatabaseFactory

@Composable
actual fun rememberRoomDatabaseFactory(): DatabaseFactory {
    val context = LocalContext.current
    return remember { DatabaseFactory(context) }
}
