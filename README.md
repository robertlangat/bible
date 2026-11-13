# Bible Daily

Bible Daily is a native Android Bible app that works fully offline. It is written in Kotlin with Jetpack Compose, Material 3, Room and DataStore.

**Download the test APK:** [BibleDaily-v0.1.0-test.apk](https://github.com/robertlangat/bible/releases/download/v0.1.0-test/BibleDaily-v0.1.0-test.apk) (from the [v0.1.0-test pre-release](https://github.com/robertlangat/bible/releases/tag/v0.1.0-test))

- **Minimum Android version:** 8.0 (API 26). Target and compile SDK: 35 (Android 15).
- **Application ID:** `com.robertlangat.bibledaily`
- **Permissions:** notifications (for the optional daily reminder) and run-at-boot (to re-arm the reminder). The app has **no internet permission**.

## Features

| Tab | What it does |
|---|---|
| **Home** | The verse of the day, chosen from your phone's local date. It has Read, Copy and Share buttons, plus a "Continue reading" shortcut and the daily reminder status. The gear icon opens Settings. |
| **Read** | The offline reader. Tap the title to pick a Testament, book and chapter. Use the bottom buttons to move to the previous or next chapter, including across books. The app remembers your position, and `A−`/`A+` change the text size. Tap any verse to bookmark, highlight (4 colours), add a note, copy or share it. |
| **Search** | Offline search by word (`shepherd`), phrase (`love one another`, or `"born anew"` in quotes for an exact match) or reference (`John 3:16`, `Jn 3:16`, `Psalm 23`, `1 Cor 13:4-7`, `II Kings 2`). Tapping a result opens the chapter and scrolls to the verse. |
| **Saved** | Your bookmarks, highlights and notes, stored only on the phone, with open and remove actions. |

Settings has the light, dark and system theme, the text size slider, the daily reminder switch and time picker, a "send a test reminder" button, and the Bible edition, source and attribution.

Copied or shared verses look like this:

```
For God so loved the world, that he gave his one and only Son, that whoever believes in him should not perish, but have eternal life.
— John 3:16 (World English Bible, WEB)
```

Accessibility: every icon button has a TalkBack label. Each verse is read as one item ("John 3:16. For God so loved… Bookmarked. Has note"), headings are marked as headings, and search result counts are announced.

## Bible text and attribution

| | |
|---|---|
| Edition | **World English Bible (WEB)**, 2020 stable text, 66 books (Protestant canon), 31,098 verses |
| Licence | **Public Domain** |
| Source | eBible.org USFX edition `eng-web` (<https://ebible.org/find/details.php?id=eng-web>), taken from the [seven1m/open-bibles](https://github.com/seven1m/open-bibles) collection (file `eng-web.usfx.xml`) |
| Attribution | "The World English Bible is in the Public Domain. That means that it is not copyrighted. However, 'World English Bible' is a Trademark of eBible.org." |

The verse text is unchanged. Footnotes, section headings and Psalm titles are left out of the bundled file. The WEB source leaves five verses empty (Luke 17:36, Acts 8:37, Acts 15:34, Acts 24:7 and Romans 16:25), so they are left out here too. The conversion script is [`scripts/usfx2tsv.py`](scripts/usfx2tsv.py). The bundled data is `app/src/main/assets/bibles/eng-web.tsv.gz`, a gzip file with one verse per line: `book_number<TAB>chapter<TAB>verse<TAB>text`.

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
2. Runs `CoreFlowsTest`: offline first launch, chapter navigation, `John 3:16` / `Jn 3:16` / `Psalm 23` / phrase search, the share intent contents, adding a bookmark, highlight and note, and the dark mode setting.
3. Force-stops the app.
4. Runs `RestartPersistenceTest` in a new process, which checks that the note, the bookmark and the reading position survived.

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
app/src/main/assets/bibles/eng-web.tsv.gz   bundled World English Bible
scripts/                   data conversion and device test scripts
```

## Licence

The app source code is © Robert Langat. The Bible text is the public-domain World English Bible.
