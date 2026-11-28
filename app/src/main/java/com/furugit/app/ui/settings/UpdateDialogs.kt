package com.furugit.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.furugit.app.core.common.tr
import com.furugit.app.github.releases.DownloadState
import com.furugit.app.github.releases.UpdateChecker
import com.furugit.app.github.releases.UpdateInfo
import com.furugit.app.github.releases.UpdateInstaller
import com.furugit.app.github.releases.UpdateState
import com.furugit.app.github.releases.Updater
import com.furugit.app.ui.components.Dialogs
import com.furugit.app.ui.components.Fonts
import com.furugit.app.ui.components.FuruButton
import com.furugit.app.ui.components.MATCH
import com.furugit.app.ui.components.Pal
import com.furugit.app.ui.components.WRAP
import com.furugit.app.ui.components.button
import com.furugit.app.ui.components.dp
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Locale
import kotlin.math.max

/** ScrollView yang tingginya dibatasi [maxPx]. */
private class MaxHeightScroll(ctx: Context, private val maxPx: Int) : ScrollView(ctx) {
    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, android.view.View.MeasureSpec.makeMeasureSpec(maxPx, android.view.View.MeasureSpec.AT_MOST))
    }
}

/**
 * Jendela pembaruan seperti FuruArc (UpdateDialog dan UpdateAnnounceDialog): judul, isi sesuai keadaan
 * (memeriksa / terbaru / ada versi baru / mengunduh / selesai), dan tombol bertumpuk selebar jendela.
 */
object UpdateDialogs {

