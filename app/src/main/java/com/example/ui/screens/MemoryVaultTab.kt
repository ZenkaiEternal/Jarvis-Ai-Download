package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
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
import com.example.data.model.MemoryEntity
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGold
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
fun MemoryVaultTab(
    memories: List<MemoryEntity>,
    onSaveMemory: (String, String, String) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onClearAllMemories: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("PREFERENCE") }
    var showAddForm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = JarvisCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CORE MEMORY VAULT",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisCyan,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = "Encrypted Local Storage // Room SQLite Database",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary
                )
            }

            IconButton(
                onClick = { showAddForm = !showAddForm },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(JarvisSurfaceHighlight)
                    .testTag("toggle_add_memory_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Directive",
                    tint = JarvisCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Security Notice Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(JarvisSurfaceVariant)
                .border(1.dp, JarvisCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = JarvisGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stored directives are dynamically injected into J.A.R.V.I.S.'s cognitive reasoning context.",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // Add Memory Form (Expandable)
        if (showAddForm) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(JarvisSurface)
                    .border(1.dp, JarvisCyan, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "RECORD NEW DIRECTIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisCyan,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("Directive Key (e.g. COFFEE_PREFERENCE)", color = JarvisTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisCyan.copy(alpha = 0.3f),
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        label = { Text("Value / Preference (e.g. Double espresso, no sugar)", color = JarvisTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisCyan.copy(alpha = 0.3f),
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { showAddForm = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisTextSecondary)
                        ) {
                            Text("CANCEL")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newKey.isNotBlank() && newValue.isNotBlank()) {
                                    onSaveMemory(newKey, newValue, newCategory)
                                    newKey = ""
                                    newValue = ""
                                    showAddForm = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisBackground)
                        ) {
                            Text("STORE DIRECTIVE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Memories List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (memories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO DIRECTIVES STORED IN MEMORY BANKS",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                items(memories, key = { it.id }) { mem ->
                    MemoryItemCard(
                        memory = mem,
                        onDelete = { onDeleteMemory(mem.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Purge All Button (Dangerous action requiring security confirmation)
        Button(
            onClick = onClearAllMemories,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("purge_all_memories_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = JarvisRedAlert.copy(alpha = 0.2f),
                contentColor = JarvisRedAlert
            ),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, JarvisRedAlert)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = JarvisRedAlert,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("PURGE MEMORY CORE (CONFIRMATION REQUIRED)", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MemoryItemCard(
    memory: MemoryEntity,
    onDelete: () -> Unit
) {
    val dateStr = remember(memory.updatedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(memory.updatedAt))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(JarvisSurface)
            .border(1.dp, JarvisCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag("memory_item_${memory.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = memory.key,
                        style = MaterialTheme.typography.titleSmall,
                        color = JarvisCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(JarvisSurfaceHighlight)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = memory.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = memory.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextPrimary
                )

                Text(
                    text = "COMMITTED: $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextMuted,
                    fontSize = 9.sp
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_memory_${memory.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Memory",
                    tint = JarvisRedAlert.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
