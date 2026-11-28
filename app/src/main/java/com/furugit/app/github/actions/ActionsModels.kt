package com.furugit.app.github.actions

data class WorkflowRun(
    val id: Long, val workflowId: Long, val workflow: String, val title: String, val number: Int,
    val status: String, val conclusion: String?, val event: String, val branch: String,
    val time: Long, val url: String, val path: String
)

data class RunJob(val id: Long, val name: String, val status: String, val conclusion: String?)
