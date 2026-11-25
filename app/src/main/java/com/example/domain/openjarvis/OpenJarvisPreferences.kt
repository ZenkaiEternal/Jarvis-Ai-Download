package com.example.domain.openjarvis

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.openJarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "openjarvis_config")

enum class OpenJarvisInferenceMode(val label: String) {
    AUTO_LOCAL_FIRST("Auto (OpenJarvis + On-Device)"),
    OPENJARVIS_SERVER_ONLY("OpenJarvis Server"),
    ON_DEVICE_ONLY("On-Device Autonomous")
}

enum class PinchAction(val label: String) {
    TOGGLE_LISTENING("Toggle Listening"),
    CYCLE_EMOTION("Cycle Expression"),
    RESET_MODEL_POSE("Reset 3D Pose"),
    RUN_DIAGNOSTICS("Run Diagnostics")
}

data class OpenJarvisConfig(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val selectedModel: String = "auto",
    val apiToken: String = "",
    val inferenceMode: OpenJarvisInferenceMode = OpenJarvisInferenceMode.AUTO_LOCAL_FIRST,
    val wakeSensitivity: Float = 0.62f,
    val speakingRate: Float = 1.04f,
    val pinchAction: PinchAction = PinchAction.TOGGLE_LISTENING,
    val picovoiceAccessKey: String = "",
    val wakeWordEnabled: Boolean = true,
    val handTrackingAutoStart: Boolean = false
) {
    companion object {
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8000"
    }
}

class OpenJarvisPreferences(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("oj_server_url")
        val SELECTED_MODEL = stringPreferencesKey("oj_selected_model")
        val API_TOKEN = stringPreferencesKey("oj_api_token")
        val INFERENCE_MODE = stringPreferencesKey("oj_inference_mode")
        val WAKE_SENSITIVITY = floatPreferencesKey("oj_wake_sensitivity")
        val SPEAKING_RATE = floatPreferencesKey("oj_speaking_rate")
        val PINCH_ACTION = stringPreferencesKey("oj_pinch_action")
        val PICOVOICE_ACCESS_KEY = stringPreferencesKey("oj_picovoice_key")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("oj_wake_word_enabled")
        val HAND_TRACKING_AUTO_START = booleanPreferencesKey("oj_hand_tracking_auto")
    }

    val configFlow: Flow<OpenJarvisConfig> = context.openJarvisDataStore.data.map { prefs ->
        val mode = prefs[Keys.INFERENCE_MODE]?.let { name ->
            OpenJarvisInferenceMode.entries.firstOrNull { it.name == name }
        } ?: OpenJarvisInferenceMode.AUTO_LOCAL_FIRST

        val pinch = prefs[Keys.PINCH_ACTION]?.let { name ->
            PinchAction.entries.firstOrNull { it.name == name }
        } ?: PinchAction.TOGGLE_LISTENING

        OpenJarvisConfig(
            serverUrl = prefs[Keys.SERVER_URL]?.takeIf { it.isNotBlank() } ?: OpenJarvisConfig.DEFAULT_SERVER_URL,
            selectedModel = prefs[Keys.SELECTED_MODEL]?.takeIf { it.isNotBlank() } ?: "auto",
            apiToken = prefs[Keys.API_TOKEN] ?: "",
            inferenceMode = mode,
            wakeSensitivity = (prefs[Keys.WAKE_SENSITIVITY] ?: 0.62f).coerceIn(0.2f, 0.95f),
            speakingRate = (prefs[Keys.SPEAKING_RATE] ?: 1.04f).coerceIn(0.75f, 1.45f),
            pinchAction = pinch,
            picovoiceAccessKey = prefs[Keys.PICOVOICE_ACCESS_KEY] ?: "",
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: true,
            handTrackingAutoStart = prefs[Keys.HAND_TRACKING_AUTO_START] ?: false
        )
    }

    suspend fun updateConfig(transform: (OpenJarvisConfig) -> OpenJarvisConfig) {
        context.openJarvisDataStore.edit { prefs ->
            val currentMode = prefs[Keys.INFERENCE_MODE]?.let { n ->
                OpenJarvisInferenceMode.entries.firstOrNull { it.name == n }
            } ?: OpenJarvisInferenceMode.AUTO_LOCAL_FIRST
            val currentPinch = prefs[Keys.PINCH_ACTION]?.let { n ->
                PinchAction.entries.firstOrNull { it.name == n }
            } ?: PinchAction.TOGGLE_LISTENING

            val current = OpenJarvisConfig(
                serverUrl = prefs[Keys.SERVER_URL] ?: OpenJarvisConfig.DEFAULT_SERVER_URL,
                selectedModel = prefs[Keys.SELECTED_MODEL] ?: "auto",
                apiToken = prefs[Keys.API_TOKEN] ?: "",
                inferenceMode = currentMode,
                wakeSensitivity = prefs[Keys.WAKE_SENSITIVITY] ?: 0.62f,
                speakingRate = prefs[Keys.SPEAKING_RATE] ?: 1.04f,
                pinchAction = currentPinch,
                picovoiceAccessKey = prefs[Keys.PICOVOICE_ACCESS_KEY] ?: "",
                wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: true,
                handTrackingAutoStart = prefs[Keys.HAND_TRACKING_AUTO_START] ?: false
            )
            val next = transform(current)
            prefs[Keys.SERVER_URL] = next.serverUrl.trim().trimEnd('/')
            prefs[Keys.SELECTED_MODEL] = next.selectedModel.trim().ifBlank { "auto" }
            prefs[Keys.API_TOKEN] = next.apiToken.trim()
            prefs[Keys.INFERENCE_MODE] = next.inferenceMode.name
            prefs[Keys.WAKE_SENSITIVITY] = next.wakeSensitivity.coerceIn(0.2f, 0.95f)
            prefs[Keys.SPEAKING_RATE] = next.speakingRate.coerceIn(0.75f, 1.45f)
            prefs[Keys.PINCH_ACTION] = next.pinchAction.name
            prefs[Keys.PICOVOICE_ACCESS_KEY] = next.picovoiceAccessKey.trim()
            prefs[Keys.WAKE_WORD_ENABLED] = next.wakeWordEnabled
            prefs[Keys.HAND_TRACKING_AUTO_START] = next.handTrackingAutoStart
        }
    }
}
