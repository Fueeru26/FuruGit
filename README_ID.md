<p align="center">
  <img src="docs/pictures/icon.png" alt="FuruGit" width="128">
</p>

<p align="center">
  <a href="LICENSE"><img alt="License: GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-blue.svg"></a>
  <img alt="Platform: Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-1.9.22-7F52FF.svg">
  <img alt="JGit" src="https://img.shields.io/badge/JGit-5.13-F05032.svg">
  <a href="https://github.com/Fueeru26/FuruGit/releases"><img alt="Release" src="https://img.shields.io/github/v/release/Fueeru26/FuruGit?include_prereleases"></a>
  <a href="https://github.com/Fueeru26/FuruGit/actions/workflows/build.yml"><img alt="Build" src="https://github.com/Fueeru26/FuruGit/actions/workflows/build.yml/badge.svg"></a>
  <a href="https://github.com/Fueeru26/FuruGit/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/Fueeru26/FuruGit/total"></a>
</p>

# FuruGit

[English](README.md) | **Bahasa Indonesia**

**FuruGit** adalah klien Git untuk Android yang menjalankan perintah Git lewat **tombol, bukan mengetik command**. Clone, stage, commit, branch, merge, stash, pull, push, lihat riwayat, dan selesaikan konflik, semuanya di HP, langsung pada repository di penyimpananmu. Aplikasi memakai [JGit](https://www.eclipse.org/jgit/), jadi semuanya berjalan di perangkat tanpa server perantara.

Antarmuka tersedia dalam **bahasa Indonesia dan Inggris** (mengikuti bahasa sistem secara bawaan), dengan tema terang dan gelap bergaya Blue Blossom yang sama dengan FuruArc.

> **Catatan**
> FuruGit mengubah file di repository-mu. Setiap tombol yang menjalankan aksi meminta konfirmasi dulu, tetapi aksi seperti *Buang perubahan* dan *Reset HARD* tidak bisa dibatalkan.

## Isi

- [Fitur](#fitur)
- [Pengaturan](#pengaturan)
- [Masuk ke GitHub](#masuk-ke-github)
- [Instalasi](#instalasi)
- [Membangun dari source](#membangun-dari-source)
- [Struktur proyek](#struktur-proyek)
- [Dokumentasi](#dokumentasi)
- [Berkontribusi](#berkontribusi)
- [Lisensi](#lisensi)
- [Kredit](#kredit)

## Fitur

| Tab | Fungsi |
| --- | --- |
| **Repo** | Clone dari GitHub, Init repository baru, buka folder repository yang sudah ada (lewat path asli, mis. `/storage/emulated/0/FuruArc/Github/FuruArc`), pilih repository aktif. |
| **Perubahan** | Status, Stage terpilih / Stage semua, Unstage, Buang perubahan terpilih, diff berwarna, Commit (dengan tombol **Pesan terakhir**), **Commit & Push** (stage, commit, cek remote, push), penyelesaian konflik (Milik saya / Milik remote / Edit manual), Batalkan merge. |
| **Branch** | Branch lokal dan remote; buat, pindah, merge, dan hapus; Stash (simpan, apply, pop, drop, hapus semua). |
| **Sync** | Fetch, Pull (merge atau rebase), Push aman (selalu fetch dulu, tidak pernah memaksa), kelola remote. |
| **Riwayat** | Log per branch, Cherry-pick, Revert, Reset soft / mixed / HARD, Tag (buat, push, hapus), salin hash commit. |

Perintah Git dan tombolnya:

| Perintah | Di FuruGit |
| --- | --- |
| `git init` / `git clone` | Repo > Init / Clone |
| `git status` / `git diff` | Tab Perubahan, ketuk file untuk melihat diff |
| `git add` / `git restore --staged` | Perubahan > Stage terpilih, Stage semua / tekan lama file yang di-stage |
| `git commit` | Perubahan > Commit |
| `git add` + `commit` + `push` | Perubahan > Commit & Push |
| `git branch` / `git switch` / `git merge` | Tab Branch |
| `git stash` | Branch > Stash perubahan |
| `git fetch` / `pull` / `push` / `remote` | Tab Sync |
| `git log` / `reset` / `revert` / `cherry-pick` / `tag` | Tab Riwayat |

**Konfirmasi sebelum setiap aksi.** Tombol yang mengeksekusi sesuatu (stage, commit, commit & push, fetch, pull, push, pindah branch, apply / pop stash, reset, push atau hapus tag, selesaikan konflik, init, simpan identitas, login GitHub, pasang pembaruan) membuka jendela konfirmasi lebih dulu.

**Aman sejak rancangan.** Push selalu fetch dulu dan menolak bila remote lebih baru; tidak ada push paksa. Di penyimpanan bersama, `core.filemode` dan `core.symlinks` dimatikan otomatis agar file tidak terlihat berubah semuanya.

## Pengaturan

Ketuk ikon gear di bilah atas.

- **Pengaturan Github**: izin penyimpanan, identitas Git (nama dan email untuk commit), akun dan autentikasi.
- **Pengaturan Aplikasi**: mode tema (Ikuti Sistem, Terang, Gelap) dan bahasa (Ikuti Sistem, Inggris, Indonesia). *Ikuti Sistem* memakai bahasa Indonesia bila bahasa perangkat Indonesia, selain itu Inggris.
- **Log Aktivitas**: operasi yang pernah dijalankan; bisa disalin atau dibersihkan.
- **Panduan**: apa itu Git, perintahnya, cara kerja aplikasi, menyelesaikan konflik, dan pemecahan masalah.
- **Periksa Pembaruan**: mencari rilis yang lebih baru di repo ini, menampilkan catatannya, mengunduh APK, dan membuka installer sistem.
- **Tentang**: pengembang, kontak, donasi, lapor masalah lewat email, dan Tentang Aplikasi.

## Masuk ke GitHub

- **Personal Access Token (PAT)**: Pengaturan > Pengaturan Github > Tambah Personal Access Token. Untuk token fine-grained beri repo izin *Contents: Read and write* dan *Metadata: Read*. Token disimpan terenkripsi dengan Android Keystore.
- **OAuth Device Flow**: buat GitHub App atau OAuth App milikmu sendiri, aktifkan **Device Flow**, isi *Client ID*-nya di aplikasi, lalu login dengan kode yang tampil. Jangan pernah memasukkan client secret ke aplikasi.

Repository publik bisa di-fetch dan di-pull tanpa akun; push dan repository private butuh akun.

## Instalasi

Unduh APK terbaru dari halaman [Releases](https://github.com/Fueeru26/FuruGit/releases) lalu pasang. Butuh **Android 8.0 (API 26)** atau lebih baru. Setiap rilis mencantumkan checksum SHA-256 APK (dan melampirkan `SHA256SUMS.txt`) untuk memeriksa unduhan. Saat pertama dibuka, beri izin **Akses semua file** agar FuruGit bisa membuka folder repository.

## Membangun dari source

Kebutuhan: JDK 17 dan Android SDK (platform 34). Gradle wrapper sudah disertakan.

```bash
git clone https://github.com/Fueeru26/FuruGit.git
cd FuruGit
./gradlew assembleDebug
```

APK debug ada di `app/build/outputs/apk/debug/`. Folder ini juga bisa dibuka di AndroidIDE atau Android Studio.

| Komponen | Versi |
| --- | --- |
| Android Gradle Plugin | 8.2.2 |
| Gradle | 8.2 |
| Kotlin | 1.9.22 |
| Mesin Git | JGit 5.13.3 |
| UI | View + Material Components 1.11 |
| compileSdk / targetSdk / minSdk | 34 / 34 / 26 |

> **Tanda tangan**
> Tidak ada keystore atau password di repository ini. Build debug tidak perlu tanda tangan. Untuk rilis bertanda tangan, sediakan keystore sendiri lewat variabel lingkungan `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, dan `ANDROID_KEY_PASSWORD`, atau lewat `keystore.properties` yang tidak di-commit di root proyek (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Tanpa itu `assembleRelease` menghasilkan APK tanpa tanda tangan.

## Struktur proyek

```
FuruGit/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/                          Tema (satu per warna aksen), font (Qanelas), ikon, layout
│       └── java/com/furugit/app/
│           ├── App.kt                    Application: bahasa, mode tema, setup JGit
│           ├── data/                     Model, preferensi, akun terenkripsi, autentikasi GitHub, pembaruan
│           ├── git/GitEngine.kt          Semua operasi Git (JGit)
│           └── ui/                       Layar, helper UI, palet (Pal.kt), terjemahan (I18n.kt)
├── docs/                                 FAQ dan Privasi (Inggris dan Indonesia), gambar
├── .github/                              Template issue dan PR, workflow (build, release), Dependabot, funding
├── README.md                             README dalam bahasa Inggris
├── CHANGELOG.md                          Catatan rilis
├── THIRD_PARTY_NOTICES.md
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── SECURITY.md
└── LICENSE
```

## Dokumentasi

- [docs/FAQ_ID.md](docs/FAQ_ID.md): pertanyaan umum dan pemecahan masalah.
- [docs/PRIVACY_ID.md](docs/PRIVACY_ID.md): apa yang disimpan di perangkat dan kapan aplikasi memakai internet.
- [CHANGELOG.md](CHANGELOG.md): catatan rilis.

## Berkontribusi

Laporan bug dan pull request sangat diterima. Baca [CONTRIBUTING.md](CONTRIBUTING.md) lebih dulu dan ikuti [Code of Conduct](CODE_OF_CONDUCT.md). Untuk masalah keamanan lihat [SECURITY.md](SECURITY.md).

## Lisensi

FuruGit berlisensi **GNU General Public License v3.0**. Lihat [LICENSE](LICENSE). Library pihak ketiga tetap memakai lisensinya sendiri, lihat [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Kredit

- Dibuat oleh **Fueeru** ([@Fueeru26](https://github.com/Fueeru26)).
- [JGit](https://www.eclipse.org/jgit/) (Eclipse Foundation): mesin Git.
- Desain mengikuti aplikasi lain milik pembuatnya, [FuruArc](https://github.com/Fueeru26/FuruArc).
