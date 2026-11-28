package com.furugit.app.github.artifacts

import com.furugit.app.core.common.GitUserError
import com.furugit.app.github.actions.GhRepo
import com.furugit.app.github.actions.GhRest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Daftar dan unduhan artifact GitHub Actions. */
object ArtifactsApi {

    suspend fun artifacts(r: GhRepo, runId: Long): List<Artifact> {
        val arr = JSONObject(GhRest.call("GET", "${r.base}/actions/runs/$runId/artifacts?per_page=100", r.token)).optJSONArray("artifacts") ?: JSONArray()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Artifact(o.getLong("id"), o.optString("name"), o.optLong("size_in_bytes"), o.optBoolean("expired"))
        }
    }

    /** Mengunduh [apiUrl] ke [dest] lewat file sementara .part; [onBytes] dipanggil tiap potongan data. Bisa dibatalkan. */
    suspend fun downloadTo(r: GhRepo, apiUrl: String, dest: File, onBytes: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val part = File(dest.parentFile, dest.name + ".part")
        val c = GhRest.openFollow(r, apiUrl)
        try {
            c.inputStream.use { ins ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        ensureActive()
                        out.write(buf, 0, n)
                        onBytes(n.toLong())
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) throw GitUserError("Gagal menyimpan file " + dest.name)
        } catch (e: Throwable) {
            part.delete()
            throw e
        } finally {
            c.disconnect()
        }
    }
}
