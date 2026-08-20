package evola.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import evola.shared.core.analytics.EvolaLog
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS secure storage backed by the **Keychain** (generic-password items), not NSUserDefaults —
 * so the on-device Anthropic key is stored encrypted at rest, matching Android's
 * EncryptedSharedPreferences. Items are scoped to a fixed service name and made available after the
 * device's first unlock (`kSecAttrAccessibleAfterFirstUnlock`), never synced to iCloud.
 */
// The `as NSString`/`as NSData`/`as String?` casts below are Kotlin/Native's toll-free-bridging
// idiom - the compiler's static analysis of these interop types is inconsistent (some casts are
// flagged as never succeeding, others as useless) even though they're all required at compile time.
@Suppress("CAST_NEVER_SUCCEEDS", "USELESS_CAST")
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSecureStore(private val service: String = "evola_secure") : SecureStore {

    override fun get(key: String): String? {
        val query = KeychainQuery()
        query.set(kSecClass, kSecClassGenericPassword)
        query.setObject(kSecAttrService, service as NSString)
        query.setObject(kSecAttrAccount, key as NSString)
        query.set(kSecReturnData, kCFBooleanTrue)
        query.set(kSecMatchLimit, kSecMatchLimitOne)
        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.dict, result.ptr)
            query.release()
            if (status == errSecSuccess) {
                val data = CFBridgingRelease(result.value) as? NSData
                data?.let { NSString.create(it, NSUTF8StringEncoding) as String? }
            } else {
                // errSecItemNotFound is the normal "no key set yet" case, not a failure.
                if (status != errSecItemNotFound) EvolaLog.d("securestore", "get failed: status=$status")
                null
            }
        }
    }

    override fun put(key: String, value: String) {
        // Keychain add fails on a duplicate; delete-then-add keeps this a simple upsert.
        remove(key)
        val query = KeychainQuery()
        query.set(kSecClass, kSecClassGenericPassword)
        query.setObject(kSecAttrService, service as NSString)
        query.setObject(kSecAttrAccount, key as NSString)
        query.setObject(kSecValueData, (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)!!)
        query.set(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
        val status = SecItemAdd(query.dict, null)
        query.release()
        if (status != errSecSuccess) EvolaLog.d("securestore", "put failed: status=$status")
    }

    override fun remove(key: String) {
        val query = KeychainQuery()
        query.set(kSecClass, kSecClassGenericPassword)
        query.setObject(kSecAttrService, service as NSString)
        query.setObject(kSecAttrAccount, key as NSString)
        val status = SecItemDelete(query.dict)
        query.release()
        if (status != errSecSuccess && status != errSecItemNotFound) EvolaLog.d("securestore", "remove failed: status=$status")
    }
}

/** Thin builder over a mutable CFDictionary, tracking the CF references it retains so they're all
 * released together in [release] — avoids leaking the bridged NSString/NSData query values. */
@OptIn(ExperimentalForeignApi::class)
private class KeychainQuery {
    private val retained = mutableListOf<CFTypeRef?>()
    val dict = CFDictionaryCreateMutable(
        null,
        0,
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
    )!!

    fun set(key: CFStringRef?, value: CFTypeRef?) {
        CFDictionaryAddValue(dict, key, value)
    }

    fun setObject(key: CFStringRef?, obj: Any?) {
        val ref = CFBridgingRetain(obj)
        retained.add(ref)
        CFDictionaryAddValue(dict, key, ref)
    }

    fun release() {
        CFRelease(dict)
        retained.forEach { ref -> if (ref != null) CFBridgingRelease(ref) }
    }
}

@Composable
actual fun rememberSecureStore(): SecureStore = remember { IosSecureStore() }
