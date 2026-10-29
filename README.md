# FastCam: Ultra Light RTSP Camera Viewer Suite

FastCam is an ultra-lightweight, high-performance Android RTSP camera viewer suite designed for near-instantaneous camera feed startup, low latency, and minimal resource overhead. The project consists of a core headless RTSP video player (`rtsp-viewer`) and a companion configuration management utility (`rtsp-config`).

For full technical specifications, IPC broadcast intents, deep links, permissions, and internal data structures, consult [API.md](API.md).

---

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Prerequisites](#2-prerequisites)
3. [Installation & Setup](#3-installation--setup)
4. [Configuration](#4-configuration)
5. [Usage Guide](#5-usage-guide)
6. [Architecture & Subsystems](#6-architecture--subsystems)
7. [Troubleshooting](#7-troubleshooting)

---

## 1. Project Overview

FastCam addresses the delay and complexity of traditional security camera apps by cutting down startup overhead and UI clutter. It is specifically engineered for embedded Android displays, smart TVs (Android TV / Leanback), dedicated surveillance monitors, and mobile devices.

### Key Capabilities

- **Sub-Second Stream Rendering:** Utilizes native LibVLC hardware acceleration with optimized network caching (`--network-caching=150ms`).
- **Headless & Embedded Operation:** Automatically launches straight into full-screen video with keep-screen-on flags.
- **Two-Module Modular Design:**
  - `rtsp-viewer`: Native playback engine, custom deep-link listener (`fastcam://video`), signature-protected configuration receiver, and isolated crash reporting subsystem.
  - `rtsp-config`: Dedicated configuration GUI equipped with active TCP pre-flight connection checks (port 554) before broadcasting updated stream credentials to the viewer.
- **Automated Network Fallback & VPN Tunneling:** Built-in hooks for WireGuard Android integration to automatically trigger VPN connection establishment (`com.wireguard.android.action.SET_TUNNEL_UP`) when operating outside local Wi-Fi networks.
- **Robust Out-of-Process Crash Recovery:** Catches runtime exceptions and isolates crash UI into a separate process (`:crash_report`), ensuring crash details can be rendered and shared even if the main UI thread crashes.

---

## 2. Prerequisites

### Hardware Requirements
- **Architecture:** ARM64 (`arm64-v8a`), ARMv7 (`armeabi-v7a`), x86, or x86_64.
- **Display:** Minimum 480p screen resolution (supports TV Leanback launchers and standard handheld devices).
- **Network Interface:** Active Wi-Fi or Ethernet connection to the local RTSP camera network, or Cellular with WireGuard VPN configured.

### Supported Operating Systems & Runtimes
- **Target Android Version:** Android 14 (API level 34)
- **Minimum Android Version:** Android 8.0 (API level 26)
- **Development Workstation:** Linux (Debian, DietPi, Linux Mint, Ubuntu), macOS, or Windows WSL2 with JDK 17 and Android SDK Platform 34 installed.

### Build Toolchain & Dependencies
- **Java Development Kit (JDK):** JDK 17 (OpenJDK 17 recommended)
- **Gradle:** 8.x (managed via Gradle Wrapper `./gradlew`)
- **Android SDK:** Build-Tools `34.0.0`, SDK Platform `34`
- **Native Player Engine:** LibVLC Android (`org.videolan.android:libvlc-all:3.6.0-eap13`)

---

## 3. Installation & Setup

### Environment Setup & Dependencies

Ensure `JAVA_HOME` points to JDK 17 and `ANDROID_HOME` points to your installed Android SDK path.

```bash
# Export SDK and Java environment variables (adjust paths as necessary)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin
```

### Compiling and Building APKs

To assemble debug APKs for both the viewer and configuration apps without user intervention, run:

```bash
# Clone repository
git clone https://github.com/fastcam/fastcam.git
cd fastcam

# Make Gradle wrapper executable
chmod +x gradlew

# Build debug APKs for both modules
./gradlew :rtsp-viewer:assembleDebug :rtsp-config:assembleDebug
```

Output APK locations:
- `rtsp-viewer/build/outputs/apk/debug/rtsp-viewer-debug.apk`
- `rtsp-config/build/outputs/apk/debug/rtsp-config-debug.apk`

### Installing via Android Debug Bridge (ADB)

Connect your Android target device via USB or TCP ADB (`adb connect <device-ip>:5555`) and execute:

```bash
# Install both applications with replace/reinstall flags (-r) and grant permissions (-g)
adb install -r -g rtsp-viewer/build/outputs/apk/debug/rtsp-viewer-debug.apk
adb install -r -g rtsp-config/build/outputs/apk/debug/rtsp-config-debug.apk
```

---

## 4. Configuration

FastCam viewer behavior can be configured dynamically through `rtsp-config`, ADB intents, deep links, or directly via application preferences.

### System Configuration Options

| Parameter / Preference | Default Value | Description |
| :--- | :--- | :--- |
| `stream_uri` | `rtsp://admin:password@192.168.1.202:554/video1` | Target RTSP camera URL. |
| Default RTSP Port | `554` | Standard RTSP port used when omitted in configuration input. |
| `--network-caching` | `150` (ms) | LibVLC network caching buffer time. |
| `--rtsp-tcp` | Enabled | Forces RTSP transport over TCP to prevent packet loss artifacts. |
| `tunnelName` | `HomeLab` | WireGuard tunnel profile name used when network transport is cellular. |
| Maximum Retries | `4` attempts | Automated reconnection retry count on connection error. |
| Retry Interval | `150` ms | Delay between reconnection attempts. |
| Buffering Timeout | `6000` ms | Maximum allowed time in buffering state before trigger stream reload. |

### Security Permission Guard

The `rtsp-viewer` application defines a signature-protected permission:
`com.sprillex.rtspviewer.permission.WRITE_CONFIG`.

Both `rtsp-viewer` and `rtsp-config` must be signed with the same signing certificate key in production to enable cross-app configuration broadcasts.

---

## 5. Usage Guide

### Launching FastCam RTSP Viewer

Launch `FastCam RTSP Viewer` using ADB:

```bash
adb shell am start -n com.sprillex.rtspviewer/.MainActivity
```

### Dynamic Stream Configuration via `rtsp-config` UI

1. Open **FastCam RTSP Config** on the target device.
2. Enter the camera **Host/IP**, **Port** (default `554`), **Path** (e.g. `video1` or `h264`), **Username**, and **Password**. Alternatively, paste a full `rtsp://` URI into the Host field.
3. Tap **Save & Sync**:
   - The app performs a background 3-second TCP socket test against `Host:Port`.
   - On success, it sends a secure broadcast update to `rtsp-viewer`.
4. Tap **Force Sync** to skip the TCP socket test and immediately broadcast the update.

### Headless CLI Stream Updates (ADB)

Send a broadcast intent directly to update the active camera feed without opening `rtsp-config`:

```bash
adb shell am broadcast \
  -a com.sprillex.rtspviewer.ACTION_UPDATE_STREAM \
  -p com.sprillex.rtspviewer \
  --es stream_uri "rtsp://admin:password@192.168.1.50:554/live/ch0"
```

### Launching via Custom Scheme Deep Link

Launch `rtsp-viewer` and configure a stream in a single command using the `fastcam://` deep link:

```bash
adb shell am start \
  -a android.intent.action.VIEW \
  -d "fastcam://video?stream=rtsp%3A%2F%2Fadmin%3Apassword%40192.168.1.100%3A554%2Fstream"
```

---

## 6. Architecture & Subsystems

```
+-------------------------------------------------------------------+
|                        FastCam Ecosystem                          |
+------------------------------------+------------------------------+
                                     |
    +--------------------------------+-------------------------------+
    |                                                                |
    v                                                                v
+-------------------------------+                  +--------------------------------+
|          rtsp-config          |                  |          rtsp-viewer           |
|  - Host/Port/Auth Entry UI    |                  |  - MainActivity (VLC Engine)   |
|  - Pre-flight TCP Handshake   |                  |  - ConfigReceiver (Broadcast)  |
|  - Broadcast Sender           |                  |  - SprillexApplication         |
+---------------+---------------+                  +---------------+----------------+
                |                                                  |
                |  com.sprillex.rtspviewer.ACTION_UPDATE_STREAM   |
                +------------------------------------------------->+
                                                                   |
                                                                   v
                                                   +--------------------------------+
                                                   |      SharedPreferences         |
                                                   |     ("viewer_config.xml")      |
                                                   +---------------+----------------+
                                                                   |
                                                                   v
                                                   +--------------------------------+
                                                   |         LibVLC Engine          |
                                                   |   - Hardware Video Decoder     |
                                                   |   - Low-latency TCP Stream     |
                                                   +---------------+----------------+
                                                                   | (On Uncaught Error)
                                                                   v
                                                   +--------------------------------+
                                                   |       :crash_report Process    |
                                                   |   - AppCrashHandler            |
                                                   |   - CrashReportActivity        |
                                                   +--------------------------------+
```

### Component Breakdown

1. **`MainActivity` (`rtsp-viewer`):**
   - Hosts `VLCVideoLayout` and attaches to LibVLC's `MediaPlayer`.
   - Manages window lifecycle (`FLAG_KEEP_SCREEN_ON`) and view attachment states (`attachViews` / `detachViews`).
   - Implements automated exponential/linear re-buffering timeouts (6000ms) and reconnection attempts (up to 4 retries).
   - Monitors network transport types and sends intent broadcasts (`com.wireguard.android.action.SET_TUNNEL_UP`) when non-Wi-Fi networks are detected.

2. **`ConfigReceiver` (`rtsp-viewer`):**
   - Registered manifest `BroadcastReceiver` protected by signature permission.
   - Listens for `com.sprillex.rtspviewer.ACTION_UPDATE_STREAM` and persists stream URIs to `SharedPreferences` (`viewer_config`).

3. **`AppCrashHandler` & `CrashReportActivity` (`rtsp-viewer`):**
   - Installed at startup by `SprillexApplication`.
   - Traps uncaught exceptions on all threads, writes full system/device diagnostic metadata and stack traces to `latest_crash_report.txt`, and launches `CrashReportActivity` running inside isolated process `:crash_report`.

4. **`MainActivity` (`rtsp-config`):**
   - User configuration utility.
   - Constructs standard RTSP URIs (`rtsp://[user:pass@]host:port/path`).
   - Runs asynchronous single-threaded TCP socket pre-flight checks against the target host/port before dispatching config broadcasts.

---

## 7. Troubleshooting

| Symptom / Error Code | Probable Root Cause | Actionable Recovery Steps |
| :--- | :--- | :--- |
| **"TCP handshake failed targeting host:port"** | Camera unreachable, wrong IP/port, or local firewall blocking RTSP. | 1. Verify camera IP & subnet.<br>2. Confirm camera RTSP port (default 554).<br>3. Tap **Force Sync** if testing remotely over VPN. |
| **"Failed to initialize video engine"** | LibVLC JNI libraries missing or CPU architecture mismatch. | 1. Clean build directory: `./gradlew clean`.<br>2. Rebuild debug APK.<br>3. Verify native ProGuard rules preserve `org.videolan.libvlc.**`. |
| **Black screen or static image on playback** | Network packet loss over UDP RTSP or hardware decoder incompatibility. | FastCam defaults to `--rtsp-tcp` and `--drop-late-frames`. Ensure camera stream supports H.264/H.265 over RTSP/TCP. |
| **Stream connection failed after 4 attempts** | Camera stream URI credentials incorrect or stream offline. | 1. Verify RTSP credentials in `rtsp-config`.<br>2. Test stream URI using `ffplay` or `vlc` on PC:<br>`vlc rtsp://admin:pass@IP:554/path`. |
| **Broadcast update ignored by viewer** | Signature permission mismatch between `rtsp-config` and `rtsp-viewer`. | Ensure both APKs are built from the exact same codebase and signed with the same debug/release keystore. |
| **WireGuard tunnel fails to launch on cellular** | WireGuard app not installed or profile name mismatch. | Ensure WireGuard Android app is installed and a profile named `HomeLab` exists. |
