**Test build of Bible Daily for sideloading. Not a Play Store release.**

Install: download `BibleDaily-v0.1.0-test.apk` on your Android phone (Android 8.0 or newer), open it, and allow "Install unknown apps" for your browser or file manager when asked.

- Offline reader: the World English Bible (Public Domain), browse by Testament, book and chapter, and pick up where you left off
- Offline search by word, phrase or reference (`John 3:16`, `Jn 3:16`, `Psalm 23`)
- Bookmark, highlight, annotate, copy and share verses (shared text includes the reference and translation)
- Verse of the day, with an optional daily reminder at a time you choose
- Text size, light and dark modes, TalkBack labels. No account and no internet permission.

This is a debug-signed build with a fixed test key. Before CI publishes it, it builds the APK, runs the unit tests, and runs emulator tests in airplane mode on Android 14. Those cover first launch, chapter navigation, search, sharing, and notes and reading position surviving an app restart.
