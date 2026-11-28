# Privasi

[English](PRIVACY.md) | **Bahasa Indonesia**

FuruGit bekerja pada repository yang kamu pilih, langsung di perangkatmu. Tidak ada akun FuruGit, dan aplikasi tidak memuat library analitik, pelaporan crash, maupun iklan.

## File kamu

Repository dibuka langsung dari penyimpanan HP lewat izin "Akses semua file" dan diproses lokal oleh JGit. FuruGit hanya mengirim data repository ke remote Git yang kamu pilih untuk fetch, pull, atau push.

## Kapan aplikasi memakai jaringan

Tidak ada yang dikirim kecuali kamu memakai salah satu fitur ini:

| Fitur | Terhubung ke | Yang dikirim |
| --- | --- | --- |
| Clone, Fetch, Pull, Push | URL remote repository-mu (mis. `github.com`) | Lalu lintas protokol Git, dan token aksesmu sebagai kredensial untuk host itu. |
| Cek token dan login OAuth | `api.github.com`, `github.com` | Token yang kamu masukkan (untuk membaca username), atau Client ID dan kode perangkat untuk OAuth Device Flow. |
| Periksa pembaruan | `api.github.com` (GitHub Releases `Fueeru26/FuruGit`) | Permintaan web biasa, dikirim saat aplikasi dibuka (paling sering tiap 6 jam) dan saat kamu menekan "Periksa pembaruan". Tidak ada data tentangmu yang dikirim. |
| Unduh pembaruan | Aset rilis di `github.com` | Hanya setelah kamu menekan "Download versi terbaru". Pemasangan selalu meminta konfirmasimu di installer sistem. |

Seperti permintaan web lainnya, ini memperlihatkan alamat IP-mu ke server yang dihubungi.

## Yang disimpan di perangkatmu

- Pengaturan (tema, bahasa, nama dan email Git), daftar repository yang kamu tambahkan, dan log aktivitas (hanya di memori) di penyimpanan privat aplikasi.
- Token akses, terenkripsi dengan Android Keystore (`EncryptedSharedPreferences`). Backup dimatikan untuk aplikasi.

Menghapus aplikasi menghapus data ini. Repository di penyimpananmu tidak ikut terhapus.

## Izin

| Izin | Alasan |
| --- | --- |
| `INTERNET` | Fitur jaringan di atas. |
| `MANAGE_EXTERNAL_STORAGE` (Akses semua file) | Membuka dan mengubah folder repository lewat path asli, yang dibutuhkan Git. |
| `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (hanya Android lama) | Hal yang sama, di Android 10 ke bawah. |
| `REQUEST_INSTALL_PACKAGES` | Membuka installer sistem untuk APK pembaruan yang kamu unduh di aplikasi. |

## Perubahan dan kontak

Bila halaman ini berubah, perubahannya dicatat di [CHANGELOG](../CHANGELOG.md). Pertanyaan: buka [diskusi atau issue](https://github.com/Fueeru26/FuruGit). Untuk masalah keamanan lihat [SECURITY.md](../SECURITY.md).
