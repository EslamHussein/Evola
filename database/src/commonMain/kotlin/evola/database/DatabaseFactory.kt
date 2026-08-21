package evola.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** Creates the platform [RoomDatabase.Builder] — Android needs a `Context`, iOS needs its own
 * documents-directory lookup, so only the builder itself is platform-specific (mirrors
 * `composeApp`'s pre-existing `DatabaseDriverFactory` split for the SQLDelight driver it replaces).
 * `:database` never exposes `Context` or any iOS API past this expect/actual boundary. */
expect class DatabaseFactory {
    fun builder(): RoomDatabase.Builder<AppDatabase>
}

fun DatabaseFactory.create(): AppDatabase = builder()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
