#!/bin/bash
set -e

# Check if the container is already running
if ! docker ps | grep -q android-builder; then
  echo "Starting Android builder container with pre-warmed Gradle daemon..."
  docker-compose up -d
  
  # Give it a moment to start up
  sleep 2
  
  # Create debug keystore in the container's home directory
  echo "Creating debug keystore..."
  docker-compose exec android-builder bash -c "mkdir -p ~/.android && keytool -genkey -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname \"CN=Android Debug,O=Android,C=US\""
  
  # Also create a debug keystore in the app directory
  docker-compose exec android-builder bash -c "cd /project && keytool -genkey -v -keystore app/debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname \"CN=Android Debug,O=Android,C=US\""
  
  # Start the Gradle daemon in the container
  echo "Starting Gradle daemon..."
  docker-compose exec android-builder bash -c "cd /project && gradle --daemon"
  sleep 2
else
  echo "Using existing Android builder container..."
fi

# Run the build in the already running container
echo "Building APK..."
docker-compose exec android-builder bash -c "cd /project && gradle assembleDebug --info"

echo "APK built successfully at: app/build/outputs/apk/debug/app-debug.apk"

# To stop the container when done, uncomment the next line
# docker-compose down 