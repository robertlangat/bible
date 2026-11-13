package com.robertlangat.bibledaily.data

enum class Testament { OLD, NEW }

/**
 * A canonical book. [number] (1..66) is the stable key used in the database, so data for any
 * translation (for example a future Kiswahili edition) lines up with bookmarks, notes and highlights.
 * [aliases] are lowercase, space-free abbreviations used by the reference parser.
 */
data class Book(
    val number: Int,
    val code: String,
    val name: String,
    val chapters: Int,
    val aliases: List<String>,
) {
    val testament: Testament get() = if (number <= 39) Testament.OLD else Testament.NEW
}

object Books {
    val all: List<Book> = listOf(
        Book(1, "GEN", "Genesis", 50, listOf("gen", "ge", "gn")),
        Book(2, "EXO", "Exodus", 40, listOf("exo", "ex", "exod")),
        Book(3, "LEV", "Leviticus", 27, listOf("lev", "le", "lv")),
        Book(4, "NUM", "Numbers", 36, listOf("num", "nu", "nm", "nb")),
        Book(5, "DEU", "Deuteronomy", 34, listOf("deut", "deu", "dt", "de")),
        Book(6, "JOS", "Joshua", 24, listOf("josh", "jos", "jsh")),
        Book(7, "JDG", "Judges", 21, listOf("judg", "jdg", "jg", "jdgs")),
        Book(8, "RUT", "Ruth", 4, listOf("rut", "ru", "rth")),
        Book(9, "1SA", "1 Samuel", 31, listOf("1sam", "1sa", "1sm", "1s")),
        Book(10, "2SA", "2 Samuel", 24, listOf("2sam", "2sa", "2sm", "2s")),
        Book(11, "1KI", "1 Kings", 22, listOf("1kgs", "1ki", "1kin", "1k")),
        Book(12, "2KI", "2 Kings", 25, listOf("2kgs", "2ki", "2kin", "2k")),
        Book(13, "1CH", "1 Chronicles", 29, listOf("1chr", "1ch", "1chron")),
        Book(14, "2CH", "2 Chronicles", 36, listOf("2chr", "2ch", "2chron")),
        Book(15, "EZR", "Ezra", 10, listOf("ezr")),
        Book(16, "NEH", "Nehemiah", 13, listOf("neh", "ne")),
        Book(17, "EST", "Esther", 10, listOf("esth", "est", "es")),
        Book(18, "JOB", "Job", 42, listOf("jb")),
        Book(19, "PSA", "Psalms", 150, listOf("ps", "psa", "psalm", "pss", "psm", "pslm")),
        Book(20, "PRO", "Proverbs", 31, listOf("prov", "pro", "prv", "pr")),
        Book(21, "ECC", "Ecclesiastes", 12, listOf("eccl", "ecc", "ec", "qoh")),
        Book(22, "SNG", "Song of Solomon", 8, listOf("song", "sng", "sos", "songofsongs", "canticles", "ss")),
        Book(23, "ISA", "Isaiah", 66, listOf("isa", "is")),
        Book(24, "JER", "Jeremiah", 52, listOf("jer", "je", "jr")),
        Book(25, "LAM", "Lamentations", 5, listOf("lam", "la")),
        Book(26, "EZK", "Ezekiel", 48, listOf("ezek", "ezk", "eze")),
        Book(27, "DAN", "Daniel", 12, listOf("dan", "da", "dn")),
        Book(28, "HOS", "Hosea", 14, listOf("hos", "ho")),
        Book(29, "JOL", "Joel", 3, listOf("jol", "joe", "jl")),
        Book(30, "AMO", "Amos", 9, listOf("amo", "am")),
        Book(31, "OBA", "Obadiah", 1, listOf("obad", "oba", "ob")),
        Book(32, "JON", "Jonah", 4, listOf("jon", "jnh")),
        Book(33, "MIC", "Micah", 7, listOf("mic", "mc")),
        Book(34, "NAM", "Nahum", 3, listOf("nah", "nam", "na")),
        Book(35, "HAB", "Habakkuk", 3, listOf("hab", "hb")),
        Book(36, "ZEP", "Zephaniah", 3, listOf("zeph", "zep", "zp")),
        Book(37, "HAG", "Haggai", 2, listOf("hag", "hg")),
        Book(38, "ZEC", "Zechariah", 14, listOf("zech", "zec", "zc")),
        Book(39, "MAL", "Malachi", 4, listOf("mal", "ml")),
        Book(40, "MAT", "Matthew", 28, listOf("matt", "mat", "mt")),
        Book(41, "MRK", "Mark", 16, listOf("mrk", "mar", "mk", "mr")),
        Book(42, "LUK", "Luke", 24, listOf("luk", "lk")),
        Book(43, "JHN", "John", 21, listOf("jhn", "jn", "joh")),
        Book(44, "ACT", "Acts", 28, listOf("act", "ac")),
        Book(45, "ROM", "Romans", 16, listOf("rom", "ro", "rm")),
        Book(46, "1CO", "1 Corinthians", 16, listOf("1cor", "1co")),
        Book(47, "2CO", "2 Corinthians", 13, listOf("2cor", "2co")),
        Book(48, "GAL", "Galatians", 6, listOf("gal", "ga")),
        Book(49, "EPH", "Ephesians", 6, listOf("eph", "ephes")),
        Book(50, "PHP", "Philippians", 4, listOf("phil", "php", "pp")),
        Book(51, "COL", "Colossians", 4, listOf("col")),
        Book(52, "1TH", "1 Thessalonians", 5, listOf("1thess", "1th", "1thes")),
        Book(53, "2TH", "2 Thessalonians", 3, listOf("2thess", "2th", "2thes")),
        Book(54, "1TI", "1 Timothy", 6, listOf("1tim", "1ti", "1tm")),
        Book(55, "2TI", "2 Timothy", 4, listOf("2tim", "2ti", "2tm")),
        Book(56, "TIT", "Titus", 3, listOf("tit", "ti")),
        Book(57, "PHM", "Philemon", 1, listOf("philem", "phm", "phlm")),
        Book(58, "HEB", "Hebrews", 13, listOf("heb")),
        Book(59, "JAS", "James", 5, listOf("jas", "jm", "jam")),
        Book(60, "1PE", "1 Peter", 5, listOf("1pet", "1pe", "1pt", "1p")),
        Book(61, "2PE", "2 Peter", 3, listOf("2pet", "2pe", "2pt", "2p")),
        Book(62, "1JN", "1 John", 5, listOf("1jn", "1jhn", "1jo", "1j", "1joh")),
        Book(63, "2JN", "2 John", 1, listOf("2jn", "2jhn", "2jo", "2j", "2joh")),
        Book(64, "3JN", "3 John", 1, listOf("3jn", "3jhn", "3jo", "3j", "3joh")),
        Book(65, "JUD", "Jude", 1, listOf("jud", "jd")),
        Book(66, "REV", "Revelation", 22, listOf("rev", "re", "rv", "revelations", "apocalypse")),
    )

    fun byNumber(number: Int): Book = all[(number - 1).coerceIn(0, all.size - 1)]

    fun byNumberOrNull(number: Int): Book? = all.getOrNull(number - 1)

    /** The chapter after (book, chapter), crossing book boundaries; null at the end of Revelation. */
    fun next(book: Int, chapter: Int): Pair<Int, Int>? {
        val b = byNumberOrNull(book) ?: return null
        return when {
            chapter < b.chapters -> book to chapter + 1
            book < all.size -> book + 1 to 1
            else -> null
        }
    }

    /** The chapter before (book, chapter), crossing book boundaries; null at Genesis 1. */
    fun previous(book: Int, chapter: Int): Pair<Int, Int>? = when {
        chapter > 1 -> book to chapter - 1
        book > 1 -> book - 1 to byNumber(book - 1).chapters
        else -> null
    }
}
