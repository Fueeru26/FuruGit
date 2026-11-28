package com.furugit.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ekspor / impor data aplikasi (pengaturan, daftar repository + repo aktif, folder output, dan akun GitHub) ke satu file
 * agar tidak perlu mengatur ulang setelah aplikasi dihapus dan dipasang lagi. Karena berisi token GitHub, file dienkripsi
 * AES-256-GCM dengan kunci dari sandi (PBKDF2-HMAC-SHA256, 120.000 putaran).
 */
object Backup {
    private const val MAGIC = "FGB1"
    private const val VERSION = 1
    private val STORES = listOf("furugit", "furugit_repos")

    private fun sp(name: String) = com.furugit.app.App.instance.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun dump(name: String): JSONArray {
        val arr = JSONArray()
        for ((k, v) in sp(name).all) {
            val o = JSONObject().put("k", k)
            val ok = when (v) {
                is String -> { o.put("t", "s").put("v", v); true }
                is Boolean -> { o.put("t", "b").put("v", v); true }
                is Int -> { o.put("t", "i").put("v", v); true }
                is Long -> { o.put("t", "l").put("v", v); true }
                is Float -> { o.put("t", "f").put("v", v.toDouble()); true }
                is Set<*> -> { o.put("t", "S").put("v", JSONArray(v.map { it.toString() })); true }
                else -> false
            }
            if (ok) arr.put(o)
        }
        return arr
    }

    private fun restore(name: String, arr: JSONArray) {
        val ed = sp(name).edit().clear()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val k = o.getString("k")
            when (o.getString("t")) {
                "s" -> ed.putString(k, o.getString("v"))
                "b" -> ed.putBoolean(k, o.getBoolean("v"))
                "i" -> ed.putInt(k, o.getInt("v"))
                "l" -> ed.putLong(k, o.getLong("v"))
                "f" -> ed.putFloat(k, o.getDouble("v").toFloat())
                "S" -> {
                    val a = o.getJSONArray("v")
                    ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
            }
        }
        ed.commit()
    }

    /** Isi cadangan (JSON biasa, belum dienkripsi). */
    fun build(): String {
        val root = JSONObject().put("app", "FuruGit").put("version", VERSION).put("created", System.currentTimeMillis())
        val prefs = JSONObject()
        for (s in STORES) prefs.put(s, dump(s))
        root.put("prefs", prefs)
        val acc = JSONArray()
        for (a in AuthStore.accounts()) {
            acc.put(JSONObject().put("host", a.host).put("user", a.username).put("token", a.token).put("type", a.type))
        }
        root.put("accounts", acc)
        return root.toString()
    }

    /** Menimpa pengaturan, daftar repo, dan akun GitHub saat ini dengan isi [json]. */
    fun apply(json: String) {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw GitUserError("Isi cadangan tidak valid.")
        }
        if (root.optString("app") != "FuruGit") throw GitUserError("Berkas ini bukan cadangan data FuruGit.")
        val prefs = root.optJSONObject("prefs") ?: throw GitUserError("Isi cadangan tidak lengkap.")
        for (s in STORES) prefs.optJSONArray(s)?.let { restore(s, it) }
        for (a in AuthStore.accounts()) AuthStore.remove(a.host)
        val acc = root.optJSONArray("accounts") ?: JSONArray()
        for (i in 0 until acc.length()) {
            val o = acc.getJSONObject(i)
            AuthStore.save(Account(o.getString("host"), o.getString("user"), o.getString("token"), o.getString("type")))
        }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 120_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16)
        val iv = ByteArray(12)
        rnd.nextBytes(salt)
        rnd.nextBytes(iv)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return MAGIC.toByteArray() + salt + iv + c.doFinal(plain)
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (data.size < 4 + 16 + 12 + 16 || String(data, 0, 4) != MAGIC) throw GitUserError("Berkas ini bukan cadangan data FuruGit.")
        val salt = data.copyOfRange(4, 20)
        val iv = data.copyOfRange(20, 32)
        val ct = data.copyOfRange(32, data.size)
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            return c.doFinal(ct)
        } catch (e: Exception) {
            throw GitUserError("Sandi salah atau berkas cadangan rusak.")
        }
    }
}
