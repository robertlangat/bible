#!/usr/bin/env bash
# Runs the Bible Daily device tests on a connected emulator/device.
#   Phase 1: fresh install, airplane mode ON, CoreFlowsTest (first launch, navigation, search, share, notes).
#   Restart: force-stop the app process.
#   Phase 2: RestartPersistenceTest in a new process (notes, bookmark, reading position survive).
# Airplane mode stays ON throughout, so the real AdMob units are never requested by CI.
# Usage: scripts/device-tests.sh <app.apk> <androidTest.apk> <output-dir>
set -uo pipefail
APK="$1"; TEST_APK="$2"; OUT="$3"
PKG=com.robertlangat.bibledaily
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
mkdir -p "$OUT/screenshots"
status=0

adb wait-for-device
# Hide system "isn't responding" dialogs from the slow CI emulator so they don't cover screenshots.
adb shell settings put global hide_error_dialogs 1 || true
adb shell getprop ro.build.version.release | sed 's/^/Android version: /' | tee "$OUT/summary.txt"
adb shell getprop ro.build.version.sdk | sed 's/^/API level: /' | tee -a "$OUT/summary.txt"

echo "== Enabling airplane mode" | tee -a "$OUT/summary.txt"
adb shell cmd connectivity airplane-mode enable
sleep 5
echo "airplane_mode_on=$(adb shell settings get global airplane_mode_on | tr -d '\r')" | tee -a "$OUT/summary.txt"
adb shell dumpsys connectivity | grep -m3 -i "active default network\|Active default network" | tee -a "$OUT/summary.txt" || true

echo "== Fresh install" | tee -a "$OUT/summary.txt"
adb uninstall $PKG >/dev/null 2>&1 || true
adb uninstall $PKG.test >/dev/null 2>&1 || true
adb install "$APK" | tee -a "$OUT/summary.txt"
adb install "$TEST_APK" | tee -a "$OUT/summary.txt"
adb shell dumpsys package $PKG | grep -A6 "requested permissions:" | tee -a "$OUT/summary.txt"

run_phase () {
  local name="$1" cls="$2"
  echo "== $name ($cls)" | tee -a "$OUT/summary.txt"
  adb shell am instrument -w -e class "$cls" $RUNNER | tee "$OUT/$name.txt"
  if grep -q "^OK (" "$OUT/$name.txt"; then
    echo "$name: PASSED $(grep '^OK (' "$OUT/$name.txt")" | tee -a "$OUT/summary.txt"
  else
    echo "$name: FAILED" | tee -a "$OUT/summary.txt"
    status=1
  fi
}

adb logcat -c
run_phase phase1-core-flows $PKG.CoreFlowsTest
adb logcat -d -s BibleDailyTest:* BibleDaily:* > "$OUT/phase1-logcat.txt" || true
grep "Shared text" "$OUT/phase1-logcat.txt" | tee -a "$OUT/summary.txt" || true

echo "== Restarting app (force-stop kills the process)" | tee -a "$OUT/summary.txt"
adb shell am force-stop $PKG
sleep 2
adb shell pidof $PKG && echo "process still alive!" | tee -a "$OUT/summary.txt" || echo "process stopped" | tee -a "$OUT/summary.txt"

run_phase phase2-after-restart $PKG.RestartPersistenceTest

echo "airplane_mode_on at end=$(adb shell settings get global airplane_mode_on | tr -d '\r')" | tee -a "$OUT/summary.txt"

# No online ads phase: the app uses real AdMob units, and automated traffic must never request them.
# The emulator stays in airplane mode for the whole run.
adb logcat -d > "$OUT/logcat-full.txt" || true
adb pull /sdcard/Android/data/$PKG/files/screenshots/. "$OUT/screenshots/" >/dev/null 2>&1 || \
  echo "could not pull screenshots" | tee -a "$OUT/summary.txt"
ls "$OUT/screenshots" | tee -a "$OUT/summary.txt"
exit $status
