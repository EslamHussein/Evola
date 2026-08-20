package evola.composeapp.core.network

import io.ktor.client.engine.HttpClientEngine

/** The platform HTTP engine for the shared client — OkHttp on Android, Darwin on iOS. Kept in
 * `:composeApp` (android + ios only) rather than `:shared`, whose jvm target is consumed by the
 * server and needs no client engine. */
expect fun platformHttpEngine(): HttpClientEngine
