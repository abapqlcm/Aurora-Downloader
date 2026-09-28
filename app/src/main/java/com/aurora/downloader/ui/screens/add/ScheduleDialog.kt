package com.aurora.downloader.ui.screens.add

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurora.downloader.download.scheduler.ScheduleConditions
import java.util.Calendar

/**
 * The "Download later" sheet: pick when and under what conditions a download
 * may start. Produces [ScheduleConditions]; the engine does the waiting.
 */
@Composable
fun ScheduleDialog(
    onConfirm: (ScheduleConditions) -> Unit,
    onDismiss: () -> Unit
) {
    var immediate by remember { mutableStateOf(true) }
    var hour by remember { mutableStateOf(21) }
    var minute by remember { mutableStateOf(0) }
    var tomorrow by remember { mutableStateOf(false) }
    var unmetered by remember { mutableStateOf(true) }
    var charging by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text("Download later", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionRow(
                    label = "Start immediately",
                    selected = immediate,
                    onClick = { immediate = true }
                )
                OptionRow(
                    label = "At a specific time",
                    selected = !immediate,
                    onClick = { immediate = false }
                )

                if (!immediate) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Start at", style = MaterialTheme.typography.bodyMedium)
                        TimeWheel(
                            hour = hour,
                            minute = minute,
                            onHourChange = { hour = it },
                            onMinuteChange = { minute = it }
                        )
                        Checkbox(
                            checked = tomorrow,
                            onCheckedChange = { tomorrow = it }
                        )
                        Text(
                            if (tomorrow) "Tomorrow" else "Today",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                ConditionRow(
                    label = "Only on Wi-Fi",
                    description = "Wait for an unmetered network",
                    checked = unmetered,
                    onCheckedChange = { unmetered = it }
                )
                ConditionRow(
                    label = "Only while charging",
                    description = "Wait until the device is plugged in",
                    checked = charging,
                    onCheckedChange = { charging = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val at = if (immediate) 0L else {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        if (tomorrow) add(Calendar.DAY_OF_MONTH, 1)
                        // A time that already passed today means tomorrow;
                        // otherwise the schedule would wait a full day.
                        if (!tomorrow && timeInMillis <= System.currentTimeMillis()) {
                            add(Calendar.DAY_OF_MONTH, 1)
                        }
                    }
                    cal.timeInMillis
                }
                onConfirm(
                    ScheduleConditions(
                        startAtEpochMillis = at,
                        requireUnmetered = unmetered,
                        requireCharging = charging
                    )
                )
            }) { Text("Schedule", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ConditionRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A crude but functional hour/minute picker — no dependency on a wheel lib. */
@Composable
private fun TimeWheel(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Stepper(
            value = hour,
            range = 0..23,
            onValueChange = onHourChange,
            format = { "%02d".format(it) }
        )
        Text(" : ", style = MaterialTheme.typography.titleMedium)
        Stepper(
            value = minute,
            range = 0..59,
            onValueChange = onMinuteChange,
            format = { "%02d".format(it) }
        )
    }
}

@Composable
private fun Stepper(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    format: (Int) -> String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable {
                    onValueChange(
                        if (value <= range.first) range.last
                        else value - 1
                    )
                },
            contentAlignment = Alignment.Center
        ) { Text("−", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.width(6.dp))
        Text(
            format(value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(34.dp)
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable {
                    onValueChange(
                        if (value >= range.last) range.first
                        else value + 1
                    )
                },
            contentAlignment = Alignment.Center
        ) { Text("+", style = MaterialTheme.typography.titleMedium) }
    }
}
