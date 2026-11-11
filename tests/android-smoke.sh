#!/usr/bin/env bash
set -euo pipefail
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
result=$(adb shell am instrument -w com.bmwcockpit.app.test/com.bmwcockpit.app.CockpitSmoke)
echo "$result"
grep -Fq 'cockpit_smoke=PASS' <<<"$result"
