package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.veronica.VeronicaStatus
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGoldBright
import com.example.ui.theme.JarvisGreen
import com.example.ui.theme.JarvisRedAlert
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceHighlight
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary

@Composable
fun VeronicaSystemDialog(
    isOpen: Boolean,
    status: VeronicaStatus,
    onDismiss: () -> Unit,
    onVerifyCode: (String) -> Boolean,
    onDeployCage: () -> Unit,
    onRepairArmor: () -> Unit,
    onHeavyStrike: () -> Unit,
    onDeactivate: () -> Unit
) {
    if (!isOpen) return

    var codeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E0A0A),
                            Color(0xFF140D05),
                            JarvisBackground
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(listOf(JarvisRedAlert, JarvisGold, JarvisRedAlert)),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
                .testTag("veronica_system_dialog")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Iron Man Crimson & Gold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(JarvisRedAlert.copy(alpha = 0.2f))
                                .border(1.dp, JarvisGold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (status.isActive) Icons.Default.Public else Icons.Default.Lock,
                                contentDescription = null,
                                tint = JarvisGoldBright,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "VERONICA PROTOCOL",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisGoldBright,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (status.isActive) "HULKBUSTER ORBITAL DEFENSE ACTIVE" else "AUTHORIZATION CODE REQUIRED",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (status.isActive) JarvisGreen else JarvisRedAlert,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(JarvisSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = JarvisTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!status.isActive) {
                    // LOCKED STATE: Enter Secret Code
                    Text(
                        text = "Enter secret authorization code or speak 'Activate Veronica' to authorize orbital deployment cage and Mark XLIV armor platform.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Secret Code Input Field
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = {
                            codeInput = it
                            errorMessage = null
                        },
                        placeholder = {
                            Text("Secret Code [Hint: 3000 or VERONICA]", color = JarvisTextMuted, fontSize = 12.sp)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("veronica_code_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisGold,
                            unfocusedBorderColor = JarvisRedAlert.copy(alpha = 0.5f),
                            focusedContainerColor = JarvisSurface,
                            unfocusedContainerColor = JarvisSurface,
                            focusedTextColor = JarvisGoldBright,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage!!,
                            color = JarvisRedAlert,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Code Preset Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(JarvisSurfaceHighlight)
                                .border(1.dp, JarvisGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable { codeInput = "3000" }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("CODE: 3000", color = JarvisGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(JarvisSurfaceHighlight)
                                .border(1.dp, JarvisGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable { codeInput = "VERONICA-3000" }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("VERONICA-3000", color = JarvisGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val success = onVerifyCode(codeInput)
                            if (!success) {
                                errorMessage = "INVALID CODE. Enter 3000 or VERONICA-3000"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_verify_veronica"),
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisRedAlert),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = JarvisGoldBright, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AUTHORIZE & DEPLOY VERONICA",
                            color = JarvisGoldBright,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    // ACTIVE STATE: Veronica Control Matrix
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(JarvisSurface.copy(alpha = 0.7f))
                            .border(1.dp, JarvisGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ORBITAL SATELLITE:", color = JarvisTextMuted, fontSize = 10.sp)
                                Text(status.orbitalAltitude, color = JarvisGoldBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("HULKBUSTER INTEGRITY:", color = JarvisTextMuted, fontSize = 10.sp)
                                Text("${status.hulkbusterIntegrity}%", color = JarvisGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { status.hulkbusterIntegrity / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = JarvisGoldBright,
                                trackColor = JarvisSurfaceHighlight
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("CONTAINMENT CAGE:", color = JarvisTextMuted, fontSize = 10.sp)
                                Text(status.containmentCageStatus, color = JarvisCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("REPLACEMENT PODS:", color = JarvisTextMuted, fontSize = 10.sp)
                                Text("${status.replacementPods} AVAILABLE", color = JarvisGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Veronica Tactical Operations
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onDeployCage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("btn_deploy_cage"),
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisGold),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = JarvisBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("DEPLOY CONTAINMENT CAGE", color = JarvisBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onHeavyStrike,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("btn_heavy_strike"),
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisRedAlert),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, tint = JarvisGoldBright, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("KINETIC STRIKE", color = JarvisGoldBright, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }

                            Button(
                                onClick = onRepairArmor,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("btn_repair_armor"),
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceHighlight),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("ARMOR POD (${status.replacementPods})", color = JarvisCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }

                        Button(
                            onClick = onDeactivate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("btn_deactivate_veronica"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            border = androidx.compose.foundation.BorderStroke(1.dp, JarvisRedAlert.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("STAND DOWN VERONICA // RETURN TO ORBIT", color = JarvisRedAlert, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
