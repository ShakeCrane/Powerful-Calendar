package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable

enum class NativeMonthKind { ALARM, ACTIVITY, DEADLINE, MEMO }

/** Display values for one formal event occurrence in the visible month. */
data class NativeMonthRow(
    val rowKey: String,
    val kind: NativeMonthKind,
    val kindLabel: String,
    val title: String,
    val summary: String,
    val sequenceLabel: String,
    val visibleDateKeys: List<String>
)

data class NativeMonthDialog(
    val onDismissRequest: () -> Unit,
    val title: @Composable () -> Unit,
    val text: @Composable () -> Unit,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: (@Composable () -> Unit)? = null
)

typealias NativeMonthPanel = @Composable (
    @Composable () -> Unit
) -> Unit

typealias NativeMonthDialogSlot = @Composable (NativeMonthDialog) -> Unit

/** Primitive visual tokens and Host-owned Compose slots required by the Native renderer. */
data class NativeMonthAppearance(
    val gridCellCornerRadiusDp: Float = 14f,
    val eventRowCornerRadiusDp: Float,
    val panel: NativeMonthPanel,
    val showDialog: NativeMonthDialogSlot,
    val kindColors: Map<NativeMonthKind, Int>
)
