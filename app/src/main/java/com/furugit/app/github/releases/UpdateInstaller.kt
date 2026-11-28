package com.furugit.app.github.releases

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.furugit.app.R
import com.furugit.app.core.common.L
import com.furugit.app.core.common.tr
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
