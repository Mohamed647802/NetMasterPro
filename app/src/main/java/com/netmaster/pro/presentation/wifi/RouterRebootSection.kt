package com.netmaster.pro.presentation.wifi

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netmaster.pro.R

@Composable
fun RouterRebootSection(
    onRebootNow: () -> Unit = {},
    onScheduleReboot: (hour: Int, minute: Int, days: Set<Int>) -> Unit = { _, _, _ -> },
    onDownloadBackup: () -> Unit = {},
    onRestoreConfig: () -> Unit = {}
) {
    var showRebootConfirmDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    // Reboot confirmation dialog
    if (showRebootConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRebootConfirmDialog = false },
            title = { Text(stringResource(R.string.reboot_confirm_title)) },
            text = { Text(stringResource(R.string.reboot_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showRebootConfirmDialog = false
                        onRebootNow()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.reboot_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Schedule reboot dialog
    if (showScheduleDialog) {
        ScheduleRebootDialog(
            onSchedule = { h, m, days ->
                showScheduleDialog = false
                onScheduleReboot(h, m, days)
            },
            onDismiss = { showScheduleDialog = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.reboot_section_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Divider()

            // Reboot Now
            Button(
                onClick = { showRebootConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.reboot_now_button))
            }

            // Schedule Reboot
            OutlinedButton(
                onClick = { showScheduleDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.reboot_schedule_button))
            }

            Spacer(Modifier.height(4.dp))

            // Config backup / restore
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDownloadBackup,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.reboot_backup_button), fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onRestoreConfig,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.RestorePage, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.reboot_restore_button), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ScheduleRebootDialog(
    onSchedule: (hour: Int, minute: Int, days: Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedHour by remember { mutableStateOf(3) }
    var selectedMinute by remember { mutableStateOf(0) }
    var selectedDays by remember { mutableStateOf(setOf(1)) } // Monday by default

    val dayLabels = listOf(
        1 to "Mo", 2 to "Tu", 3 to "We", 4 to "Th",
        5 to "Fr", 6 to "Sa", 7 to "Su"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reboot_schedule_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Time picker button
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                selectedHour = hour
                                selectedMinute = minute
                            },
                            selectedHour,
                            selectedMinute,
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            R.string.reboot_schedule_time,
                            "%02d:%02d".format(selectedHour, selectedMinute)
                        )
                    )
                }

                // Day picker
                Text(stringResource(R.string.reboot_schedule_days), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayLabels.forEach { (dayNum, label) ->
                        FilterChip(
                            selected = dayNum in selectedDays,
                            onClick = {
                                val current = selectedDays.toMutableSet()
                                if (dayNum in current) current.remove(dayNum) else current.add(dayNum)
                                selectedDays = current
                            },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSchedule(selectedHour, selectedMinute, selectedDays) },
                enabled = selectedDays.isNotEmpty()
            ) {
                Text(stringResource(R.string.reboot_schedule_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
