package evola.composeapp

// The iOS Simulator shares the host machine's network stack directly.
actual fun defaultServerBaseUrl(): String = "http://127.0.0.1:8081"
