package dev.powerfulcalendar.ui.v2.nativeui

import dev.powerfulcalendar.ui.v2.VisualTab

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * First-party fallback. All tabs retain their original host routes.
 * The profile-specific Memo page remains reachable from the timeline header.
 */
@Composable
fun NativePowerNavigation(
    selected: VisualTab?,
    onSelect: (VisualTab) -> Unit,
    settingsIcon: @Composable () -> Unit
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        NavigationBarItem(
            selected = selected == VisualTab.TIMELINE,
            onClick = { onSelect(VisualTab.TIMELINE) },
            icon = { Icon(Icons.Default.Timeline, contentDescription = null) },
            label = { Text("时间轴") }
        )
        NavigationBarItem(
            selected = selected == VisualTab.MONTH,
            onClick = { onSelect(VisualTab.MONTH) },
            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
            label = { Text("日历") }
        )
        NavigationBarItem(
            selected = selected == VisualTab.AI,
            onClick = { onSelect(VisualTab.AI) },
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
            label = { Text("AI 助手") }
        )
        NavigationBarItem(
            selected = selected == VisualTab.TOOLS,
            onClick = { onSelect(VisualTab.TOOLS) },
            icon = settingsIcon,
            label = { Text("设置") }
        )
    }
}
