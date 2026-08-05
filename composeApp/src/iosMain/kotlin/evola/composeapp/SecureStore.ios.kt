package evola.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

class IosSecureStore : SecureStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun get(key: String): String? = defaults.stringForKey(key)
    override fun put(key: String, value: String) { defaults.setObject(value, key) }
    override fun remove(key: String) { defaults.removeObjectForKey(key) }
}

@Composable
actual fun rememberSecureStore(): SecureStore = remember { IosSecureStore() }
