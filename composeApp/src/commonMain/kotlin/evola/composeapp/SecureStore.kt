package evola.composeapp

import androidx.compose.runtime.Composable

/** Small encrypted key/value store for on-device secrets — currently the user's Anthropic API key,
 * which the serverless app uses to call Claude directly. Separate from [SessionStorage] (which is
 * being retired with auth). Android: AES/GCM via an Android Keystore-backed key over plain
 * SharedPreferences; iOS: NSUserDefaults for now (Keychain deferred, matching the existing iOS
 * session-storage choice). */
interface SecureStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"

@Composable
expect fun rememberSecureStore(): SecureStore
