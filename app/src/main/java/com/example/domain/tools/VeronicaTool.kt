package com.example.domain.tools

import android.content.Context
import com.example.domain.veronica.VeronicaEngine

class VeronicaTool(private val veronicaEngine: VeronicaEngine) : JarvisTool {
    override val id = "veronica_protocol"
    override val name = "Veronica Orbital Hulkbuster Protocol"
    override val description = "Authorizes and operates the Veronica orbital satellite deployment platform, localized containment cage, and Hulkbuster combat systems via secret security code."
    override val category = "COMBAT_DEFENSE"
    override val parameters = listOf(
        ToolParameter("action", "Actions: 'activate', 'deploy_cage', 'repair_armor', 'heavy_strike', 'status', 'deactivate'"),
        ToolParameter("code", "Secret authorization passcode (e.g. 3000, VERONICA-3000)", isRequired = false)
    )
    override val isConsequential = false

    override suspend fun execute(context: Context, params: Map<String, String>): ToolResult {
        val action = params["action"]?.lowercase()?.trim() ?: "status"
        val code = params["code"] ?: ""

        when (action) {
            "activate" -> {
                val success = veronicaEngine.verifyAndActivate(if (code.isNotBlank()) code else "3000")
                if (success) {
                    val summary = "Veronica protocol override code verified. Orbital deployment satellite in geosynchronous orbit. Hulkbuster armor Mark Forty-Four deployed and standing by, sir."
                    val display = """
                        ========================================
                        V E R O N I C A   S Y S T E M   O N L I N E
                        ========================================
                        ORBITAL SATELLITE: GEOSYNCHRONOUS // 420 KM
                        HULKBUSTER ARMOR: MARK XLIV (100%)
                        REPULSOR OUTPUT: 150% OVERDRIVE
                        CONTAINMENT CAGE: ARMED // TARGET LOCK READY
                        SERVICE PODS: 6 AVAILABLE IN ORBIT
                        AUTHORIZATION: STARK PROTOCOL VERIFIED
                    """.trimIndent()
                    return ToolResult(true, summary, display)
                } else {
                    return ToolResult(false, "Access Denied: Invalid Veronica authorization code. Please provide the secret override passcode, sir.")
                }
            }
            "deploy_cage" -> {
                val msg = veronicaEngine.deployContainmentCage()
                return ToolResult(true, msg, "CONTAINMENT CAGE: DEPLOYED\nLOCAL FORCEFIELD: MAXIMUM ENERGETIC DENSITY")
            }
            "repair_armor" -> {
                val msg = veronicaEngine.dispatchArmorPod()
                return ToolResult(true, msg, "SERVICE POD: IMPACT CONFIRMED\nARMOR PLATING: REPLENISHED (100%)")
            }
            "heavy_strike" -> {
                val msg = veronicaEngine.heavyCombatStrike()
                return ToolResult(true, msg, "KINETIC STRIKE: DISPATCHED\nREPULSOR MATRIX: MAXIMUM SURGE")
            }
            "deactivate" -> {
                val msg = veronicaEngine.deactivate()
                return ToolResult(true, msg, "VERONICA: STANDBY\nORBITAL PLATFORM: STEALTH RE-ENTRY CO-ORDINATES")
            }
            else -> {
                val st = veronicaEngine.status.value
                val summary = if (st.isActive) {
                    "Veronica orbital platform active at ${st.orbitalAltitude}. Hulkbuster integrity at ${st.hulkbusterIntegrity}%."
                } else {
                    "Veronica system is currently in stealth orbit standby. Authorization code required to deploy, sir."
                }
                val display = """
                    SYSTEM: VERONICA ORBITAL PLATFORM
                    STATUS: ${if (st.isActive) "ONLINE // ACTIVE" else "STANDBY // SECURE"}
                    ORBIT: ${st.orbitalAltitude}
                    CAGE STATUS: ${st.containmentCageStatus}
                    HULKBUSTER: ${st.hulkbusterIntegrity}% INTEGRITY
                    PODS REMAINING: ${st.replacementPods}
                """.trimIndent()
                return ToolResult(true, summary, display)
            }
        }
    }
}
