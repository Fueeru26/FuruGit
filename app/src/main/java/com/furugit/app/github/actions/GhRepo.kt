package com.furugit.app.github.actions

/** Alamat repo di GitHub + token akun untuk memanggil REST API. */
class GhRepo(val owner: String, val repo: String, val token: String) {
    val base get() = "https://api.github.com/repos/$owner/$repo"
}
