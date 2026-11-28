package com.furugit.app.core.common

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.furugit.app.R
import com.furugit.app.data.preferences.Prefs

/** Notifikasi progres dan hasil unduhan; hanya aktif bila "Aktifkan Notifikasi" dinyalakan dan izin diberikan. */
object Notifier {
    private const val CHANNEL = "downloads"
    private const val ID = 4201
    private const val RUNS = "runs"

    private fun allowed(ctx: Context): Boolean {
        if (!Prefs.downloadNotify) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(RUNS) == null) {
                nm.createNotificationChannel(NotificationChannel(RUNS, tr("Run selesai", "Finished runs"), NotificationManager.IMPORTANCE_DEFAULT))
            }
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Unduhan", "Downloads"), NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    private fun openApp(ctx: Context): PendingIntent? {
        val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return null
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("MissingPermission")
    fun progress(ctx: Context, title: String, text: String, done: Long, total: Long) {
        if (!allowed(ctx)) return
        ensureChannel(ctx)
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
        if (total > 0) b.setProgress(1000, ((done * 1000) / total).toInt().coerceIn(0, 1000), false)
        else b.setProgress(0, 0, true)
        NotificationManagerCompat.from(ctx).notify(ID, b.build())
    }

    @SuppressLint("MissingPermission")
    fun finish(ctx: Context, ok: Boolean, title: String, text: String, cancelled: Boolean) {
        if (!allowed(ctx)) return
        if (cancelled) {
            NotificationManagerCompat.from(ctx).cancel(ID)
            return
        }
        ensureChannel(ctx)
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(if (ok) tr("Unduhan berhasil", "Download complete") else tr("Unduhan gagal", "Download failed"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setContentIntent(openApp(ctx))
        NotificationManagerCompat.from(ctx).notify(ID, b.build())
    }

    /** Pemberitahuan sebuah run GitHub Actions telah selesai. */
    @SuppressLint("MissingPermission")
    fun runDone(ctx: Context, title: String, detail: String, id: Int) {
        if (!allowed(ctx)) return
        ensureChannel(ctx)
        val b = NotificationCompat.Builder(ctx, RUNS)
            .setSmallIcon(R.drawable.ic_actions)
            .setContentTitle(title)
            .setContentText(detail)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
        NotificationManagerCompat.from(ctx).notify(5000 + (id and 0xFFFF), b.build())
    }
}
