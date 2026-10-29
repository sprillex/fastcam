# FastCam API & Technical Interface Specification

This document details the public interfaces, IPC mechanisms, deep links, permissions, configuration storage formats, and system integration points exposed and utilized by the FastCam Android application suite (`rtsp-viewer` and `rtsp-config`).

---

## Table of Contents
1. [Custom Intent Broadcast APIs](#1-custom-intent-broadcast-apis)
2. [Deep Link Routing Schema](#2-deep-link-routing-schema)
3. [Custom Security Permissions](#3-custom-security-permissions)
4. [Shared Preferences & State Persistence](#4-shared-preferences--state-persistence)
5. [WireGuard VPN Integration Protocols](#5-wireguard-vpn-integration-protocols)
6. [App Crash Handling & Telemetry Specifications](#6-app-crash-handling--telemetry-specifications)

---

## 1. Custom Intent Broadcast APIs

FastCam allows external or companion applications (such as `rtsp-config`) to dynamically update the active RTSP stream URI via Android explicit or implicit broadcast intents.

### Update Stream Broadcast

- **Action Name:** `com.sprillex.rtspviewer.ACTION_UPDATE_STREAM`
- **Target Component:** `com.sprillex.rtspviewer/.ConfigReceiver`
- **Target Package:** `com.sprillex.rtspviewer`
- **Required Broadcast Permission:** `com.sprillex.rtspviewer.permission.WRITE_CONFIG`
- **Protection Level:** `signature`

#### Intent Extras

| Extra Key | Data Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `stream_uri` | `String` | Yes | The fully qualified RTSP URL (e.g., `rtsp://admin:password@192.168.1.50:554/h264`). |

#### ADB CLI Invocation Example

```bash
adb shell am broadcast \
  -a com.sprillex.rtspviewer.ACTION_UPDATE_STREAM \
  -p com.sprillex.rtspviewer \
  --es stream_uri "rtsp://admin:secret@192.168.1.100:554/stream1"
```

---

## 2. Deep Link Routing Schema

FastCam RTSP Viewer handles deep-link URLs matching the custom `fastcam://` URI scheme. Upon receiving a valid deep link, `MainActivity` parses the target RTSP stream, saves it to configuration storage, and instantly initiates playback.

### URL Format

```
fastcam://video?stream=<ENCODED_RTSP_URL>
```

#### URI Components

- **Scheme:** `fastcam`
- **Host:** `video`
- **Query Parameter:**
  - `stream` (`String`): The target RTSP stream URI.

#### ADB CLI Invocation Example

```bash
adb shell am start \
  -a android.intent.action.VIEW \
  -d "fastcam://video?stream=rtsp%3A%2F%2F192.168.1.120%3A554%2Flive"
```

---

## 3. Custom Security Permissions

To prevent unauthorized third-party applications from hijacking or altering camera streams, FastCam enforces a custom signature-level permission.

```xml
<permission
    android:name="com.sprillex.rtspviewer.permission.WRITE_CONFIG"
    android:protectionLevel="signature" />
```

- **Permission Name:** `com.sprillex.rtspviewer.permission.WRITE_CONFIG`
- **Protection Level:** `signature` (Only applications signed with the same signing key certificate can obtain this permission).
- **Usage:** Granted to `rtsp-config` and `rtsp-viewer` to allow secure cross-app broadcast execution.

---

## 4. Shared Preferences & State Persistence

FastCam RTSP Viewer persists stream configuration locally using standard Android `SharedPreferences`.

- **Preference Storage Name:** `viewer_config`
- **Access Mode:** `MODE_PRIVATE` (`0`)

### Preference Keys

| Key Name | Data Type | Default Fallback Value | Description |
| :--- | :--- | :--- | :--- |
| `stream_uri` | `String` | `rtsp://admin:password@192.168.1.202:554/video1` | Current active RTSP video stream source URI. |

---

## 5. WireGuard VPN Integration Protocols

FastCam includes automated fallback network detection. When launched on non-Wi-Fi networks (e.g., Cellular Data), it attempts to bring up a WireGuard VPN tunnel to ensure secure access to local cameras.

### Tunnel Control Intents

- **Bring Tunnel UP Action:** `com.wireguard.android.action.SET_TUNNEL_UP`
- **Bring Tunnel DOWN Action:** `com.wireguard.android.action.SET_TUNNEL_DOWN`

#### Broadcast Extras

| Extra Key | Data Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `name` | `String` | `HomeLab` | The exact name of the WireGuard tunnel configured in the WireGuard Android app. |

---

## 6. App Crash Handling & Telemetry Specifications

`SprillexApplication` registers an uncaught exception handler (`AppCrashHandler`) to capture runtime failures, isolate crash reporting into a distinct process, and store crash logs on disk.

### File Storage Specification

- **File Path:** `<context.filesDir>/latest_crash_report.txt`
- **Format:** Plain text UTF-8

### Crash Report Format

```text
=== APP CRASH REPORT ===
Package: com.sprillex.rtspviewer
Version: 1.0 (1)
Device: Google Pixel 7 (SDK 34, Android 14)
Thread: main

=== STACK TRACE ===
java.lang.RuntimeException: Sample exception trace...
```

### Crash Report Process & Activity

- **Activity:** `com.sprillex.rtspviewer.CrashReportActivity`
- **Process Name:** `:crash_report` (Runs in an isolated process so UI crash rendering succeeds even if the main UI thread fails)
- **Intent Extra:** `extra_crash_report` (`String`)
- **Share Intent Extras:** `EXTRA_PACKAGE_NAME`, `EXTRA_STACK_TRACE`
