package com.furugit.app.git

import com.furugit.app.data.AuthStore
import com.furugit.app.data.ActivityLog
import com.furugit.app.data.BranchInfo
import com.furugit.app.data.ChangeKind
import com.furugit.app.data.CommitInfo
import com.furugit.app.data.FileChange
import com.furugit.app.data.GitUserError
import com.furugit.app.data.OpResult
import com.furugit.app.data.Prefs
import com.furugit.app.data.RemoteInfo
import com.furugit.app.data.StashInfo
import com.furugit.app.data.StatusInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.TextProgressMonitor
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Pembungkus JGit. Aturan utama: satu operasi per repository pada satu waktu (Mutex),
 * UI tidak pernah memanggil JGit langsung.
 */
object GitEngine {

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

    /** Menulis baris progres JGit ke log aktivitas. */
    private class LogWriter : java.io.Writer() {
        private val buf = StringBuilder()

        override fun write(cbuf: CharArray, off: Int, len: Int) {
            buf.append(cbuf, off, len)
            var i = buf.indexOf("\n")
            while (i >= 0) {
                val line = buf.substring(0, i).substringAfterLast('\r').trim()
                if (line.isNotEmpty()) ActivityLog.add("   · $line")
                buf.delete(0, i + 1)
                i = buf.indexOf("\n")
            }
        }

        override fun flush() {}
        override fun close() {}
    }

    fun progress(): ProgressMonitor = TextProgressMonitor(LogWriter())

    // ------------------------------------------------------------------ repository

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

    // ------------------------------------------------------------------ status

    private fun tracking(g: Git): Pair<Int, Int>? = try {
        val t = BranchTrackingStatus.of(g.repository, g.repository.fullBranch)
        if (t == null) null else Pair(t.aheadCount, t.behindCount)
    } catch (e: Exception) {
        null
    }

    fun hasCommits(g: Git): Boolean = g.repository.resolve("HEAD") != null

    fun status(g: Git): StatusInfo {
        val repo = g.repository
        val st = g.status().call()
        val staged = ArrayList<FileChange>()
        for (p in st.added.sorted()) staged.add(FileChange(p, ChangeKind.ADDED, true))
        for (p in st.changed.sorted()) staged.add(FileChange(p, ChangeKind.MODIFIED, true))
        for (p in st.removed.sorted()) staged.add(FileChange(p, ChangeKind.DELETED, true))
        val unstaged = ArrayList<FileChange>()
        for (p in st.modified.sorted()) unstaged.add(FileChange(p, ChangeKind.MODIFIED, false))
        for (p in st.missing.sorted()) unstaged.add(FileChange(p, ChangeKind.DELETED, false))
        for (p in st.untracked.sorted()) unstaged.add(FileChange(p, ChangeKind.UNTRACKED, false))
        val tr = tracking(g)
        return StatusInfo(
            branch = repo.branch ?: "?",
            state = repo.repositoryState.name,
            hasCommits = hasCommits(g),
            staged = staged,
            unstaged = unstaged,
            conflicts = st.conflicting.sorted(),
            hasTracking = tr != null,
            ahead = tr?.first ?: 0,
            behind = tr?.second ?: 0
        )
    }

    // ------------------------------------------------------------------ add / restore / commit

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
        if (hasCommits(g)) g.reset().addPath(path).call()
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

