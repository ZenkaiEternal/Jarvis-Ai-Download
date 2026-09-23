package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NoteEntity
import com.example.data.model.ReminderEntity
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGreen
import com.example.ui.theme.JarvisRedAlert
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceHighlight
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MissionLogsTab(
    notes: List<NoteEntity>,
    reminders: List<ReminderEntity>,
    onAddNote: (String, String, String) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onAddReminder: (String, Long, String) -> Unit,
    onToggleReminder: (Long, Boolean) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableIntStateOf(0) } // 0: Tactical Notes, 1: Reminders
    var showAddDialog by remember { mutableStateOf(false) }
    var itemTitle by remember { mutableStateOf("") }
    var itemContent by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(16.dp)
    ) {
        // Top Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(JarvisSurfaceVariant)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (subTab == 0) JarvisCyan else JarvisSurfaceVariant)
                    .clickable { subTab = 0 }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TACTICAL NOTES (${notes.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (subTab == 0) JarvisBackground else JarvisTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (subTab == 1) JarvisCyan else JarvisSurfaceVariant)
                    .clickable { subTab = 1 }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "REMINDERS (${reminders.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (subTab == 1) JarvisBackground else JarvisTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (subTab == 0) "MISSION LOG ENTRIES" else "SCHEDULED PROTOCOLS",
                style = MaterialTheme.typography.titleSmall,
                color = JarvisCyan,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = { showAddDialog = !showAddDialog },
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(JarvisSurfaceHighlight)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create",
                    tint = JarvisCyan
                )
            }
        }

        // Add form
        if (showAddDialog) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(JarvisSurface)
                    .border(1.dp, JarvisCyan, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = itemTitle,
                        onValueChange = { itemTitle = it },
                        label = { Text("Title / Objective", color = JarvisTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisCyan.copy(alpha = 0.3f),
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        singleLine = true
                    )

                    if (subTab == 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = itemContent,
                            onValueChange = { itemContent = it },
                            label = { Text("Content / Coordinates", color = JarvisTextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisCyan,
                                unfocusedBorderColor = JarvisCyan.copy(alpha = 0.3f),
                                focusedTextColor = JarvisTextPrimary,
                                unfocusedTextColor = JarvisTextPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { showAddDialog = false }) {
                            Text("CANCEL", color = JarvisTextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (itemTitle.isNotBlank()) {
                                    if (subTab == 0) {
                                        onAddNote(itemTitle, itemContent.ifBlank { "Recorded." }, "TACTICAL")
                                    } else {
                                        onAddReminder(itemTitle, System.currentTimeMillis() + 3600000L, "NORMAL")
                                    }
                                    itemTitle = ""
                                    itemContent = ""
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisBackground)
                        ) {
                            Text("SAVE ENTRY", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (subTab == 0) {
                if (notes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("NO TACTICAL NOTES RECORDED", color = JarvisTextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    items(notes, key = { it.id }) { note ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(JarvisSurface)
                                .border(1.dp, JarvisCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(note.title, style = MaterialTheme.typography.titleSmall, color = JarvisCyan, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(note.content, style = MaterialTheme.typography.bodyMedium, color = JarvisTextPrimary)
                                }
                                IconButton(onClick = { onDeleteNote(note.id) }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = JarvisRedAlert)
                                }
                            }
                        }
                    }
                }
            } else {
                if (reminders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("NO SCHEDULED PROTOCOLS", color = JarvisTextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    items(reminders, key = { it.id }) { rem ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(JarvisSurface)
                                .border(1.dp, if (rem.isCompleted) JarvisGreen.copy(alpha = 0.3f) else JarvisGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { onToggleReminder(rem.id, !rem.isCompleted) }) {
                                        Icon(
                                            imageVector = if (rem.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = "Toggle",
                                            tint = if (rem.isCompleted) JarvisGreen else JarvisGold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = rem.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (rem.isCompleted) JarvisTextMuted else JarvisTextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "PRIORITY: ${rem.priority}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (rem.priority == "HIGH") JarvisRedAlert else JarvisGold,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                                IconButton(onClick = { onDeleteReminder(rem.id) }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = JarvisRedAlert)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
