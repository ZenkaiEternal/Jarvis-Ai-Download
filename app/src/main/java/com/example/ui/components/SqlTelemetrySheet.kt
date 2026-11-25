package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FaceProfileEntity
import com.example.data.model.FaceRecognitionEventEntity
import com.example.data.model.FingertipMovementEntity
import com.example.data.model.LogReportEntity
import com.example.data.model.SqlTelemetrySummary
import com.example.domain.vision.FaceRecognitionOutput
import com.example.domain.vision.FaceSignatureMatcher
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanBright
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGreen
import com.example.ui.theme.JarvisHoloWhite
import com.example.ui.theme.JarvisRedAlert
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SqlArchiveTab(val label: String) {
    REPORTS("Log Reports"),
    FINGERTIPS("Fingertips"),
    FACIAL("Facial ID"),
    SCHEMA("SQL Schema")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SqlTelemetrySheet(
    isOpen: Boolean,
    summary: SqlTelemetrySummary,
    logReports: List<LogReportEntity>,
    fingertipMovements: List<FingertipMovementEntity>,
    faceEvents: List<FaceRecognitionEventEntity>,
    faceProfiles: List<FaceProfileEntity>,
    currentFaceState: FaceRecognitionOutput,
    syncStatusMessage: String?,
    onDismiss: () -> Unit,
    onGenerateReport: () -> Unit,
    onSyncToServer: () -> Unit,
    onEnrollFace: (String) -> Unit,
    onDeleteFaceProfile: (Long) -> Unit,
    onClearTelemetry: () -> Unit,
    onBuildExportReport: suspend () -> String
) {
    if (!isOpen) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(SqlArchiveTab.REPORTS) }
    var reportCategoryFilter by remember { mutableStateOf("ALL") }
    var enrollNameInput by remember { mutableStateOf("Operator") }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JarvisSurface,
        contentColor = JarvisTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "SQL Database",
                        tint = JarvisCyanBright,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "SQL TELEMETRY ARCHIVE",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisCyanBright,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "On-Device Room SQLite • Cloud SQL Sync Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close SQL Telemetry",
                        tint = JarvisTextSecondary
                    )
                }
            }

            // Summary Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SqlMetricCard(
                    label = "FINGERTIPS",
                    value = summary.fingertipCount.toString(),
                    accent = JarvisCyanBright,
                    modifier = Modifier.weight(1f)
                )
                SqlMetricCard(
                    label = "FACE IDS",
                    value = "${summary.faceEventCount}/${summary.enrolledFaceCount}",
                    accent = JarvisGreen,
                    modifier = Modifier.weight(1f)
                )
                SqlMetricCard(
                    label = "REPORTS",
                    value = summary.logReportCount.toString(),
                    accent = JarvisElectricBlue,
                    modifier = Modifier.weight(1f)
                )
                SqlMetricCard(
                    label = "UNSYNCED",
                    value = summary.unsyncedCount.toString(),
                    accent = if (summary.unsyncedCount > 0) JarvisGold else JarvisGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGenerateReport,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("generate_sql_report_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyan,
                        contentColor = JarvisBackground
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("LOG SNAPSHOT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSyncToServer,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("sync_sql_server_button"),
                    border = BorderStroke(1.dp, JarvisElectricBlue.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Sync SQL",
                        tint = JarvisCyanBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("CLOUD SYNC", style = MaterialTheme.typography.labelSmall, color = JarvisCyanBright)
                }

                IconButton(
                    onClick = {
                        scope.launch {
                            val exportText = onBuildExportReport()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "JARVIS X SQL Telemetry Report")
                                putExtra(Intent.EXTRA_TEXT, exportText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export SQL Report"))
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(JarvisSurfaceVariant)
                        .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
                        .testTag("export_sql_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export SQL report",
                        tint = JarvisCyan
                    )
                }

                IconButton(
                    onClick = onClearTelemetry,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(JarvisSurfaceVariant)
                        .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
                        .testTag("clear_sql_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear SQL logs",
                        tint = JarvisRedAlert
                    )
                }
            }

            if (!syncStatusMessage.isNullOrBlank()) {
                Text(
                    text = syncStatusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisGold,
                    maxLines = 2
                )
            }

            // Tab Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SqlArchiveTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                            selectedLabelColor = JarvisCyanBright
                        )
                    )
                }
            }

            // Content Area
            when (selectedTab) {
                SqlArchiveTab.REPORTS -> {
                    val categories = listOf("ALL", "FINGERTIP", "FACE_RECOGNITION", "VOICE_CMD", "OPENJARVIS_AI", "SYSTEM")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = reportCategoryFilter == cat,
                                onClick = { reportCategoryFilter = cat },
                                label = { Text(cat.replace("_", " "), style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JarvisElectricBlue.copy(alpha = 0.24f),
                                    selectedLabelColor = JarvisHoloWhite
                                )
                            )
                        }
                    }

                    val filtered = if (reportCategoryFilter == "ALL") {
                        logReports
                    } else {
                        logReports.filter { it.category == reportCategoryFilter }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (filtered.isEmpty()) {
                            item {
                                EmptySqlHint("No log reports captured yet for this filter.")
                            }
                        } else {
                            items(filtered, key = { it.id }) { report ->
                                val sevColor = when (report.severity) {
                                    "ALERT" -> JarvisRedAlert
                                    "WARN" -> JarvisGold
                                    else -> JarvisCyanBright
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(JarvisSurfaceVariant.copy(alpha = 0.72f))
                                        .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${report.category} • ${report.title}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = sevColor,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = timeFormatter.format(Date(report.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = JarvisTextSecondary
                                        )
                                    }
                                    Text(
                                        text = report.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisHoloWhite
                                    )
                                }
                            }
                        }
                    }
                }

                SqlArchiveTab.FINGERTIPS -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (fingertipMovements.isEmpty()) {
                            item {
                                EmptySqlHint("Enable hand tracking and move your fingertips to record SQL coordinates.")
                            }
                        } else {
                            items(fingertipMovements, key = { it.id }) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(JarvisSurfaceVariant.copy(alpha = 0.72f))
                                        .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${row.gesture} (${row.handCount}H) • Zoom ${String.format(Locale.US, "%.2fx", row.zoomScale)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = JarvisCyanBright,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "Tip=(%.2f, %.2f) Offset=(%.2f, %.2f) Tilt=(%.1f°, %.1f°)",
                                                row.fingertipX, row.fingertipY, row.offsetX, row.offsetY, row.yawDeg, row.pitchDeg
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = JarvisTextPrimary
                                        )
                                    }
                                    Text(
                                        text = timeFormatter.format(Date(row.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                SqlArchiveTab.FACIAL -> {
                    // Live Face Enrollment Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(JarvisSurfaceVariant)
                            .border(1.dp, JarvisCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Face enrollment",
                                tint = JarvisGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (currentFaceState.faceDetected && currentFaceState.descriptor.isNotEmpty()) {
                                    "Live Face Ready (${currentFaceState.recognizedLabel})"
                                } else {
                                    "Enable camera tracking & look at camera to enroll face"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = JarvisHoloWhite
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = enrollNameInput,
                                onValueChange = { enrollNameInput = it },
                                label = { Text("Profile Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("enroll_face_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JarvisCyan,
                                    unfocusedBorderColor = JarvisBorder,
                                    focusedTextColor = JarvisHoloWhite,
                                    unfocusedTextColor = JarvisTextPrimary
                                )
                            )
                            Button(
                                onClick = { onEnrollFace(enrollNameInput) },
                                enabled = currentFaceState.faceDetected && currentFaceState.descriptor.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = JarvisGreen,
                                    contentColor = JarvisBackground
                                ),
                                modifier = Modifier.testTag("enroll_face_button")
                            ) {
                                Text("ENROLL", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (faceProfiles.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                faceProfiles.forEach { profile ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(JarvisBackground)
                                            .border(1.dp, JarvisGreen.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                            .clickable { onDeleteFaceProfile(profile.id) }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${profile.personName} ×",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = JarvisGreen
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (faceEvents.isEmpty()) {
                            item {
                                EmptySqlHint("No facial recognition events recorded yet.")
                            }
                        } else {
                            items(faceEvents, key = { it.id }) { ev ->
                                val statusColor = when (ev.status) {
                                    "VERIFIED" -> JarvisGreen
                                    "UNRECOGNIZED" -> JarvisRedAlert
                                    else -> JarvisCyanBright
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(JarvisSurfaceVariant.copy(alpha = 0.72f))
                                        .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${ev.recognizedLabel} • ${ev.status} (${String.format(Locale.US, "%.0f%%", ev.confidence * 100f)})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = timeFormatter.format(Date(ev.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = JarvisTextSecondary
                                        )
                                    }
                                    val smileTxt = ev.smilingProbability?.let { String.format(Locale.US, "Smile %.0f%%", it * 100f) } ?: "Smile n/a"
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "%s | Head=(%.1f°, %.1f°, %.1f°)",
                                            smileTxt, ev.headYawDeg, ev.headPitchDeg, ev.headRollDeg
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                SqlArchiveTab.SCHEMA -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "Local Room SQLite (`jarvis_core.db`) + Companion Cloud SQL PostgreSQL DDL (synced via OpenJarvis `POST /v1/telemetry/sql`):",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextSecondary
                            )
                        }
                        item {
                            Text(
                                text = FaceSignatureMatcher.POSTGRES_DDL_SCHEMA,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = JarvisCyanBright,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(JarvisBackground)
                                    .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SqlMetricCard(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(JarvisSurfaceVariant)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptySqlHint(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(JarvisSurfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = JarvisTextSecondary
        )
    }
}
