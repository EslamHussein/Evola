package evola.composeapp.core.database

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import evola.database.DatabaseFactory

@Composable
actual fun rememberRoomDatabaseFactory(): DatabaseFactory = remember { DatabaseFactory() }
