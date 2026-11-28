package com.furugit.app.git.repository

import com.furugit.app.core.common.GitUserError
import com.furugit.app.github.auth.AuthStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor

/**
 * Pembungkus JGit. Aturan utama: satu operasi per repository pada satu waktu (Mutex),
 * UI tidak pernah memanggil JGit langsung.
 */
object GitRepository {

    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Membuka repository di [path], menjalankan [block], lalu menutupnya. Berjalan di IO. */
    suspend fun <T> use(path: String, block: (Git) -> T): T = withContext(Dispatchers.IO) {
        val mutex = locks.getOrPut(path) { Mutex() }
        mutex.withLock {
            val dir = File(path)
            if (!File(dir, ".git").exists()) throw GitUserError("Folder ini bukan repository Git: $path")
            Git.open(dir).use(block)
        }
    }

    suspend fun initRepo(dir: File) = withContext(Dispatchers.IO) {
        if (File(dir, ".git").exists()) throw GitUserError("Folder ini sudah menjadi repository Git.")
        Git.init().setDirectory(dir).setInitialBranch("main").call().close()
        normalizeConfig(dir)
    }

    suspend fun clone(url: String, dir: File, pm: ProgressMonitor) = withContext(Dispatchers.IO) {
        if (dir.exists() && (dir.listFiles()?.isNotEmpty() == true)) {
            throw GitUserError("Folder tujuan tidak kosong: ${dir.absolutePath}")
        }
        Git.cloneRepository()
            .setURI(url)
            .setDirectory(dir)
            .setCredentialsProvider(AuthStore.providerFor(url))
            .setProgressMonitor(pm)
            .call()
            .close()
        normalizeConfig(dir)
    }

    /**
     * Shared storage Android (FUSE) tidak mendukung bit executable & symlink, sehingga
     * Git akan melaporkan semua file "berubah". Matikan keduanya untuk repo di sana.
     */
    fun normalizeConfig(dir: File) {
        Git.open(dir).use { g ->
            val c = g.repository.config
            c.setBoolean("core", null, "filemode", false)
            c.setBoolean("core", null, "symlinks", false)
            c.save()
        }
    }

    fun hasCommits(g: Git): Boolean = g.repository.resolve("HEAD") != null
}
