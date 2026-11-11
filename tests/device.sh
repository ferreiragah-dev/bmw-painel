#!/usr/bin/env bash
set -euo pipefail
adb shell wm size 720x1280
adb shell wm density 200
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm clear com.bmwcockpit.app
mkdir -p evidence
for case in CockpitTest RestartCheck; do
    adb shell am force-stop com.bmwcockpit.app
    adb shell am instrument -w -r -e class "com.bmwcockpit.app.$case" com.bmwcockpit.app.test/androidx.test.runner.AndroidJUnitRunner |
        tee "evidence/$case.log"
    adb exec-out run-as com.bmwcockpit.app tar -cf - -C files review | tar -xf - -C evidence || true
    grep -Eq 'OK \(1 test\)' "evidence/$case.log"
done
test -s evidence/review/19-portrait-settings.png
