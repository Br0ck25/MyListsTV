package tv.myliststv.launcher.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted backup of the whole setup. Everything is encrypted here with a recovery code only the owner holds;
 * My Lists stores an opaque blob under a hash of that code and cannot read it.
 */
object Backup {
    private const val SERVER = "https://mylistsaddon.com/api/tv-backup"
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"        // no 0/O or 1/I
    private val shape = Regex("^[A-Z2-9]{4}(-[A-Z2-9]{4}){3}$")

    fun isCode(s: String) = shape.matches(clean(s))
    fun clean(s: String) = s.trim().uppercase().replace(" ", "")

    /** 16 characters from a 32-letter alphabet: 80 bits, shown as XXXX-XXXX-XXXX-XXXX. */
    fun newCode(): String {
        val r = SecureRandom()
        return (0 until 16).map { ALPHABET[r.nextInt(ALPHABET.length)] }.chunked(4).joinToString("-") { it.joinToString("") }
    }

    /** The server never sees the code, only this hash of it. */
    fun idOf(code: String): String = MessageDigest.getInstance("SHA-256").digest(("id:" + clean(code)).toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    private fun key(code: String, salt: ByteArray, sha256: Boolean): SecretKeySpec {
        val algo = if (sha256) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val raw = SecretKeyFactory.getInstance(algo).generateSecret(PBEKeySpec(clean(code).toCharArray(), salt, 100_000, 256)).encoded
        return SecretKeySpec(raw, "AES")
    }

    /** Blob = base64( format byte, 16-byte salt, 12-byte iv, AES-256-GCM ciphertext ). Format 1 uses SHA-256, 2 is the fallback for old Android. */
    fun encrypt(plain: String, code: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }; val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        var fmt = 1
        val k = try { key(code, salt, true) } catch (_: Exception) { fmt = 2; key(code, salt, false) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv)) }
        return Base64.encodeToString(byteArrayOf(fmt.toByte()) + salt + iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    /** Null when the code is wrong or the blob was changed: GCM checks both. */
    fun decrypt(blob: String, code: String): String? = try {
        val b = Base64.decode(blob.trim(), Base64.NO_WRAP)
        val salt = b.copyOfRange(1, 17); val iv = b.copyOfRange(17, 29)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(code, salt, b[0].toInt() == 1), GCMParameterSpec(128, iv)) }
        String(c.doFinal(b, 29, b.size - 29))
    } catch (_: Exception) { null }

    /** What a backup holds for the profile in use: its addons (with their keys), Library and account. The main profile's backup also holds the profile list and the box settings. Not caches, not watch history. */
    fun snapshot(): String = JSONObject().apply {
        put("v", 1)
        put("addons", JSONArray().also { a -> AddonStore.saved().forEach { a.put(JSONObject().put("url", it.url).put("on", it.enabled)) } })
        put("account", Prefs.accountLink)
        put("library", Library.export())
        if (Profiles.activeId == Profiles.all().first().id) {
            put("profiles", JSONArray().also { a -> Profiles.all().forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("kids", it.kids).put("avatar", it.avatar)) } })
            put("settings", Prefs.export())
        }
    }.toString()

    /** Returns a short summary of what was restored, or null if the data is not a backup. Goes to the profile in use. */
    fun apply(c: Context, json: String): String? = try {
        val o = JSONObject(json)
        if (o.optInt("v") != 1) null else {
            val addons = o.getJSONArray("addons")
            AddonStore.restore((0 until addons.length()).map { addons.getJSONObject(it).let { a -> AddonStore.Saved(a.getString("url"), a.optBoolean("on", true)) } })
            o.optJSONArray("profiles")?.let { ps -> Profiles.restore((0 until ps.length()).map { ps.getJSONObject(it).let { p -> Profiles.P(p.getString("id"), p.getString("name"), p.optBoolean("kids"), p.optString("avatar")) } }, Profiles.activeId) }
            Prefs.accountLink = o.optString("account"); o.optJSONObject("settings")?.let { Prefs.import(it) }; Library.import(o.opt("library")); WatchStore.init(c); AddonStore.needsRefresh = true
            "${addons.length()} addons" + (if (o.has("settings")) ", the profiles and your settings" else "") + " for ${Profiles.active().name}"
        }
    } catch (_: Exception) { null }

    // ---- network (call from the UI; replies arrive on the main thread) ----

    /** The code for this box, made on first use. Kept in app-private storage so later backups need no typing. */
    fun codeForThisBox(): String = Prefs.backupCode.ifEmpty { newCode().also { Prefs.backupCode = it } }

    fun upload(code: String, reply: (String) -> Unit) = Http.background {
        val msg = try {
            val status = Http.put("$SERVER?id=${idOf(code)}", encrypt(snapshot(), code))
            if (status in 200..299) { Prefs.lastBackup = System.currentTimeMillis(); "Backed up." } else "The server refused the backup (HTTP $status)."
        } catch (e: Exception) { "Could not reach My Lists: ${e.message}" }
        Http.post { reply(msg) }
    }

    fun restore(c: Context, code: String, reply: (String) -> Unit) = Http.background {
        val msg = try {
            val blob = Http.fetchBlocking("$SERVER?id=${idOf(code)}", 10_000, 15_000)
            val plain = decrypt(blob, code)
            if (plain == null) "That code does not open this backup."
            else apply(c, plain)?.let { Prefs.backupCode = clean(code); "Restored $it." } ?: "That backup is not readable."
        } catch (e: Exception) { if (e.message?.contains("404") == true) "No backup found for that code." else "Could not reach My Lists: ${e.message}" }
        Http.post { reply(msg) }
    }

    /** After a change, refresh the online backup if this box already has a code. */
    fun auto() { if (Prefs.backupCode.isNotEmpty()) upload(Prefs.backupCode) { } }
}
