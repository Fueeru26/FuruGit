package com.furugit.app.data

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
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.furugit.app.ui.tr
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Rilis GitHub terbaru: [apkUrl] = unduhan langsung APK pertama di rilis (null bila rilis tidak punya APK). */
class UpdateInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSize: Long
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()

    /** Repo belum punya rilis (HTTP 404). */
    object NoRelease : UpdateState()
    class Available(val info: UpdateInfo) : UpdateState()
    class Failed(val reason: String) : UpdateState()
}

/** Info dasar aplikasi / repo. */
object Updater {
    const val REPO = "Fueeru26/FuruGit"
    const val REPO_URL = "https://github.com/Fueeru26/FuruGit"

    fun current(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /** Buka installer sistem. Android meminta izin "pasang dari sumber ini" pada pemasangan pertama. */
    fun install(ctx: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        ctx.startActivity(UpdateInstaller.installIntent(ctx, file))
    }
}

/** Pengamat perubahan status: dipanggil di thread utama. */
private class Listeners {
    private val list = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun add(l: () -> Unit) {
        if (!list.contains(l)) list.add(l)
    }

    fun remove(l: () -> Unit) {
        list.remove(l)
    }

    fun fire() {
        val run = { for (l in ArrayList(list)) l() }
        if (Looper.myLooper() === Looper.getMainLooper()) run() else main.post(run)
    }
}

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

sealed class DownloadState {
    object Idle : DownloadState()
    class Running(val done: Long, val total: Long) : DownloadState()
    class Done(val file: File) : DownloadState()
    class Failed(val reason: String) : DownloadState()
}

/**
 * Mengunduh APK rilis GitHub ke cache aplikasi lalu menyerahkannya ke installer paket sistem.
 * Progres tampil di aplikasi dan di notifikasi; pemasangan selalu butuh persetujuan pengguna.
 */
object UpdateInstaller {
    private const val NOTIF_ID = 7302

    private val listeners = Listeners()

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)

    @Volatile
    var state: DownloadState = DownloadState.Idle
        private set

    @Volatile
    private var cancelled = false

    @Volatile
    private var running = false

    private fun setState(s: DownloadState) {
        state = s
        listeners.fire()
    }

    fun start(ctx: Context, info: UpdateInfo) {
        val apk = info.apkUrl ?: return
        if (running) return
        running = true
        cancelled = false
        val app = ctx.applicationContext
        setState(DownloadState.Running(0, info.apkSize))
        Thread {
            try {
                val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val part = File(dir, "FuruGit-${info.tag}.apk.part")
                val out = File(dir, "FuruGit-${info.tag}.apk")
                download(app, apk, info, part)
                if (cancelled) {
                    part.delete()
                    setState(DownloadState.Idle)
                    cancelNotification(app)
                    return@Thread
                }
                if (info.apkSize > 0 && part.length() != info.apkSize) {
                    part.delete()
                    throw java.io.IOException("size mismatch")
                }
                if (!part.renameTo(out)) throw java.io.IOException("rename failed")
                setState(DownloadState.Done(out))
                notifyDone(app, out, info)
            } catch (e: Exception) {
                setState(DownloadState.Failed(e.message ?: e.javaClass.simpleName))
                cancelNotification(app)
            } finally {
                running = false
            }
        }.start()
    }

    fun cancel() {
        cancelled = true
    }

    fun reset(ctx: Context) {
        if (!running) {
            setState(DownloadState.Idle)
            cancelNotification(ctx.applicationContext)
        }
    }

    private fun download(app: Context, url0: String, info: UpdateInfo, target: File) {
        // GitHub membalas dengan pengalihan ke CDN-nya; ikuti manual (paling banyak 5 kali)
        var url = URL(url0)
        var con: HttpURLConnection
        var hops = 0
        while (true) {
            con = url.openConnection() as HttpURLConnection
            con.instanceFollowRedirects = false
            con.connectTimeout = 10000
            con.readTimeout = 15000
            con.setRequestProperty("User-Agent", "FuruGit")
            val code = con.responseCode
            if (code in 300..399 && hops < 5) {
                val loc = con.getHeaderField("Location") ?: throw java.io.IOException("redirect without location")
                con.disconnect()
                url = URL(url, loc)
                hops++
                continue
            }
            if (code != 200) {
                con.disconnect()
                throw java.io.IOException("HTTP $code")
            }
            break
        }
        try {
            val total = con.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
            var done = 0L
            var lastUi = 0L
            con.inputStream.use { ins ->
                target.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled) return
                        val n = ins.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastUi > 250) {
                            lastUi = now
                            setState(DownloadState.Running(done, total))
                            notifyProgress(app, info, done, total)
                        }
                    }
                }
            }
        } finally {
            con.disconnect()
        }
    }

    internal fun installIntent(ctx: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // ---- notifikasi (hanya bila diizinkan) ----

    private fun builder(app: Context): NotificationCompat.Builder? {
        if (!UpdateChecker.notifAllowed(app)) return null
        UpdateChecker.ensureChannel(app)
        return NotificationCompat.Builder(app, UpdateChecker.NOTIF_CHANNEL)
    }

    private fun notifyProgress(app: Context, info: UpdateInfo, done: Long, total: Long) {
        try {
            val b = builder(app) ?: return
            b.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(tr("Mengunduh pembaruan", "Downloading update"))
                .setContentText(info.tag)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
            if (total > 0) b.setProgress(100, (done * 100 / total).toInt().coerceIn(0, 100), false) else b.setProgress(0, 0, true)
            NotificationManagerCompat.from(app).notify(NOTIF_ID, b.build())
        } catch (e: Exception) {
            // notifikasi hanya tambahan
        }
    }

    private fun notifyDone(app: Context, file: File, info: UpdateInfo) {
        try {
            val b = builder(app) ?: return
            val pi = PendingIntent.getActivity(app, 2, installIntent(app, file), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(tr("Unduhan selesai", "Download complete"))
                .setContentText(info.tag + " · " + tr("Ketuk untuk memasang.", "Tap to install."))
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(pi)
            NotificationManagerCompat.from(app).notify(NOTIF_ID, b.build())
        } catch (e: Exception) {
            // notifikasi hanya tambahan
        }
    }

    private fun cancelNotification(app: Context) {
        try {
            NotificationManagerCompat.from(app).cancel(NOTIF_ID)
        } catch (e: Exception) {
        }
    }
}
