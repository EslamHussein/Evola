package evola.shared.core

/**
 * Minimal cross-platform logging via stdout. On Android this surfaces in logcat (tag `System.out`,
 * level I); on iOS it prints to the device console. Grep for the `EVOLA/` prefix. Deliberately
 * dependency-free — enough to diagnose on-device extraction/AI failures without a logging library.
 */
object EvolaLog {
    fun d(area: String, message: String) {
        println("EVOLA/$area: $message")
    }
}
