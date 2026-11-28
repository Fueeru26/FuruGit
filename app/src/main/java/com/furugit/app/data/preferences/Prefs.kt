package com.furugit.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.furugit.app.App

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

    /** Folder tujuan unduhan artefak dan log (kosong = belum diatur). */
    var downloadDir: String
        get() = sp.getString("download_dir", "") ?: ""
        set(v) {
            sp.edit().putString("download_dir", v).apply()
        }

    /** Notifikasi progres dan hasil unduhan. */
    var downloadNotify: Boolean
        get() = sp.getBoolean("download_notify", false)
        set(v) {
            sp.edit().putBoolean("download_notify", v).apply()
        }

    /** Ekstrak artefak .zip otomatis ke folder bernama artefak (zip dihapus setelahnya). */
    var downloadExtract: Boolean
        get() = sp.getBoolean("download_extract", false)
        set(v) {
            sp.edit().putBoolean("download_extract", v).apply()
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
