# Bible Daily

Bible Daily is a native Android Bible app. Reading, search and saved items work fully offline. It is written in Kotlin with Jetpack Compose, Material 3, Room and DataStore, and shows Google AdMob ads.

**Download the test APK:** [BibleDaily-v0.1.0-test.apk](https://github.com/robertlangat/bible/releases/download/v0.1.0-test/BibleDaily-v0.1.0-test.apk) (from the [v0.1.0-test pre-release](https://github.com/robertlangat/bible/releases/tag/v0.1.0-test))

- **Minimum Android version:** 8.0 (API 26). Target and compile SDK: 36 (Android 16), as Google Play requires for new apps since 31 August 2026.
- **Version:** 1.0.0 (version code 2). Test builds are named `1.0.0-test`.
- **Application ID:** `com.robertlangat.bibledaily`
- **Permissions:** notifications (for the optional daily reminder), run-at-boot (to re-arm the reminder), and internet and network state (used **only** to load AdMob ads; the Bible never needs a connection).

## Features

| Tab | What it does |
|---|---|
| **Home** | The verse of the day, chosen from your phone's local date. It has Read, Copy and Share buttons, plus a "Continue reading" shortcut and the daily reminder status. The gear icon opens Settings. |
| **Read** | The offline reader. Tap the title to pick a Testament, book and chapter. Use the bottom buttons to move to the previous or next chapter, including across books. The app remembers your position, and `A−`/`A+` change the text size. Tap any verse to bookmark, highlight (4 colours), add a note, copy or share it. |
| **Search** | Offline search by word (`shepherd`), phrase (`love one another`, or `"born anew"` in quotes for an exact match) or reference (`John 3:16`, `Jn 3:16`, `Psalm 23`, `1 Cor 13:4-7`, `II Kings 2`). Tapping a result opens the chapter and scrolls to the verse. |
| **Saved** | Your bookmarks, highlights and notes, stored only on the phone, with open and remove actions. |

The default look is white screens with black text and black accents. Settings has the light (default), dark and system theme, the text size slider, the daily reminder switch and time picker, a "send a test reminder" button, and the Bible edition, source and attribution.

Copied or shared verses look like this:

```
For God so loved the world, that he gave his one and only Son, that whoever believes in him should not perish, but have eternal life.
— John 3:16 (World English Bible, WEB)
```

Accessibility: every icon button has a TalkBack label. Each verse is read as one item ("John 3:16. For God so loved… Bookmarked. Has note"), headings are marked as headings, and search result counts are announced.

## Ads (AdMob)

Every build (store and test) uses the real ad units from the Bible Daily AdMob account. No Google test units remain in the project.

| Format | Ad unit | When it shows |
|---|---|---|
| App ID | `ca-app-pub-8306955081407788~9890443209` | |
| Banner | `ca-app-pub-8306955081407788/5623895132` | Adaptive banner above the bottom tabs. It takes no space until an ad loads, so it is invisible offline. |
| Interstitial | `ca-app-pub-8306955081407788/2304788532` | At most once every 3 previous/next chapter moves, and never within 2 minutes of another full-screen ad. |
| App open | **off** (no unit yet) | When returning to the app from the background. To turn it on, put an App Open unit ID in `ADMOB_APP_OPEN_ID` in `app/build.gradle.kts`. |

The IDs are in `app/build.gradle.kts`, and the ad logic is in `app/src/main/java/com/robertlangat/bibledaily/ads/Ads.kt`. The ad formats were checked end to end with Google's test units before the switch.

**Protect your AdMob account:**
- **Test devices:** add every phone you test on in **AdMob › Settings › Test devices**. That device then gets production ads in test mode.
- **Don't tap your own live ads.**
- **CI stays offline:** the emulator runs in airplane mode for the whole run, so automated tests never request your ads.

**Consent:** the app uses Google's User Messaging Platform. On start it requests consent information and shows a consent form only where one is required *and* you have created the message in **AdMob › Privacy & messaging** (create a European regulations / GDPR message, and a US state regulations message if you want it). Ads start only once consent allows them. When required, an **Ad privacy choices** button appears in Settings.

**Before your first release with ads:**
- Publish an `app-ads.txt` file on the developer website you list in each store.
- Add the privacy policy URL ([PRIVACY.md](PRIVACY.md), e.g. `https://github.com/robertlangat/bible/blob/main/PRIVACY.md`).
- In Play Console, declare that the app contains ads and uses the advertising ID, and complete the Data safety form (AdMob collects the advertising ID, device and app info, and ad interactions).

## Store release (Google Play, Amazon Appstore, Huawei AppGallery)

| File | Use it for |
|---|---|
| `BibleDaily-1.0.0.aab` (signed Android App Bundle) | **Google Play** (required format), **Amazon Appstore** (accepted for Amazon-signed apps, the default for new apps), **Huawei AppGallery** |
| `BibleDaily-1.0.0.apk` (signed universal APK) | **Amazon Appstore** or **Huawei AppGallery**, if you prefer an APK there |

Both are signed with your private **upload key** (`bibledaily-upload.p12`, alias `upload`). The key and its password are **not** in this repository and never go to GitHub. Keep a backup of the key file and its password somewhere safe (for example a password manager plus an offline copy). On Google Play, enrol in **Play App Signing** (the default for new apps). Google then holds the app signing key, and if you ever lose the upload key, you can ask Google to reset it. Amazon and Huawei re-sign apps with their own keys by default.

How the files are produced:
1. CI builds unsigned `bundleRelease` and `assembleRelease` outputs and pushes them to the `release-build` branch.
2. They are signed outside GitHub. The AAB is signed with `jarsigner` (SHA256withRSA); the APK uses APK Signature Scheme v2 (`scripts/sign_apk_v2.py`, or `apksigner` from the Android SDK).
3. The signed files are pushed to the `signed-release` branch. The **Verify signed store builds** workflow then:
   - checks the APK with `apksigner verify` and the AAB with `jarsigner -verify` and `bundletool validate`
   - installs both on an Android 16 emulator in airplane mode and confirms the app launches without crashing.

To sign a new version yourself with the Android SDK:

```bash
# bump versionCode/versionName in app/build.gradle.kts first
./gradlew bundleRelease assembleRelease
jarsigner -sigalg SHA256withRSA -digestalg SHA-256 -keystore bibledaily-upload.p12 \
  -signedjar BibleDaily.aab app/build/outputs/bundle/release/app-release.aab upload
zipalign -p 4 app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk
apksigner sign --ks bibledaily-upload.p12 --ks-key-alias upload --out BibleDaily.apk aligned.apk
```

## Bible text and attribution

| | |
|---|---|
| Edition | **World English Bible (WEB)**, 2020 stable text, 66 books (Protestant canon), 31,098 verses |
| Licence | **Public Domain** |
| Source | eBible.org USFX edition `eng-web` (<https://ebible.org/find/details.php?id=eng-web>), taken from the [seven1m/open-bibles](https://github.com/seven1m/open-bibles) collection (file `eng-web.usfx.xml`) |
| Attribution | "The World English Bible is in the Public Domain. That means that it is not copyrighted. However, 'World English Bible' is a Trademark of eBible.org." |

The verse text is unchanged. Footnotes, section headings and Psalm titles are left out of the bundled file. The WEB source leaves five verses empty (Luke 17:36, Acts 8:37, Acts 15:34, Acts 24:7 and Romans 16:25), so they are left out here too. The conversion script is [`scripts/usfx2tsv.py`](scripts/usfx2tsv.py). The bundled data is `app/src/main/assets/bibles/eng-web.tsv.gzip`, a gzip file with one verse per line: `book_number<TAB>chapter<TAB>verse<TAB>text`.

The same attribution is shown in the app under **Settings › About this Bible**.

### Adding a Kiswahili edition later

Every translation goes into the same `verses` table, keyed by `(translationId, book, chapter, verse)`. Bookmarks, highlights and notes are keyed by `(book, chapter, verse)` only, so they stay attached when the user switches translations. To add a verified Kiswahili Bible:

1. Confirm the edition's redistribution rights and record them.
2. Convert it to the same TSV format (`scripts/usfx2tsv.py` handles USFX; OSIS needs a similar small script). Save it as `app/src/main/assets/bibles/swa-<id>.tsv.gz`.
3. Add a `Translation(...)` entry to `data/Translations.kt` with `languageCode = "sw"`, the verse count, the source and attribution, and `bookNames` (the 66 Kiswahili book names, e.g. "Mwanzo", "Kutoka", …). Add it to `Translations.all`.
4. Call `bible.ensureImported(...)` for it, and add a translation picker to Settings (the `translationId` setting already exists).

The open-bibles collection includes a `swa-swahili.osis.xml` file labelled "Public Domain", but its edition and source are not documented, so it is **not** bundled until its rights are verified.

## Build

Requirements: JDK 17 and the Android SDK (platform 35 and build tools). Android Studio Ladybug or newer includes both.

```bash
git clone https://github.com/robertlangat/bible.git
cd bible
./gradlew assembleDebug            # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # unit tests: parser, search, data completeness
```

The debug build is signed with the committed test key `app/debug.keystore` (password `android`), so each new test APK installs over the previous one. This key is public and must not be used for a Play Store release.

### Device tests

The emulator tests are in `app/src/androidTest`. `scripts/device-tests.sh` runs them like this:

1. Turns on airplane mode and does a fresh install.
2. Runs `CoreFlowsTest` (still in airplane mode): offline first launch, chapter navigation, `John 3:16` / `Jn 3:16` / `Psalm 23` / phrase search, the share intent contents, adding a bookmark, highlight and note, and the dark mode setting.
3. Force-stops the app.
4. Runs `RestartPersistenceTest` in a new process, which checks that the note, the bookmark and the reading position survived.
Airplane mode stays on for the whole run, so automated tests never request the real ad units.

```bash
./gradlew assembleDebug assembleDebugAndroidTest
scripts/device-tests.sh app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk device-test-output
```

GitHub Actions (`.github/workflows/android.yml`) runs on every push to `main`. It builds the APK, runs the unit tests, runs the device tests on an Android 14 emulator, and publishes the APK to the `v0.1.0-test` pre-release only when all of that passes. Screenshots and logs from the latest emulator run are pushed to the `ci-artifacts` branch.

## Install on your phone

1. On the phone, open the [APK link](https://github.com/robertlangat/bible/releases/download/v0.1.0-test/BibleDaily-v0.1.0-test.apk) and download it.
2. Open the downloaded file. If Android asks, allow **Install unknown apps** for your browser or Files app, then tap **Install**.
3. Open **Bible Daily**. The first launch takes a few seconds to prepare the Bible, and it works in airplane mode.

To install from a computer instead, run `adb install -r BibleDaily-v0.1.0-test.apk`.

To get the daily reminder, open **Home › Change**, turn on **Remind me every day**, allow notifications and choose a time. The reminder uses an inexact alarm, so it can arrive a few minutes late when the phone is in battery saver or Doze mode.

## Project layout

```
app/src/main/java/com/robertlangat/bibledaily/
  BibleDailyApp.kt         app container, first-launch import
  MainActivity.kt
  data/                    Books (66-book canon), Translations, Room database, repositories, DataStore settings
  domain/                  reference parser, search normalisation, verse of the day, share text
  reminder/                AlarmManager scheduling, notification and boot receivers
  ui/                      Compose screens: home, read, search, saved, settings, and shared verse actions
app/src/main/assets/bibles/eng-web.tsv.gzip   bundled World English Bible
scripts/                   data conversion and device test scripts
```

## Licence

The app source code is © Robert Langat. The Bible text is the public-domain World English Bible.
