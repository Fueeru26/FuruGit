package com.furugit.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.furugit.app.App
import com.furugit.app.git.repository.RepoEntry
import org.json.JSONArray
import org.json.JSONObject

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

    /** Dipanggil setiap daftar repo atau repo aktif berubah (MainActivity memakainya untuk menandai tab usang). */
    var onChanged: (() -> Unit)? = null

    private fun save(items: List<RepoEntry>) {
        val arr = JSONArray()
        for (e in items) arr.put(JSONObject().put("name", e.name).put("path", e.path))
        sp.edit().putString("repos", arr.toString()).apply()
        onChanged?.invoke()
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
            onChanged?.invoke()
        }

    fun active(): RepoEntry? = list().firstOrNull { it.path == activePath }
}
