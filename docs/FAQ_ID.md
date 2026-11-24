# FAQ dan pemecahan masalah

[English](FAQ.md) | **Bahasa Indonesia**

## Umum

**Versi Android apa yang didukung?**
Android 8.0 (API 26) dan lebih baru.

**Kenapa FuruGit butuh "Akses semua file"?**
Git bekerja pada folder asli. FuruGit membuka repository lewat path aslinya (mis. `/storage/emulated/0/RepoKu`), tidak menyalinnya, dan itu butuh izin ini. Tanpa izin, aplikasi tidak bisa membuka atau mengubah folder repository.

**Apakah file saya diunggah ke suatu tempat?**
Tidak. Repository diproses di perangkatmu. Data keluar hanya saat kamu Clone, Fetch, Pull, atau Push ke remote pilihanmu. Lihat [PRIVACY_ID.md](PRIVACY_ID.md).

**Apakah FuruGit menjalankan command line?**
Tidak. Aplikasi memakai JGit, implementasi Git dalam Java, jadi tidak ada program `git` dan tidak ada terminal.

## Masuk dan sinkronisasi

**"Autentikasi gagal".**
Periksa tokenmu di Pengaturan > Pengaturan Github. Personal Access Token fine-grained butuh *Contents: Read and write* dan *Metadata: Read* untuk repository itu. Repository private dan push selalu butuh akun.

**"Push ditolak" atau "remote punya commit baru".**
Ada yang push lebih dulu (atau perangkat lain). Buka tab Sync, Pull, lalu Push lagi. FuruGit tidak pernah push paksa.

**Login GitHub (OAuth) tidak mulai.**
Isi Client ID GitHub App / OAuth App milikmu dengan **Device Flow** aktif. Jangan pernah memasukkan client secret.

**"Nama & email Git belum diatur".**
Isi Pengaturan > Pengaturan Github > Identitas Git.

## File dan repository

**Semua file terlihat berubah.**
FuruGit mematikan `core.filemode` dan `core.symlinks` saat membuka repository, karena penyimpanan bersama tidak mendukung izin file Linux. Jika repository dibuka oleh alat lain, tutup dan buka lagi di tab Repo FuruGit. Perbedaan akhir baris (CRLF / LF) juga bisa membuat file terlihat berubah.

**"Repository terkunci" (`.git/index.lock`).**
Operasi Git sebelumnya terputus. Bila tidak ada aplikasi Git lain yang berjalan, hapus file `.git/index.lock`.

**Tidak bisa membuka folder repository.**
Pastikan "Akses semua file" sudah diberikan (Pengaturan > Pengaturan Github > Izin penyimpanan).

## Merge dan membatalkan

**Bagaimana menyelesaikan konflik?**
Tab Perubahan > Konflik: pilih Milik saya, Milik remote, atau Edit untuk tiap file, lalu Commit untuk menuntaskan merge. Batalkan merge mengembalikan ke commit terakhir.

**Aksi apa yang tidak bisa dibatalkan?**
*Buang perubahan terpilih* dan *Reset HARD* menghapus pekerjaan yang belum di-commit secara permanen. *Reset soft / mixed* menyimpan perubahanmu. Pekerjaan yang sudah di-commit biasanya bisa dipulihkan lewat riwayat (Reset hanya memindahkan branch).

## Pembaruan

**Bagaimana pembaruan bekerja?**
Pengaturan > Periksa Pembaruan membaca rilis `Fueeru26/FuruGit`. Bila ada versi lebih baru, kamu bisa mengunduh APK dan membuka installer sistem. Android meminta izin memasang dari FuruGit pada pemasangan pertama.
