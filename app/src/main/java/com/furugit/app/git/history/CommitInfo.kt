package com.furugit.app.git.history

data class CommitInfo(
    val id: String,
    val shortId: String,
    val message: String,
    val author: String,
    val time: Long
)
