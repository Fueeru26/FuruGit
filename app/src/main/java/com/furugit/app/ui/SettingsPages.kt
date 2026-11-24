package com.furugit.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.furugit.app.R
import com.furugit.app.data.ActivityLog
import com.furugit.app.data.Prefs
import com.furugit.app.data.UpdateInfo
import com.furugit.app.data.Updater
import com.furugit.app.git.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** Link berwarna aksen dan bergaris bawah; hanya teksnya yang bisa diketuk. */
fun LinearLayout.linkText(text: String, onClick: () -> Unit): TextView {
    val t = TextView(context)
    t.text = tx(text)
    t.textSize = 15f
    t.typeface = Fonts.bold(context)
    t.paintFlags = t.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
    t.setTextColor(context.color(R.color.furu_accent))
    t.setPadding(0, context.dp(6), 0, context.dp(6))
    t.setOnClickListener { onClick() }
    addView(t, LinearLayout.LayoutParams(WRAP, WRAP))
    return t
}

fun Context.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Dialogs.toast(this, tr("Tidak bisa membuka tautan", "Cannot open the link"))
    }
}

// ───────────────────────── Menu utama Pengaturan ─────────────────────────

class SettingsHomeFragment : BaseFragment() {
    override fun refresh() {
        content.removeAllViews()
        val act = requireActivity() as MainActivity
        fun row(icon: Int, id: String, en: String, subId: String, subEn: String, f: () -> androidx.fragment.app.Fragment) {
            content.rowCard(icon, tr(id, en), tr(subId, subEn), onClick = { act.push(f(), id) })
        }
        row(R.drawable.ic_shield, "Pengaturan Github", "GitHub Settings",
            "Izin penyimpanan, identitas Git, akun dan autentikasi", "Storage permission, Git identity, accounts and authentication") { GithubSettingsFragment() }
        row(R.drawable.ic_tune, "Pengaturan Aplikasi", "App Settings",
            "Tampilan (mode dan warna tema) dan bahasa", "Appearance (theme mode and color) and language") { AppSettingsFragment() }
        row(R.drawable.ic_log, "Log Aktivitas", "Activity Log",
            "Catatan operasi Git yang dijalankan", "Record of the Git operations that were run") { LogFragment() }
        row(R.drawable.ic_guide, "Panduan", "Guide",
            "Apa itu Git, daftar perintah, dan cara kerja aplikasi", "What Git is, its commands, and how the app works") { GuideFragment() }
        row(R.drawable.ic_download, "Periksa Pembaruan", "Check for Updates",
            "Cari versi baru di GitHub Releases", "Look for a new version on GitHub Releases") { UpdateFragment() }
        row(R.drawable.ic_info, "Tentang", "About",
            "Pengembang, kontak, donasi, dan tentang aplikasi", "Developer, contact, donation, and about the app") { AboutFragment() }
    }
}

// ───────────────────────── Pengaturan Aplikasi ─────────────────────────

class AppSettingsFragment : BaseFragment() {

    /** Kartu pilihan tunggal: baris yang dipilih diberi tanda centang. */
    private fun LinearLayout.options(items: List<String>, selected: Int, onPick: (Int) -> Unit) {
        card {
            items.forEachIndexed { i, text ->
                val row = LinearLayout(context)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.minimumHeight = context.dp(44)
                row.isClickable = true
                row.setOnClickListener { onPick(i) }
                val tv = TextView(context)
                tv.text = text
                tv.textSize = 16f
                tv.typeface = if (i == selected) Fonts.bold(context) else Fonts.regular(context)
                tv.setTextColor(context.color(R.color.furu_text))
                row.addView(tv, LinearLayout.LayoutParams(0, WRAP, 1f))
                if (i == selected) {
                    val iv = ImageView(context)
                    iv.setImageResource(R.drawable.ic_check)
                    iv.imageTintList = ColorStateList.valueOf(context.color(R.color.furu_accent))
                    row.addView(iv, LinearLayout.LayoutParams(context.dp(24), context.dp(24)))
                }
                addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
            }
        }
    }

