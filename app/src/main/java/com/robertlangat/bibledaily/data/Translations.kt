package com.robertlangat.bibledaily.data

/**
 * A bundled Bible edition. Each translation ships as a gzip TSV asset (named `.gzip`, because the
 * Android asset packager strips a `.gz` suffix)
 * (`book_number \t chapter \t verse \t text`) and is imported into the shared `verses` table,
 * keyed by [id]. To add a Kiswahili edition later, add the asset, a [Translation] entry with
 * `languageCode = "sw"` and localized [bookNames], and register it in [Translations.all].
 */
data class Translation(
    val id: String,
    val abbreviation: String,
    val name: String,
    val languageCode: String,
    val languageName: String,
    val assetPath: String,
    val expectedVerses: Int,
    val dataVersion: Int,
    val editionNote: String,
    val source: String,
    val sourceUrl: String,
    val license: String,
    val attribution: String,
    /** Localized book names in canonical order (66 entries); null means use the English names. */
    val bookNames: List<String>? = null,
) {
    fun bookName(book: Book): String = bookNames?.getOrNull(book.number - 1) ?: book.name
}

object Translations {
    val WEB = Translation(
        id = "WEB",
        abbreviation = "WEB",
        name = "World English Bible",
        languageCode = "en",
        languageName = "English",
        assetPath = "bibles/eng-web.tsv.gzip",
        expectedVerses = 31098,
        dataVersion = 1,
        editionNote = "World English Bible (2020 stable text), 66 books (Protestant canon). " +
            "Footnotes, section headings and Psalm titles are not shown; the verse text is unchanged.",
        source = "eBible.org USFX edition (eng-web), via the open-bibles collection " +
            "(github.com/seven1m/open-bibles)",
        sourceUrl = "https://ebible.org/find/details.php?id=eng-web",
        license = "Public Domain",
        attribution = "The World English Bible is in the Public Domain. That means that it is not " +
            "copyrighted. However, \"World English Bible\" is a Trademark of eBible.org. " +
            "Text provided by eBible.org.",
    )

    val all: List<Translation> = listOf(WEB)

    val default: Translation = WEB

    fun byId(id: String?): Translation = all.firstOrNull { it.id == id } ?: default
}
