package com.furugit.app.github.workflows

/** Satu job di file workflow lokal (folder .github/workflows). */
data class LocalJob(val key: String, val name: String)

/** Satu input workflow_dispatch (type: boolean / string / choice / number / environment). */
data class DispatchInput(val name: String, val type: String, val default: String, val options: List<String>, val description: String)

/** Satu file workflow lokal beserta job dan input workflow_dispatch-nya. */
data class LocalWorkflow(
    val file: String, val name: String, val dispatch: Boolean, val jobs: List<LocalJob>,
    val inputs: List<DispatchInput> = emptyList()
)
