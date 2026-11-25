package com.furugit.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.furugit.app.App
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    private val sp: SharedPreferences
        get() = App.instance.getSharedPreferences("furugit", Context.MODE_PRIVATE)

    var gitName: String
        get() = sp.getString("git_name", "") ?: ""
        set(v) {
            sp.edit().putString("git_name", v).apply()
        }

    var gitEmail: String
        get() = sp.getString("git_email", "") ?: ""
        set(v) {
            sp.edit().putString("git_email", v).apply()
        }

    var clientId: String
        get() = sp.getString("client_id", "") ?: ""
        set(v) {
            sp.edit().putString("client_id", v).apply()
        }

    var pullRebase: Boolean
        get() = sp.getBoolean("pull_rebase", false)
        set(v) {
            sp.edit().putBoolean("pull_rebase", v).apply()
        }

    /** 0 = ikuti sistem, 1 = terang, 2 = gelap */
    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(v) {
            sp.edit().putInt("theme_mode", v).apply()
        }

    /** "system", "en", atau "id" */
    var lang: String
        get() = sp.getString("lang", "system") ?: "system"
        set(v) {
            sp.edit().putString("lang", v).apply()
        }

    var lastParent: String
        get() = sp.getString("last_parent", "/storage/emulated/0") ?: "/storage/emulated/0"
        set(v) {
            sp.edit().putString("last_parent", v).apply()
        }
}

object RepoStore {
    private val sp: SharedPreferences
        get() = App.instance.getSharedPreferences("furugit_repos", Context.MODE_PRIVATE)

    fun list(): List<RepoEntry> {
        val arr = try {
            JSONArray(sp.getString("repos", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<RepoEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(RepoEntry(o.getString("name"), o.getString("path")))
        }
        return out
    }

    private fun save(items: List<RepoEntry>) {
        val arr = JSONArray()
        for (e in items) arr.put(JSONObject().put("name", e.name).put("path", e.path))
        sp.edit().putString("repos", arr.toString()).apply()
    }

    fun add(e: RepoEntry) {
        save(list().filter { it.path != e.path } + e)
        activePath = e.path
    }

    fun remove(path: String) {
        save(list().filter { it.path != path })
        if (activePath == path) activePath = null
    }

    var activePath: String?
        get() = sp.getString("active", null)
        set(v) {
            sp.edit().putString("active", v).apply()
        }

    fun active(): RepoEntry? = list().firstOrNull { it.path == activePath }
}

/** Token disimpan terenkripsi (Android Keystore lewat EncryptedSharedPreferences). */
object AuthStore {
    private val prefs: SharedPreferences by lazy {
        val ctx = App.instance
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx,
            "furugit_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

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

object ActivityLog {
    private val lines = ArrayList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun add(msg: String) {
        lines.add("[" + fmt.format(Date()) + "] " + msg)
        while (lines.size > 400) lines.removeAt(0)
    }

    @Synchronized
    fun all(): List<String> = ArrayList(lines)

    @Synchronized
    fun clear() {
        lines.clear()
    }
}
