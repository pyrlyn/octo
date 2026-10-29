package octo.feature.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import octo.core.agent.PermissionKind
import octo.core.agent.Question
import octo.core.task.NewAttachment
import octo.core.task.PendingRequest
import octo.design.Choice
import octo.design.Dot
import octo.design.Elevation
import octo.design.Gap
import octo.design.GhostButton
import octo.design.GlassField
import octo.design.GlassLevel
import octo.design.LargeGap
import octo.design.PrimaryButton
import octo.design.Pyrlyn
import octo.design.SecondaryButton
import octo.design.Text
import octo.design.glass
import octo.feature.common.AttachButton
import octo.feature.common.AttachmentInput
import octo.feature.common.PendingAttachments
import octo.feature.common.label
import octo.feature.common.takesAttachments

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
        Choice(option, option in values, {
            vm.answer(requestId, question.id, option, toggle = question.multiSelect)
        }, multiple = question.multiSelect)
    }
}