    private fun swatches(parent: LinearLayout) {
        val dark = Pal.isDark(ctx)
        val all = Accent.values().toList()
        val cur = Accent.of(Prefs.accent)
        parent.card {
            for (chunk in all.chunked(5)) {
                val line = LinearLayout(context)
                line.orientation = LinearLayout.HORIZONTAL
                for (a in chunk) {
                    val cell = FrameLayout(context)
                    val dot = FrameLayout(context)
                    val bg = GradientDrawable()
                    bg.shape = GradientDrawable.OVAL
                    bg.setColor(if (dark) a.dark else a.light)
                    if (a == cur) bg.setStroke(context.dp(3), context.color(R.color.furu_text))
                    dot.background = bg
                    if (a == cur) {
                        val iv = ImageView(context)
                        iv.setImageResource(R.drawable.ic_check)
                        iv.imageTintList = ColorStateList.valueOf(if (dark) Color.parseColor("#101214") else Color.WHITE)
                        dot.addView(iv, FrameLayout.LayoutParams(context.dp(24), context.dp(24), Gravity.CENTER))
                    }
                    dot.setOnClickListener {
                        if (a != Accent.of(Prefs.accent)) {
                            Prefs.accent = a.id
                            requireActivity().recreate()
                        }
                    }
                    cell.addView(dot, FrameLayout.LayoutParams(context.dp(44), context.dp(44), Gravity.CENTER))
                    line.addView(cell, LinearLayout.LayoutParams(0, context.dp(56), 1f))
                }
                // isi sisa sel agar lingkaran sejajar dengan baris di atasnya
                for (k in chunk.size until 5) line.addView(android.view.View(context), LinearLayout.LayoutParams(0, context.dp(56), 1f))
                addView(line, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            label(tr("Warna terpilih: ", "Selected color: ") + (if (L.isId) cur.nameId else cur.nameEn), 13f, dim = true)
        }
    }

    override fun refresh() {
        content.removeAllViews()

        content.title(tr("Pengaturan Tampilan", "Appearance"))
        content.label(tr("Mode Tema", "Theme Mode"), 15f, bold = true)
        content.options(
            listOf(tr("Ikuti Sistem", "Follow System"), tr("Terang", "Light"), tr("Gelap", "Dark")),
            Prefs.themeMode
        ) { i ->
            if (i != Prefs.themeMode) {
                Prefs.themeMode = i
                AppCompatDelegate.setDefaultNightMode(
                    when (i) {
                        1 -> AppCompatDelegate.MODE_NIGHT_NO
                        2 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
                refresh()
            }
        }
        content.space(8)
        content.label(tr("Warna Tema", "Theme Color"), 15f, bold = true)
        swatches(content)

        content.title(tr("Pengaturan Bahasa", "Language"))
        val codes = listOf(L.SYSTEM, L.EN, L.ID)
        content.options(
            listOf(tr("Ikuti Sistem", "Follow System"), "English", "Indonesia"),
            codes.indexOf(Prefs.lang).coerceAtLeast(0)
        ) { i ->
            if (codes[i] != Prefs.lang) {
                Prefs.lang = codes[i]
                L.reload()
                requireActivity().recreate()
            }
        }
        content.hint(
            tr(
                "Ikuti Sistem: Indonesia jika bahasa perangkat Indonesia, selain itu Inggris.",
                "Follow System: Indonesian if the device language is Indonesian, otherwise English."
            )
        )
    }
}

// ───────────────────────── Panduan ─────────────────────────

class GuideFragment : BaseFragment() {

    private fun LinearLayout.section(title: String, body: String, open: Boolean = false) {
        card {
            val head = TextView(context)
            head.text = title
            head.textSize = 16f
            head.typeface = Fonts.bold(context)
            head.setTextColor(context.color(R.color.furu_text))
            addView(head, LinearLayout.LayoutParams(MATCH, WRAP))
            val b = TextView(context)
            b.text = body
            b.textSize = 14f
            b.typeface = Fonts.regular(context)
            b.setTextColor(context.color(R.color.furu_muted))
            b.setLineSpacing(0f, 1.15f)
            b.setPadding(0, context.dp(8), 0, 0)
            b.visibility = if (open) android.view.View.VISIBLE else android.view.View.GONE
            addView(b, LinearLayout.LayoutParams(MATCH, WRAP))
            head.setOnClickListener { b.visibility = if (b.visibility == android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE }
            head.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }
    }

    override fun refresh() {
        content.removeAllViews()
        content.hint(tr("Ketuk judul untuk membuka atau menutup.", "Tap a title to expand or collapse."))
        for ((t, b) in GuideText.sections()) content.section(t, b)
    }
}

object GuideText {
    fun sections(): List<Pair<String, String>> = if (L.isId) ID else EN

    private val ID = listOf(
        "Apa itu Git?" to "Git adalah sistem kontrol versi: ia mencatat setiap perubahan pada file proyekmu sebagai riwayat. Kamu bisa kembali ke kondisi lama, mencoba ide di jalur terpisah, dan bekerja bersama orang lain tanpa saling menimpa file.\n\nGitHub adalah layanan penyimpanan repository Git di internet. Git sendiri bekerja di perangkatmu; GitHub dipakai untuk mencadangkan dan berbagi.",
        "Istilah dasar" to "• Repository (repo): folder proyek beserta seluruh riwayatnya (folder tersembunyi .git).\n• Commit: satu catatan perubahan yang diberi pesan.\n• Staging: memilih file mana yang akan masuk ke commit berikutnya.\n• Branch: jalur kerja terpisah; branch utama biasanya main.\n• Remote: salinan repo di server, mis. origin di GitHub.\n• HEAD: posisi commit yang sedang aktif.\n• Konflik: dua perubahan di bagian file yang sama yang tidak bisa digabung otomatis.",
        "Perintah Git ↔ tombol di FuruGit" to "• git init → tab Repo › Init\n• git clone → tab Repo › Clone\n• git status → tab Perubahan (daftar file berubah) dan kartu repo aktif\n• git add → tab Perubahan › Stage terpilih / Stage semua\n• git restore --staged → tab Perubahan › tekan lama file di-stage (Unstage)\n• git restore / clean → tab Perubahan › Buang perubahan terpilih\n• git commit → tab Perubahan › Commit\n• git add + commit + push → tab Perubahan › Commit & Push\n• git diff → ketuk sebuah file di tab Perubahan\n• git branch / switch → tab Branch\n• git merge → tab Branch › ketuk branch › Merge\n• git stash → tab Branch › Stash perubahan (Apply, Pop, Drop)\n• git remote → tab Sync › Daftar remote\n• git fetch / pull / push → tab Sync › Fetch / Pull / Push\n• git log → tab Riwayat\n• git reset → tab Riwayat › ketuk commit › Reset soft / mixed / HARD\n• git revert → tab Riwayat › ketuk commit › Revert\n• git cherry-pick → tab Riwayat › ketuk commit › Cherry-pick\n• git tag → tab Riwayat › Tags dan Buat tag di sini",
        "Alur kerja sehari-hari" to "1. Siapkan akun: Pengaturan › Pengaturan Github › isi Identitas Git, lalu tambahkan Personal Access Token atau login OAuth.\n2. Tab Repo: Clone repo dari GitHub, atau Buka folder repo yang sudah ada di HP.\n3. Ubah file dengan aplikasi lain (editor, game, dll.).\n4. Tab Perubahan: centang file lalu Stage, tulis pesan, Commit (atau Commit & Push sekaligus).\n5. Tab Sync: Pull dulu bila perlu, lalu Push untuk mengirim ke GitHub.",
        "Cara kerja aplikasi" to "• FuruGit memakai JGit, implementasi Git murni Java, jadi berjalan penuh di perangkatmu tanpa server perantara.\n• Repo dibuka langsung dari penyimpanan HP lewat izin \"Akses semua file\", bukan salinan di dalam aplikasi.\n• Setiap aksi yang mengubah data meminta konfirmasi lebih dulu.\n• Push selalu fetch dulu dan menolak bila remote lebih baru; tidak ada push paksa.\n• Token disimpan terenkripsi di perangkat dan hanya dikirim ke host yang cocok.\n• Karena penyimpanan bersama tidak mendukung izin file dan symlink Linux, core.filemode dan core.symlinks dimatikan otomatis agar file tidak terlihat berubah semuanya.\n• Log Aktivitas mencatat operasi yang dijalankan, berguna untuk melapor masalah.",
        "Menyelesaikan konflik" to "Konflik muncul saat Merge atau Pull. Buka tab Perubahan, bagian Konflik, lalu pilih per file: Milik saya (versi lokal), Milik remote (versi dari remote), atau Edit untuk menyunting isi dan menghapus penanda <<<<<<<, =======, >>>>>>>. Setelah semua file selesai, tekan Commit untuk menuntaskan merge. Tombol Batalkan merge mengembalikan semuanya ke commit terakhir.",
        "Pemecahan masalah" to "• \"Nama & email Git belum diatur\": isi Identitas Git di Pengaturan Github.\n• \"Autentikasi gagal\": periksa token; PAT fine-grained butuh izin Contents (Read and write) dan Metadata (Read) untuk repo tersebut.\n• \"Push ditolak\" atau remote punya commit baru: lakukan Pull dulu, lalu Push.\n• Tidak bisa membuka folder repo: pastikan izin Akses semua file aktif.\n• \"Repository terkunci\": ada file .git/index.lock sisa; hapus bila tidak ada operasi Git lain yang berjalan.\n• Reset HARD dan Buang perubahan tidak bisa dibatalkan; pastikan dulu isinya."
    )

    private val EN = listOf(
        "What is Git?" to "Git is a version control system: it records every change to your project files as a history. You can go back to an older state, try ideas on a separate line of work, and collaborate without overwriting each other's files.\n\nGitHub is an online service that hosts Git repositories. Git itself runs on your device; GitHub is used for backup and sharing.",
        "Basic terms" to "• Repository (repo): the project folder together with its whole history (the hidden .git folder).\n• Commit: one recorded change with a message.\n• Staging: choosing which files go into the next commit.\n• Branch: a separate line of work; the main branch is usually main.\n• Remote: a copy of the repo on a server, e.g. origin on GitHub.\n• HEAD: the commit you are currently on.\n• Conflict: two changes to the same part of a file that cannot be merged automatically.",
        "Git commands ↔ FuruGit buttons" to "• git init → Repo tab › Init\n• git clone → Repo tab › Clone\n• git status → Changes tab (list of changed files) and the active repo card\n• git add → Changes tab › Stage selected / Stage all\n• git restore --staged → Changes tab › long press a staged file (Unstage)\n• git restore / clean → Changes tab › Discard selected changes\n• git commit → Changes tab › Commit\n• git add + commit + push → Changes tab › Commit & Push\n• git diff → tap a file in the Changes tab\n• git branch / switch → Branch tab\n• git merge → Branch tab › tap a branch › Merge\n• git stash → Branch tab › Stash changes (Apply, Pop, Drop)\n• git remote → Sync tab › Remotes\n• git fetch / pull / push → Sync tab › Fetch / Pull / Push\n• git log → History tab\n• git reset → History tab › tap a commit › Reset soft / mixed / HARD\n• git revert → History tab › tap a commit › Revert\n• git cherry-pick → History tab › tap a commit › Cherry-pick\n• git tag → History tab › Tags and Create tag here",
        "Everyday workflow" to "1. Set up your account: Settings › GitHub Settings › fill in Git Identity, then add a Personal Access Token or log in with OAuth.\n2. Repo tab: Clone a repo from GitHub, or Open an existing repo folder on your phone.\n3. Edit files with other apps (an editor, a game, etc.).\n4. Changes tab: check files and Stage, write a message, Commit (or Commit & Push in one go).\n5. Sync tab: Pull first if needed, then Push to send to GitHub.",
        "How the app works" to "• FuruGit uses JGit, a pure-Java Git implementation, so it runs entirely on your device with no middle server.\n• Repos are opened directly from your phone's storage through the \"All files access\" permission, not as copies inside the app.\n• Every action that changes data asks for confirmation first.\n• Push always fetches first and refuses if the remote is newer; there is no force push.\n• Tokens are stored encrypted on the device and sent only to the matching host.\n• Because shared storage does not support Linux file modes and symlinks, core.filemode and core.symlinks are turned off automatically so files do not all look changed.\n• The Activity Log records the operations that were run, which helps when reporting a problem.",
        "Resolving conflicts" to "Conflicts appear on Merge or Pull. Open the Changes tab, Conflicts section, then choose per file: Mine (local version), Theirs (remote version), or Edit to edit the content and remove the <<<<<<<, =======, >>>>>>> markers. When every file is resolved, press Commit to finish the merge. Abort merge returns everything to the last commit.",
        "Troubleshooting" to "• \"Git name & email are not set\": fill in Git Identity in GitHub Settings.\n• \"Authentication failed\": check the token; a fine-grained PAT needs Contents (Read and write) and Metadata (Read) for that repo.\n• \"Push rejected\" or the remote has new commits: Pull first, then Push.\n• Cannot open a repo folder: make sure All files access is granted.\n• \"Repository is locked\": a leftover .git/index.lock file; delete it if no other Git operation is running.\n• Reset HARD and Discard changes cannot be undone; double-check first."
    )
}

// ───────────────────────── Periksa Pembaruan ─────────────────────────

class UpdateFragment : BaseFragment() {
    private var checked = false
    private var info: UpdateInfo? = null
    private var error: String? = null
    private var progressText: String? = null
    private var apk: File? = null
    private var working = false

    override fun refresh() {
        content.removeAllViews()
        val cur = Updater.current(ctx)
        content.card {
            label(tr("Versi terpasang", "Installed version"), 12f, dim = true)
            label("FuruGit $cur", 20f, bold = true)
            label(tr("Sumber: GitHub Releases Fueeru26/FuruGit", "Source: GitHub Releases of Fueeru26/FuruGit"), 12f, dim = true)
        }
        content.space(4)
        val b = content.button(tr("Periksa pembaruan", "Check for updates")) { check() }
        b.isEnabled = !working

        if (error != null) content.card { label(tx(error!!), 14f) }
        val i = info
        if (checked && error == null) {
            if (i == null) {
                content.card { label(tr("Belum ada rilis di GitHub.", "There are no releases on GitHub yet."), 14f) }
            } else if (Updater.compare(i.version, cur) <= 0) {
                content.card { label(tr("Kamu sudah memakai versi terbaru.", "You are on the latest version."), 14f, bold = true) }
            } else {
                content.card {
                    label(tr("Versi baru tersedia", "New version available"), 12f, dim = true)
                    label("FuruGit ${i.version}", 20f, bold = true)
                    if (i.notes.isNotBlank()) {
                        space(6)
                        label(i.notes.trim().take(1500), 13f, dim = true)
                    }
                    if (i.apkUrl == null) {
                        space(6)
                        hint(tr("Rilis ini belum punya file APK. Buka halaman rilis untuk detailnya.", "This release has no APK file yet. Open the release page for details."))
                        linkText(tr("Buka halaman rilis", "Open release page")) { ctx.openLink(i.pageUrl) }
                    }
                }
                if (i.apkUrl != null) {
                    val f = apk
                    if (f != null && f.exists()) {
                        content.button(tr("Pasang pembaruan", "Install update")) {
                            sure("Pasang pembaruan?", "Installer sistem akan terbuka dan meminta persetujuanmu.", "Pasang") { Updater.install(ctx, f) }
                        }
                    } else {
                        val d = content.button(tr("Unduh pembaruan", "Download update")) {
                            sure("Unduh pembaruan?", "File APK akan diunduh dari GitHub (${i.apkSize / 1024 / 1024} MB).", "Unduh") { download(i) }
                        }
                        d.isEnabled = !working
                    }
                    if (progressText != null) content.hint(progressText!!)
                }
            }
        }
    }

    private fun check() {
        val act = requireActivity() as MainActivity
        working = true
        error = null
        apk = null
        progressText = null
        refresh()
        act.lifecycleScope.launch {
            act.setBusy(true)
            try {
                info = Updater.latest()
                checked = true
                ActivityLog.add("Periksa pembaruan: " + (info?.tag ?: "-"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.friendly()
                ActivityLog.add("✘ Periksa pembaruan: " + e.friendly())
            } finally {
                working = false
                act.setBusy(false)
                if (isAdded && view != null) refresh()
            }
        }
    }

    private fun download(i: UpdateInfo) {
        val act = requireActivity() as MainActivity
        working = true
        progressText = tr("Mengunduh…", "Downloading…")
        refresh()
        act.lifecycleScope.launch {
            act.setBusy(true)
            try {
                ActivityLog.add("▶ Unduh pembaruan ${i.tag}")
                apk = Updater.download(act, i) { done, total ->
                    val pct = if (total > 0) " ${done * 100 / total}%" else ""
                    act.runOnUiThread { progressText = tr("Mengunduh…", "Downloading…") + pct; if (isAdded && view != null && working) refreshProgressOnly() }
                }
                progressText = tr("Unduhan selesai. Tekan Pasang pembaruan.", "Download finished. Tap Install update.")
                ActivityLog.add("✔ Unduh pembaruan ${i.tag}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                progressText = null
                error = e.friendly()
                ActivityLog.add("✘ Unduh pembaruan: " + e.friendly())
            } finally {
                working = false
                act.setBusy(false)
                if (isAdded && view != null) refresh()
            }
        }
    }

    /** Memperbarui teks progres tanpa membangun ulang seluruh layar. */
    private fun refreshProgressOnly() {
        val last = content.getChildAt(content.childCount - 1)
        if (last is TextView && progressText != null) last.text = progressText
    }
}

// ───────────────────────── Tentang ─────────────────────────

class AboutFragment : BaseFragment() {
    private var withLog = false

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
            col.label(tr("Pengembang FuruGit", "FuruGit developer"), 14f, dim = true)
            row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
            val logo = ImageView(context)
            logo.setImageResource(R.drawable.app_icon)
            logo.clipToOutline = true
            val bg = GradientDrawable()
            bg.cornerRadius = context.dp(12).toFloat()
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
            r.linkText("Email") { ctx.openLink("mailto:fueerugame@gmail.com") }
            r.addView(android.view.View(context), LinearLayout.LayoutParams(context.dp(28), 1))
            r.linkText("Github") { ctx.openLink(Updater.REPO_URL) }
            addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
            label("fueerugame@gmail.com · Fueeru26/FuruGit", 12f, dim = true)
        }

        // Donasi
        content.card {
            label(tr("Donasi", "Donation"), 12f, dim = true)
            val r = LinearLayout(context)
            r.orientation = LinearLayout.VERTICAL
            fun item(name: String, note: String, url: String) {
                val line = LinearLayout(context)
                line.orientation = LinearLayout.HORIZONTAL
                line.gravity = Gravity.CENTER_VERTICAL
                line.linkText(name) { ctx.openLink(url) }
                val n = TextView(context)
                n.text = "  $note"
                n.textSize = 13f
                n.typeface = Fonts.regular(context)
                n.setTextColor(context.color(R.color.furu_muted))
                line.addView(n)
                r.addView(line)
            }
            item("Ko-fi", tr("(Untuk Global)", "(For Global)"), "https://ko-fi.com/fueeru")
            item("Trakteer", tr("(Khusus ID)", "(Indonesia only)"), "https://trakteer.id/fueeru")
            addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
            label(tr("Dukungan Anda membantu pengembangan berikutnya. Terima kasih!", "Your support helps further development. Thank you!"), 13f, dim = true)
        }

        // Lapor masalah
        content.card {
            label(tr("Lapor Masalah", "Report a Problem"), 12f, dim = true)
            label(
                tr(
                    "Membuka aplikasi email Anda dengan alamat pengembang, versi aplikasi, dan info perangkat sudah terisi. Tidak ada yang dikirim otomatis; Anda bisa mengubah isinya sebelum mengirim.",
                    "Opens your email app with the developer's address, app version and device info filled in. Nothing is sent automatically; you can edit the message before sending."
                ), 13f, dim = true
            )
            val cb = CheckBox(context)
            cb.text = tr("Sertakan log aktivitas terakhir", "Include the latest activity log")
            cb.typeface = Fonts.regular(context)
            cb.isChecked = withLog
            cb.setOnCheckedChangeListener { _, on -> withLog = on }
            addView(cb)
            button(tr("Kirim laporan lewat email", "Send report by email")) {
                sure("Kirim laporan?", "Aplikasi email akan terbuka dengan laporan yang sudah terisi.", "Buka email") { sendReport() }
            }
        }

        // Tentang Aplikasi (paling bawah)
        content.card {
            label(tr("Tentang Aplikasi", "About the App"), 12f, dim = true)
            label("FuruGit ${Updater.current(ctx)}", 20f, bold = true)
            space(4)
            label(
                tr(
                    "FuruGit adalah klien Git untuk Android yang menjalankan perintah Git lewat tombol, tanpa perlu mengetik command. Kelola repository langsung di penyimpanan HP: clone, commit, branch, merge, stash, pull/push, riwayat, dan penyelesaian konflik, dengan konfirmasi sebelum setiap aksi penting. Semua berjalan di perangkatmu memakai JGit, tanpa server perantara.",
                    "FuruGit is a Git client for Android that runs Git commands with buttons, with no need to type commands. Manage repositories directly in your phone's storage: clone, commit, branch, merge, stash, pull/push, history and conflict resolution, with a confirmation before every important action. Everything runs on your device using JGit, with no middle server."
                ), 14f
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
