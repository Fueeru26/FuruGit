package com.furugit.app.github.auth

import android.content.SharedPreferences
import com.furugit.app.core.security.SecurePrefs
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.json.JSONArray
import org.json.JSONObject

/** Token disimpan terenkripsi (Android Keystore lewat EncryptedSharedPreferences). */
object AuthStore {
    private val prefs: SharedPreferences by lazy { SecurePrefs.open("furugit_secure") }

    fun accounts(): List<Account> {
        val arr = try {
            JSONArray(prefs.getString("accounts", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<Account>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Account(o.getString("host"), o.getString("user"), o.getString("token"), o.getString("type")))
        }
        return out
    }

    private fun persist(items: List<Account>) {
        val arr = JSONArray()
        for (a in items) {
            arr.put(JSONObject().put("host", a.host).put("user", a.username).put("token", a.token).put("type", a.type))
        }
        prefs.edit().putString("accounts", arr.toString()).apply()
    }

    /** Satu akun per host; menyimpan akun baru untuk host yang sama akan menggantikannya. */
    fun save(a: Account) {
        persist(accounts().filter { !it.host.equals(a.host, true) } + a)
    }

    fun remove(host: String) {
        persist(accounts().filter { !it.host.equals(host, true) })
    }

    fun hostOf(url: String): String? = try {
        URIish(url).host
    } catch (e: Exception) {
        null
    }

    fun accountFor(url: String): Account? {
        val host = hostOf(url) ?: return null
        return accounts().firstOrNull { it.host.equals(host, true) }
    }

    fun providerFor(url: String): CredentialsProvider? {
        val acc = accountFor(url) ?: return null
        return UsernamePasswordCredentialsProvider(acc.username.ifBlank { "git" }, acc.token)
    }
}
