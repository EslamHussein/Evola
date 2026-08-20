package evola.composeapp.core.analytics

import evola.shared.core.analytics.EvolaLog

actual fun installCrashLogging() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        EvolaLog.d("crash", "uncaught on thread=${thread.name}: ${throwable.stackTraceToString()}")
        previous?.uncaughtException(thread, throwable)
    }
}
