package com.furugit.app.ui.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Outline
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import com.furugit.app.R
import com.furugit.app.core.common.ActivityLog
import com.furugit.app.core.common.tr
import com.furugit.app.github.releases.Updater
import com.furugit.app.ui.components.BaseFragment
import com.furugit.app.ui.components.Dialogs
import com.furugit.app.ui.components.Fonts
import com.furugit.app.ui.components.MATCH
import com.furugit.app.ui.components.WRAP
import com.furugit.app.ui.components.button
import com.furugit.app.ui.components.card
import com.furugit.app.ui.components.color
import com.furugit.app.ui.components.dp
import com.furugit.app.ui.components.gap
import com.furugit.app.ui.components.label
import com.furugit.app.ui.components.linkText
import com.furugit.app.ui.components.openLink

// ───────────────────────── Tentang ─────────────────────────

class AboutFragment : BaseFragment() {
    private var withLog = false

    /** Ikon + tautan; hanya teksnya yang bisa diketuk (seperti FuruArc). */
    private fun LinearLayout.iconLink(icon: Int, text: String, url: String) {
        val r = LinearLayout(context)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        val iv = ImageView(context)
        iv.setImageResource(icon)
        iv.imageTintList = ColorStateList.valueOf(context.color(R.color.furu_text))
        r.addView(iv, LinearLayout.LayoutParams(context.dp(22), context.dp(22)).apply { marginEnd = context.dp(10) })
        r.linkText(text) { ctx.openLink(url) }
        addView(r, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = context.dp(28) })
    }

    override fun refresh() {
        content.removeAllViews()

        // Pengembang
        content.card {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val col = LinearLayout(context)
            col.orientation = LinearLayout.VERTICAL
            col.label("Fueeru", 20f, bold = true)
            col.label(tr("Pengembang FuruGit", "FuruGit developer"), 14f, dim = true, gap = 2)
            row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
            val logo = ImageView(context)
            logo.setImageResource(R.drawable.app_icon)
            logo.clipToOutline = true
            logo.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: android.view.View, o: android.graphics.Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, v.context.dp(12).toFloat())
                }
            }
            row.addView(logo, LinearLayout.LayoutParams(context.dp(52), context.dp(52)))
            addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        // Kontak
        content.card {
            label(tr("Kontak", "Contact"), 12f, dim = true)
            val r = LinearLayout(context)
            r.orientation = LinearLayout.HORIZONTAL
            r.iconLink(R.drawable.ic_email, "Email", "mailto:fueerugame@gmail.com")
            r.iconLink(R.drawable.ic_github, "Github", Updater.REPO_URL)
            addView(r, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(10) })
        }

        // Donasi: hanya nama platform yang menjadi tautan
        content.card {
            label(tr("Donasi", "Donation"), 12f, dim = true)
            val r = LinearLayout(context)
            r.orientation = LinearLayout.HORIZONTAL
            r.linkText("Ko-fi") { ctx.openLink("https://ko-fi.com/fueeru") }
            r.addView(android.view.View(context), LinearLayout.LayoutParams(context.dp(28), 1))
            r.linkText("Trakteer") { ctx.openLink("https://trakteer.id/fueeru") }
            addView(r, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(10) })
            label(tr("Dukungan Anda membantu pengembangan berikutnya. Terima kasih!", "Your support helps further development. Thank you!"), 13f, dim = true, gap = 6)
        }

        // Lapor masalah
        content.card {
            label(tr("Lapor Masalah", "Report a Problem"), 12f, dim = true)
            label(
                tr(
                    "Membuka aplikasi email Anda dengan alamat pengembang, versi aplikasi, dan info perangkat sudah terisi. Tidak ada yang dikirim otomatis; Anda bisa mengubah isinya sebelum mengirim.",
                    "Opens your email app with the developer's address, app version and device info filled in. Nothing is sent automatically; you can edit the message before sending."
                ), 14f, dim = true, gap = 10
            )
            val cb = CheckBox(context)
            cb.text = tr("Sertakan log aktivitas terakhir", "Include the latest activity log")
            cb.typeface = Fonts.regular(context)
            cb.setTextColor(context.color(R.color.furu_text))
            cb.isChecked = withLog
            cb.setOnCheckedChangeListener { _, on -> withLog = on }
            addView(cb, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(10) })
            button(tr("Kirim laporan lewat email", "Send report by email")) {
                sure("Kirim laporan?", "Aplikasi email akan terbuka dengan laporan yang sudah terisi.", "Buka email") { sendReport() }
            }
        }

        // Tentang Aplikasi (paling bawah)
        content.card {
            label(tr("Tentang Aplikasi", "About the App"), 12f, dim = true)
            label("FuruGit ${Updater.current(ctx)}", 20f, bold = true, gap = 10)
            label(
                tr(
                    "FuruGit adalah klien Git untuk Android yang menjalankan perintah Git lewat tombol, tanpa perlu mengetik command. Kelola repository langsung di penyimpanan HP: clone, commit, branch, merge, stash, pull/push, riwayat, dan penyelesaian konflik, dengan konfirmasi sebelum setiap aksi penting. Semua berjalan di perangkatmu memakai JGit, tanpa server perantara.",
                    "FuruGit is a Git client for Android that runs Git commands with buttons, with no need to type commands. Manage repositories directly in your phone's storage: clone, commit, branch, merge, stash, pull/push, history and conflict resolution, with a confirmation before every important action. Everything runs on your device using JGit, with no middle server."
                ), 14f, gap = 6
            )
        }
    }

    private fun sendReport() {
        val body = StringBuilder()
        body.append(tr("Jelaskan masalahnya (langkah yang dilakukan, pesan error):", "Describe the problem (steps taken, error message):")).append("\n\n\n---\n")
        body.append("FuruGit ").append(Updater.current(ctx)).append('\n')
        body.append("Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        body.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        if (withLog) {
            val lines = ActivityLog.all().takeLast(80)
            if (lines.isNotEmpty()) body.append("\nLog:\n").append(lines.joinToString("\n"))
        }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf("fueerugame@gmail.com"))
            .putExtra(Intent.EXTRA_SUBJECT, tr("Laporan masalah FuruGit", "FuruGit problem report"))
            .putExtra(Intent.EXTRA_TEXT, body.toString())
        try {
            startActivity(i)
        } catch (e: Exception) {
            Dialogs.toast(ctx, tr("Tidak ada aplikasi email", "No email app found"))
        }
    }
}
