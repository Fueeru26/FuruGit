package com.furugit.app.ui.settings

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.furugit.app.R
import com.furugit.app.core.common.L
import com.furugit.app.core.common.tr
import com.furugit.app.ui.components.BaseFragment
import com.furugit.app.ui.components.Fonts
import com.furugit.app.ui.components.MATCH
import com.furugit.app.ui.components.WRAP
import com.furugit.app.ui.components.card
import com.furugit.app.ui.components.color
import com.furugit.app.ui.components.dp
import com.furugit.app.ui.components.hint

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
        "Perintah Git ↔ tombol di FuruGit" to "• git init → tab Repositori › Init\n• git clone → tab Repositori › Clone\n• git status → tab Perubahan (daftar file berubah) dan kartu repo aktif\n• git add → tab Perubahan › Stage terpilih / Stage semua\n• git restore --staged → tab Perubahan › tekan lama file di-stage (Unstage)\n• git restore / clean → tab Perubahan › Buang perubahan terpilih\n• git commit → tab Perubahan › Commit\n• git add + commit + push → tab Perubahan › Commit & Push\n• git diff → ketuk sebuah file di tab Perubahan\n• git branch / switch → tab Kelola › Branch\n• git merge → tab Kelola › Branch › ketuk branch › Merge\n• git stash → tab Kelola › Branch › Stash perubahan (Apply, Pop, Drop)\n• git remote → tab Kelola › Sinkronisasi › Daftar remote\n• git fetch / pull / push → tab Kelola › Sinkronisasi › Fetch / Pull / Push\n• git log → tab Aksi › Riwayat commit\n• git reset → tab Aksi › Riwayat commit › ketuk commit › Reset soft / mixed / HARD\n• git revert → tab Aksi › Riwayat commit › ketuk commit › Revert\n• git cherry-pick → tab Aksi › Riwayat commit › ketuk commit › Cherry-pick\n• git tag → tab Aksi › Riwayat commit › Tags dan Buat tag di sini",
        "Alur kerja sehari-hari" to "1. Siapkan akun: Pengaturan › Pengaturan Github › isi Identitas Git, lalu tambahkan Personal Access Token atau login OAuth.\n2. Tab Repositori: Clone repo dari GitHub, atau Buka folder repo yang sudah ada di HP.\n3. Ubah file dengan aplikasi lain (editor, game, dll.).\n4. Tab Perubahan: centang file lalu Stage, tulis pesan, Commit (atau Commit & Push sekaligus).\n5. Tab Kelola › Sinkronisasi: Pull dulu bila perlu, lalu Push untuk mengirim ke GitHub.",
        "Cara kerja aplikasi" to "• FuruGit memakai JGit, implementasi Git murni Java, jadi berjalan penuh di perangkatmu tanpa server perantara.\n• Repo dibuka langsung dari penyimpanan HP lewat izin \"Akses semua file\", bukan salinan di dalam aplikasi.\n• Setiap aksi yang mengubah data meminta konfirmasi lebih dulu.\n• Push selalu fetch dulu dan menolak bila remote lebih baru; tidak ada push paksa.\n• Token disimpan terenkripsi di perangkat dan hanya dikirim ke host yang cocok.\n• Karena penyimpanan bersama tidak mendukung izin file dan symlink Linux, core.filemode dan core.symlinks dimatikan otomatis agar file tidak terlihat berubah semuanya.\n• Log Aktivitas mencatat operasi yang dijalankan, berguna untuk melapor masalah.",
        "Menyelesaikan konflik" to "Konflik muncul saat Merge atau Pull. Buka tab Perubahan, bagian Konflik, lalu pilih per file: Milik saya (versi lokal), Milik remote (versi dari remote), atau Edit untuk menyunting isi dan menghapus penanda <<<<<<<, =======, >>>>>>>. Setelah semua file selesai, tekan Commit untuk menuntaskan merge. Tombol Batalkan merge mengembalikan semuanya ke commit terakhir.",
        "Pemecahan masalah" to "• \"Nama & email Git belum diatur\": isi Identitas Git di Pengaturan Github.\n• \"Autentikasi gagal\": periksa token; PAT fine-grained butuh izin Contents (Read and write) dan Metadata (Read) untuk repo tersebut.\n• \"Push ditolak\" atau remote punya commit baru: lakukan Pull dulu, lalu Push.\n• Tidak bisa membuka folder repo: pastikan izin Akses semua file aktif.\n• \"Repository terkunci\": ada file .git/index.lock sisa; hapus bila tidak ada operasi Git lain yang berjalan.\n• Reset HARD dan Buang perubahan tidak bisa dibatalkan; pastikan dulu isinya."
    )

    private val EN = listOf(
        "What is Git?" to "Git is a version control system: it records every change to your project files as a history. You can go back to an older state, try ideas on a separate line of work, and collaborate without overwriting each other's files.\n\nGitHub is an online service that hosts Git repositories. Git itself runs on your device; GitHub is used for backup and sharing.",
        "Basic terms" to "• Repository (repo): the project folder together with its whole history (the hidden .git folder).\n• Commit: one recorded change with a message.\n• Staging: choosing which files go into the next commit.\n• Branch: a separate line of work; the main branch is usually main.\n• Remote: a copy of the repo on a server, e.g. origin on GitHub.\n• HEAD: the commit you are currently on.\n• Conflict: two changes to the same part of a file that cannot be merged automatically.",
        "Git commands ↔ FuruGit buttons" to "• git init → Repositories tab › Init\n• git clone → Repositories tab › Clone\n• git status → Changes tab (list of changed files) and the active repo card\n• git add → Changes tab › Stage selected / Stage all\n• git restore --staged → Changes tab › long press a staged file (Unstage)\n• git restore / clean → Changes tab › Discard selected changes\n• git commit → Changes tab › Commit\n• git add + commit + push → Changes tab › Commit & Push\n• git diff → tap a file in the Changes tab\n• git branch / switch → Manage tab › Branch\n• git merge → Manage tab › Branch › tap a branch › Merge\n• git stash → Manage tab › Branch › Stash changes (Apply, Pop, Drop)\n• git remote → Manage tab › Synchronization › Remotes\n• git fetch / pull / push → Manage tab › Synchronization › Fetch / Pull / Push\n• git log → Actions tab › Commit history\n• git reset → Actions tab › Commit history › tap a commit › Reset soft / mixed / HARD\n• git revert → Actions tab › Commit history › tap a commit › Revert\n• git cherry-pick → Actions tab › Commit history › tap a commit › Cherry-pick\n• git tag → Actions tab › Commit history › Tags and Create tag here",
        "Everyday workflow" to "1. Set up your account: Settings › GitHub Settings › fill in Git Identity, then add a Personal Access Token or log in with OAuth.\n2. Repositories tab: Clone a repo from GitHub, or Open an existing repo folder on your phone.\n3. Edit files with other apps (an editor, a game, etc.).\n4. Changes tab: check files and Stage, write a message, Commit (or Commit & Push in one go).\n5. Manage tab › Synchronization: Pull first if needed, then Push to send to GitHub.",
        "How the app works" to "• FuruGit uses JGit, a pure-Java Git implementation, so it runs entirely on your device with no middle server.\n• Repos are opened directly from your phone's storage through the \"All files access\" permission, not as copies inside the app.\n• Every action that changes data asks for confirmation first.\n• Push always fetches first and refuses if the remote is newer; there is no force push.\n• Tokens are stored encrypted on the device and sent only to the matching host.\n• Because shared storage does not support Linux file modes and symlinks, core.filemode and core.symlinks are turned off automatically so files do not all look changed.\n• The Activity Log records the operations that were run, which helps when reporting a problem.",
        "Resolving conflicts" to "Conflicts appear on Merge or Pull. Open the Changes tab, Conflicts section, then choose per file: Mine (local version), Theirs (remote version), or Edit to edit the content and remove the <<<<<<<, =======, >>>>>>> markers. When every file is resolved, press Commit to finish the merge. Abort merge returns everything to the last commit.",
        "Troubleshooting" to "• \"Git name & email are not set\": fill in Git Identity in GitHub Settings.\n• \"Authentication failed\": check the token; a fine-grained PAT needs Contents (Read and write) and Metadata (Read) for that repo.\n• \"Push rejected\" or the remote has new commits: Pull first, then Push.\n• Cannot open a repo folder: make sure All files access is granted.\n• \"Repository is locked\": a leftover .git/index.lock file; delete it if no other Git operation is running.\n• Reset HARD and Discard changes cannot be undone; double-check first."
    )
}
