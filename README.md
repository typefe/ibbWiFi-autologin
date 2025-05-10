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

### Using Docker (Recommended)

This project uses a persistent Docker container with a pre-warmed Gradle daemon to speed up builds.

#### Initial Setup

```bash
# Build the Docker image and start the container with a pre-warmed Gradle daemon
docker-compose up -d
```

#### Fast Builds

After the initial setup, use the fast-build script for quick builds:

```bash
./fast-build.sh
```

This script:
1. Uses the already running container with a warm Gradle daemon
2. Builds the app without starting a new daemon
3. Preserves Gradle and Android SDK caches between builds

The resulting APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Installing on a Device

To install the APK on a connected Android device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Stopping the Container

When you're done developing, you can stop the container:

```bash
docker-compose down
```

## Traditional Build (Slower)

If you prefer the traditional approach (slower, starts a new Gradle daemon each time):

```bash
./build.sh
```

## Building with Docker

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

The resulting APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Using docker-compose

Alternatively, you can use docker-compose:

1. Start the container:
```
docker-compose run --rm android-builder bash
```

2. Inside the container, run:
```
chmod +x init-project.sh
./init-project.sh
./gradlew assembleDebug
```

## Troubleshooting

If you encounter issues with the build:

1. Make sure all directories have proper permissions
2. Check that the Android SDK is properly installed in the container
3. Verify that your device is properly connected and recognized by ADB 