package tv.myliststv.launcher.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps secrets (addon links that carry debrid keys, IPTV logins, the backup recovery code) with a key that lives in the
 * Android Keystore and never leaves it, so a copy of the app's files cannot be read elsewhere.
 * A stored value starts with "enc1:" when wrapped. A plain value (from before this existed, or on a box whose Keystore
 * does not work) still reads fine and is wrapped the next time it is saved.
 */
object Secure {
    private const val ALIAS = "myliststv_secrets"
    private const val PREFIX = "enc1:"

    /** Where the key comes from. Replaced in tests, because there is no Keystore off a device. */
    internal var keyProvider: () -> SecretKey? = { keystoreKey() }

    private fun keystoreKey(): SecretKey? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    } catch (_: Throwable) { null }

    /** Encrypts [plain]. If anything about the Keystore misbehaves (some cheap boxes have broken ones), returns it unchanged rather than risk losing it. */
    fun wrap(plain: String): String {
        if (plain.isEmpty()) return plain
        return try {
            val key = keyProvider() ?: return plain
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
            val stored = PREFIX + Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
            if (unwrap(stored) == plain) stored else plain       // only keep it wrapped if it demonstrably reads back
        } catch (_: Throwable) { plain }
    }

    /** True for a stored value that should be wrapped but is not yet. Lets start-up skip the Keystore when everything is already wrapped. */
    fun needsWrap(stored: String?) = !stored.isNullOrEmpty() && !stored.startsWith(PREFIX)

    /** The original text, or null if a wrapped value cannot be opened (key lost). */
    fun unwrap(stored: String): String? {
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val b = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, keyProvider() ?: return null, GCMParameterSpec(128, b.copyOfRange(0, 12))) }
            String(c.doFinal(b, 12, b.size - 12))
        } catch (_: Throwable) { null }
    }
}
