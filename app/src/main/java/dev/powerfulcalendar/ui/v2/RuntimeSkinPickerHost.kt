package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneOffset

/** Platform picker intents are promoted into a host-owned modal composable only for v2 packs. */
internal object RuntimePickerRequest {
    var current by mutableStateOf<PickerVisualContext?>(null)
        private set

    fun show(request: PickerVisualContext) { current = request }
    fun dismiss() { current = null }
}

/**
 * Runs on top of root chrome so imported UI can never hide this modal behind app content.
 * No date mutation is committed until the explicit confirm widget is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuntimeSkinPickerHost() {
    val request = RuntimePickerRequest.current ?: return
    RuntimePickerModal(request)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuntimePickerModal(request: PickerVisualContext) {
        val dateStart = request.initialDate.atStartOfDay(ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val date = rememberDatePickerState(initialSelectedDateMillis = dateStart)
        val time = rememberTimePickerState(
            initialHour = request.initialHour, initialMinute = request.initialMinute,
            is24Hour = true
        )
        val close = { RuntimePickerRequest.dismiss() }
        val confirm = {
            RuntimePickerRequest.dismiss()
            if (request.kind == PickerKind.DATE) {
                val millis = date.selectedDateMillis
                if (millis != null) {
                    request.onDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
            } else request.onTime(time.hour, time.minute)
        }
        val node = PowerCalSkinRegistry.active.runtime?.screens?.get("picker")
        if (node == null || hasNative(node)) {
            LaunchedEffect(request) {
                RuntimePickerRequest.dismiss()
                request.Native()
            }
            return
        }
        Dialog(
            onDismissRequest = close,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                RuntimeSkinCompose.scene(
                    "picker",
                    RuntimeSkinBindings(
                        native = { },
                        actions = mapOf("native" to {
                            RuntimePickerRequest.dismiss()
                            request.Native()
                        }),
                        widget = { kind ->
                            when (kind) {
                                "picker-title" -> Text(
                                    if (request.kind == PickerKind.DATE) "选择日期" else "选择时间",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                "picker-date" -> if (request.kind == PickerKind.DATE) {
                                    DatePicker(state = date)
                                }
                                "picker-time" -> if (request.kind == PickerKind.TIME) {
                                    TimePicker(state = time)
                                }
                                "picker-confirm" -> TextButton(onClick = confirm) { Text("确定") }
                                "picker-dismiss" -> TextButton(onClick = close) { Text("取消") }
                            }
                        }
                    ),
                    Modifier.fillMaxWidth(),
                    manageRecovery = false
                )
            }
        }
}

private fun hasNative(node: RuntimeSkinNode): Boolean =
    node.type == "native" || node.children.any { hasNative(it) }
