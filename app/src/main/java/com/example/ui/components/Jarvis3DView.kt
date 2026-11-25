package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.JarvisVoiceState
import com.example.domain.ai.JarvisEmotion
import java.util.Locale

enum class HoloModelMode(val label: String) {
    JARVIS_CORE("JARVIS CORE"),
    ENDURANCE("ENDURANCE")
}

private class Jarvis3DBridge(
    private val mainHandler: Handler,
    private val onTapped: () -> Unit,
    private val onPoseResetCallback: () -> Unit
) {
    @JavascriptInterface
    fun onReactorTapped() {
        mainHandler.post { onTapped() }
    }

    @JavascriptInterface
    fun onPoseReset() {
        mainHandler.post { onPoseResetCallback() }
    }
}

/**
 * Interactive Three.js 3D Holo-Workshop supporting:
 * - Procedural Circuit JARVIS Neural Matrix Sphere
 * - Interstellar Endurance 12-Module Centrifuge + Ranger + Web Audio sound
 * - Fingertip translation, gesture zoom (0.55x..2.15x), multi-touch drag/pinch, wireframe, & exploded view
 */
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
fun Jarvis3DView(
    voiceState: JarvisVoiceState,
    amplitude: Float,
    emotion: JarvisEmotion,
    targetYawDeg: Float,
    targetPitchDeg: Float,
    targetOffsetX: Float = 0f,
    targetOffsetY: Float = 0f,
    targetZoom: Float = 1.0f,
    modelMode: HoloModelMode = HoloModelMode.JARVIS_CORE,
    wireframeEnabled: Boolean = false,
    explodedFactor: Float = 0f,
    soundEnabled: Boolean = true,
    wakeBurstNonce: Int,
    resetTouchNonce: Int = 0,
    onReactorTapped: () -> Unit = {},
    onPoseReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageReady by remember { mutableStateOf(false) }

    val latestOnTap by rememberUpdatedState(onReactorTapped)
    val latestOnReset by rememberUpdatedState(onPoseReset)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val bridge = remember {
        Jarvis3DBridge(
            mainHandler = mainHandler,
            onTapped = { latestOnTap() },
            onPoseResetCallback = { latestOnReset() }
        )
    }

    AndroidView(
        modifier = modifier.testTag("jarvis_3d_model"),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER

                settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = true
                    domStorageEnabled = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                }

                addJavascriptInterface(bridge, "AndroidBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        isPageReady = true
                    }
                }

                loadUrl("file:///android_asset/jarvis_3d.html")
                webViewRef = this
            }
        }
    )

    LaunchedEffect(isPageReady, targetYawDeg, targetPitchDeg, targetOffsetX, targetOffsetY, targetZoom) {
        if (isPageReady) {
            val js = String.format(
                Locale.US,
                "window.jarvis3d && window.jarvis3d.setPose(%.2f, %.2f, %.3f, %.3f, %.3f);",
                targetYawDeg,
                targetPitchDeg,
                targetOffsetX,
                targetOffsetY,
                targetZoom.coerceIn(0.55f, 2.15f)
            )
            webViewRef?.evaluateJavascript(js, null)
        }
    }

    LaunchedEffect(isPageReady, modelMode, wireframeEnabled, explodedFactor, soundEnabled) {
        if (isPageReady) {
            val js = String.format(
                Locale.US,
                "window.jarvis3d && window.jarvis3d.setWorkshopOptions('%s', %b, %.2f, %b);",
                modelMode.name,
                wireframeEnabled,
                explodedFactor.coerceIn(0f, 1f),
                soundEnabled
            )
            webViewRef?.evaluateJavascript(js, null)
        }
    }

    LaunchedEffect(isPageReady, resetTouchNonce) {
        if (isPageReady && resetTouchNonce > 0) {
            webViewRef?.evaluateJavascript(
                "window.jarvis3d && window.jarvis3d.resetTouchOffsets();",
                null
            )
        }
    }

    LaunchedEffect(isPageReady, voiceState, amplitude, emotion) {
        if (isPageReady) {
            val js = String.format(
                Locale.US,
                "window.jarvis3d && window.jarvis3d.setState('%s', %.3f, '%s', '%s', %.2f, %.2f);",
                voiceState.name,
                amplitude.coerceIn(0.05f, 1.0f),
                emotion.primaryColorHex,
                emotion.accentColorHex,
                emotion.reactorSpeedMultiplier,
                emotion.modelGlowIntensity
            )
            webViewRef?.evaluateJavascript(js, null)
        }
    }

    LaunchedEffect(isPageReady, wakeBurstNonce) {
        if (isPageReady && wakeBurstNonce > 0) {
            webViewRef?.evaluateJavascript(
                "window.jarvis3d && window.jarvis3d.triggerWakeBurst();",
                null
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                removeJavascriptInterface("AndroidBridge")
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }
}
