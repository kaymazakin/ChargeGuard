# ChargeGuard

ChargeGuard is a native Android battery safety app that monitors charging temperature, battery status, voltage, current, and system battery health.

## Features

- Real-time battery percentage, temperature, voltage, and charging status
- Adjustable charging temperature alarm
- Foreground service for background monitoring
- Overheat alerts with notification, vibration, and alarm behavior
- Full-charge and night charging protection alerts
- Daily battery report with max temperature, charging duration, and heat events
- Estimated battery health score based on charging habits and system battery state
- Lightweight native Java implementation without Gradle

## Tech Stack

- Java
- Android `BatteryManager` API
- Android foreground service
- Native Android custom views
- Manual APK build script using Android SDK tools

## Build

This project uses the local Android SDK directly.

```powershell
node build-apk.js
```

The generated APK is written to:

```text
ChargeGuard.apk
```

## Notes

Android does not expose exact long-term battery capacity health to normal apps on all devices. ChargeGuard shows the system-reported battery health state when available and calculates an estimated habit-based health score from temperature, long charging sessions, and charging behavior.
