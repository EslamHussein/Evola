package evola.composeapp.core.analytics

/** Installs a platform uncaught-exception hook that writes the crash to [evola.shared.core.analytics.EvolaLog]
 * before the process dies, so a crash leaves a readable stack trace in the log file instead of just
 * disappearing. Call once, as early as possible in the platform entry point (before Compose starts). */
expect fun installCrashLogging()
