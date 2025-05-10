# IBB WiFi Captive Portal Auto Login

A containerized Android build environment for developing a captive portal auto-login app.

## Optimized Build Process

This project uses a persistent Docker container with a pre-warmed Gradle daemon to speed up builds.

### Initial Setup

```bash
# Build the Docker image and start the container with a pre-warmed Gradle daemon
docker-compose up -d
```

### Fast Builds

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