    private fun identity(g: Git): PersonIdent {
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

    fun diff(g: Git, c: FileChange): String {
        val limit = 40_000
        if (c.kind == ChangeKind.UNTRACKED) {
            val f = File(g.repository.workTree, c.path)
            if (f.isDirectory) return "(folder)"
            if (f.length() > 200_000) return "(file besar, pratinjau dilewati)"
            val t = f.readText()
            return "+++ file baru: ${c.path}\n" + t.take(limit).lines().joinToString("\n") { "+$it" }
        }
        val out = ByteArrayOutputStream()
        try {
            g.diff().setPathFilter(PathFilter.create(c.path)).setCached(c.staged).setOutputStream(out).call()
        } catch (e: Exception) {
            return "(diff tidak tersedia: ${e.message})"
        }
        val s = out.toString("UTF-8")
        return if (s.isBlank()) "(tidak ada perbedaan teks)" else if (s.length > limit) s.take(limit) + "\n… (dipotong)" else s
    }

    // ------------------------------------------------------------------ konflik

    fun resolveKeep(g: Git, path: String, mine: Boolean) {
        val stage = if (mine) CheckoutCommand.Stage.OURS else CheckoutCommand.Stage.THEIRS
        g.checkout().addPath(path).setStage(stage).call()
        g.add().addFilepattern(path).call()
    }

    fun readWorkFile(g: Git, path: String): String = File(g.repository.workTree, path).readText()

    fun writeResolved(g: Git, path: String, text: String) {
        File(g.repository.workTree, path).writeText(text)
        g.add().addFilepattern(path).call()
    }

    fun abortMerge(g: Git) {
        g.reset().setMode(ResetCommand.ResetType.HARD).call()
    }

    // ------------------------------------------------------------------ branch

    fun branches(g: Git): List<BranchInfo> {
        val cur = g.repository.branch
        val out = ArrayList<BranchInfo>()
        for (r in g.branchList().call()) {
            val n = Repository.shortenRefName(r.name)
            out.add(BranchInfo(n, n == cur, false))
        }
        for (r in g.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()) {
            val n = Repository.shortenRefName(r.name)
            if (!n.endsWith("/HEAD")) out.add(BranchInfo(n, false, true))
        }
        return out
    }

    fun createBranch(g: Git, name: String) {
        if (!Repository.isValidRefName("refs/heads/$name")) throw GitUserError("Nama branch tidak valid.")
        if (!hasCommits(g)) throw GitUserError("Buat commit pertama dulu sebelum membuat branch.")
        g.checkout().setCreateBranch(true).setName(name).call()
    }

    fun switchBranch(g: Git, name: String, isRemote: Boolean) {
        if (g.status().call().hasUncommittedChanges()) {
            throw GitUserError("Ada perubahan yang belum di-commit. Commit atau stash dulu agar tidak tertimpa.")
        }
        if (isRemote) {
            val local = name.substringAfter('/')
            val exists = g.branchList().call().any { Repository.shortenRefName(it.name) == local }
            if (exists) {
                g.checkout().setName(local).call()
            } else {
                g.checkout().setCreateBranch(true).setName(local).setStartPoint(name)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).call()
            }
        } else {
            g.checkout().setName(name).call()
        }
    }

    fun deleteBranch(g: Git, name: String, force: Boolean) {
        val res = g.branchDelete().setBranchNames(name).setForce(force).call()
        if (res.isEmpty()) {
            throw GitUserError("Branch belum di-merge. Hapus paksa jika kamu yakin.")
        }
    }

    private fun mergeOutcome(r: MergeResult): OpResult {
        val status = r.mergeStatus
        return when (status) {
            MergeResult.MergeStatus.CONFLICTING -> {
                val files = r.conflicts?.keys?.toList() ?: emptyList()
                OpResult(false, "Merge menghasilkan konflik pada ${files.size} file.", files)
            }
            MergeResult.MergeStatus.FAILED -> {
                val files = r.failingPaths?.keys?.toList() ?: emptyList()
                OpResult(false, "Merge gagal: file lokal akan tertimpa. Commit/stash dulu.", files)
            }
            else -> if (status.isSuccessful) OpResult(true, "Merge berhasil ($status).")
            else OpResult(false, "Merge tidak berhasil: $status")
        }
    }

    fun merge(g: Git, source: String): OpResult {
        if (g.status().call().hasUncommittedChanges()) {
            throw GitUserError("Ada perubahan yang belum di-commit. Commit atau stash dulu sebelum merge.")
        }
        val id = g.repository.resolve(source) ?: throw GitUserError("Branch '$source' tidak ditemukan.")
        val r = g.merge().include(id).setCommit(true)
            .setMessage("Merge $source into ${g.repository.branch}").call()
        return mergeOutcome(r)
    }

    // ------------------------------------------------------------------ stash

    fun stashSave(g: Git, message: String, includeUntracked: Boolean) {
        val who = identity(g)
        val msg = message.ifBlank { "WIP on ${g.repository.branch}" }
        g.stashCreate().setPerson(who).setIncludeUntracked(includeUntracked)
            .setWorkingDirectoryMessage(msg).call()
            ?: throw GitUserError("Tidak ada perubahan untuk di-stash.")
    }

    fun stashList(g: Git): List<StashInfo> {
        if (!hasCommits(g)) return emptyList()
        return g.stashList().call().mapIndexed { i, c -> StashInfo(i, c.shortMessage, c.commitTime * 1000L) }
    }

    fun stashApply(g: Git, index: Int) {
        g.stashApply().setStashRef("stash@{" + index + "}").call()
    }

    fun stashDrop(g: Git, index: Int) {
        g.stashDrop().setStashRef(index).call()
    }

    fun stashPop(g: Git, index: Int) {
        stashApply(g, index)
        stashDrop(g, index)
    }

