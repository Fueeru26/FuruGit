package com.furugit.app.github.auth

import com.furugit.app.core.common.GitUserError
import com.furugit.app.core.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object GitHubApi {
    /** Mengembalikan username pemilik token, atau melempar GitUserError jika token ditolak. */
    suspend fun username(token: String): String = withContext(Dispatchers.IO) {
        val (code, body) = Http.request(
            "GET", "https://api.github.com/user",
            headers = mapOf("Authorization" to "Bearer $token")
        )
        if (code == 401 || code == 403) throw GitUserError("Token ditolak oleh GitHub (HTTP $code).")
        if (code !in 200..299) throw GitUserError("GitHub membalas HTTP $code.")
        JSONObject(body).getString("login")
    }
}
