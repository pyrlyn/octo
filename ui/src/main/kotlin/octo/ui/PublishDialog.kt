package octo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import octo.core.publish.PublishResult
import octo.ui.vm.PublishViewModel

@Composable
internal fun PublishDialog(
    vm: PublishViewModel,
    onDismiss: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val result = state.result
    GlassDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(DialogWidth),
        title = { Text(if (result == null) "Commit, push and open a pull request" else "Published") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                if (result != null) {
                    Published(result)
                } else {
                    GlassField(
                        state.commitMessage,
                        vm::editCommitMessage,
                        Modifier.fillMaxWidth(),
                        label = { Text("Commit message") },
                    )
                    GlassField(
                        state.title,
                        vm::editTitle,
                        Modifier.fillMaxWidth(),
                        label = { Text("Pull request title") },
                    )
                    GlassField(
                        state.body,
                        vm::editBody,
                        Modifier.fillMaxWidth(),
                        label = { Text("Description") },
                        minLines = 3,
                    )
                    Text(
                        "Uses your own git and gh login. Nothing is committed when the worktree is clean.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.error?.let { Notice(it, Tone.Error) }
            }
        },
        confirmButton = {
            if (result == null) {
                PrimaryButton(
                    onClick = vm::publish,
                    enabled = state.canPublish,
                ) { Text(if (state.running) "Publishing…" else "Publish") }
            } else {
                PrimaryButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = { if (result == null) GhostButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Published(result: PublishResult) {
    val uri = LocalUriHandler.current
    Text(
        result.commit?.let { "Committed ${it.take(SHORT_SHA)} and pushed." }
            ?: "Nothing new to commit; pushed the branch.",
    )
    Text(if (result.created) "Opened a pull request:" else "The branch already has an open pull request:")
    GhostButton(onClick = { uri.openUri(result.pullRequestUrl) }) { Text(result.pullRequestUrl) }
}

private const val SHORT_SHA = 8
