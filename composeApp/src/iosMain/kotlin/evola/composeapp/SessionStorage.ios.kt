package evola.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

private const val KEY_REFRESH_TOKEN = "refresh_token"

/**
 * NSUserDefaults, not Keychain - a real Keychain-backed implementation is deferred until iOS
 * verification resumes (see project plan); this is enough to keep the multiplatform build
 * working and to prove the auto-login flow, just not yet the right long-term storage for iOS.
 */
class IosSessionStorage : SessionStorage {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun saveRefreshToken(token: String) {
        defaults.setObject(token, KEY_REFRESH_TOKEN)
    }

    override fun loadRefreshToken(): String? = defaults.stringForKey(KEY_REFRESH_TOKEN)

    override fun clear() {
        defaults.removeObjectForKey(KEY_REFRESH_TOKEN)
    }
}

@Composable
actual fun rememberSessionStorage(): SessionStorage = remember { IosSessionStorage() }
