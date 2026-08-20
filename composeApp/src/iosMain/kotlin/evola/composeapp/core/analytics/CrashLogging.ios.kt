package evola.composeapp.core.analytics

import evola.shared.core.analytics.EvolaLog
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook

@OptIn(ExperimentalNativeApi::class)
actual fun installCrashLogging() {
    setUnhandledExceptionHook { throwable ->
        EvolaLog.d("crash", "uncaught: ${throwable.stackTraceToString()}")
    }
}
