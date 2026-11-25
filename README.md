# PROJECT JARVIS X — ANDROID EDITION

OpenJarvis-powered futuristic holographic Android AI assistant featuring an interactive Three.js 3D core, real-time audio-reactive Canvas reactor, CameraX + MediaPipe front-camera hand tracking, local `"Jarvis"` wake-word detection, and full OpenJarvis backend integration.

---

## 1. Architecture Overview

JARVIS X bridges a native **Kotlin + Jetpack Compose** Android application with the **OpenJarvis** ecosystem ([github.com/open-jarvis/OpenJarvis](https://github.com/open-jarvis/OpenJarvis.git)):

- **`OpenJarvisBridge`** (`com.example.domain.openjarvis.OpenJarvisBridge`): Connects over HTTP/SSE (`OkHttp`) to `jarvis serve` FastAPI routes:
  - `GET /health` & `GET /v1/info` — backend health, active engine, version
  - `GET /v1/models` — dynamic model discovery and selection
  - `GET /v1/agents` & `GET /v1/skills` — agent/skill inventory
  - `POST /v1/chat/completions` — OpenAI-compatible SSE token streaming (`stream: true`) with conversation context
  - `POST /v1/memory/store` — optional memory synchronization
- **On-Device Autonomous Fallback (`JarvisBrain` + Room)**: Preserves local intent routing, hardware device tools (`SystemControlTool`, `AppLauncherTool`, `CalculatorTool`, `AntiVirusTool`, `NotesAndRemindersTool`), and encrypted-key/DataStore configuration so core tools work offline.
- **Interactive 3D Holographic Model (`Jarvis3DView` + `assets/jarvis_3d.html` + `assets/three.min.js`)**: Local bundled Three.js WebGL sphere/lattice/gyroscopic core with audio-reactive vertex displacement, 8 emotional color/velocity states, touch drag, and gesture pose interpolation.
- **Animated Reactor Core (`ArcReactorView`)**: Compose Canvas multi-ring rotating energy core, orbital glowing particles, 48-bar circular audio-reactive spectrum, and wake-word shockwave animation.
- **Front-Camera Hand Tracking (`HandTrackingManager` + `HandGestureProcessor`)**: CameraX `ImageAnalysis` + MediaPipe `HandLandmarker` (`assets/hand_landmarker.task`) running locally on-device with EMA smoothing, step clamping, and hand-disappearance recovery:
  - `OPEN PALM`: Enables 3D model rotation tracking
  - `CLOSED FIST`: Pauses rotation
  - `HAND MOVEMENT`: Smoothly rotates the 3D Jarvis model (yaw & pitch)
  - `PINCH`: Triggers configurable action (Toggle Listening, Cycle Expression, Reset 3D Pose, Diagnostics)
- **Local Wake-Word Engine (`LocalWakeWordEngine` + `VoiceEngine`)**:
  - Uses **Picovoice Porcupine** (`BuiltInKeyword.JARVIS`) locally when the operator supplies an AccessKey in Engine Config.
  - Defaults to zero-key **Local Acoustic `AudioRecord` VAD + On-Device `SpeechRecognizer`** keyword detection (`"Jarvis"` / `"Hey Jarvis"`) with configurable sensitivity, duplicate activation cooldown, and compliant Android foreground service mode (`JarvisBackgroundService`).
- **Jarvis Personality (`JarvisEmotionEngine`)**: Models 8 simulated emotional states (`CALMNESS`, `CURIOSITY`, `HAPPINESS`, `EXCITEMENT`, `CONCENTRATION`, `CONFUSION`, `CONCERN`, `SATISFACTION`) synchronized across response phrasing, TTS pitch/rate, 3D model glow/speed, and reactor color.

---

## 2. On-Device vs. Companion Backend Requirements

| Capability | Runs Standalone On-Device? | Notes |
|---|---|---|
| 3D Jarvis Model & Canvas Reactor | Yes | Bundled `three.min.js` + `jarvis_3d.html` in APK assets |
| Hand Tracking (MediaPipe + CameraX) | Yes | Bundled `hand_landmarker.task` in APK assets; processed locally |
| Local Wake-Word (`"Jarvis"`) | Yes | Zero-key VAD+STT built in; optional Picovoice key via Config UI |
| Device Tools (Flashlight, Volume, Apps, Calculator, Notes/Reminders, Anti-Virus) | Yes | Room DB & Android system APIs |
| Full Local/Remote OpenJarvis LLM & Skills (`qwen3`, `llama`, Ollama, etc.) | Requires Companion Backend | Run `jarvis serve` on desktop/server; connect via URL |
| Optional Cloud Gemini Auxiliary Fallback | Requires Internet + Key | Supply `GEMINI_API_KEY` via AI Studio Secrets / `.env` |

---

## 3. Running OpenJarvis Backend (`jarvis serve`)

1. Install and start OpenJarvis on your host machine:
   ```bash
   jarvis serve --host 0.0.0.0 --port 8000
   ```
2. **Android Emulator**: Default URL in JARVIS X is pre-set to `http://10.0.2.2:8000`.
3. **Physical Smartphone via USB**:
   ```bash
   adb reverse tcp:8000 tcp:8000
   ```
   Then set the Server URL in JARVIS X (`OPENJARVIS CORE LINK` pill -> Engine Config) to `http://127.0.0.1:8000` (or use your host LAN IP such as `http://192.168.1.50:8000`, or an HTTPS reverse-proxy URL with Bearer Token).

---

## 4. Building & Installing the APK

```bash
# Compile & verify resources
gradle assembleDebug

# Run pure JVM unit tests
gradle :app:testDebugUnitTest --tests com.example.ExampleUnitTest
```

Output Debug APK location:
`app/build/outputs/apk/debug/app-debug.apk`

Install onto a connected Android smartphone:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. Privacy & Security Notes

- No API keys, OpenJarvis bearer tokens, or Picovoice AccessKeys are hardcoded in source or APK resources.
- Camera frames are analyzed strictly in-memory on-device by MediaPipe and discarded immediately; tracking stops cleanly when disabled or backgrounded.
- Microphone capture for wake-word detection runs locally and respects Android runtime permissions and foreground service notifications.
