package com.furugit.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class WorkflowRun(
    val id: Long, val workflowId: Long, val workflow: String, val title: String, val number: Int,
    val status: String, val conclusion: String?, val event: String, val branch: String,
    val time: Long, val url: String, val path: String
)

data class Artifact(val id: Long, val name: String, val size: Long, val expired: Boolean)

data class RunJob(val id: Long, val name: String, val status: String, val conclusion: String?)

/** Satu job di file workflow lokal (folder .github/workflows). */
data class LocalJob(val key: String, val name: String)

/** Satu input workflow_dispatch (type: boolean / string / choice / number / environment). */
data class DispatchInput(val name: String, val type: String, val default: String, val options: List<String>, val description: String)

/** Satu file workflow lokal beserta job dan input workflow_dispatch-nya. */
data class LocalWorkflow(
    val file: String, val name: String, val dispatch: Boolean, val jobs: List<LocalJob>,
    val inputs: List<DispatchInput> = emptyList()
)

/** Alamat repo di GitHub + token akun untuk memanggil REST API. */
class GhRepo(val owner: String, val repo: String, val token: String) {
    val base get() = "https://api.github.com/repos/$owner/$repo"
}

/** Klien GitHub Actions (REST API). Semua fungsi blocking di Dispatchers.IO dan melempar [GitUserError] bila gagal. */
object ActionsApi {

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    fun refFor(remoteUrl: String?): GhRepo {
        if (remoteUrl == null) throw GitUserError("Repository ini belum punya remote.")
        val m = Regex("""github\.com[/:]([^/]+)/([^/]+?)(?:\.git)?/?$""", RegexOption.IGNORE_CASE).find(remoteUrl.trim())
            ?: throw GitUserError("Fitur Aksi hanya untuk repository di github.com.")
        val acc = AuthStore.accountFor(remoteUrl)
            ?: throw GitUserError("Belum ada akun GitHub. Tambahkan di Pengaturan › Pengaturan Github (token butuh izin Actions).")
        return GhRepo(m.groupValues[1], m.groupValues[2], acc.token)
    }

    private fun fail(code: Int, body: String): Nothing {
        val msg = try {
            JSONObject(body).optString("message")
        } catch (e: Exception) {
            ""
        }
        throw GitUserError(
            when (code) {
                401 -> "Token ditolak oleh GitHub (HTTP 401)."
                403 -> "GitHub menolak (HTTP 403). Token perlu izin Actions: Read and write (PAT klasik: scope repo + workflow). " + msg
                404 -> "Tidak ditemukan (HTTP 404). Periksa token punya akses ke repo ini. " + msg
                409, 422 -> "GitHub tidak bisa menjalankannya sekarang: $msg"
                else -> "GitHub membalas HTTP $code. $msg"
            }.trim()
        )
    }

    private suspend fun call(method: String, url: String, token: String, json: String? = null): String =
        withContext(Dispatchers.IO) {
            val (code, body) = Http.api(method, url, token, json)
            if (code !in 200..299) fail(code, body)
            body
        }

    private fun parseRun(o: JSONObject) = WorkflowRun(
        id = o.getLong("id"),
        workflowId = o.optLong("workflow_id"),
        workflow = o.optString("name"),
        title = o.optString("display_title").ifBlank { o.optString("name") },
        number = o.optInt("run_number"),
        status = o.optString("status"),
        conclusion = if (o.isNull("conclusion")) null else o.optString("conclusion"),
        event = o.optString("event"),
        branch = o.optString("head_branch"),
        time = try {
            iso.parse(o.optString("created_at"))?.time ?: 0L
        } catch (e: Exception) {
            0L
        },
        url = o.optString("html_url"),
        path = o.optString("path")
    )

    /** Daftar run terbaru; [branch] juga berlaku untuk nama tag (head_branch berisi nama tag pada run dari tag). */
    suspend fun runs(r: GhRepo, branch: String?, perPage: Int = 30, workflow: String? = null, page: Int = 1, status: String? = null): List<WorkflowRun> {
        val scope = if (workflow != null) "/actions/workflows/$workflow/runs" else "/actions/runs"
        var url = "${r.base}$scope?per_page=$perPage&page=$page"
        if (branch != null) url += "&branch=" + java.net.URLEncoder.encode(branch, "UTF-8")
        if (status != null) url += "&status=$status"
        val arr = JSONObject(call("GET", url, r.token)).optJSONArray("workflow_runs") ?: JSONArray()
        return (0 until arr.length()).map { parseRun(arr.getJSONObject(it)) }
    }

