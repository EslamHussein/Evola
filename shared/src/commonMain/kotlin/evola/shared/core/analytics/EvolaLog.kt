package evola.shared.core.analytics

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.mutableLoggerConfigInit
import co.touchlab.kermit.platformLogWriter

/**
 * Cross-platform logging backed by Kermit. Always writes to the platform console (Logcat on
 * Android, os_log on iOS) via the default [platformLogWriter]. [attachFileWriter] adds a second,
 * persistent sink — wired from `:composeApp` at startup once a writable app directory is known —
 * so network/DB/app-logic failures can be read back from a local log file instead of reproduced
 * with a screenshot.
 */
object EvolaLog {
    private val logger = Logger(
        config = mutableLoggerConfigInit(platformLogWriter(), minSeverity = Severity.Debug),
        tag = "EVOLA",
    )

    fun d(area: String, message: String) {
        logger.d(tag = area) { message }
    }

    fun attachFileWriter(writer: LogWriter) {
        val config = logger.mutableConfig
        config.logWriterList = config.logWriterList + writer
    }
}
