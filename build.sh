#!/bin/bash
set -e

if command -v docker-compose >/dev/null 2>&1; then
  DOCKER_COMPOSE="docker-compose"
elif docker compose version >/dev/null 2>&1; then
  DOCKER_COMPOSE="docker compose"
else
  echo "docker-compose/compose is required but not installed."
  exit 1
fi

# Check if the container is already running
if ! docker ps | grep -q android-builder; then
  echo "Starting Android builder container with pre-warmed Gradle daemon..."
  $DOCKER_COMPOSE up -d
  
  # Give it a moment to start up
  sleep 2
  
  # Create debug keystore in the container's home directory
  echo "Creating debug keystore..."
  $DOCKER_COMPOSE exec android-builder bash -c "mkdir -p ~/.android && keytool -genkey -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname \"CN=Android Debug,O=Android,C=US\""
  
  # Also create a debug keystore in the app directory
  $DOCKER_COMPOSE exec android-builder bash -c "cd /project && keytool -genkey -v -keystore app/debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname \"CN=Android Debug,O=Android,C=US\""
  
  # Start the Gradle daemon in the container
  echo "Starting Gradle daemon..."
  $DOCKER_COMPOSE exec android-builder bash -c "cd /project && gradle --daemon"
  sleep 2
else
  echo "Using existing Android builder container..."
fi

# Run the build in the already running container
echo "Building APK..."
$DOCKER_COMPOSE exec android-builder bash -c "cd /project && gradle assembleDebug --info"

echo "APK built successfully at: app/build/outputs/apk/debug/app-debug.apk"

# To stop the container when done, uncomment the next line
# $DOCKER_COMPOSE down
