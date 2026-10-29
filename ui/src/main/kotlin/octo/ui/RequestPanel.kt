package octo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import octo.core.agent.PermissionKind
import octo.core.agent.Question
import octo.core.task.NewAttachment
import octo.core.task.PendingRequest
import octo.ui.theme.Elevation
import octo.ui.theme.GlassLevel
import octo.ui.theme.Pyrlyn
import octo.ui.theme.glass
import octo.ui.vm.TaskViewModel

/** What the agent waits on; the task stays blocked until every request here is answered. */
@Composable
internal fun RequestPanel(
    request: PendingRequest,
    answers: Map<String, List<String>>,
    /** Files attached to free-text answers, by question id. */
    files: Map<String, List<NewAttachment>>,
    vm: TaskViewModel,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(Pyrlyn.surfaceShape, GlassLevel.Floating, Elevation.E2)
            .padding(LargeGap),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        when (request) {
            is PendingRequest.Permission -> Permission(request, vm)
            is PendingRequest.Questions -> Questions(request, answers, files, vm)
        }
    }
}

@Composable
private fun Asks(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
        Dot(Pyrlyn.colors.warning)
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Permission(
    request: PendingRequest.Permission,
    vm: TaskViewModel,
) {
    Asks("The agent asks: ${request.title}")
    Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
        request.options.forEach { option ->
            val allow = option.kind == PermissionKind.AllowOnce || option.kind == PermissionKind.AllowAlways
            if (allow) {
                PrimaryButton(onClick = { vm.choose(request.id, option.id) }) { Text(option.label) }
            } else {
                SecondaryButton(onClick = { vm.choose(request.id, option.id) }) { Text(option.label) }
            }
        }
    }
}

@Composable
private fun Questions(
    request: PendingRequest.Questions,
    answers: Map<String, List<String>>,
    files: Map<String, List<NewAttachment>>,
    vm: TaskViewModel,
) {
    Asks("The agent asks")
    request.questions.forEach { Question(request.id, it, answers[it.id].orEmpty(), files[it.id].orEmpty(), vm) }
    Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
        PrimaryButton(onClick = { vm.submitAnswers(request.id) }) { Text("Answer") }
        GhostButton(onClick = { vm.dismiss(request.id) }) { Text("Dismiss") }
    }
}

@Composable
private fun Question(
    requestId: String,
    question: Question,
    values: List<String>,
    files: List<NewAttachment>,
    vm: TaskViewModel,
) {
    Text(question.text, color = Pyrlyn.colors.fgMuted)
    if (question.options.isEmpty()) {
        val input =
            remember(vm, requestId, question.id) {
                AttachmentInput({ vm.attachToAnswer(requestId, question.id, it) }, vm::showError)
            }
        PendingAttachments(files) { vm.detachFromAnswer(requestId, question.id, it) }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Gap)) {
            GlassField(
                value = values.firstOrNull().orEmpty(),
                onValueChange = { vm.answer(requestId, question.id, it) },
                modifier = Modifier.weight(1f).takesAttachments(input),
            )
            AttachButton(input)
        }
        return
    }
    question.options.forEach { option ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (question.multiSelect) {
                Checkbox(
                    checked = option in values,
                    onCheckedChange = { vm.answer(requestId, question.id, option, toggle = true) },
                )
            } else {
                RadioButton(selected = option in values, onClick = { vm.answer(requestId, question.id, option) })
            }
            Text(option)
        }
    }
}
