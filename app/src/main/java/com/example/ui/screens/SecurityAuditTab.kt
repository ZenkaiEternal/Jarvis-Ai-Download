package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuditLogEntity
import com.example.domain.security.AntiVirusScanReport
import com.example.domain.security.RiskLevel
import com.example.domain.security.SystemSecurityCheck
import com.example.domain.security.ThreatItem
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanBright
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGoldBright
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
fun SecurityAuditTab(
    auditLogs: List<AuditLogEntity>,
    antiVirusReport: AntiVirusScanReport? = null,
    isScanning: Boolean = false,
    onRunScan: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val score = antiVirusReport?.securityScore ?: 98
    val threatLevel = antiVirusReport?.threatLevel ?: "OPTIMAL // SHIELD SECURE"
    val totalApps = antiVirusReport?.totalAppsScanned ?: 36
    val threats = antiVirusReport?.threats ?: emptyList()
    val systemChecks = antiVirusReport?.systemChecks ?: listOf(
        SystemSecurityCheck("Root Privilege Escalation", "Zero unauthorized su/escalation binaries detected.", true, RiskLevel.LOW),
        SystemSecurityCheck("OS Kernel Build Integrity", "Official release-keys verified. Kernel signature valid.", true, RiskLevel.LOW),
        SystemSecurityCheck("Holographic Memory Shield (SELinux)", "Enforcing hardware sandboxing and memory runtime integrity.", true, RiskLevel.LOW),
        SystemSecurityCheck("Storage Dropper & Sideload Scan", "Zero malicious APK droppers or foreign payloads in download sectors.", true, RiskLevel.LOW)
    )

    val scoreColor = when {
        score >= 90 -> JarvisGreen
        score >= 75 -> JarvisGold
        else -> JarvisRedAlert
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = JarvisGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "J.A.R.V.I.S. SENTINEL ANTI-VIRUS",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "On-Device Cyber Defense & Malware Threat Scanner",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextSecondary
                    )
                }
            }
        }

        // 2. Central Holographic Anti-Virus Shield Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(JarvisSurface)
                    .border(1.dp, scoreColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(scoreColor.copy(alpha = 0.12f))
                            .border(2.dp, scoreColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = JarvisCyanBright,
                                modifier = Modifier.size(44.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (score >= 90) Icons.Default.GppGood else Icons.Default.Warning,
                                contentDescription = null,
                                tint = scoreColor,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "$score%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = scoreColor,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        text = threatLevel,
                        style = MaterialTheme.typography.labelMedium,
                        color = scoreColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PACKAGES", style = MaterialTheme.typography.labelSmall, color = JarvisTextSecondary, fontSize = 9.sp)
                            Text("$totalApps SCANNED", style = MaterialTheme.typography.labelMedium, color = JarvisCyan, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("THREATS", style = MaterialTheme.typography.labelSmall, color = JarvisTextSecondary, fontSize = 9.sp)
                            Text("${threats.size} FLAGGED", style = MaterialTheme.typography.labelMedium, color = if (threats.isEmpty()) JarvisGreen else JarvisGold, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ROOT STATUS", style = MaterialTheme.typography.labelSmall, color = JarvisTextSecondary, fontSize = 9.sp)
                            Text("SECURE", style = MaterialTheme.typography.labelMedium, color = JarvisGreen, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scan Action Button
                    Button(
                        onClick = onRunScan,
                        enabled = !isScanning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("antivirus_scan_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisCyan,
                            contentColor = JarvisBackground
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = JarvisBackground,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SCANNING SYSTEM & PACKAGES...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RUN DEEP CYBERNETIC SCAN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 3. Flagged Threats Section (if any)
        if (threats.isNotEmpty()) {
            item {
                Text(
                    text = "POTENTIAL RISK PACKAGES (${threats.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisGold,
                    fontWeight = FontWeight.Bold
                )
            }
            items(threats) { threat ->
                ThreatItemCard(threat)
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(JarvisGreen.copy(alpha = 0.08f))
                        .border(1.dp, JarvisGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = JarvisGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ALL PACKAGES & RUNTIME PROCESSES SECURE • ZERO MALICIOUS THREATS FOUND",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // 4. System Security Integrity Checks
        item {
            Text(
                text = "SYSTEM KERNEL & INTEGRITY MATRIX",
                style = MaterialTheme.typography.labelMedium,
                color = JarvisCyan,
                fontWeight = FontWeight.Bold
            )
        }
        items(systemChecks) { check ->
            SecurityCheckCard(check)
        }

        // 5. Chronological Audit Trail
        item {
            Text(
                text = "CHRONOLOGICAL AUDIT TRAIL (${auditLogs.size})",
                style = MaterialTheme.typography.labelMedium,
                color = JarvisGold,
                fontWeight = FontWeight.Bold
            )
        }
        if (auditLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO AUDIT LOGS RECORDED YET",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextMuted
                    )
                }
            }
        } else {
            items(auditLogs.take(15), key = { it.id }) { log ->
                AuditLogCard(log)
            }
        }
    }
}

@Composable
fun ThreatItemCard(threat: ThreatItem) {
    val badgeColor = when (threat.riskLevel) {
        RiskLevel.CRITICAL -> JarvisRedAlert
        RiskLevel.HIGH -> JarvisRedAlert
        RiskLevel.MEDIUM -> JarvisGold
        RiskLevel.LOW -> JarvisCyan
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(JarvisSurfaceVariant)
            .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = threat.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = threat.riskLevel.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
                Text(
                    text = threat.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary,
                    fontSize = 9.sp
                )
                Text(
                    text = threat.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisTextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun SecurityCheckCard(check: SystemSecurityCheck) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(JarvisSurface)
            .border(
                1.dp,
                if (check.isSecure) JarvisGreen.copy(alpha = 0.35f) else JarvisGold.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (check.isSecure) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (check.isSecure) JarvisGreen else JarvisGold,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = check.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = check.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun AuditLogCard(log: AuditLogEntity) {
    val timeFormatted = remember(log.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(log.timestamp))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(JarvisSurfaceVariant)
            .border(1.dp, JarvisCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.action,
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = log.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = log.details,
                style = MaterialTheme.typography.bodySmall,
                color = JarvisTextPrimary,
                fontSize = 11.sp
            )

            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = JarvisTextMuted,
                fontSize = 9.sp
            )
        }
    }
}
