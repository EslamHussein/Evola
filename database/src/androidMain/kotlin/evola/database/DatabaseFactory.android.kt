package evola.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual class DatabaseFactory(private val context: Context) {
    actual fun builder(): RoomDatabase.Builder<AppDatabase> {
        val dbFile = context.applicationContext.getDatabasePath(DATABASE_FILE_NAME)
        return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, dbFile.absolutePath)
    }
}
