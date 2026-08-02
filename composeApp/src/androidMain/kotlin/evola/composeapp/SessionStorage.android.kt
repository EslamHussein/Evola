package evola.composeapp

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_NAME = "evola_session"
private const val KEY_REFRESH_TOKEN = "refresh_token"

class AndroidSessionStorage(context: Context) : SessionStorage {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun saveRefreshToken(token: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    override fun loadRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    override fun clear() {
        prefs.edit().remove(KEY_REFRESH_TOKEN).apply()
    }
}

@Composable
actual fun rememberSessionStorage(): SessionStorage {
    val context = LocalContext.current
    return remember { AndroidSessionStorage(context.applicationContext) }
}
