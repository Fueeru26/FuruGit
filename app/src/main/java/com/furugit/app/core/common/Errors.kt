package com.furugit.app.core.common

/** Mengubah exception teknis menjadi pesan yang bisa dipahami pengguna. */
fun Throwable.friendly(): String {
    if (this is GitUserError) return message ?: "Terjadi kesalahan."
    val chain = generateSequence(this) { it.cause }.toList()
    val all = chain.joinToString(" | ") { it.message ?: it.javaClass.simpleName }
    return when {
        chain.any { it is java.net.UnknownHostException } || all.contains("Unable to resolve host", true) ->
            "Tidak ada koneksi internet / host tidak ditemukan."
        all.contains("not authorized", true) || all.contains("Authentication is required", true) ||
            all.contains("Auth fail", true) || all.contains("401") || all.contains("403") ->
            "Autentikasi gagal. Periksa akun/token di Pengaturan (PAT butuh izin Contents: Read and write)."
        all.contains("index.lock", true) || all.contains("LockFailed", true) ->
            "Repository terkunci (.git/index.lock). Jika tidak ada operasi Git lain, hapus file itu."
        all.contains("EACCES") || all.contains("Permission denied", true) ->
            "Tidak punya izin akses file. Pastikan izin 'Akses semua file' sudah diberikan."
        all.contains("CheckoutConflict", true) || all.contains("conflict", true) ->
            "Ada perubahan lokal yang bentrok: $all"
        else -> "${javaClass.simpleName}: ${message ?: ""}".trim()
    }
}
