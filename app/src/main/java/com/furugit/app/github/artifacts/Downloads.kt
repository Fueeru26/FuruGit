package com.furugit.app.github.artifacts

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.furugit.app.App
import com.furugit.app.core.common.GitUserError
import com.furugit.app.core.common.Notifier
import com.furugit.app.core.common.friendly
import com.furugit.app.core.common.tr
import com.furugit.app.core.storage.safeFileName
import com.furugit.app.data.preferences.Prefs
import com.furugit.app.github.actions.GhRepo
import java.io.File
import java.lang.ref.WeakReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Satu berkas yang diunduh: nama file tujuan, URL API GitHub, dan perkiraan ukuran (0 = tidak diketahui). */
class DlItem(val fileName: String, val apiUrl: String, val sizeHint: Long)

/** APK hasil unduhan: [file] = APK itu sendiri, atau .zip bila [entry] (nama entri APK di dalam zip) terisi. */
class ApkRef(val label: String, val file: File, val entry: String?)

class DownloadTask(val title: String, val items: List<DlItem>) {
    @Volatile var doneBytes = 0L
    val totalBytes: Long = items.sumOf { it.sizeHint }
    @Volatile var currentIndex = 0
    @Volatile var currentName = ""
    @Volatile var state = RUNNING
    @Volatile var message = ""
    @Volatile var apks: List<ApkRef> = emptyList()

    /** Dipanggil di thread utama setiap ada perubahan progres / status. */
    var listener: ((DownloadTask) -> Unit)? = null
    internal var job: Job? = null

    fun cancel() {
        job?.cancel()
    }

    companion object {
        const val RUNNING = 0
        const val OK = 1
        const val FAIL = 2
        const val CANCELLED = 3
    }
}

private fun uniqueFile(dir: File, name: String): File {
    var f = File(dir, name)
    if (!f.exists()) return f
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var i = 1
    while (true) {
        f = File(dir, "$base ($i)$ext")
        if (!f.exists()) return f
        i++
    }
}

/**
 * Pengunduh berlingkup aplikasi: unduhan tetap berjalan walau jendela progres ditutup ("Proses di Latar Belakang").
 * Satu tugas aktif pada satu waktu. Setelah selesai, jendela hasil (ceklis / silang) tampil bila aplikasi sedang terbuka.
 */
object Downloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    var current: DownloadTask? = null
        private set

    /** Activity yang sedang tampil (diisi MainActivity.onResume), dipakai untuk menampilkan jendela hasil. */
    var host: WeakReference<Activity>? = null

    /** Dipasang oleh lapisan UI: dipanggil di thread utama saat unduhan selesai dan [host] masih tampil. */
    var onFinished: ((Activity, DownloadTask) -> Unit)? = null

    private var lastEmit = 0L

    private fun emit(t: DownloadTask, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastEmit < 250L) return
        lastEmit = now
        main.post {
            t.listener?.invoke(t)
            val app = com.furugit.app.App.instance
            if (t.state == DownloadTask.RUNNING) {
                val label = t.currentName + " (" + (t.currentIndex + 1) + "/" + t.items.size + ")"
                Notifier.progress(app, t.title, label, t.doneBytes, t.totalBytes)
            }
        }
    }

    fun start(gh: GhRepo, dir: File, title: String, items: List<DlItem>): DownloadTask {
        val t = DownloadTask(title, items)
        current = t
        t.job = scope.launch {
            try {
                if (!dir.isDirectory && !dir.mkdirs()) {
                    throw GitUserError(tr("Folder output tidak bisa dibuka: ", "Cannot open the output folder: ") + dir.absolutePath)
                }
                val found = ArrayList<ApkRef>()
                items.forEachIndexed { i, item ->
                    t.currentIndex = i
                    t.currentName = item.fileName
                    emit(t, true)
                    val dest = uniqueFile(dir, safeFileName(item.fileName))
                    ArtifactsApi.downloadTo(gh, item.apiUrl, dest) { n ->
                        t.doneBytes += n
                        emit(t, false)
                    }
                    if (dest.name.endsWith(".zip", true)) found.addAll(afterZip(dir, dest))
                }
                t.apks = found
                t.state = DownloadTask.OK
                t.message = tr("Unduhan berhasil. Disimpan di ", "Download complete. Saved to ") + dir.absolutePath
            } catch (e: CancellationException) {
                t.state = DownloadTask.CANCELLED
                t.message = tr("Unduhan dibatalkan.", "Download cancelled.")
            } catch (e: Throwable) {
                t.state = DownloadTask.FAIL
                t.message = tr("Unduhan gagal.\n", "Download failed.\n") + e.friendly()
            } finally {
                if (current === t) current = null
                main.post {
                    t.listener?.invoke(t)
                    val app = com.furugit.app.App.instance
                    Notifier.finish(app, t.state == DownloadTask.OK, t.title, t.message, t.state == DownloadTask.CANCELLED)
                    val act = host?.get()
                    if (act != null && !act.isFinishing) onFinished?.invoke(act, t)
                }
            }
        }
        return t
    }

    /**
     * Setelah artefak .zip terunduh: bila "ekstrak otomatis" menyala, isinya diekstrak ke folder bernama artefak
     * dan .zip dihapus; APK di dalamnya dikumpulkan agar bisa langsung diinstal.
     */
    private suspend fun afterZip(dir: File, zip: File): List<ApkRef> = withContext(Dispatchers.IO) {
        if (Prefs.downloadExtract) {
            val target = uniqueFile(dir, zip.name.removeSuffix(".zip").removeSuffix(".ZIP"))
            extractZip(zip, target)
            zip.delete()
            target.walkTopDown().filter { it.isFile && it.name.endsWith(".apk", true) }
                .map { ApkRef(it.relativeTo(target).path, it, null) }.toList()
        } else {
            try {
                java.util.zip.ZipFile(zip).use { zf ->
                    zf.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".apk", true) }
                        .map { ApkRef(it.name, zip, it.name) }.toList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun extractZip(zip: File, target: File) {
        target.mkdirs()
        val base = target.canonicalPath + File.separator
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val out = File(target, e.name)
                if (!out.canonicalPath.startsWith(base)) throw GitUserError("Entri zip tidak aman: " + e.name)
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zin.copyTo(it) }
                }
                zin.closeEntry()
            }
        }
    }

    /** Menyalin APK ke [cacheDir]/updates (mengambil dari zip bila perlu) dan mengembalikan filenya. */
    suspend fun prepareApk(cacheDir: File, ref: ApkRef): File = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "updates")
        dir.mkdirs()
        val out = File(dir, safeFileName(File(ref.entry ?: ref.file.name).name))
        if (ref.entry == null) {
            ref.file.copyTo(out, overwrite = true)
        } else {
            java.util.zip.ZipFile(ref.file).use { zf ->
                val e = zf.getEntry(ref.entry) ?: throw GitUserError("APK tidak ditemukan di dalam zip.")
                zf.getInputStream(e).use { ins -> out.outputStream().use { ins.copyTo(it) } }
            }
        }
        out
    }
}
