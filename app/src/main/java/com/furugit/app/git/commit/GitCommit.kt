package com.furugit.app.git.commit

import com.furugit.app.core.common.GitUserError
import com.furugit.app.data.preferences.Prefs
import com.furugit.app.git.repository.GitRepository
import com.furugit.app.git.status.ChangeKind
import com.furugit.app.git.status.FileChange
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.RepositoryState

/** Stage, unstage, buang perubahan, dan commit. */
object GitCommit {

    fun stage(g: Git, items: List<FileChange>) {
        for (c in items) {
            if (c.kind == ChangeKind.DELETED) g.rm().setCached(true).addFilepattern(c.path).call()
            else g.add().addFilepattern(c.path).call()
        }
    }

    fun stageAll(g: Git) {
        g.add().addFilepattern(".").call()
        g.add().setUpdate(true).addFilepattern(".").call() // ikut menandai file yang dihapus
    }

    fun unstage(g: Git, path: String) {
        if (GitRepository.hasCommits(g)) g.reset().addPath(path).call()
        else g.rm().setCached(true).addFilepattern(path).call()
    }

    /** Membuang perubahan di working tree. File untracked dihapus dari disk. */
    fun discard(g: Git, c: FileChange) {
        val f = File(g.repository.workTree, c.path)
        if (c.kind == ChangeKind.UNTRACKED) {
            f.deleteRecursively()
        } else {
            g.checkout().addPath(c.path).call()
        }
    }

    internal fun identity(g: Git): PersonIdent {
        var name = Prefs.gitName
        var email = Prefs.gitEmail
        val cfg = g.repository.config
        if (name.isBlank()) name = cfg.getString("user", null, "name") ?: ""
        if (email.isBlank()) email = cfg.getString("user", null, "email") ?: ""
        if (name.isBlank() || email.isBlank()) {
            throw GitUserError("Nama & email Git belum diatur. Buka Pengaturan → Identitas Git.")
        }
        return PersonIdent(name, email)
    }

    fun commit(g: Git, message: String): String {
        if (message.isBlank()) throw GitUserError("Pesan commit tidak boleh kosong.")
        val st = g.status().call()
        if (st.conflicting.isNotEmpty()) throw GitUserError("Masih ada file konflik. Selesaikan dulu di tab Perubahan.")
        val merging = g.repository.repositoryState != RepositoryState.SAFE
        if (!merging && st.added.isEmpty() && st.changed.isEmpty() && st.removed.isEmpty()) {
            throw GitUserError("Tidak ada perubahan yang di-stage.")
        }
        val who = identity(g)
        val c = g.commit().setMessage(message).setAuthor(who).setCommitter(who).call()
        return c.name.substring(0, 7)
    }
}
