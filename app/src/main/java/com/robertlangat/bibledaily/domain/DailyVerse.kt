package com.robertlangat.bibledaily.domain

import java.time.LocalDate

/** Picks the verse of the day from a curated list, using the phone's local calendar date. */
object VerseOfDay {
    val references: List<String> = listOf(
        "John 3:16", "Psalm 23:1", "Proverbs 3:5", "Philippians 4:13", "Romans 8:28",
        "Jeremiah 29:11", "Joshua 1:9", "Isaiah 40:31", "Matthew 11:28", "Philippians 4:6",
        "Psalm 46:1", "Romans 12:2", "Hebrews 11:1", "1 John 4:19", "Lamentations 3:22",
        "Psalm 119:105", "Matthew 6:33", "2 Corinthians 5:17", "Galatians 5:22", "Ephesians 2:8",
        "Isaiah 41:10", "Psalm 37:4", "Romans 15:13", "1 Corinthians 13:4", "John 14:6",
        "Matthew 5:16", "Psalm 27:1", "Proverbs 16:3", "Micah 6:8", "Colossians 3:23",
        "1 Peter 5:7", "James 1:5", "Psalm 34:8", "John 16:33", "Deuteronomy 31:6",
        "Psalm 121:1", "Romans 5:8", "Hebrews 13:8", "2 Timothy 1:7", "Zephaniah 3:17",
        "Isaiah 26:3", "Psalm 91:1", "John 15:5", "Matthew 22:37", "Ephesians 4:32",
        "1 Thessalonians 5:16", "Psalm 139:14", "Romans 10:9", "Nahum 1:7", "Psalm 100:4",
        "John 1:1", "Genesis 1:1", "Psalm 51:10", "Proverbs 18:10", "Isaiah 53:5",
        "Matthew 28:20", "Revelation 21:4", "Psalm 118:24", "Joshua 24:15", "1 John 1:9",
        "Hebrews 4:16", "Psalm 19:14", "John 11:25", "Romans 6:23", "Galatians 2:20",
        "Psalm 16:11", "Isaiah 9:6", "James 1:17", "Mark 10:27", "Luke 1:37",
    )

    private val parsed: List<BibleReference> = references.map {
        requireNotNull(ReferenceParser.parse(it)) { "Bad verse-of-the-day reference: $it" }
    }

    fun referenceFor(date: LocalDate): BibleReference =
        parsed[Math.floorMod(date.toEpochDay(), parsed.size.toLong()).toInt()]
}

object ShareText {
    /** Text used for both Copy and Share: verse, reference and translation name. */
    fun format(verseText: String, reference: String, translationName: String, abbreviation: String): String =
        "$verseText\n— $reference ($translationName, $abbreviation)"
}
