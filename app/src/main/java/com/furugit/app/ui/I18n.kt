package com.furugit.app.ui

import com.furugit.app.data.Prefs
import java.util.Locale

/**
 * Bahasa aplikasi. Sumber teks di kode berbahasa Indonesia; bahasa Inggris diterjemahkan
 * lewat kamus [D] (kecocokan persis, atau pola dengan {}) dan fungsi [tr] untuk teks baru.
 * Pilihan "Ikuti Sistem": Indonesia bila bahasa sistem Indonesia, selain itu Inggris.
 */
object L {
    const val SYSTEM = "system"
    const val EN = "en"
    const val ID = "id"

    @Volatile
    var isId: Boolean = false
        private set

    fun reload() {
        isId = when (Prefs.lang) {
            ID -> true
            EN -> false
            else -> Locale.getDefault().language.let { it == "id" || it == "in" }
        }
    }
}

/** Teks dua bahasa langsung di tempat pemakaian. */
fun tr(id: String, en: String): String = if (L.isId) id else en

private class Pat(val re: Regex, val en: String)

private val exact = HashMap<String, String>()
private val pats = ArrayList<Pat>()

private fun init() {
    if (exact.isNotEmpty()) return
    for ((id, en) in D) {
        if (id.contains("{}")) {
            val parts = id.split("{}")
            val re = Regex(parts.joinToString("(.*)") { Regex.escape(it) }, RegexOption.DOT_MATCHES_ALL)
            pats.add(Pat(re, en))
        } else exact[id] = en
    }
}

/** Terjemahkan teks sumber (Indonesia) sesuai bahasa terpilih. */
fun tx(s: CharSequence): String {
    val text = s.toString()
    if (L.isId || text.isEmpty()) return text
    init()
    exact[text]?.let { return it }
    for (p in pats) {
        val m = p.re.matchEntire(text) ?: continue
        var out = p.en
        for (i in 1 until m.groupValues.size) out = out.replaceFirst("{}", tx(m.groupValues[i]))
        return out
    }
    if (text.contains('\n')) return text.split('\n').joinToString("\n") { tx(it) }
    return text
}

