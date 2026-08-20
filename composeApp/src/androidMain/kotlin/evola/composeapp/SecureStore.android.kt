package evola.composeapp

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import evola.shared.core.analytics.EvolaLog

private const val PREFS_NAME = "evola_secure"

/**
 * Android secure storage over [EncryptedSharedPreferences]. Hardened against the well-known failure
 * mode where the encrypted store becomes undecryptable after a keystore reset / app reinstall:
 * `create()` (and reads) can throw `AEADBadTagException`/`InvalidProtocolBufferException`. Rather
 * than crash on launch or get permanently stuck, we drop the corrupt store and rebuild an empty one
 * so the user can simply re-enter their key. Every operation also fails soft so a transient keystore
 * error never takes down the app.
 */
class AndroidSecureStore(private val context: Context) : SecureStore {

    private val prefs: SharedPreferences = createEncryptedPrefs(recreateOnFailure = true)

    private fun createEncryptedPrefs(recreateOnFailure: Boolean): SharedPreferences =
        try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            if (!recreateOnFailure) throw e
            // The existing encrypted prefs can't be opened (keystore reset / reinstall). Wipe the
            // corrupt file and rebuild — the stored key is lost, but the app stays usable and the
            // user can re-enter it in Profile.
            EvolaLog.d("securestore", "encrypted prefs unreadable, recreating: ${e::class.simpleName}")
            context.deleteSharedPreferences(PREFS_NAME)
            createEncryptedPrefs(recreateOnFailure = false)
        }

    override fun get(key: String): String? =
        runCatching { prefs.getString(key, null) }.getOrElse {
            EvolaLog.d("securestore", "get failed: ${it::class.simpleName}")
            null
        }

    override fun put(key: String, value: String) {
        runCatching { prefs.edit().putString(key, value).commit() }
            .onFailure { EvolaLog.d("securestore", "put failed: ${it::class.simpleName}") }
    }

    override fun remove(key: String) {
        runCatching { prefs.edit().remove(key).commit() }
            .onFailure { EvolaLog.d("securestore", "remove failed: ${it::class.simpleName}") }
    }
}

@Composable
actual fun rememberSecureStore(): SecureStore {
    val context = LocalContext.current
    return remember { AndroidSecureStore(context.applicationContext) }
}
