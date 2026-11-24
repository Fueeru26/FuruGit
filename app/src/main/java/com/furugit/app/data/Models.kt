package com.furugit.app.data

data class RepoEntry(val name: String, val path: String)

enum class ChangeKind { ADDED, MODIFIED, DELETED, UNTRACKED }

data class FileChange(val path: String, val kind: ChangeKind, val staged: Boolean)

data class StatusInfo(
    val branch: String,
    val state: String,
    val hasCommits: Boolean,
    val staged: List<FileChange>,
    val unstaged: List<FileChange>,
    val conflicts: List<String>,
    val hasTracking: Boolean,
    val ahead: Int,
    val behind: Int
) {
    val isMerging: Boolean get() = state != "SAFE"
    val hasUncommitted: Boolean get() = staged.isNotEmpty() || unstaged.any { it.kind != ChangeKind.UNTRACKED } || conflicts.isNotEmpty()
}

data class CommitInfo(
    val id: String,
    val shortId: String,
    val message: String,
    val author: String,
    val time: Long
)

data class BranchInfo(val name: String, val isCurrent: Boolean, val isRemote: Boolean)

data class StashInfo(val index: Int, val message: String, val time: Long)

data class RemoteInfo(val name: String, val url: String)

data class Account(val host: String, val username: String, val token: String, val type: String)

/** Hasil operasi Git yang bisa "gagal secara wajar" (mis. konflik). */
data class OpResult(val ok: Boolean, val message: String, val conflicts: List<String> = emptyList())

/** Error yang pesannya sudah ramah dibaca pengguna. */
class GitUserError(message: String) : Exception(message)
