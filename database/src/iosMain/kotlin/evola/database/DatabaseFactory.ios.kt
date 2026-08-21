@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package evola.database

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual class DatabaseFactory {
    actual fun builder(): RoomDatabase.Builder<AppDatabase> {
        val documentsDirectory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        val dbFilePath = requireNotNull(documentsDirectory?.path) { "Couldn't resolve iOS documents directory" } + "/$DATABASE_FILE_NAME"
        return Room.databaseBuilder<AppDatabase>(name = dbFilePath)
    }
}
