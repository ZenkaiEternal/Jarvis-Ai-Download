# JARVIS 3D Gesture Control — Windows 11 Desktop Edition

Electron + Three.js + MediaPipe Hands desktop application modeled after [`Jarvis-3D-GestureControl`](https://rmanmetaverse.github.io/Jarvis-3D-GestureControl/).

## Features
- **3D Models**: Procedural Circuit JARVIS Neural Core, Interstellar Endurance (with procedural Web Audio harmonic sound), plus **Load `.GLB` / `.GLTF`** custom 3D model import.
- **Interactive Controls**: Wireframe mode, Exploded view slider, Sound toggle, Center/Reset, and Fullscreen.
- **Webcam Hand Gestures + Mouse/Trackpad**: Open palm / fingertip movement rotates and translates, Thumb+Index spread or two-hand spread zooms, Closed fist locks rotation.

## Build the `.exe` on Windows 11
1. Export this project from AI Studio (or copy the `desktop-win/` folder to your Windows 11 PC).
2. Install **Node.js LTS** (`https://nodejs.org/`).
3. Double-click **`BUILD_WINDOWS_EXE.bat`** (or run `npm install && npm run dist` inside `desktop-win/`).
4. Run the generated executable in `desktop-win/dist/`:
   - `JARVIS-3D-GestureControl-Win11-x64.exe` (single portable `.exe`)
   - `JARVIS-3D-GestureControl-Setup-Win11-x64.exe` (Windows 11 installer)
