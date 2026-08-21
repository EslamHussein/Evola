package evola.composeapp.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import evola.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import org.robolectric.RuntimeEnvironment

/** Test-only Room database builder for Robolectric-backed ViewModel tests. Deliberately NOT the
 * production [evola.database.DatabaseFactory] android actual: that builds a real file-backed
 * database (wrong for tests - use in-memory instead) with [androidx.sqlite.driver.bundled.BundledSQLiteDriver],
 * whose native library doesn't load under Robolectric's host-JVM sandbox and hangs every query
 * indefinitely rather than failing cleanly. [AndroidSQLiteDriver] (the classic SQLiteOpenHelper-
 * based driver) is what Robolectric's own SQLite shadow layer is built around, so it's the one
 * driver that actually works here - production code never uses it. */
fun testAppDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
    .setDriver(AndroidSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
