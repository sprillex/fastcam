# FastCam Authoritative Implementation Plan

This document serves as the authoritative implementation sequence and progress log for the FastCam project.

---

## Task Progress & Status Matrix

| Task ID | Component | Description | Status | Verification |
| :--- | :--- | :--- | :--- | :--- |
| **FC-101** | Shared Utils | Implement `RtspUriUtils` utility for parsing, validating, building, and masking RTSP URIs | **COMPLETED** | Unit test `RtspUriUtilsTest` passed |
| **FC-102** | Unit Tests | Add JUnit 4 testing setup and unit tests for `RtspUriUtils` | **COMPLETED** | Executed `gradle :rtsp-viewer:test` |
| **FC-103** | Security | Mask sensitive passwords in `MainActivity` Logcat logs and `rtsp-config` status views | **COMPLETED** | Code inspection & assembleDebug verification |
| **FC-104** | Security | Sanitize and validate incoming `stream_uri` in `ConfigReceiver` and deep link handler | **COMPLETED** | Tested with invalid URIs & assembleDebug |
| **FC-105** | Player Engine | Optimize LibVLC parameters (`:clock-jitter=0`, `:clock-synchro=0`, `:live-caching=150`) | **COMPLETED** | Clean build & assembleDebug |
| **FC-106** | Reliability | Implement progressive exponential backoff reconnection strategy (500ms to 5000ms) | **COMPLETED** | Verified in `MainActivity` error handling |
| **FC-107** | Reliability | Fix buffering timeout handler to prevent infinite re-buffering loops | **COMPLETED** | Verified in `MainActivity` handler logic |
| **FC-108** | Network | Register `ConnectivityManager.NetworkCallback` for live network change detection & WireGuard tunnel triggers | **COMPLETED** | Verified in `MainActivity` lifecycle |
| **FC-109** | Documentation | Update `ROADMAP.md` and `IMPLEMENTATION_PLAN.md` with audit and roadmap details | **COMPLETED** | Synchronized with project state |

---

## Detailed Task Records

### FC-101 & FC-102: RTSP URI Utilities & Unit Tests
- Created `com.sprillex.rtspviewer.RtspUriUtils` and `com.sprillex.rtspconfig.RtspUriUtils`.
- Implemented `isValidRtspUri()`, `maskCredentials()`, and `buildRtspUri()`.
- Added unit tests in `RtspUriUtilsTest.kt`.

### FC-103 & FC-104: Security Hardening & Input Sanitization
- Updated `ConfigReceiver.kt` to validate `stream_uri` before persisting to `SharedPreferences`.
- Updated `MainActivity.kt` deep link handler to reject invalid URIs.
- Replaced plain text URI logging with `RtspUriUtils.maskCredentials()`.
- Updated `rtsp-config` status text view to display masked URIs.

### FC-105, FC-106, FC-107 & FC-108: Speed, Reliability & Network Adaptation
- Added low-latency LibVLC media options.
- Replaced static 150ms retry interval with exponential backoff (500ms, 1000ms, 2000ms, 4000ms).
- Fixed `bufferingTimeoutRunnable` to cancel buffering timer and transition to error handling on timeout.
- Integrated `ConnectivityManager.NetworkCallback` in `MainActivity` to listen for network state transitions and trigger WireGuard VPN tunnel intents.
