# IBB WiFi Captive Portal Auto Login

An Android app that automatically logs into the IBB WiFi captive portal when connected to the network.

## Features

- **Automatic Login**: Detects when connected to IBB WiFi and automatically logs in
- **Credential Storage**: Securely stores your login credentials
- **Format Validation**: Ensures phone number and MAC address are in the correct format
- **Automatic Formatting**: Helps format your phone number as you type
- **MAC Address Helper**: Includes instructions to find your device's MAC address

## How to Use

1. **Enter Your Credentials**:
   - Phone Number: Must be in format `(500) 123 23 24` (will be auto-formatted)
   - Password: Your IBB WiFi password
   - MAC Address: Your device's WiFi MAC address in format `XX:XX:XX:XX:XX:XX`

2. **Save Settings**: Tap the "Save Settings" button to store your credentials

3. **Connect to IBB WiFi**: The app will automatically detect when you connect to IBB WiFi and attempt to log in

4. **Check Connection**: Use the "Check Connection" button to manually verify your connection status

## Finding Your MAC Address

Since Android 6.0+, apps cannot programmatically access the MAC address for privacy reasons. You need to manually enter it:

- **On Android 10+**: Go to Settings > About Phone > Status > Wi-Fi MAC address
- **On Android 9 and below**: Go to Settings > Wi-Fi > Menu (three dots) > Advanced > MAC address

## Important Notes

- Phone number must be in the exact format `(500) 123 23 24`
- MAC address must be in the format `XX:XX:XX:XX:XX:XX` (uppercase or lowercase letters A-F and numbers 0-9)
- The app requires the following permissions:
  - ACCESS_FINE_LOCATION (to detect WiFi networks)
  - ACCESS_WIFI_STATE
  - ACCESS_NETWORK_STATE
  - INTERNET
  - CHANGE_WIFI_STATE

## Build Instructions

### Prerequisites

- Android SDK Platform Tools (for `adb`)
- Device with USB debugging enabled (for installation)

### Building and Installing

1. **Build the APK**:
   ```bash
   ./build.sh
   ```

2. **Install on Connected Device**:
   ```bash
   ./install.sh
   ```
   This script checks if:
   - The APK has been built
   - An Android device is connected with USB debugging enabled

   If both conditions are met, it installs the app on your device.

The resulting APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Using Docker (Alternative Method)

If you prefer to use Docker for building:

1. Build the Docker image:
```
docker build -t android-builder .
```

2. Run the container and initialize the project:
```
docker run --rm -v $(pwd):/project android-builder bash -c "cd /project && chmod +x init-project.sh && ./init-project.sh"
```

3. Build the APK:
```
docker run --rm -v $(pwd):/project android-builder bash -c "cd /project && ./gradlew assembleDebug"
```

## Troubleshooting

If you encounter issues with the build:

1. Make sure all directories have proper permissions
2. Check that the Android SDK is properly installed
3. Verify that your device is properly connected and recognized by ADB 
   ```bash
   adb devices
   ```
4. Ensure USB debugging is enabled on your device 