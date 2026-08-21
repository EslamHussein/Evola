package evola.database

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

/** JVM target only exists for `:shared`'s `jvmTest` suite to exercise real Room queries — never
 * shipped. Builds the database in a temp file per instance rather than in-memory, since Room KMP's
 * JVM path doesn't support `:memory:` the way the old SQLDelight JDBC driver did. */
actual class DatabaseFactory {
    actual fun builder(): RoomDatabase.Builder<AppDatabase> {
        val dbFile = File.createTempFile("evola-test-", ".db").apply { deleteOnExit() }
        return Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
    }
}
