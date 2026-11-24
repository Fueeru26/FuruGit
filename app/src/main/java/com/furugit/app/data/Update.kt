package com.furugit.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val apkUrl: String?,
    val apkSize: Long,
    val pageUrl: String
)

/** Pembaruan dari GitHub Releases repo FuruGit (mekanisme sama dengan FuruArc). */
object Updater {
    const val REPO = "Fueeru26/FuruGit"
    const val REPO_URL = "https://github.com/Fueeru26/FuruGit"

    fun current(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /** "v1.05-beta" -> [1, 5]; hanya angka sebelum tanda '-'. */
    fun parse(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').split('.')
            .map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }

    fun compare(a: String, b: String): Int {
        val x = parse(a)
        val y = parse(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /** Rilis non-draft dengan versi tertinggi, atau null bila belum ada rilis. */
    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val (code, body) = Http.request("GET", "https://api.github.com/repos/$REPO/releases?per_page=30")
        if (code == 404) return@withContext null
        if (code !in 200..299) throw GitUserError("GitHub membalas HTTP $code.")
        val arr = JSONArray(body)
        var best: UpdateInfo? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optBoolean("draft")) continue
            val tag = o.optString("tag_name")
            if (tag.isEmpty()) continue
            if (best != null && compare(tag, best.tag) <= 0) continue
            var apk: String? = null
            var size = 0L
            val assets = o.optJSONArray("assets")
            if (assets != null) for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                if (a.optString("name").endsWith(".apk", true)) {
                    apk = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
            best = UpdateInfo(tag, tag.removePrefix("v").removePrefix("V"), o.optString("body"), apk, size, o.optString("html_url", REPO_URL))
        }
        best
    }

    /** Unduh APK ke cache/updates. [onProgress] dipanggil dari thread IO. */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val url = info.apkUrl ?: throw GitUserError("Rilis ini tidak punya file APK.")
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, "FuruGit-${info.tag}.apk.part")
        val out = File(dir, "FuruGit-${info.tag}.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "FuruGit")
            if (c.responseCode != 200) throw GitUserError("Unduhan gagal: HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
            var done = 0L
            var last = 0L
            c.inputStream.use { ins ->
                part.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 250) {
                            last = now
                            onProgress(done, total)
                        }
                    }
                }
            }
            onProgress(done, total)
        } finally {
            c.disconnect()
        }
        if (info.apkSize > 0 && part.length() != info.apkSize) {
            part.delete()
            throw GitUserError("Ukuran file tidak cocok. Coba unduh lagi.")
        }
        if (!part.renameTo(out)) throw GitUserError("Gagal menyimpan file unduhan.")
        out
    }

    /** Buka installer sistem. Android meminta izin "pasang dari sumber ini" pada pemasangan pertama. */
    fun install(ctx: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
