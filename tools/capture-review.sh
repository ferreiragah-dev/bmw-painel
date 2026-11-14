#!/usr/bin/env bash
# Installs the APKs on the running emulator and runs the screenshot tour in several head-unit resolutions.
# Screenshots are pulled to review/ci/ (never into the app resources).
set -euo pipefail
APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
OUT=review/ci
PKG=com.bmwcockpit.app
mkdir -p "$OUT"
adb install -r "$APK"
adb install -r "$TEST_APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true

run() { # prefix size density full orientation(0=landscape user rotation 1)
  local prefix=$1 size=$2 dens=$3 full=$4 rot=$5
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation "$rot"
  if [ "$rot" = "1" ]; then # natural orientation is portrait: swap for landscape head-unit sizes
    local w=${size%x*} h=${size#*x}; adb shell wm size "${h}x${w}"
  else adb shell wm size "$size"; fi
  adb shell wm density "$dens"
  sleep 2
  adb shell am instrument -w -e class com.bmwcockpit.app.ScreenshotTour -e prefix "$prefix" -e full "$full" \
    $PKG.test/androidx.test.runner.AndroidJUnitRunner | tee "$OUT/$prefix-instrument.txt"
  grep -q "OK (" "$OUT/$prefix-instrument.txt"
}

run hu1280x720 1280x720 160 true 1
run hu1024x600 1024x600 160 false 1
run hu800x480 800x480 160 false 1
run hu1280x480 1280x480 160 false 1
run ultrawide1920x720 1920x720 160 false 1
run portrait 1080x2400 420 false 0

adb shell wm size reset
adb shell wm density reset
adb pull /sdcard/Android/data/$PKG/files/review "$OUT/" || true
ls -la "$OUT"
