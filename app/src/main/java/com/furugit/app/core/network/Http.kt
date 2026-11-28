package com.furugit.app.core.network

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Http {
    /** Blocking. Panggil dari Dispatchers.IO. Mengembalikan (kodeHttp, body). */
    fun request(
        method: String,
        url: String,
        form: Map<String, String>? = null,
        headers: Map<String, String> = emptyMap()
    ): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "FuruGit")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (form != null) {
                c.doOutput = true
                val body = form.entries.joinToString("&") {
                    URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
                }
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            c.disconnect()
        }
    }
}