    fun stashClear(g: Git) {
        g.stashDrop().setAll(true).call()
    }

    // ------------------------------------------------------------------ remote

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

    fun defaultRemote(g: Git): String? {
        val names = remotes(g).map { it.name }
        return if ("origin" in names) "origin" else names.firstOrNull()
    }

    private fun creds(g: Git, remote: String): CredentialsProvider? {
        val url = g.repository.config.getString("remote", remote, "url") ?: return null
        return AuthStore.providerFor(url)
    }

    fun fetch(g: Git, remote: String, pm: ProgressMonitor?) {
        g.fetch().setRemote(remote).setRemoveDeletedRefs(true)
            .setCredentialsProvider(creds(g, remote)).setProgressMonitor(pm).call()
    }

    fun pull(g: Git, remote: String, rebase: Boolean, pm: ProgressMonitor?): OpResult {
        val repo = g.repository
        val branch = repo.branch
        if (repo.fullBranch?.startsWith("refs/heads/") != true) throw GitUserError("HEAD sedang detached. Pindah ke sebuah branch dulu.")
        if (rebase && g.status().call().hasUncommittedChanges()) {
            throw GitUserError("Rebase butuh working tree bersih. Commit atau stash dulu.")
        }
        val cmd = g.pull().setRemote(remote).setRebase(rebase)
            .setCredentialsProvider(creds(g, remote)).setProgressMonitor(pm)
        if (repo.config.getString("branch", branch, "merge") == null) cmd.setRemoteBranchName(branch)
        val r = cmd.call()
        if (r.isSuccessful) return OpResult(true, "Pull berhasil.")
        if (rebase) {
            try {
                g.rebase().setOperation(RebaseCommand.Operation.ABORT).call()
            } catch (_: Exception) {
            }
            return OpResult(false, "Rebase menemui konflik dan dibatalkan. Coba Pull dengan mode Merge.")
        }
        val mr = r.mergeResult ?: return OpResult(false, "Pull tidak berhasil.")
        return mergeOutcome(mr)
    }

    private fun checkPush(results: Iterable<PushResult>): Boolean {
        var allUpToDate = true
        for (res in results) {
            for (u in res.remoteUpdates) {
                when (u.status) {
                    RemoteRefUpdate.Status.OK -> allUpToDate = false
                    RemoteRefUpdate.Status.UP_TO_DATE -> {}
                    else -> throw GitUserError(
                        "Push ditolak (${u.status}). " + (u.message ?: "") +
                            if (u.status == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD) " Lakukan Pull dulu." else ""
                    )
                }
            }
        }
        return allUpToDate
    }

    private fun requireBranch(g: Git) {
        if (!hasCommits(g)) throw GitUserError("Belum ada commit untuk dikirim.")
        if (g.repository.fullBranch?.startsWith("refs/heads/") != true) {
            throw GitUserError("HEAD sedang detached. Pindah ke sebuah branch dulu.")
        }
    }

    fun push(g: Git, remote: String, pm: ProgressMonitor?): OpResult {
        requireBranch(g)
        val branch = g.repository.branch
        val results = g.push().setRemote(remote)
            .setRefSpecs(RefSpec("refs/heads/$branch:refs/heads/$branch"))
            .setCredentialsProvider(creds(g, remote)).setProgressMonitor(pm).call()
        val upToDate = checkPush(results)
        val cfg = g.repository.config
        if (cfg.getString("branch", branch, "remote") == null) {
            cfg.setString("branch", branch, "remote", remote)
            cfg.setString("branch", branch, "merge", "refs/heads/$branch")
            cfg.save()
        }
        return OpResult(true, if (upToDate) "Sudah up to date, tidak ada yang dikirim." else "Push ke $remote/$branch berhasil.")
    }

    /** Fetch dulu; jika remote lebih baru, jangan push (tidak pernah push paksa). */
    fun safePush(g: Git, remote: String, pm: ProgressMonitor?): OpResult {
        requireBranch(g)
        fetch(g, remote, pm)
        val tr = tracking(g)
        if (tr != null && tr.second > 0) {
            throw GitUserError("Remote punya ${tr.second} commit baru yang belum ada di lokal. Lakukan Pull dulu, lalu Push.")
        }
        return push(g, remote, pm)
    }

    fun pushTag(g: Git, remote: String, tag: String, pm: ProgressMonitor?) {
        val results = g.push().setRemote(remote).setRefSpecs(RefSpec("refs/tags/$tag"))
            .setCredentialsProvider(creds(g, remote)).setProgressMonitor(pm).call()
        checkPush(results)
    }

