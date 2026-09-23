package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.tools.AppLauncherTool
import com.example.domain.tools.CalculatorTool
import com.example.domain.tools.SystemControlTool
import com.example.domain.tools.WeatherTool
import com.example.domain.tools.WebSearchTool
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGreen
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceHighlight
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TacticalToolsTab(
    onExecuteAction: (String) -> Unit,
    onOpenDocScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mathExpr by remember { mutableStateOf("25 * 4.5 + sqrt(144)") }
    var weatherCity by remember { mutableStateOf("San Francisco") }
    var searchQuery by remember { mutableStateOf("quantum telemetry") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MODULAR TACTICAL SUBSYSTEMS",
            style = MaterialTheme.typography.titleMedium,
            color = JarvisCyan,
            fontWeight = FontWeight.ExtraBold
        )

        // 1. Weather Telemetry Card
        ToolHudCard(
            title = "METEOROLOGICAL SCANNER",
            icon = Icons.Default.Cloud,
            category = "TELEMETRY"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = weatherCity,
                    onValueChange = { weatherCity = it },
                    label = { Text("Sector / City", color = JarvisTextSecondary) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisCyan.copy(alpha = 0.3f),
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onExecuteAction("What is the weather in $weatherCity?") },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisBackground)
                ) {
                    Text("SCAN", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Computational Math Card
        ToolHudCard(
            title = "COMPUTATIONAL MATH CORE",
            icon = Icons.Default.Calculate,
            category = "COMPUTATION"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = mathExpr,
                    onValueChange = { mathExpr = it },
                    label = { Text("Mathematical Formula", color = JarvisTextSecondary) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisCyan.copy(alpha = 0.3f),
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onExecuteAction("calculate $mathExpr") },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisBackground)
                ) {
                    Text("SOLVE", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Application & Link Nexus
        ToolHudCard(
            title = "APPLICATION & NEXUS INTENTS",
            icon = Icons.Default.Language,
            category = "SYSTEM INTENTS"
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIntentButton("Optical Camera", Icons.Default.CameraAlt) {
                    onExecuteAction("open camera")
                }
                AppIntentButton("System Settings", Icons.Default.Settings) {
                    onExecuteAction("open settings")
                }
                AppIntentButton("Navigation Maps", Icons.Default.Map) {
                    onExecuteAction("open maps")
                }
                AppIntentButton("Native Calculator", Icons.Default.Calculate) {
                    onExecuteAction("open calculator")
                }
                AppIntentButton("YouTube Media", Icons.Default.PlayArrow) {
                    onExecuteAction("open youtube")
                }
            }
        }

        // 4. Hardware Diagnostics & Flashlight
        ToolHudCard(
            title = "HARDWARE TELEMETRY & CONTROLS",
            icon = Icons.Default.Sensors,
            category = "HARDWARE"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onExecuteAction("battery status and system diagnostics") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceHighlight, contentColor = JarvisCyan)
                ) {
                    Text("SYSTEM REPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onExecuteAction("turn on flashlight") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisBackground)
                ) {
                    Icon(imageVector = Icons.Default.FlashlightOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("TORCH ON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onExecuteAction("turn off flashlight") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceVariant, contentColor = JarvisTextSecondary)
                ) {
                    Icon(imageVector = Icons.Default.FlashlightOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("OFF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. Document & Code Analyzer
        ToolHudCard(
            title = "NEURAL DOCUMENT ANALYZER",
            icon = Icons.Default.Description,
            category = "INTELLIGENCE"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Decompile, analyze word count, and summarize text documents or code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisTextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onOpenDocScanner,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisBackground)
                ) {
                    Text("OPEN SCANNER", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 6. Tactical Web Search
        ToolHudCard(
            title = "TACTICAL WEB SEARCH",
            icon = Icons.Default.Search,
            category = "EXTERNAL NETWORK"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Query network", color = JarvisTextSecondary) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisCyan.copy(alpha = 0.3f),
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onExecuteAction("search for $searchQuery") },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisBackground)
                ) {
                    Text("SEARCH", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ToolHudCard(
    title: String,
    icon: ImageVector,
    category: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JarvisSurface)
            .border(1.dp, JarvisCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = JarvisCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            content()
        }
    }
}

@Composable
fun AppIntentButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(JarvisSurfaceVariant)
            .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = JarvisCyan,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = JarvisTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
