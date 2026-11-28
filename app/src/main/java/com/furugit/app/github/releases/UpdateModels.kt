package com.furugit.app.github.releases

import java.io.File

/** Rilis GitHub terbaru: [apkUrl] = unduhan langsung APK pertama di rilis (null bila rilis tidak punya APK). */
class UpdateInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSize: Long
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()

    /** Repo belum punya rilis (HTTP 404). */
    object NoRelease : UpdateState()
    class Available(val info: UpdateInfo) : UpdateState()
    class Failed(val reason: String) : UpdateState()
}

sealed class DownloadState {
    object Idle : DownloadState()
    class Running(val done: Long, val total: Long) : DownloadState()
    class Done(val file: File) : DownloadState()
    class Failed(val reason: String) : DownloadState()
}