    private fun openUrl(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, url, Toast.LENGTH_LONG).show()
        }
    }

    private fun mb(b: Long): String = String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)

    /** Catatan rilis GitHub (Markdown) jadi teks biasa: tanpa tanda judul, tebal, dan strip daftar. */
    private fun cleanNotes(s: String): String = s.trim().lines().joinToString("\n") { l ->
        l.replace(Regex("^#+\\s*"), "")
            .replace("**", "")
            .replace("`", "")
            .replace(Regex("^\\s*[-*]\\s+"), "\u2022 ")
    }

    /** Jendela "Periksa pembaruan": hasil pemeriksaan, unduh dan pasang rilis terbaru. */
    fun showCheck(ctx: Context): AlertDialog {
        return UpdateWindow(ctx, null, null).show()
    }

    /** Jendela yang muncul sekali saat pemeriksaan otomatis menemukan versi baru. [onDownload] dipanggil setelah unduhan dimulai. */
    fun showAnnounce(ctx: Context, info: UpdateInfo, onDownload: () -> Unit, onClosed: () -> Unit): AlertDialog {
        return UpdateWindow(ctx, info, onDownload, onClosed).show()
    }

    private class UpdateWindow(
        val ctx: Context,
        val announce: UpdateInfo?,
        val onDownload: (() -> Unit)?,
        val onClosed: () -> Unit = {}
    ) {
        private val pal = Pal.of(ctx)
        private lateinit var dlg: AlertDialog
        private val titleView = TextView(ctx)
        private val body = LinearLayout(ctx)
        private val buttons = LinearLayout(ctx)
        private val listener: () -> Unit = { if (::dlg.isInitialized && dlg.isShowing) render() }

        fun show(): AlertDialog {
            val root = LinearLayout(ctx)
            root.orientation = LinearLayout.VERTICAL
            root.setPadding(ctx.dp(20), ctx.dp(20), ctx.dp(20), ctx.dp(20))
            titleView.textSize = 20f
            titleView.typeface = Fonts.bold(ctx)
            titleView.setTextColor(pal.text)
            root.addView(titleView, LinearLayout.LayoutParams(MATCH, WRAP))
            body.orientation = LinearLayout.VERTICAL
            root.addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
            buttons.orientation = LinearLayout.VERTICAL
            root.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(12) })

            // jendela lebar: tepi kiri / kanan hanya 4dp (hPad FuruArc)
            val b = Dialogs.builder(ctx)
            b.setBackgroundInsetStart(ctx.dp(4))
            b.setBackgroundInsetEnd(ctx.dp(4))
            b.setBackgroundInsetTop(ctx.dp(24))
            b.setBackgroundInsetBottom(ctx.dp(24))
            dlg = b.setView(root).setCancelable(true).create()
            dlg.setOnDismissListener {
                UpdateChecker.removeListener(listener)
                UpdateInstaller.removeListener(listener)
                onClosed()
            }
            if (announce == null) {
                UpdateChecker.addListener(listener)
                UpdateInstaller.addListener(listener)
            }
            render()
            dlg.show()
            return dlg
        }

        // ---- bahan susun ----

        private fun text(s: String, sp: Float, muted: Boolean = false, semi: Boolean = false, topDp: Int = 12): TextView {
            val t = TextView(ctx)
            t.text = s
            t.textSize = sp
            t.typeface = if (semi) Fonts.semibold(ctx) else Fonts.regular(ctx)
            t.setTextColor(if (muted) pal.muted else pal.text)
            body.addView(t, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(topDp) })
            return t
        }

        private fun button(label: String, primary: Boolean, onClick: () -> Unit) {
            val b = FuruButton(ctx, primary)
            b.text = label
            b.setOnClickListener { onClick() }
            buttons.addView(b, LinearLayout.LayoutParams(MATCH, ctx.dp(44)).apply {
                if (buttons.childCount > 0) topMargin = ctx.dp(8)
            })
        }

        private fun close() = dlg.dismiss()

        private fun updateBody(info: UpdateInfo) {
            val installed = Updater.current(ctx)
            text(info.name, 17f, semi = true)
            text("${tr("Terpasang", "Installed")}: $installed  ·  ${tr("Terbaru", "Latest")}: ${info.tag}", 13f, muted = true)
            if (info.notes.isNotBlank()) {
                text(tr("Catatan rilis", "Release notes"), 12f, muted = true)
                val maxPx = minOf(ctx.dp(380), (ctx.resources.displayMetrics.heightPixels * 0.42f).toInt())
                val sv = MaxHeightScroll(ctx, maxPx)
                sv.overScrollMode = View.OVER_SCROLL_NEVER
                val t = TextView(ctx)
                t.text = cleanNotes(info.notes)
                t.textSize = 14f
                t.typeface = Fonts.regular(ctx)
                t.setTextColor(pal.text)
                t.setLineSpacing(0f, 1.2f)
                sv.addView(t)
                body.addView(sv, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(12) })
            }
            if (info.apkUrl == null) text(tr("Rilis ini belum melampirkan file APK; halaman rilis akan dibuka.", "This release has no APK attached; the release page will be opened."), 12f, muted = true)
        }

        private fun render() {
            body.removeAllViews()
            buttons.removeAllViews()
            if (announce != null) {
                titleView.text = tr("Versi baru tersedia", "A new version is available")
                updateBody(announce)
                button(tr("Download versi terbaru", "Download latest version"), true) {
                    if (announce.apkUrl != null) {
                        UpdateInstaller.start(ctx, announce)
                        onDownload?.invoke()
                    } else openUrl(ctx, announce.pageUrl)
                    close()
                }
                button(tr("Nanti", "Later"), false) { close() }
                return
            }

            val st = UpdateChecker.state
            val dl = UpdateInstaller.state
            val info = (st as? UpdateState.Available)?.info
            titleView.text = if (dl is DownloadState.Running || dl is DownloadState.Done) tr("Mengunduh pembaruan", "Downloading update") else tr("Periksa pembaruan", "Check for updates")

            // isi
            when {
                dl is DownloadState.Running -> {
                    text(info?.tag ?: "", 15f, topDp = 12)
                    val bar = LinearProgressIndicator(ctx)
                    bar.setIndicatorColor(pal.accent)
                    bar.trackColor = pal.disabledBg
                    bar.trackCornerRadius = ctx.dp(4)
                    if (dl.total > 0) {
                        bar.max = 1000
                        bar.setProgressCompat(((dl.done * 1000) / dl.total).toInt().coerceIn(0, 1000), false)
                    } else bar.isIndeterminate = true
                    body.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(12) })
                    text(
                        if (dl.total > 0) "${mb(dl.done)} / ${mb(dl.total)}  ·  ${(dl.done * 100 / dl.total).toInt()}%" else mb(dl.done),
                        13f, muted = true
                    )
                }
                dl is DownloadState.Done -> text(tr("Unduhan selesai. Tekan Install aplikasi untuk memasang.", "Download complete. Tap Install app to install it."), 15f)
                st is UpdateState.Checking || st is UpdateState.Idle -> {
                    val row = LinearLayout(ctx)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.gravity = Gravity.CENTER_VERTICAL
                    val sp = CircularProgressIndicator(ctx)
                    sp.isIndeterminate = true
                    sp.indicatorSize = ctx.dp(22)
                    sp.trackThickness = ctx.dp(2)
                    sp.setIndicatorColor(pal.accent)
                    row.addView(sp, LinearLayout.LayoutParams(ctx.dp(22), ctx.dp(22)))
                    val t = TextView(ctx)
                    t.text = tr("Memeriksa pembaruan…", "Checking for updates…")
                    t.textSize = 15f
                    t.typeface = Fonts.regular(ctx)
                    t.setTextColor(pal.text)
                    row.addView(t, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = ctx.dp(12) })
                    body.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(12) })
                }
                st is UpdateState.UpToDate -> {
                    text(tr("Kamu sudah memakai versi terbaru.", "You are using the latest version."), 15f)
                    text("${tr("Terpasang", "Installed")}: ${Updater.current(ctx)}", 13f, muted = true)
                }
                st is UpdateState.NoRelease -> text(tr("Pembaruan aplikasi belum tersedia saat ini.", "App updates are not available right now."), 15f)
                st is UpdateState.Failed -> {
                    text(tr("Gagal memeriksa pembaruan. Pastikan perangkat terhubung ke internet.", "Could not check for updates. Make sure the device is connected to the internet."), 15f)
                    text(st.reason, 12f, muted = true, topDp = 12)
                }
                info != null -> {
                    if (dl is DownloadState.Failed) {
                        text(tr("Unduhan gagal.", "The download failed."), 14f)
                        text(dl.reason, 12f, muted = true)
                    }
                    updateBody(info)
                }
            }

            // tombol bertumpuk
            when {
                dl is DownloadState.Running -> {
                    button(tr("Batalkan unduhan", "Cancel download"), false) { UpdateInstaller.cancel() }
                    button(tr("Tutup", "Close"), false) { close() }
                }
                dl is DownloadState.Done -> {
                    button(tr("Install aplikasi", "Install app"), true) {
                        try {
                            Updater.install(ctx, dl.file)
                        } catch (e: Exception) {
                            Toast.makeText(ctx, e.message ?: "install failed", Toast.LENGTH_LONG).show()
                        }
                    }
                    button(tr("Tutup", "Close"), false) { close() }
                }
                info != null -> {
                    button(tr("Download versi terbaru", "Download latest version"), true) {
                        if (info.apkUrl != null) UpdateInstaller.start(ctx, info) else {
                            openUrl(ctx, info.pageUrl)
                            close()
                        }
                    }
                    button(tr("Tutup", "Close"), false) { close() }
                }
                st is UpdateState.NoRelease || st is UpdateState.Failed -> {
                    button(tr("Periksa pembaruan", "Check for updates"), true) { UpdateChecker.check(ctx) }
                    button(tr("Tutup", "Close"), false) { close() }
                }
                else -> button(tr("Tutup", "Close"), false) { close() }
            }
        }
    }
}
