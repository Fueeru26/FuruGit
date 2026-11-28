package com.furugit.app.core.storage

/** Nama file aman untuk penyimpanan. */
fun safeFileName(s: String): String = s.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "file" }
