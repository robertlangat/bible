#!/usr/bin/env bash
# Runs the Bible Daily device tests on a connected emulator/device.
#   Phase 1: fresh install, airplane mode ON, CoreFlowsTest (first launch, navigation, search, share, notes).
#   Restart: force-stop the app process.
#   Phase 2: RestartPersistenceTest in a new process (notes, bookmark, reading position survive).
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

echo "airplane_mode_on after offline phases=$(adb shell settings get global airplane_mode_on | tr -d '\r')" | tee -a "$OUT/summary.txt"

# ---- Phase 3: ads (network ON). Reported, but does not block the release: ad fill depends on Google's servers.
echo "== Disabling airplane mode for the ads phase" | tee -a "$OUT/summary.txt"
adb shell cmd connectivity airplane-mode disable
for i in $(seq 1 30); do adb shell ping -c 1 -W 2 8.8.8.8 >/dev/null 2>&1 && break; sleep 2; done
offline_status=$status
run_phase phase3-ads-online $PKG.AdsSmokeTest
status=$offline_status
grep -h "BibleDailyAds" "$OUT"/*.txt >/dev/null 2>&1 || true

echo "== App open ad: cold start, go Home, return" | tee -a "$OUT/summary.txt"
adb shell am force-stop $PKG
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity >/dev/null
for i in $(seq 1 45); do adb logcat -d -s BibleDailyAds:I | grep -q "App open ad loaded" && break; sleep 2; done
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell am start -n $PKG/.MainActivity >/dev/null
sleep 8
adb exec-out screencap -p > "$OUT/screenshots-adb-14_app_open_ad.png"
adb logcat -d -s BibleDailyAds:I | tee "$OUT/ads-logcat.txt"
if grep -q "App open ad shown" "$OUT/ads-logcat.txt"; then
  echo "app-open-ad: PASSED (shown on return to the app)" | tee -a "$OUT/summary.txt"
else
  echo "app-open-ad: NOT SHOWN" | tee -a "$OUT/summary.txt"
fi
adb shell input keyevent KEYCODE_BACK
adb logcat -d > "$OUT/logcat-full.txt" || true
adb pull /sdcard/Android/data/$PKG/files/screenshots/. "$OUT/screenshots/" >/dev/null 2>&1 || \
  echo "could not pull screenshots" | tee -a "$OUT/summary.txt"
mv "$OUT/screenshots-adb-14_app_open_ad.png" "$OUT/screenshots/14_app_open_ad.png" 2>/dev/null || true
ls "$OUT/screenshots" | tee -a "$OUT/summary.txt"
exit $status
