#!/bin/bash
set -e

echo "Installing APK to connected device..."

# Check if the APK exists
if [ ! -f app/build/outputs/apk/debug/app-debug.apk ]; then
  echo "APK not found! Please build the app first using ./fast-build.sh"
  exit 1
fi

# Check if any Android device is connected
if ! adb devices | grep -q "device$"; then
  echo "No Android device connected. Please connect a device and enable USB debugging."
  exit 1
fi

# Install the APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo "Installation complete!"
echo "You can now open the app on your device." 