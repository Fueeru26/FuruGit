package com.furugit.app.github.actions

import com.furugit.app.core.common.GitUserError
import com.furugit.app.core.network.Http
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Permintaan ke REST API GitHub (blocking; panggil dari Dispatchers.IO). */
fun Http.api(method: String, url: String, token: String, json: String? = null): Pair<Int, String> {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "FuruGit")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        c.setRequestProperty("Authorization", "Bearer $token")
        if (method == "POST") {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write((json ?: "{}").toByteArray()) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return code to text
    } finally {
        c.disconnect()
    }
}

/** Pemanggilan REST API GitHub yang dipakai bersama: penanganan error HTTP dan pengikutan pengalihan. */
object GhRest {

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

    internal suspend fun call(method: String, url: String, token: String, json: String? = null): String =
        withContext(Dispatchers.IO) {
            val (code, body) = Http.api(method, url, token, json)
            if (code !in 200..299) fail(code, body)
            body
        }

    /** Membuka koneksi ke URL API dan mengikuti pengalihan (tanpa header Authorization ke host lain, seperti yang dituntut GitHub). */
    internal fun openFollow(r: GhRepo, url: String): HttpURLConnection {
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
}
