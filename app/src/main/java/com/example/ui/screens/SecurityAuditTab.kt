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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuditLogEntity
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGreen
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(16.dp)
    ) {
        // Header
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
                    text = "SECURITY PROTOCOLS & AUDIT MATRIX",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Zero-Trust Architecture // Sandbox Verification",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Security Status Indicators
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecurityProtocolItem(
                title = "Credential Vault (Secrets Gradle Plugin)",
                subtitle = "API credentials secured through BuildConfig & .env, never exposed in client source.",
                isSecure = true
            )
            SecurityProtocolItem(
                title = "Consequential Action Gate",
                subtitle = "High-risk directives (memory purges, hardware triggers) require interactive HUD confirmation.",
                isSecure = true
            )
            SecurityProtocolItem(
                title = "Input Payload Sanitization",
                subtitle = "Command injection detection and size boundary validation enabled on all inputs.",
                isSecure = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "CHRONOLOGICAL AUDIT TRAIL (${auditLogs.size})",
            style = MaterialTheme.typography.labelMedium,
            color = JarvisGold,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Audit Logs List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (auditLogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 30.dp),
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
                items(auditLogs, key = { it.id }) { log ->
                    AuditLogCard(log)
                }
            }
        }
    }
}

@Composable
fun SecurityProtocolItem(
    title: String,
    subtitle: String,
    isSecure: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(JarvisSurface)
            .border(1.dp, JarvisGreen.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isSecure) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                tint = if (isSecure) JarvisGreen else JarvisGold,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
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
