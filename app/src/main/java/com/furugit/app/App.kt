package com.furugit.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.furugit.app.core.common.ActivityLog
import com.furugit.app.core.common.L
import com.furugit.app.data.preferences.Prefs
import org.eclipse.jgit.util.FS

class App : Application() {

    companion object {
        lateinit var instance: App
            private set
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashHandler.install(base)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Proses ":crash" hanya untuk menampilkan layar error; jangan jalankan init aplikasi.
        if (CrashHandler.isCrashProcess()) return
        com.furugit.app.core.common.L.reload()
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            when (com.furugit.app.data.preferences.Prefs.themeMode) {
                1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        // JGit membaca konfigurasi dari "user.home"; di Android nilainya tidak ada.
        System.setProperty("user.home", filesDir.absolutePath)
        try {
            FS.DETECTED.setUserHome(filesDir)
        } catch (_: Throwable) {
        }
        ActivityLog.add("FuruGit dimulai")
    }
}
