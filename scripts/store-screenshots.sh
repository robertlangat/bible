#!/usr/bin/env bash
# Captures store screenshots on a 1080x1920 emulator: clean demo-mode status bar, airplane mode ON
# (no ads are requested), then runs the device tests, which save screenshots along the way.
set -uo pipefail
OUT="$1"
adb wait-for-device
adb shell settings put global hide_error_dialogs 1 || true
adb shell wm size | tee "$OUT-size.txt" || true
adb shell settings put global sysui_demo_allowed 1
demo () { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 0900
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile show -e datatype none -e level 4
demo network -e airplane hide
demo notifications -e visible false
bash scripts/device-tests.sh apks/debug/app-debug.apk apks/androidTest/debug/app-debug-androidTest.apk "$OUT"
