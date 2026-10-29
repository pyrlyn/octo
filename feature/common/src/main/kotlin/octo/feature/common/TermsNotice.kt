package octo.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import octo.core.agent.VendorTerms
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.AgentInfo
import octo.design.DialogWidth
import octo.design.Gap
import octo.design.GhostButton
import octo.design.GlassDialog
import octo.design.PrimaryButton

/** Vendor terms the user still has to accept before [agent] runs. */
data class TermsNotice(
    val agent: AgentInfo,
    val terms: VendorTerms,
)

/** Octo never decides this for the user: the vendor's own terms are linked and must be accepted here. */
@Composable
fun TermsDialog(
    notice: TermsNotice,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val uri = LocalUriHandler.current
    GlassDialog(
        onDismissRequest = onDecline,
        modifier = Modifier.width(DialogWidth),
        title = { Text("${notice.agent.displayName}: vendor terms") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                Text(notice.terms.summary)
                GhostButton(onClick = { uri.openUri(notice.terms.url) }) { Text("Read the terms") }
            }
        },
        confirmButton = { PrimaryButton(onClick = onAccept) { Text("I understand, continue") } },
        dismissButton = { GhostButton(onClick = onDecline) { Text("Back") } },
    )
}

/** Octo never accepts vendor terms for the user: core stores their consent, then [retry] runs again. */
fun CoroutineScope.acceptAndRetry(
    catalog: AgentCatalog,
    notice: TermsNotice,
    onError: (Exception) -> Unit,
    retry: suspend () -> Unit,
): Job =
    action(onError) {
        catalog.acknowledgeTerms(notice.agent.id)
        retry()
    }
