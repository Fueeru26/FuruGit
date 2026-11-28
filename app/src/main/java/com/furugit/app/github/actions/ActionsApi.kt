package com.furugit.app.github.actions

import com.furugit.app.core.common.GitUserError
import com.furugit.app.core.common.L
import com.furugit.app.github.auth.AuthStore
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
        val arr = JSONObject(GhRest.call("GET", url, r.token)).optJSONArray("workflow_runs") ?: JSONArray()
        return (0 until arr.length()).map { parseRun(arr.getJSONObject(it)) }
    }

    suspend fun jobs(r: GhRepo, runId: Long): List<RunJob> {
        val arr = JSONObject(GhRest.call("GET", "${r.base}/actions/runs/$runId/jobs?per_page=100", r.token)).optJSONArray("jobs") ?: JSONArray()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            RunJob(o.getLong("id"), o.optString("name"), o.optString("status"), if (o.isNull("conclusion")) null else o.optString("conclusion"))
        }
    }

    suspend fun deleteRun(r: GhRepo, runId: Long) {
        GhRest.call("DELETE", "${r.base}/actions/runs/$runId", r.token)
    }

    suspend fun rerun(r: GhRepo, runId: Long) {
        GhRest.call("POST", "${r.base}/actions/runs/$runId/rerun", r.token)
    }

    /** Membatalkan run yang sedang berjalan atau antre. */
    suspend fun cancel(r: GhRepo, runId: Long) {
        GhRest.call("POST", "${r.base}/actions/runs/$runId/cancel", r.token)
    }

    suspend fun rerunJob(r: GhRepo, jobId: Long) {
        GhRest.call("POST", "${r.base}/actions/jobs/$jobId/rerun", r.token)
    }

    suspend fun dispatch(r: GhRepo, workflowFile: String, ref: String, inputs: Map<String, String> = emptyMap()) {
        val o = JSONObject().put("ref", ref)
        if (inputs.isNotEmpty()) {
            val i = JSONObject()
            for ((k, v) in inputs) i.put(k, v)
            o.put("inputs", i)
        }
        GhRest.call("POST", "${r.base}/actions/workflows/$workflowFile/dispatches", r.token, o.toString())
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

    /** Teks log satu job (GitHub hanya menyediakan log setelah job selesai). */
    suspend fun jobLog(r: GhRepo, jobId: Long): String = withContext(Dispatchers.IO) {
        val c = GhRest.openFollow(r, "${r.base}/actions/jobs/$jobId/logs")
        try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
