package evola.composeapp

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import evola.shared.core.analytics.EvolaLog
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val PREFS_NAME = "evola_secure"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "evola_secure_store_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val GCM_IV_LENGTH_BYTES = 12

/**
 * Android secure storage over a plain [SharedPreferences] file whose values are AES/GCM-encrypted
 * with a key that never leaves the Android Keystore. Rolls its own encryption directly on top of
 * `android.security.keystore` (the same layer Jetpack Security's now-deprecated
 * `EncryptedSharedPreferences`/`MasterKey` used to sit on) rather than depending on that deprecated
 * wrapper, since Google has not shipped a direct replacement for it yet.
 *
 * Hardened against the well-known failure mode where the Keystore key becomes unusable after a
 * keystore reset / app reinstall: key generation and decryption can throw
 * `KeyPermanentlyInvalidatedException`/`AEADBadTagException`/similar. Rather than crash on launch or
 * get permanently stuck, we drop the corrupt store and key and rebuild empty ones so the user can
 * simply re-enter their key. Every operation also fails soft so a transient keystore error never
 * takes down the app.
 */
class AndroidSecureStore(private val context: Context) : SecureStore {

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    private val prefs: SharedPreferences = createStore(recreateOnFailure = true)

    private fun createStore(recreateOnFailure: Boolean): SharedPreferences =
        try {
            secretKey()
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            if (!recreateOnFailure) throw e
            // The Keystore key can't be loaded/used (keystore reset / reinstall). Wipe the corrupt
            // key and prefs file and rebuild — the stored value is lost, but the app stays usable
            // and the user can re-enter it in Profile.
            EvolaLog.d("securestore", "keystore key unusable, recreating: ${e::class.simpleName}")
            runCatching { keyStore.deleteEntry(KEY_ALIAS) }
            context.deleteSharedPreferences(PREFS_NAME)
            createStore(recreateOnFailure = false)
        }

    private fun secretKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val ciphertext = cipher.doFinal(value.encodeToByteArray())
        // iv is not secret; prepending it lets decrypt() recover it without a separate field.
        val ivAndCiphertext = cipher.iv + ciphertext
        return Base64.encodeToString(ivAndCiphertext, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val ivAndCiphertext = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = ivAndCiphertext.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = ivAndCiphertext.copyOfRange(GCM_IV_LENGTH_BYTES, ivAndCiphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        return cipher.doFinal(ciphertext).decodeToString()
    }

    override fun get(key: String): String? =
        runCatching { prefs.getString(key, null)?.let(::decrypt) }.getOrElse {
            EvolaLog.d("securestore", "get failed: ${it::class.simpleName}")
            null
        }

    override fun put(key: String, value: String) {
        runCatching { prefs.edit().putString(key, encrypt(value)).commit() }
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
