package com.furugit.app.git.remote

import com.furugit.app.core.common.GitUserError
import com.furugit.app.git.repository.GitRepository
import com.furugit.app.github.auth.AuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish

/** Daftar dan pengelolaan remote, serta pengecekan koneksi ke remote. */
object GitRemote {

    fun remotes(g: Git): List<RemoteInfo> =
        g.remoteList().call().map { RemoteInfo(it.name, it.urIs.firstOrNull()?.toString() ?: "") }

    fun addRemote(g: Git, name: String, url: String) {
        if (name.isBlank() || url.isBlank()) throw GitUserError("Nama dan URL remote wajib diisi.")
        val uri = try {
            URIish(url)
        } catch (e: Exception) {
            throw GitUserError("URL remote tidak valid.")
        }
        g.remoteAdd().setName(name).setUri(uri).call()
    }

    fun removeRemote(g: Git, name: String) {
        g.remoteRemove().setRemoteName(name).call()
    }

    /** URL remote bernama [remote] (atau remote bawaan bila null); null bila repo belum punya remote. */
    suspend fun remoteUrl(path: String, remote: String? = null): String? = GitRepository.use(path) { g ->
        val n = remote ?: defaultRemote(g)
        if (n == null) null else g.repository.config.getString("remote", n, "url")
    }

    /**
     * Mengecek apakah remote bisa dihubungi (ls-remote). Gagal jaringan (tanpa internet, host tidak ketemu, timeout) = false.
     * Gagal autentikasi atau repo privat tetap dihitung terhubung karena server berhasil dijangkau.
     */
    suspend fun reachable(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Git.lsRemoteRepository().setRemote(url).setHeads(true).setTimeout(8)
                .setCredentialsProvider(AuthStore.providerFor(url)).call()
            true
        } catch (e: Exception) {
            val chain = generateSequence<Throwable>(e) { it.cause }.toList()
            val all = chain.joinToString(" | ") { it.message ?: "" }
            val net = chain.any {
                it is java.net.UnknownHostException || it is java.net.ConnectException ||
                    it is java.net.SocketTimeoutException || it is java.net.NoRouteToHostException
            } || all.contains("Unable to resolve host", true) || all.contains("timed out", true) ||
                all.contains("Network is unreachable", true) || all.contains("Failed to connect", true)
            !net
        }
    }

    fun defaultRemote(g: Git): String? {
        val names = remotes(g).map { it.name }
        return if ("origin" in names) "origin" else names.firstOrNull()
    }
}
