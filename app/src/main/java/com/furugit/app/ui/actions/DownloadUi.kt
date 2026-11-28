package com.furugit.app.ui.actions

import android.content.Context
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.furugit.app.core.common.fmtBytes
import com.furugit.app.core.common.tr
import com.furugit.app.data.preferences.Prefs
import com.furugit.app.github.actions.GhRepo
import com.furugit.app.github.artifacts.DlItem
import com.furugit.app.github.artifacts.DownloadTask
import com.furugit.app.github.artifacts.Downloads
import com.furugit.app.ui.components.DBtn
import com.furugit.app.ui.components.Dialogs
import com.furugit.app.ui.components.Fonts
import com.furugit.app.ui.components.MATCH
import com.furugit.app.ui.components.Pal
import com.furugit.app.ui.components.WRAP
import com.furugit.app.ui.components.dp
import com.furugit.app.ui.components.vbox
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File
import kotlin.math.max

/** Jendela pilih-lalu-unduh (artefak atau log) dan jendela "Proses Unduh". */
object DownloadUi {

    class Entry(val label: String, val sub: String?, val item: DlItem)

    /**
     * Jendela berisi daftar dengan centang (semua tercentang awalnya) dan tombol Tutup + Download.
     * Download baru bisa jalan bila Folder Output sudah diatur di Pengaturan Aplikasi.
     */
    fun choose(ctx: Context, gh: GhRepo, title: String, entries: List<Entry>) {
        val pal = Pal.of(ctx)
        val box = vbox(ctx)
        val checks = ArrayList<CheckBox>()
        for (e in entries) {
            val cb = CheckBox(ctx)
            cb.text = e.label + (if (e.sub != null) "  ·  " + e.sub else "")
            cb.textSize = 15f
            cb.typeface = Fonts.semibold(ctx)
            cb.isChecked = true
            checks.add(cb)
            box.addView(cb, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val warn = TextView(ctx)
        warn.textSize = 14f
        warn.typeface = Fonts.regular(ctx)
        warn.setTextColor(0xFFE5484D.toInt())
        warn.visibility = View.GONE
        box.addView(warn, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(8) })
        val noFolder = Prefs.downloadDir.isBlank()
        Dialogs.show(
            ctx, title, null, box,
            listOf(
                DBtn(tr("Tutup", "Close")),
                DBtn(tr("Download", "Download"), primary = true, keepOpen = true, dimmed = noFolder) { dlg ->
                    val dir = Prefs.downloadDir
                    val picked = entries.filterIndexed { i, _ -> checks[i].isChecked }
                    if (dir.isBlank()) {
                        warn.text = tr("Atur Folder Output terlebih dahulu di Pengaturan Aplikasi", "Set the Output Folder first in App Settings")
                        warn.visibility = View.VISIBLE
                    } else if (picked.isEmpty()) {
                        warn.text = tr("Centang minimal satu berkas.", "Tick at least one file.")
                        warn.visibility = View.VISIBLE
                    } else {
                        dlg.dismiss()
                        DownloadResult.attach()
                        val task = Downloads.start(gh, File(dir), title, picked.map { it.item })
                        showProgress(ctx, task)
                    }
                }
            )
        )
    }

    /**
     * Jendela "Proses Unduh": bilah progres + tombol Batal dan "Proses di Latar Belakang" (menutup jendela saja,
     * unduhan tetap jalan). Menutup sendiri saat selesai; jendela hasil muncul dari [Downloads].
     */
    fun showProgress(ctx: Context, task: DownloadTask) {
        val pal = Pal.of(ctx)
        val box = vbox(ctx)
        val name = TextView(ctx)
        name.textSize = 15f
        name.typeface = Fonts.semibold(ctx)
        name.setTextColor(pal.text)
        box.addView(name, LinearLayout.LayoutParams(MATCH, WRAP))
        val bar = LinearProgressIndicator(ctx)
        bar.setIndicatorColor(pal.accent)
        bar.trackColor = pal.disabledBg
        bar.trackCornerRadius = ctx.dp(4)
        box.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(12) })
        val info = TextView(ctx)
        info.textSize = 13f
        info.typeface = Fonts.regular(ctx)
        info.setTextColor(pal.muted)
        box.addView(info, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(8) })

        var dlg: androidx.appcompat.app.AlertDialog? = null
        fun render(t: DownloadTask) {
            name.text = t.currentName + "  (" + (t.currentIndex + 1) + "/" + t.items.size + ")"
            if (t.totalBytes > 0) {
                if (bar.isIndeterminate) bar.isIndeterminate = false
                bar.max = 1000
                bar.setProgressCompat(((t.doneBytes * 1000) / t.totalBytes).toInt().coerceIn(0, 1000), false)
                info.text = fmtBytes(t.doneBytes) + " / " + fmtBytes(t.totalBytes)
            } else {
                if (!bar.isIndeterminate) bar.isIndeterminate = true
                info.text = fmtBytes(t.doneBytes)
            }
            if (t.state != DownloadTask.RUNNING) {
                try {
                    dlg?.dismiss()
                } catch (e: Exception) {
                }
            }
        }
        dlg = Dialogs.show(
            ctx, tr("Proses Unduh", "Downloading"), null, box,
            listOf(
                DBtn(tr("Batal", "Cancel")) { task.cancel() },
                DBtn(tr("Proses di Latar Belakang", "Run in Background"), true) { task.listener = null }
            ),
            cancelable = false
        )
        task.listener = { render(it) }
        render(task)
    }
}
