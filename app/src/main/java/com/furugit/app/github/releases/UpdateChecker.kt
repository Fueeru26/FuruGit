package com.furugit.app.github.releases

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.furugit.app.R
import com.furugit.app.core.common.L
import com.furugit.app.core.common.tr
import com.furugit.app.data.preferences.Prefs
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Memeriksa https://github.com/Fueeru26/FuruGit/releases untuk versi yang lebih baru dari yang terpasang
 * (mekanisme sama dengan FuruArc). APK rilis baru bisa diunduh di dalam aplikasi ([UpdateInstaller]);
 * pemasangan selalu butuh persetujuan pengguna.
 */
object UpdateChecker {
    const val RELEASES_URL = "https://github.com/${Updater.REPO}/releases"
    private const val API_URL = "https://api.github.com/repos/${Updater.REPO}/releases?per_page=10"
    internal const val NOTIF_CHANNEL = "furugit_update"
    private const val NOTIF_ID = 7301
    private const val AUTO_INTERVAL_MS = 6L * 60 * 60 * 1000

    private val listeners = Listeners()

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)

    @Volatile
    var state: UpdateState = UpdateState.Idle
        private set

    /** Diisi saat pemeriksaan otomatis menemukan versi baru yang belum diumumkan: aplikasi menampilkan jendela sekali per versi. */
    @Volatile
    var announce: UpdateInfo? = null
        set(v) {
            field = v
            listeners.fire()
        }

    val available: Boolean get() = state is UpdateState.Available

    @Volatile
    private var running = false

    /** Pemeriksaan manual diminta saat pemeriksaan otomatis masih berjalan: hasilnya ditampilkan (tidak senyap). */
    @Volatile
    private var manualPending = false

    /** "v1.06" vs "1.5.2": bandingkan angkanya; true bila [latest] lebih baru dari [current]. */
    fun isNewer(latest: String, current: String): Boolean {
        fun nums(v: String) = Regex("\\d+").findAll(v).map { it.value.toIntOrNull() ?: 0 }.toList()
        val a = nums(latest)
        val b = nums(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Pemeriksaan manual (menu): selalu bertanya ke GitHub dan menampilkan hasilnya di [state]. */
    fun check(ctx: Context) = start(ctx.applicationContext, manual = true)

    /** Pemeriksaan otomatis saat aplikasi dibuka: senyap, paling sering tiap 6 jam, dan tidak melakukan apa pun tanpa koneksi. */
    fun autoCheck(ctx: Context) {
        val c = ctx.applicationContext
        val sp = c.getSharedPreferences("update", Context.MODE_PRIVATE)
        val last = sp.getLong("last", 0L)
        if (System.currentTimeMillis() - last < AUTO_INTERVAL_MS && state !is UpdateState.Idle) return
        start(c, manual = false)
    }

    private fun setState(s: UpdateState) {
        state = s
        listeners.fire()
    }

    private fun start(c: Context, manual: Boolean) {
        if (running) {
            if (manual) {
                manualPending = true
                setState(UpdateState.Checking)
            }
            return
        }
        running = true
        if (manual) setState(UpdateState.Checking)
        Thread {
            val r = try {
                fetch(c)
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
            val sp = c.getSharedPreferences("update", Context.MODE_PRIVATE)
            Handler(Looper.getMainLooper()).post {
                running = false
                val man = manual || manualPending
                manualPending = false
                if (r is UpdateState.Failed && !man) return@post // senyap: offline dsb.
                setState(r)
                sp.edit().putLong("last", System.currentTimeMillis()).apply()
                if (!man && r is UpdateState.Available && sp.getString("announced", "") != r.info.tag) {
                    sp.edit().putString("announced", r.info.tag).apply()
                    announce = r.info
                    notifyAvailable(c, r.info)
                }
            }
        }.start()
    }

    internal fun notifAllowed(c: Context): Boolean {
        if (!Prefs.downloadNotify) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(c).areNotificationsEnabled()
    }

    internal fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(NOTIF_CHANNEL, "FuruGit updates", NotificationManager.IMPORTANCE_DEFAULT)
            c.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    /**
     * Notifikasi sistem untuk versi baru (sekali per versi). Mengikuti pengaturan "Aktifkan Notifikasi";
     * tanpa izin tidak ada yang ditampilkan.
     */
    private fun notifyAvailable(c: Context, info: UpdateInfo) {
        try {
            if (!notifAllowed(c)) return
            ensureChannel(c)
            val open = Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(c, NOTIF_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(tr("Versi baru tersedia", "A new version is available"))
                .setContentText(info.tag + " · " + tr("Ketuk untuk membuka halaman rilis.", "Tap to open the release page."))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            NotificationManagerCompat.from(c).notify(NOTIF_ID, n)
        } catch (e: Exception) {
            // notifikasi hanya tambahan
        }
    }

    private fun fetch(c: Context): UpdateState {
        val con = URL(API_URL).openConnection() as HttpURLConnection
        try {
            con.connectTimeout = 8000
            con.readTimeout = 8000
            con.setRequestProperty("Accept", "application/vnd.github+json")
            con.setRequestProperty("User-Agent", "FuruGit")
            val code = con.responseCode
            if (code == 404) return UpdateState.NoRelease
            if (code != 200) return UpdateState.Failed("HTTP $code")
            // Endpoint "latest" melewati pra-rilis, jadi baca daftar rilis dan ambil versi tertinggi
            // di antara rilis yang sudah terbit (bukan draft), pra-rilis termasuk.
            val list = JSONArray(con.inputStream.bufferedReader().use { it.readText() })
            var j: JSONObject? = null
            for (i in 0 until list.length()) {
                val r = list.getJSONObject(i)
                if (r.optBoolean("draft") || r.optString("tag_name").isEmpty()) continue
                if (j == null || isNewer(r.optString("tag_name"), j.optString("tag_name"))) j = r
            }
            if (j == null) return UpdateState.NoRelease
            val tag = j.optString("tag_name")
            var apk: String? = null
            var size = 0L
            val assets = j.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
            val info = UpdateInfo(tag, j.optString("name").ifEmpty { tag }, j.optString("body"), j.optString("html_url").ifEmpty { RELEASES_URL }, apk, size)
            return if (isNewer(tag, Updater.current(c))) UpdateState.Available(info) else UpdateState.UpToDate
        } finally {
            con.disconnect()
        }
    }
}
