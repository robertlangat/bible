package com.robertlangat.bibledaily.domain

import com.robertlangat.bibledaily.data.Book
import com.robertlangat.bibledaily.data.Books

data class BibleReference(
    val book: Book,
    val chapter: Int,
    val verse: Int? = null,
    val verseEnd: Int? = null,
    /** True when the user typed only a book name (no chapter), e.g. "Job". */
    val bookOnly: Boolean = false,
) {
    fun label(bookName: String = book.name): String = buildString {
        append(bookName).append(' ').append(chapter)
        if (verse != null) {
            append(':').append(verse)
            if (verseEnd != null && verseEnd > verse) append('-').append(verseEnd)
        }
    }
}

/**
 * Parses references such as "John 3:16", "Jn 3:16", "Psalm 23", "1 Cor 13:4-7", "II Kings 2",
 * "Song of Solomon 2:1" and "Rev.22.21". Returns null when the text is not a reference.
 */
object ReferenceParser {

    private val pattern = Regex(
        """^\s*([1-3]?\s*[\p{L}][\p{L}.\s]*?)\s*(\d+)?(?:\s*[:.]\s*(\d+)(?:\s*[-–—]\s*(\d+))?)?\s*$""",
    )

    private val exactNames: Map<String, Book> = Books.all.associateBy { key(it.name) }

    private val aliases: Map<String, Book> = buildMap {
        for (book in Books.all) {
            put(key(book.name), book)
            put(book.code.lowercase(), book)
            book.aliases.forEach { put(it, book) }
        }
    }

    fun parse(input: String): BibleReference? {
        val match = pattern.matchEntire(input.trim()) ?: return null
        val (bookPart, chapterPart, versePart, verseEndPart) = match.destructured
        val bookKey = key(bookPart)
        if (bookKey.isEmpty()) return null

        val chapter = chapterPart.toIntOrNull()
        if (chapter == null) {
            // Book name alone (e.g. "Psalms") opens chapter 1, but only for full names, so ordinary
            // search words such as "is" or "am" are not mistaken for books.
            if (versePart.isNotEmpty()) return null
            val book = exactNames[bookKey] ?: (if (bookKey == "psalm") exactNames["psalms"] else null) ?: return null
            return BibleReference(book, 1, bookOnly = true)
        }

        val book = aliases[bookKey] ?: prefixMatch(bookKey) ?: return null
        if (chapter < 1 || chapter > book.chapters) return null
        val verse = versePart.toIntOrNull()?.takeIf { it >= 1 }
        val verseEnd = verseEndPart.toIntOrNull()?.takeIf { verse != null && it > verse }
        if (versePart.isNotEmpty() && verse == null) return null
        return BibleReference(book, chapter, verse, verseEnd)
    }

    /** Normalizes a book name: lowercase, no dots/spaces, "I/II/III/First/Second/Third" → 1/2/3. */
    internal fun key(raw: String): String {
        var s = raw.lowercase().replace('.', ' ').trim().replace(Regex("\\s+"), " ")
        s = s.replace(Regex("^(iii|third|3rd)\\s+"), "3")
            .replace(Regex("^(ii|second|2nd)\\s+"), "2")
            .replace(Regex("^(i|first|1st)\\s+"), "1")
        return s.replace(" ", "")
    }

    private fun prefixMatch(key: String): Book? {
        if (key.length < 3) return null
        val candidates = aliases.filterKeys { it.startsWith(key) }.values.toSet()
        return candidates.singleOrNull()
    }
}
