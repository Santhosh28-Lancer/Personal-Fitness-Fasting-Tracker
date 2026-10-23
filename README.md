# Grand Line

**Grand Line** is a One Piece-inspired Android fitness and fasting tracker built with Kotlin and Jetpack Compose. It combines daily activity tracking with a themed, reward-based experience.

## Features

- Daily workout calendar and completion tracking
- Fasting session log and fasting progress
- Home-screen fasting clock widget
- Earnable **Berries** for tracked goals and workout completion
- Unlockable character-inspired colour themes
- Import workout schedules from an image using ML Kit text recognition
- Google Calendar integration for calendar event workflows
- Local persistence for app preferences and tracking data

## Tech stack

- Kotlin
- Android SDK / Android Gradle Plugin
- Jetpack Compose and Material 3
- AndroidX Activity, Core KTX, and Lifecycle
- ML Kit Text Recognition
- Gradle Kotlin DSL

## Requirements

Use a recent Android Studio version that supports the Gradle and Android Gradle Plugin versions declared in this repository. The project currently declares `compileSdk 37`, `minSdk 26`, and `targetSdk 37`. The Gradle wrapper is included.

## Build

1. Open the repository root in Android Studio.
2. Allow Gradle sync to finish and install any required Android SDK platform.
3. Run the **app** configuration on an Android device or emulator.

From a terminal at the repository root, you can also run:

```bash
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

The debug APK is normally written to `app/build/outputs/apk/debug/` if the build succeeds.

## Permissions and data

The app requests calendar read/write permissions for its Google Calendar workflow. Review these permissions and test calendar behaviour on a device before distributing the app. Fasting, workout, and theme progress is stored locally by the app; this repository does not imply cloud sync or account backup.

## Status

This repository contains the project source and resources from the uploaded **Grand Line** project. The upload was cleaned of Gradle caches, generated build output, IDE workspace state, and local SDK configuration. The Android app has not been rebuilt or device-tested as part of this repository upload, so validate the build and core flows before treating it as a release.
