package com.furugit.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class WorkflowRun(
    val id: Long, val workflowId: Long, val workflow: String, val title: String, val number: Int,
    val status: String, val conclusion: String?, val event: String, val branch: String,
    val time: Long, val url: String, val path: String
)

data class RunJob(val id: Long, val name: String, val status: String, val conclusion: String?)

/** Satu job di file workflow lokal (folder .github/workflows). */
data class LocalJob(val key: String, val name: String)

/** Satu file workflow lokal beserta job-nya. */
data class LocalWorkflow(val file: String, val name: String, val dispatch: Boolean, val jobs: List<LocalJob>)

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
    suspend fun runs(r: GhRepo, branch: String?, perPage: Int = 30, workflow: String? = null, page: Int = 1): List<WorkflowRun> {
        val scope = if (workflow != null) "/actions/workflows/$workflow/runs" else "/actions/runs"
        var url = "${r.base}$scope?per_page=$perPage&page=$page"
        if (branch != null) url += "&branch=" + java.net.URLEncoder.encode(branch, "UTF-8")
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

    suspend fun rerunJob(r: GhRepo, jobId: Long) {
        call("POST", "${r.base}/actions/jobs/$jobId/rerun", r.token)
    }

    suspend fun dispatch(r: GhRepo, workflowFile: String, ref: String) {
        call("POST", "${r.base}/actions/workflows/$workflowFile/dispatches", r.token, JSONObject().put("ref", ref).toString())
    }

    /**
     * Menghapus semua run milik satu workflow (run yang masih berjalan dilewati). Workflow yang file .yml-nya sudah
     * dihapus dari repo hilang dari daftar GitHub setelah semua run-nya terhapus. Mengembalikan (terhapus, dilewati).
     */
    suspend fun deleteWorkflowRuns(r: GhRepo, workflowId: Long): Pair<Int, Int> {
        var deleted = 0
        var skipped = 0
        repeat(10) {
            val batch = runs(r, null, 100, workflow = workflowId.toString())
            if (batch.isEmpty()) return deleted to skipped
            var progressed = false
            for (run in batch) {
                if (run.status != "completed") {
                    skipped++
                    continue
                }
                deleteRun(r, run.id)
                deleted++
                progressed = true
            }
            if (!progressed) return deleted to skipped
        }
        return deleted to skipped
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
        return LocalWorkflow(f.name, name, dispatch, jobs)
    }
}
