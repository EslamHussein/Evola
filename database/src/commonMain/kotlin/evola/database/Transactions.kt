package evola.database

import androidx.room.Transactor
import androidx.room.useWriterConnection

/** KMP-common equivalent of the Android-only `RoomDatabase.withTransaction` KTX extension (that
 * one lives in room-runtime's androidMain, unusable from :shared's iOS target) - wraps [block] in a
 * single write transaction. DAO calls made inside [block] against this same [AppDatabase] instance
 * participate in the already-open transaction via Room's own coroutine-context propagation. */
suspend fun <R> AppDatabase.inTransaction(block: suspend () -> R): R =
    useWriterConnection { it.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) { block() } }
