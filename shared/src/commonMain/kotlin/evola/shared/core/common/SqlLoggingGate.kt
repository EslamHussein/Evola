package evola.shared.core.common

/** Lets a bulk DB operation (e.g. `GermanNounImporter`'s one-time ~100k-row import) suppress the
 * per-statement SQL logging both platforms' `LogSqliteDriver` normally does for every query -
 * valuable for regular single-query debugging, but it turns a bulk import into tens of thousands
 * of synchronous log writes (measured: ~5 minutes for 102k rows with logging on, vs. seconds
 * without). Global rather than threaded through DI because the driver wrapper (composeApp) and
 * the importer (:shared) don't otherwise share a reference to toggle per-call. */
object SqlLoggingGate {
    var suppressed: Boolean = false
}
