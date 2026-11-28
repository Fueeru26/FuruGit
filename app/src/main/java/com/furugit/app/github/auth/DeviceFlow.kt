package com.furugit.app.github.auth

import com.furugit.app.core.common.GitUserError
import com.furugit.app.core.common.L
import com.furugit.app.core.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * GitHub Device Authorization Flow (tanpa client secret di APK).
 * Butuh Client ID dari GitHub App / OAuth App milikmu dengan "Device Flow" diaktifkan.
 */
object DeviceFlow {
    class Code(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val interval: Int,
        val expiresIn: Int
    )

    suspend fun requestCode(clientId: String): Code = withContext(Dispatchers.IO) {
        val (code, body) = Http.request(
            "POST", "https://github.com/login/device/code",
            form = mapOf("client_id" to clientId, "scope" to "repo")
        )
        val o = JSONObject(body)
        if (code !in 200..299 || o.has("error")) {
            throw GitUserError("Gagal memulai login: " + o.optString("error_description", o.optString("error", "HTTP $code")))
        }
        Code(
            o.getString("device_code"),
            o.getString("user_code"),
            o.getString("verification_uri"),
            o.optInt("interval", 5),
            o.optInt("expires_in", 900)
        )
    }

    /** Menunggu sampai pengguna menyetujui. Batalkan coroutine-nya untuk berhenti. */
    suspend fun awaitToken(clientId: String, c: Code): String {
        var interval = c.interval
        val deadline = System.currentTimeMillis() + c.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val o = withContext(Dispatchers.IO) {
                val (_, body) = Http.request(
                    "POST", "https://github.com/login/oauth/access_token",
                    form = mapOf(
                        "client_id" to clientId,
                        "device_code" to c.deviceCode,
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
                    )
                )
                JSONObject(body)
            }
            if (o.has("access_token")) return o.getString("access_token")
            when (o.optString("error")) {
                "authorization_pending" -> {}
                "slow_down" -> interval += 5
                "expired_token" -> throw GitUserError("Kode kedaluwarsa. Ulangi login.")
                "access_denied" -> throw GitUserError("Login dibatalkan di GitHub.")
                else -> throw GitUserError("Login gagal: " + o.optString("error_description", o.optString("error")))
            }
        }
        throw GitUserError("Kode kedaluwarsa. Ulangi login.")
    }
}