private val D: List<Pair<String, String>> = listOf(
    // navigasi & judul
    "Repo" to "Repo", "Repositori" to "Repositories", "Perubahan" to "Changes", "Riwayat" to "History",
    "Pengaturan" to "Settings", "Log aktivitas" to "Activity log", "Log Aktivitas" to "Activity Log",
    "Pengaturan Github" to "GitHub Settings", "Pengaturan Aplikasi" to "App Settings", "Panduan" to "Guide",
    "Periksa Pembaruan" to "Check for Updates", "Tentang" to "About",
    "Jalankan Stage?" to "Run Stage?", "Stage semua file?" to "Stage all files?", "Semua perubahan di working tree akan di-stage." to "All changes in the working tree will be staged.",
    "Stage file terpilih?" to "Stage selected files?", "Pakai" to "Use", "Pindah" to "Switch", "Pop" to "Pop", "Apply" to "Apply", "Mulai" to "Start", "Reset" to "Reset",
    "Reset SOFT?" to "Reset SOFT?", "Reset MIXED?" to "Reset MIXED?", "Unduh" to "Download", "Pasang" to "Install", "Buka email" to "Open email",
    "Pasang pembaruan?" to "Install update?", "Installer sistem akan terbuka dan meminta persetujuanmu." to "The system installer will open and ask for your approval.",
    "Unduh pembaruan?" to "Download update?", "File APK akan diunduh dari GitHub ({} MB)." to "The APK file will be downloaded from GitHub ({} MB).",
    "Kirim laporan?" to "Send report?", "Aplikasi email akan terbuka dengan laporan yang sudah terisi." to "The email app opens with the report filled in.",
    "Semua catatan aktivitas dihapus." to "All activity records are deleted.",
    "Rilis ini tidak punya file APK." to "This release has no APK file.", "Unduhan gagal: HTTP {}" to "Download failed: HTTP {}",
    "Ukuran file tidak cocok. Coba unduh lagi." to "File size mismatch. Try downloading again.", "Gagal menyimpan file unduhan." to "Failed to save the downloaded file.",
    "Periksa pembaruan: {}" to "Check for updates: {}", "✘ Periksa pembaruan: {}" to "✘ Check for updates: {}", "▶ Unduh pembaruan {}" to "▶ Download update {}",
    "✔ Unduh pembaruan {}" to "✔ Download update {}", "✘ Unduh pembaruan: {}" to "✘ Download update: {}",
    // tombol & dialog umum
    "Ya" to "Yes", "Batal" to "Cancel", "Tutup" to "Close", "Hapus" to "Delete", "Simpan" to "Save",
    "Tersimpan" to "Saved", "Tambah" to "Add", "Bersihkan" to "Clear", "Pilih" to "Choose",
    "Konfirmasi" to "Confirm", "Lanjutkan" to "Continue", "Jalankan" to "Run", "Perhatian" to "Notice",
    "{} gagal" to "{} failed",
    // Repo
    "Buka folder" to "Open folder", "Belum ada repository. Clone dari GitHub, buat baru dengan Init, atau buka folder repo yang sudah ada di HP." to "No repositories yet. Clone from GitHub, create one with Init, or open an existing repo folder on your phone.",
    "{}   ● aktif" to "{}   ● active",
    "Memuat status…" to "Loading status…",
    "Tap = jadikan aktif · tekan lama = hapus dari daftar (file di HP tidak dihapus)." to "Tap = make active · long press = remove from list (files on your phone are not deleted).",
    "Status {}" to "Status {}", "Bersih, tidak ada perubahan" to "Clean, no changes", "{} file berubah" to "{} file(s) changed",
    "Status: {}" to "State: {}", "(belum ada)" to "(none)",
    "Clone repository" to "Clone repository", "URL repository (https://github.com/user/repo)" to "Repository URL (https://github.com/user/repo)",
    "Nama folder" to "Folder name", "Folder induk: {}" to "Parent folder: {}", "Pilih folder induk" to "Choose parent folder", "Folder induk" to "Parent folder",
    "URL dan nama folder wajib diisi" to "URL and folder name are required",
    "Pilih folder untuk Init" to "Choose a folder for Init", "Pilih folder repository" to "Choose repository folder",
    "Buka {}" to "Open {}", "Bukan repository" to "Not a repository",
    "Folder ini belum punya .git. Jalankan Init di sini?" to "This folder has no .git. Run Init here?",
    "Hapus dari daftar?" to "Remove from list?", "{}\n{}\n\nFile repository di HP TIDAK dihapus." to "{}\n{}\n\nThe repository files on your phone are NOT deleted.",
    "Jalankan Clone?" to "Run Clone?", "Repository akan diunduh ke:\n{}" to "The repository will be downloaded to:\n{}",
    "Jalankan Init?" to "Run Init?", "Membuat repository Git baru di:\n{}" to "Creates a new Git repository in:\n{}",
    // Changes
    "Operasi merge sedang berlangsung ({})" to "A merge is in progress ({})",
    "Selesaikan konflik, lalu Commit untuk menyelesaikan merge." to "Resolve the conflicts, then Commit to finish the merge.",
    "Batalkan merge" to "Abort merge", "Batalkan merge?" to "Abort merge?", "Batalkan" to "Abort",
    "Working tree dikembalikan ke commit terakhir (HEAD)." to "The working tree is restored to the last commit (HEAD).",
    "Konflik ({})" to "Conflicts ({})", "Milik saya" to "Mine", "Milik remote" to "Theirs", "Edit" to "Edit",
    "Di-stage ({})" to "Staged ({})", "Unstage?" to "Unstage?", "Tap = lihat diff · tekan lama = unstage" to "Tap = view diff · long press = unstage",
    "Belum di-stage ({})" to "Not staged ({})", "Tidak ada perubahan di working tree." to "No changes in the working tree.",
    "Stage terpilih" to "Stage selected", "Stage semua" to "Stage all", "Buang perubahan terpilih" to "Discard selected changes",
    "Commit" to "Commit", "Pesan commit" to "Commit message", "Deskripsi (opsional)" to "Description (optional)", "Commit & Push" to "Commit & Push",
    "Commit = hanya yang sudah di-stage. Commit & Push = otomatis stage file tercentang → commit → cek remote → push." to "Commit = staged files only. Commit & Push = automatically stage checked files → commit → check remote → push.",
    "Pilih file dulu" to "Select files first", "Stage {} file" to "Stage {} file(s)",
    "Perubahan di {} file akan HILANG permanen." to "Changes in {} file(s) will be permanently LOST.",
    "\n{} file baru (untracked) akan DIHAPUS dari HP." to "\n{} new (untracked) file(s) will be DELETED from your phone.",
    "Buang perubahan?" to "Discard changes?", "Buang" to "Discard", "Buang {} file" to "Discard {} file(s)",
    "Commit {} dibuat" to "Commit {} created", "Selesaikan {}" to "Resolve {}", "Baca {}" to "Read {}", "Simpan {}" to "Save {}",
    "Simpan & tandai selesai" to "Save & mark resolved", "Hapus penanda <<<<<<<, =======, >>>>>>> lalu pilih isi akhirnya." to "Remove the <<<<<<<, =======, >>>>>>> markers, then choose the final content.",
    "Isi file" to "File content", "Masih ada penanda konflik" to "Conflict markers remain",
    "Hapus baris <<<<<<< / ======= / >>>>>>> dulu, lalu simpan lagi." to "Remove the <<<<<<< / ======= / >>>>>>> lines first, then save again.",
    "Stage file terpilih?" to "Stage selected files?", "Stage semua file?" to "Stage all files?",
    "Buat commit?" to "Create commit?", "Hanya file yang sudah di-stage yang masuk ke commit." to "Only staged files go into the commit.",
    "Commit & Push?" to "Commit & Push?", "File tercentang akan di-stage, di-commit, lalu dikirim ke remote." to "Checked files will be staged, committed, then pushed to the remote.",
    "Pakai versi milik saya?" to "Use my version?", "Pakai versi milik remote?" to "Use the remote version?",
    "Konflik pada {} diselesaikan dengan versi tersebut." to "The conflict in {} is resolved with that version.",
    // Branches
    "Branch baru" to "New branch", "Aktif: {}" to "Active: {}", "⚠ Ada merge yang belum selesai. Selesaikan di tab Perubahan." to "⚠ A merge is unfinished. Finish it in the Changes tab.",
    "Lokal" to "Local", "Belum ada branch (buat commit pertama dulu)." to "No branches yet (make the first commit first).", "Branch aktif" to "Active branch",
    "Stash perubahan" to "Stash changes", "Hapus semua" to "Delete all", "Tidak ada stash" to "No stashes", "Tidak ada stash." to "No stashes.",
    "Hapus semua stash?" to "Delete all stashes?", "Tidak bisa dikembalikan." to "This cannot be undone.", "Hapus semua stash" to "Delete all stashes",
    "Branch baru (langsung pindah)" to "New branch (switch immediately)", "Nama branch, mis. feature-translation" to "Branch name, e.g. feature-translation",
    "Buat branch {}" to "Create branch {}", "Checkout sebagai branch lokal" to "Checkout as local branch", "Pindah ke branch ini" to "Switch to this branch",
    "Pindah ke {}" to "Switch to {}", "Merge ke {}" to "Merge into {}", "Merge?" to "Merge?", "Gabungkan {} ke {}." to "Merge {} into {}.", "Merge {}" to "Merge {}",
    "Hapus branch" to "Delete branch", "Hapus paksa (belum di-merge)" to "Force delete (not merged)", "Ini branch yang sedang aktif." to "This is the active branch.",
    "Hapus branch?" to "Delete branch?", "Hapus branch {}" to "Delete branch {}", "Pesan (opsional)" to "Message (optional)", "Sertakan file baru (untracked)" to "Include new (untracked) files",
    "Stash" to "Stash", "Apply (stash tetap ada)" to "Apply (keep the stash)", "Pop (apply lalu hapus)" to "Pop (apply, then delete)", "Drop (hapus)" to "Drop (delete)",
    "Apply stash" to "Apply stash", "Pop stash" to "Pop stash", "Drop stash" to "Drop stash", "Drop stash?" to "Drop stash?", "Drop" to "Drop",
    "Pindah branch?" to "Switch branch?", "Working tree akan berpindah ke branch tersebut." to "The working tree will switch to that branch.",
    "Jalankan Stash?" to "Run Stash?", "Perubahan disimpan ke stash dan working tree dibersihkan." to "Changes are saved to the stash and the working tree is cleaned.",
    "Apply stash?" to "Apply stash?", "Perubahan dari stash diterapkan; stash tetap ada." to "Stash changes are applied; the stash is kept.",
    "Pop stash?" to "Pop stash?", "Perubahan dari stash diterapkan lalu stash dihapus." to "Stash changes are applied, then the stash is deleted.",
    "Buat branch?" to "Create branch?", "Branch {} dibuat dan langsung dipindah ke sana." to "Branch {} is created and switched to immediately.",
    // Sync
    "Repository ini belum punya remote." to "This repository has no remote yet.", "Tambah remote" to "Add remote", "(belum dikirim)" to "(not pushed)",
    "Branch: {}" to "Branch: {}", "Remote: {}" to "Remote: {}",
    "⚠ Belum ada akun untuk host ini. Repo publik bisa fetch/pull; push & repo private butuh akun di Pengaturan." to "⚠ No account for this host. Public repos can fetch/pull; push and private repos need an account in Settings.",
    "Akun: {}" to "Account: {}", "Fetch" to "Fetch", "Pull" to "Pull", "Push" to "Push", "Fetch {}" to "Fetch {}", "Pull {}" to "Pull {}", "Push {}" to "Push {}",
    "Pull dengan rebase (lanjutan; default: merge)" to "Pull with rebase (advanced; default: merge)",
    "Push selalu fetch dulu dan menolak jika remote lebih baru. Tidak ada push paksa." to "Push always fetches first and refuses if the remote is newer. There is no force push.",
    "Daftar remote" to "Remotes", "Jadikan remote aktif" to "Make active remote", "Ubah URL" to "Change URL", "Hapus remote" to "Delete remote",
    "URL baru untuk {}" to "New URL for {}", "Ubah URL {}" to "Change URL {}", "Hapus remote?" to "Delete remote?", "Hapus remote {}" to "Delete remote {}",
    "Nama" to "Name", "Tambah remote {}" to "Add remote {}",
    "Jalankan Fetch?" to "Run Fetch?", "Mengambil data terbaru dari {} tanpa mengubah file." to "Gets the latest data from {} without changing your files.",
    "Jalankan Pull?" to "Run Pull?", "Mengambil dan menggabungkan perubahan dari {} ke branch aktif." to "Fetches and merges changes from {} into the active branch.",
    "Jalankan Push?" to "Run Push?", "Mengirim commit branch aktif ke {}." to "Sends the active branch's commits to {}.",
    "Ubah URL remote?" to "Change remote URL?", "Tambah remote?" to "Add remote?",
    // History
    "Belum ada commit." to "No commits yet.", "Menampilkan maksimal 150 commit terbaru." to "Shows at most the 150 latest commits.",
    "Daftar branch" to "Branch list", "Lihat riwayat branch" to "View branch history",
    "Cherry-pick ke branch aktif" to "Cherry-pick to the active branch", "Revert commit ini" to "Revert this commit",
    "Reset soft ke sini (simpan perubahan, staged)" to "Reset soft to here (keep changes, staged)",
    "Reset mixed ke sini (simpan perubahan, unstaged)" to "Reset mixed to here (keep changes, unstaged)",
    "Reset HARD ke sini (buang perubahan)" to "Reset HARD to here (discard changes)",
    "Buat tag di sini" to "Create tag here", "Salin hash" to "Copy hash",
    "Cherry-pick?" to "Cherry-pick?", "Salin commit {} ke branch aktif." to "Copy commit {} to the active branch.", "Cherry-pick {}" to "Cherry-pick {}",
    "Revert?" to "Revert?", "Membuat commit baru yang membatalkan {}." to "Creates a new commit that undoes {}.", "Revert {}" to "Revert {}",
    "Reset HARD?" to "Reset HARD?", "Semua perubahan yang belum di-commit akan HILANG dan branch dipindah ke {}." to "All uncommitted changes will be LOST and the branch moves to {}.",
    "Reset {}?" to "Reset {}?", "Branch dipindah ke {}; perubahan tetap disimpan." to "The branch moves to {}; changes are kept.",
    "Nama tag" to "Tag name", "mis. v1.0.0" to "e.g. v1.0.0", "Tag {}" to "Tag {}", "Hash disalin" to "Hash copied",
    "Belum ada tag. Tekan sebuah commit → Buat tag di sini." to "No tags yet. Tap a commit → Create tag here.",
    "Push tag ke remote" to "Push tag to remote", "Hapus tag lokal" to "Delete local tag", "Push tag {}" to "Push tag {}", "Hapus tag {}" to "Delete tag {}",
    "Push tag?" to "Push tag?", "Hapus tag?" to "Delete tag?", "Buat tag?" to "Create tag?", "Tag {} dibuat di commit {}." to "Tag {} is created on commit {}.",
    // BaseFragment
    "Belum ada repository aktif" to "No active repository",
    "Buka tab Repo, lalu Clone, Init, atau Buka folder repository yang sudah ada." to "Open the Repo tab, then Clone, Init, or open an existing repository folder.",
    "File:\n{}" to "Files:\n{}",
    // Helpers
    "Pilih folder ini" to "Choose this folder", "Folder baru" to "New folder", "Nama folder" to "Folder name",
    "Izin akses file" to "File access permission",
    "FuruGit perlu izin \"Akses semua file\" agar bisa membuka dan mengubah folder repository langsung di penyimpanan HP." to "FuruGit needs the \"All files access\" permission to open and change repository folders directly in your phone's storage.",
    "Beri izin" to "Grant permission", "Nanti" to "Later",
    // GitEngine / pesan
    "Folder ini bukan repository Git: {}" to "This folder is not a Git repository: {}", "Folder ini sudah menjadi repository Git." to "This folder is already a Git repository.",
    "Folder tujuan tidak kosong: {}" to "The target folder is not empty: {}",
    "Nama & email Git belum diatur. Buka Pengaturan → Identitas Git." to "Git name & email are not set. Open Settings → GitHub Settings → Git Identity.",
    "Pesan commit tidak boleh kosong." to "The commit message cannot be empty.", "Masih ada file konflik. Selesaikan dulu di tab Perubahan." to "Conflicting files remain. Resolve them in the Changes tab first.",
    "Tidak ada perubahan yang di-stage." to "There are no staged changes.", "Nama branch tidak valid." to "Invalid branch name.",
    "Buat commit pertama dulu sebelum membuat branch." to "Make the first commit before creating a branch.",
    "Ada perubahan yang belum di-commit. Commit atau stash dulu agar tidak tertimpa." to "There are uncommitted changes. Commit or stash first so they are not overwritten.",
    "Branch belum di-merge. Hapus paksa jika kamu yakin." to "The branch is not merged. Force delete if you are sure.",
    "Merge menghasilkan konflik pada {} file." to "The merge produced conflicts in {} file(s).", "Merge gagal: file lokal akan tertimpa. Commit/stash dulu." to "Merge failed: local files would be overwritten. Commit/stash first.",
    "Merge berhasil ({})." to "Merge succeeded ({}).", "Merge tidak berhasil: {}" to "Merge did not succeed: {}",
    "Ada perubahan yang belum di-commit. Commit atau stash dulu sebelum merge." to "There are uncommitted changes. Commit or stash before merging.",
    "Branch '{}' tidak ditemukan." to "Branch '{}' not found.", "Tidak ada perubahan untuk di-stash." to "There are no changes to stash.",
    "Nama dan URL remote wajib diisi." to "Remote name and URL are required.", "URL remote tidak valid." to "Invalid remote URL.",
    "HEAD sedang detached. Pindah ke sebuah branch dulu." to "HEAD is detached. Switch to a branch first.",
    "Rebase butuh working tree bersih. Commit atau stash dulu." to "Rebase needs a clean working tree. Commit or stash first.",
    "Pull berhasil." to "Pull succeeded.", "Rebase menemui konflik dan dibatalkan. Coba Pull dengan mode Merge." to "Rebase hit conflicts and was aborted. Try Pull in Merge mode.",
    "Pull tidak berhasil." to "Pull did not succeed.", "Push ditolak ({}" to "Push rejected ({}",
    "Belum ada commit untuk dikirim." to "There are no commits to send.", "Sudah up to date, tidak ada yang dikirim." to "Already up to date, nothing was sent.",
    "Push ke {} berhasil." to "Push to {} succeeded.",
    "Remote punya {} commit baru yang belum ada di lokal. Lakukan Pull dulu, lalu Push." to "The remote has {} new commit(s) that are not local yet. Pull first, then Push.",
    "Commit {} dibuat, tetapi repository belum punya remote. Tambahkan di tab Sync." to "Commit {} was created, but the repository has no remote. Add one in the Sync tab.",
    "Commit {} sudah tersimpan lokal, tetapi push gagal: {}" to "Commit {} is saved locally, but the push failed: {}",
    "Ada perubahan yang belum di-commit. Commit atau stash dulu." to "There are uncommitted changes. Commit or stash first.",
    "Revert menyebabkan konflik, sehingga dibatalkan. Tidak ada yang berubah." to "Revert caused conflicts and was aborted. Nothing changed.",
    "Commit di-revert (commit pembatalan baru dibuat)." to "Commit reverted (a new undo commit was created).", "Cherry-pick berhasil." to "Cherry-pick succeeded.",
    "Cherry-pick gagal/konflik ({}) dan dibatalkan. Tidak ada yang berubah." to "Cherry-pick failed/conflicted ({}) and was aborted. Nothing changed.",
    "Nama tag tidak valid." to "Invalid tag name.", "Belum ada remote." to "No remote yet.", "Terjadi kesalahan." to "An error occurred.",
    "Tidak ada koneksi internet / host tidak ditemukan." to "No internet connection / host not found.",
    "Autentikasi gagal. Periksa akun/token di Pengaturan (PAT butuh izin Contents: Read and write)." to "Authentication failed. Check the account/token in Settings (a PAT needs Contents: Read and write).",
    "Repository terkunci (.git/index.lock). Jika tidak ada operasi Git lain, hapus file itu." to "The repository is locked (.git/index.lock). If no other Git operation is running, delete that file.",
    "Tidak punya izin akses file. Pastikan izin 'Akses semua file' sudah diberikan." to "No file access permission. Make sure 'All files access' is granted.",
    "Ada perubahan lokal yang bentrok: {}" to "Local changes conflict: {}",
    "Token ditolak oleh GitHub (HTTP {})." to "The token was rejected by GitHub (HTTP {}).", "GitHub membalas HTTP {}." to "GitHub replied HTTP {}.",
    "Gagal memulai login: {}" to "Failed to start login: {}", "Kode kedaluwarsa. Ulangi login." to "The code expired. Log in again.",
    "Login dibatalkan di GitHub." to "Login was cancelled on GitHub.", "Login gagal: {}" to "Login failed: {}",
    "FuruGit dimulai" to "FuruGit started", "Akun {} disimpan" to "Account {} saved", "✔ Login GitHub sebagai {}" to "✔ Logged in to GitHub as {}", "✘ Login GitHub: {}" to "✘ GitHub login: {}",
    "Commit {} dibuat" to "Commit {} created", "Validasi token GitHub" to "Validate GitHub token",
    // Pengaturan GitHub
    "Izin penyimpanan" to "Storage permission", "✔ Akses semua file aktif" to "✔ All files access is on",
    "✘ Belum diberi izin. Tanpa ini FuruGit tidak bisa membuka folder repo." to "✘ Not granted. Without it FuruGit cannot open repo folders.",
    "Identitas Git" to "Git Identity", "Dipakai sebagai author/committer pada setiap commit." to "Used as author/committer for every commit.",
    "Simpan identitas" to "Save identity", "Simpan identitas?" to "Save identity?", "Nama dan email ini dipakai pada setiap commit berikutnya." to "This name and email are used on every following commit.",
    "Akun & autentikasi" to "Accounts & Authentication", "Gagal membuka penyimpanan aman: {}" to "Failed to open secure storage: {}", "Belum ada akun." to "No accounts yet.",
    "{} · {} · token tersimpan terenkripsi" to "{} · {} · token stored encrypted", "Tambah Personal Access Token" to "Add Personal Access Token",
    "Login GitHub (OAuth Device Flow) butuh Client ID dari GitHub App / OAuth App milikmu dengan \"Device Flow\" diaktifkan. Jangan memasukkan client secret." to "GitHub login (OAuth Device Flow) needs a Client ID from your own GitHub App / OAuth App with \"Device Flow\" enabled. Never enter a client secret.",
    "GitHub Client ID" to "GitHub Client ID", "Login GitHub (OAuth)" to "GitHub login (OAuth)", "Login GitHub" to "GitHub login",
    "Hapus akun?" to "Delete account?", "Personal Access Token" to "Personal Access Token",
    "Fine-grained PAT: izin Contents (Read and write) + Metadata (Read). Diperlakukan seperti password." to "Fine-grained PAT: Contents (Read and write) + Metadata (Read) permissions. Treat it like a password.",
    "Username (opsional untuk GitHub)" to "Username (optional for GitHub)", "Token kosong" to "Token is empty",
    "Client ID kosong" to "Client ID is empty", "Isi GitHub Client ID dulu, atau pakai Personal Access Token." to "Enter a GitHub Client ID first, or use a Personal Access Token.",
    "Buka {} lalu masukkan kode:\n\n{}\n\nMenunggu persetujuan…" to "Open {} and enter the code:\n\n{}\n\nWaiting for approval…",
    "Salin kode & buka GitHub" to "Copy code & open GitHub", "Simpan token?" to "Save token?", "Token akan disimpan terenkripsi di perangkat ini." to "The token is stored encrypted on this device.",
    "Mulai login GitHub?" to "Start GitHub login?", "Kode perangkat akan dibuat dan kamu diminta menyetujuinya di GitHub." to "A device code is created and you approve it on GitHub.",
    "Login gagal" to "Login failed",
    // Log
    "Belum ada aktivitas." to "No activity yet.", "Bersihkan log?" to "Clear the log?",
    "Salin log" to "Copy log", "Log disalin" to "Log copied",
)
