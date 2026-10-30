package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable
import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.ui.v2.nativeui.NativeAiBatchAuthorizationReview as NativeBatchReview

/** A read-only, frozen preview. The only write authorization is the explicit confirm callback. */
@Composable
internal fun AiBatchAuthorizationReview(
    mode: AiBatchRunner.Mode,
    cards: List<DraftCard>,
    busy: Boolean,
    onAuthorize: (List<DraftCard>) -> Unit,
    onDismiss: () -> Unit
) {
    val snapshot = cards.toList()
    NativeBatchReview(
        model = projectNativeAiBatchAuthorization(mode, snapshot),
        busy = busy,
        onAuthorize = { onAuthorize(snapshot) },
        onDismiss = onDismiss,
        showDialog = { dialog ->
            SkinAlertDialog(
                onDismissRequest = dialog.onDismissRequest,
                title = dialog.title,
                text = dialog.text,
                confirmButton = dialog.confirmButton,
                dismissButton = dialog.dismissButton
            )
        }
    )
}