    /** Stage → commit → fetch → cek sinkron → push. */
    fun quickSync(g: Git, include: List<FileChange>, message: String, pm: ProgressMonitor?): OpResult {
        if (include.isNotEmpty()) stage(g, include)
        val id = commit(g, message)
        ActivityLog.add("Commit $id dibuat")
        val remote = defaultRemote(g)
            ?: throw GitUserError("Commit $id dibuat, tetapi repository belum punya remote. Tambahkan di tab Sync.")
        val res = try {
            safePush(g, remote, pm)
        } catch (e: GitUserError) {
            throw GitUserError("Commit $id sudah tersimpan lokal, tetapi push gagal: ${e.message}")
        }
        return res.copy(message = "Commit $id → ${res.message}")
    }

    // ------------------------------------------------------------------ history

    fun log(g: Git, ref: String?, max: Int = 150): List<CommitInfo> {
        val start = g.repository.resolve(ref ?: "HEAD") ?: return emptyList()
        return g.log().add(start).setMaxCount(max).call().map {
            CommitInfo(it.name, it.name.substring(0, 7), it.shortMessage, it.authorIdent.name, it.commitTime * 1000L)
        }
    }

    private fun requireClean(g: Git) {
        if (g.status().call().hasUncommittedChanges()) {
            throw GitUserError("Ada perubahan yang belum di-commit. Commit atau stash dulu.")
        }
    }

    fun revert(g: Git, commitId: String): OpResult {
        requireClean(g)
        val r = g.revert().include(ObjectId.fromString(commitId)).call()
        if (r == null) {
            g.reset().setMode(ResetCommand.ResetType.HARD).call()
            return OpResult(false, "Revert menyebabkan konflik, sehingga dibatalkan. Tidak ada yang berubah.")
        }
        return OpResult(true, "Commit di-revert (commit pembatalan baru dibuat).")
    }

    fun cherryPick(g: Git, commitId: String): OpResult {
        requireClean(g)
        val r = g.cherryPick().include(ObjectId.fromString(commitId)).call()
        return when (r.status) {
            org.eclipse.jgit.api.CherryPickResult.CherryPickStatus.OK -> OpResult(true, "Cherry-pick berhasil.")
            else -> {
                g.reset().setMode(ResetCommand.ResetType.HARD).call()
                OpResult(false, "Cherry-pick gagal/konflik (${r.status}) dan dibatalkan. Tidak ada yang berubah.")
            }
        }
    }

    fun reset(g: Git, commitId: String, mode: ResetCommand.ResetType) {
        g.reset().setMode(mode).setRef(commitId).call()
    }

    // ------------------------------------------------------------------ tag

    fun tags(g: Git): List<String> =
        g.tagList().call().map { Repository.shortenRefName(it.name) }.sorted()

    fun createTag(g: Git, name: String, commitId: String) {
        if (!Repository.isValidRefName("refs/tags/$name")) throw GitUserError("Nama tag tidak valid.")
        val c = g.repository.parseCommit(ObjectId.fromString(commitId))
        g.tag().setName(name).setObjectId(c).setAnnotated(false).call()
    }

    fun deleteTag(g: Git, name: String) {
        g.tagDelete().setTags(name).call()
    }
}

/** Mengubah exception teknis menjadi pesan yang bisa dipahami pengguna. */
fun Throwable.friendly(): String {
    if (this is GitUserError) return message ?: "Terjadi kesalahan."
    val chain = generateSequence(this) { it.cause }.toList()
    val all = chain.joinToString(" | ") { it.message ?: it.javaClass.simpleName }
    return when {
        chain.any { it is java.net.UnknownHostException } || all.contains("Unable to resolve host", true) ->
            "Tidak ada koneksi internet / host tidak ditemukan."
        all.contains("not authorized", true) || all.contains("Authentication is required", true) ||
            all.contains("Auth fail", true) || all.contains("401") || all.contains("403") ->
            "Autentikasi gagal. Periksa akun/token di Pengaturan (PAT butuh izin Contents: Read and write)."
        all.contains("index.lock", true) || all.contains("LockFailed", true) ->
            "Repository terkunci (.git/index.lock). Jika tidak ada operasi Git lain, hapus file itu."
        all.contains("EACCES") || all.contains("Permission denied", true) ->
            "Tidak punya izin akses file. Pastikan izin 'Akses semua file' sudah diberikan."
        all.contains("CheckoutConflict", true) || all.contains("conflict", true) ->
            "Ada perubahan lokal yang bentrok: $all"
        else -> "${javaClass.simpleName}: ${message ?: ""}".trim()
    }
}