    suspend fun jobs(r: GhRepo, runId: Long): List<RunJob> {
        val arr = JSONObject(call("GET", "${r.base}/actions/runs/$runId/jobs?per_page=100", r.token)).optJSONArray("jobs") ?: JSONArray()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            RunJob(o.getLong("id"), o.optString("name"), o.optString("status"), if (o.isNull("conclusion")) null else o.optString("conclusion"))
        }
    }

    suspend fun deleteRun(r: GhRepo, runId: Long) {
        call("DELETE", "${r.base}/actions/runs/$runId", r.token)
    }

    suspend fun rerun(r: GhRepo, runId: Long) {
        call("POST", "${r.base}/actions/runs/$runId/rerun", r.token)
    }

    /** Membatalkan run yang sedang berjalan atau antre. */
    suspend fun cancel(r: GhRepo, runId: Long) {
        call("POST", "${r.base}/actions/runs/$runId/cancel", r.token)
    }

    suspend fun rerunJob(r: GhRepo, jobId: Long) {
        call("POST", "${r.base}/actions/jobs/$jobId/rerun", r.token)
    }

    suspend fun dispatch(r: GhRepo, workflowFile: String, ref: String, inputs: Map<String, String> = emptyMap()) {
        val o = JSONObject().put("ref", ref)
        if (inputs.isNotEmpty()) {
            val i = JSONObject()
            for ((k, v) in inputs) i.put(k, v)
            o.put("inputs", i)
        }
        call("POST", "${r.base}/actions/workflows/$workflowFile/dispatches", r.token, o.toString())
    }

    /** Semua run (sampai [pages] x 100) untuk penghapusan massal. */
    suspend fun runsAll(r: GhRepo, branch: String?, pages: Int = 5): List<WorkflowRun> {
        val out = ArrayList<WorkflowRun>()
        for (p in 1..pages) {
            val b = runs(r, branch, 100, null, p)
            out.addAll(b)
            if (b.size < 100) break
        }
        return out
    }

    suspend fun artifacts(r: GhRepo, runId: Long): List<Artifact> {
        val arr = JSONObject(call("GET", "${r.base}/actions/runs/$runId/artifacts?per_page=100", r.token)).optJSONArray("artifacts") ?: JSONArray()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Artifact(o.getLong("id"), o.optString("name"), o.optLong("size_in_bytes"), o.optBoolean("expired"))
        }
    }

    /** Membuka koneksi ke URL API dan mengikuti pengalihan (tanpa header Authorization ke host lain, seperti yang dituntut GitHub). */
    private fun openFollow(r: GhRepo, url: String): HttpURLConnection {
        var u = url
        var auth = true
        var hops = 0
        while (true) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20000
            c.readTimeout = 60000
            c.setRequestProperty("User-Agent", "FuruGit")
            if (auth) {
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                c.setRequestProperty("Authorization", "Bearer ${r.token}")
            }
            val code = c.responseCode
            if (code in 300..399 && hops < 5) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) fail(code, "")
                u = loc
                auth = false
                hops++
                continue
            }
            if (code !in 200..299) {
                val body = c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                c.disconnect()
                fail(code, body)
            }
            return c
        }
    }

    /** Teks log satu job (GitHub hanya menyediakan log setelah job selesai). */
    suspend fun jobLog(r: GhRepo, jobId: Long): String = withContext(Dispatchers.IO) {
        val c = openFollow(r, "${r.base}/actions/jobs/$jobId/logs")
        try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Mengunduh [apiUrl] ke [dest] lewat file sementara .part; [onBytes] dipanggil tiap potongan data. Bisa dibatalkan. */
    suspend fun downloadTo(r: GhRepo, apiUrl: String, dest: File, onBytes: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val part = File(dest.parentFile, dest.name + ".part")
        val c = openFollow(r, apiUrl)
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

    /** Memindai file workflow di working tree repo lokal: nama, ada tidaknya workflow_dispatch, dan daftar job-nya. */
    fun scanLocal(repoPath: String): List<LocalWorkflow> {
        val dir = File(repoPath, ".github/workflows")
        val files = dir.listFiles { f -> f.isFile && (f.name.endsWith(".yml") || f.name.endsWith(".yaml")) }
            ?.sortedBy { it.name.lowercase() } ?: return emptyList()
        return files.map { parseWorkflow(it) }
    }

    private fun unquote(s: String): String = s.trim().trim('"', '\'')

    internal fun parseWorkflow(f: File): LocalWorkflow {
        val lines = try {
            f.readLines()
        } catch (e: Exception) {
            emptyList()
        }
        var name = f.name
        var dispatch = false
        val jobs = ArrayList<LocalJob>()
        var inJobs = false
        var curKey: String? = null
        var curName: String? = null
        fun flush() {
            curKey?.let { jobs.add(LocalJob(it, curName ?: it)) }
            curKey = null
            curName = null
        }
        for (raw in lines) {
            val line = raw.substringBefore(" #").trimEnd()
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val indent = line.length - line.trimStart().length
            if (indent == 0) {
                if (inJobs) flush()
                inJobs = line.startsWith("jobs:")
                if (line.startsWith("name:")) name = unquote(line.removePrefix("name:")).ifBlank { f.name }
                continue
            }
            if (!inJobs) {
                if (line.contains("workflow_dispatch")) dispatch = true
                continue
            }
            if (indent == 2 && line.trim().endsWith(":") && !line.trim().startsWith("-")) {
                flush()
                curKey = unquote(line.trim().removeSuffix(":"))
            } else if (indent == 4 && line.trim().startsWith("name:") && curKey != null && curName == null) {
                curName = unquote(line.trim().removePrefix("name:"))
            }
        }
        if (inJobs) flush()
        return LocalWorkflow(f.name, name, dispatch, jobs, parseInputs(lines))
    }

    private fun indentOf(s: String) = s.length - s.trimStart().length

    /** Membaca input di blok workflow_dispatch > inputs (nama, type, default, options, description). */
    private fun parseInputs(rawLines: List<String>): List<DispatchInput> {
        val lines = rawLines.map { it.substringBefore(" #").trimEnd() }
        val idx = lines.indexOfFirst { it.trim().startsWith("workflow_dispatch:") }
        if (idx < 0) return emptyList()
        val wdIndent = indentOf(lines[idx])
        var i = idx + 1
        var inputsIdx = -1
        var inputsIndent = 0
        while (i < lines.size) {
            val l = lines[i]
            if (l.isBlank() || l.trim().startsWith("#")) {
                i++
                continue
            }
            if (indentOf(l) <= wdIndent) break
            if (l.trim() == "inputs:") {
                inputsIdx = i
                inputsIndent = indentOf(l)
                break
            }
            i++
        }
        if (inputsIdx < 0) return emptyList()
        val res = ArrayList<DispatchInput>()
        var name: String? = null
        var type = "string"
        var def = ""
        var desc = ""
        val opts = ArrayList<String>()
        var inOptions = false
        var itemIndent = -1
        fun flush() {
            val n = name
            if (n != null) res.add(DispatchInput(n, type, def, opts.toList(), desc))
            name = null
            type = "string"
            def = ""
            desc = ""
            opts.clear()
            inOptions = false
        }
        var j = inputsIdx + 1
        while (j < lines.size) {
            val raw = lines[j]
            j++
            if (raw.isBlank() || raw.trim().startsWith("#")) continue
            val ind = indentOf(raw)
            if (ind <= inputsIndent) break
            val t = raw.trim()
            if (itemIndent < 0) itemIndent = ind
            if (ind == itemIndent && t.endsWith(":")) {
                flush()
                name = unquote(t.removeSuffix(":"))
                continue
            }
            if (name == null) continue
            if (inOptions && t.startsWith("- ")) {
                opts.add(unquote(t.removePrefix("- ")))
                continue
            }
            inOptions = false
            when {
                t.startsWith("type:") -> type = unquote(t.removePrefix("type:"))
                t.startsWith("default:") -> def = unquote(t.removePrefix("default:"))
                t.startsWith("description:") -> desc = unquote(t.removePrefix("description:"))
                t.startsWith("options:") -> inOptions = true
            }
        }
        flush()
        return res
    }
}
