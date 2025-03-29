# Android WebView Dashboard Wrapper

This project is a lightweight Kotlin Android app that acts as a wrapper for a web dashboard. It includes integration with Sunmi’s inbuilt printer service, allowing you to print receipts directly from the device. The app also supports automatic login injection and secure credential management via Gradle configuration.

## Features

- **WebView Dashboard**  
  Loads a configurable dashboard URL in a full-screen WebView and automatically refreshes when the app resumes.

- **Auto Login Injection**  
  When a login page is detected, the app injects JavaScript to autofill login credentials and submits the form.

- **Sunmi Printer Integration**  
  Connects to Sunmi’s inbuilt printer service using a remote dependency library. It prints text receipts with configurable formatting (e.g., left alignment, line wrapping).

- **Secure Credential Management**  
  Credentials are injected via build configuration (from `local.properties` into `BuildConfig`), keeping sensitive data out of your source code.

## Prerequisites

- [Android Studio](https://developer.android.com/studio) with Kotlin support.
- An Android device or emulator with WebView support.
- A device with Sunmi inbuilt printer service (optional for printing functionality).
- Internet connectivity for accessing your dashboard and remote resources.

## Setup

1. **Clone the Repository**

```bash
git clone https://github.com/brunomoyse/tsb-android-wrapper.git
cd yourrepo
```

2. **Configure Local Properties**

Create a local.properties file in the root of the project (this file is ignored by Git) with your credentials and signing information:

```
# For auto connection to the tsb-dashboard
userEmail=your-email@example.com
userPassword=yourSecurePassword
# For app signing
storeFile=keystore/my-release-key.jks
storePassword=YourKeystorePassword
keyAlias=my-release-key
keyPassword=YourKeyPassword
```

3. **Sync and Build**
Open the project in Android Studio, then sync with Gradle. Build the project with:

```bash
./gradlew clean assembleDebug
```

## Running the App

1. **Install on a Device or Emulator**
- Connect your device via USB and enable Developer Options with USB Debugging.
- In Android Studio, click the Run button or use:
```bash
./gradlew installDebug
```

2. Debugging the WebView
To inspect the WebView content, enable debugging by calling:
```kotlin
WebView.setWebContentsDebuggingEnabled(true)
```

Then open Chrome on your computer and navigate to chrome://inspect/#devices to inspect the WebView.

## Printer Functionality

The app binds to Sunmi’s inbuilt printer service and prints receipts. When a print command is triggered via JavaScript from the web dashboard, the app:
- Initializes the printer.
- Sets text alignment and formatting.
- Sends the receipt text to be printed and advances the paper.
- Logs the results of the printing process.

To trigger printing from your web dashboard, call the exposed JavaScript interface:

```js
window.PrintHandler.print("Your receipt content here");
```