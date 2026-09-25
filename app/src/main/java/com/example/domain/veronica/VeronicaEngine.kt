package com.example.domain.veronica

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VeronicaStatus(
    val isActive: Boolean = false,
    val secretCode: String = "VERONICA-3000",
    val orbitalAltitude: String = "420 KM // LOW EARTH ORBIT",
    val satelliteStatus: String = "STANDBY // GEOSYNCHRONOUS",
    val hulkbusterIntegrity: Int = 100,
    val repulsorOutput: Int = 150,
    val containmentCageStatus: String = "READY FOR DEPLOYMENT",
    val replacementPods: Int = 6,
    val lastAction: String = "System on high standby"
)

class VeronicaEngine(private val context: Context) {

    private val _status = MutableStateFlow(VeronicaStatus())
    val status: StateFlow<VeronicaStatus> = _status.asStateFlow()

    companion object {
        val ACCEPTED_CODES = listOf("VERONICA-3000", "3000", "VERONICA", "MARK-44", "MARK 44", "HULKBUSTER", "PROTOCOL-VERONICA")
    }

    fun verifyAndActivate(inputCode: String): Boolean {
        val clean = inputCode.uppercase().trim().replace(" ", "").replace("-", "")
        val matches = ACCEPTED_CODES.any { it.replace(" ", "").replace("-", "") == clean }
        if (matches || inputCode.contains("3000") || inputCode.uppercase().contains("VERONICA")) {
            _status.value = _status.value.copy(
                isActive = true,
                satelliteStatus = "ORBITAL COMBAT CAGE ARMED // GEOSYNCHRONOUS",
                containmentCageStatus = "ARMED // TARGET LOCK READY",
                hulkbusterIntegrity = 100,
                repulsorOutput = 150,
                lastAction = "Veronica protocol authorized via security code verification."
            )
            return true
        }
        return false
    }

    fun deployContainmentCage(): String {
        if (!_status.value.isActive) return "Veronica system is offline. Authorization code required."
        _status.value = _status.value.copy(
            containmentCageStatus = "DEPLOYED // FORCEFIELD ACTIVE",
            lastAction = "Orbital containment cage deployed to localized perimeter."
        )
        return "Veronica orbital containment cage deployed, sir. Perimeter secured."
    }

    fun dispatchArmorPod(): String {
        if (!_status.value.isActive) return "Veronica system is offline. Authorization code required."
        val currentPods = _status.value.replacementPods
        if (currentPods <= 0) return "All orbital replacement pods have been expended, sir."
        _status.value = _status.value.copy(
            replacementPods = currentPods - 1,
            hulkbusterIntegrity = 100,
            lastAction = "Orbital service pod dispatched. Mark XLIV armor integrity restored to 100%."
        )
        return "Orbital replacement pod dispatched from Veronica satellite. Hulkbuster armor plates replenished to 100%, sir."
    }

    fun heavyCombatStrike(): String {
        if (!_status.value.isActive) return "Veronica system is offline. Authorization code required."
        _status.value = _status.value.copy(
            lastAction = "Heavy repulsor overdrive strike executed. Kinetic output nominal."
        )
        return "Veronica heavy kinetic repulsor strike dispatched, sir. Threat neutralized."
    }

    fun deactivate(): String {
        _status.value = VeronicaStatus(isActive = false, lastAction = "Veronica returned to high orbital stealth standby.")
        return "Veronica system deactivated, sir. Orbital satellite returning to stealth coordinates."
    }
}
