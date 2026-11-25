# JARVIS 3D Gesture Control — Windows 11 Edition

Standalone Windows 11 desktop application modeled after [`Jarvis-3D-GestureControl`](https://rmanmetaverse.github.io/Jarvis-3D-GestureControl/).

## Option 1: Run Instantly on Stock Windows 11 (Zero Install)
1. Export/unzip this project onto your Windows 11 laptop.
2. Double-click **`desktop-win\RUN_ON_WINDOWS.bat`**.
3. Windows 11 starts a local `127.0.0.1` loopback host via built-in PowerShell and opens **JARVIS 3D Gesture Control** in a frameless Microsoft Edge App window with webcam hand-tracking support.

## Option 2: Build a Standalone `.EXE` on Windows 11
1. Install **Node.js LTS** (`https://nodejs.org/`).
2. Double-click **`desktop-win\BUILD_WINDOWS_EXE.bat`** (runs `npm install && npm run dist`).
3. Run the compiled executables in `desktop-win\dist\`:
   - `JARVIS-3D-GestureControl-Win11-x64.exe` (single portable `.exe`)
   - `JARVIS-3D-GestureControl-Setup-Win11-x64.exe` (Windows 11 installer)

## Features
- **4 Built-In 3D Holograms**: `JARVIS Core` (Procedural Circuit), `Arc Reactor` (Mark VII), `Endurance` (Interstellar + procedural Web Audio sound), and `Cyber Globe`.
- **Custom 3D Import**: Click `Load .GLB` or drag-and-drop any `.glb` / `.gltf` file directly onto the window.
- **Gestures & Controls**: Webcam MediaPipe Hands (move, rotate, zoom, fist lock), mouse/trackpad drag & scroll, wireframe, explode slider, color tint switcher, PNG snapshot, and fullscreen.
