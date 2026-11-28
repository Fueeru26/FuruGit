package com.furugit.app.core.common

/** Hasil operasi Git yang bisa "gagal secara wajar" (mis. konflik). */
data class OpResult(val ok: Boolean, val message: String, val conflicts: List<String> = emptyList())

/** Error yang pesannya sudah ramah dibaca pengguna. */
class GitUserError(message: String) : Exception(message)
