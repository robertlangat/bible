package com.robertlangat.bibledaily

import com.robertlangat.bibledaily.data.Books
import com.robertlangat.bibledaily.data.Translations
import com.robertlangat.bibledaily.domain.ReferenceParser
import com.robertlangat.bibledaily.domain.SearchText
import com.robertlangat.bibledaily.domain.ShareText
import com.robertlangat.bibledaily.domain.TextQuery
import com.robertlangat.bibledaily.domain.VerseOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.zip.GZIPInputStream

class ReferenceParserTest {
    private fun check(input: String, book: String, chapter: Int, verse: Int? = null, end: Int? = null) {
        val ref = requireRef(input)
        assertEquals(input, book, ref.book.name)
        assertEquals(input, chapter, ref.chapter)
        assertEquals(input, verse, ref.verse)
        assertEquals(input, end, ref.verseEnd)
    }

    private fun requireRef(input: String) =
        ReferenceParser.parse(input) ?: throw AssertionError("Could not parse: $input")

    @Test fun fullAndAbbreviatedJohn() {
        check("John 3:16", "John", 3, 16)
        check("Jn 3:16", "John", 3, 16)
        check("jn3:16", "John", 3, 16)
        check("Jhn 3.16", "John", 3, 16)
        check("  john   3 : 16 ", "John", 3, 16)
    }

    @Test fun psalmsAndChapters() {
        check("Psalm 23", "Psalms", 23)
        check("Psalms 23", "Psalms", 23)
        check("Ps 23:1", "Psalms", 23, 1)
        check("Psalm 119:105", "Psalms", 119, 105)
    }

    @Test fun numberedBooksAndRanges() {
        check("1 Cor 13:4-7", "1 Corinthians", 13, 4, 7)
        check("1Co 13", "1 Corinthians", 13)
        check("II Kings 2", "2 Kings", 2)
        check("First John 4:19", "1 John", 4, 19)
        check("3 John 1:4", "3 John", 1, 4)
        check("Song of Solomon 2:1", "Song of Solomon", 2, 1)
        check("Rev.22.21", "Revelation", 22, 21)
        check("Gen 1:1", "Genesis", 1, 1)
        check("Philippians 4:13", "Philippians", 4, 13)
        check("Phil 4:13", "Philippians", 4, 13)
        check("Phlm 1", "Philemon", 1)
        check("Isa 53:5", "Isaiah", 53, 5)
        check("Is 53", "Isaiah", 53)
    }

    @Test fun bookOnlyRequiresFullName() {
        val job = ReferenceParser.parse("Job")
        assertNotNull(job)
        assertTrue(job!!.bookOnly)
        assertEquals(1, job.chapter)
        assertNull(ReferenceParser.parse("love"))
        assertNull(ReferenceParser.parse("is"))
        assertNull(ReferenceParser.parse("born anew"))
        assertNull(ReferenceParser.parse("for god so loved"))
    }

    @Test fun invalidChaptersRejected() {
        assertNull(ReferenceParser.parse("John 22"))
        assertNull(ReferenceParser.parse("Psalm 151"))
        assertNull(ReferenceParser.parse("Jude 2"))
        assertNull(ReferenceParser.parse("Xyz 3:16"))
    }

    @Test fun labels() {
        assertEquals("John 3:16", ReferenceParser.parse("jn 3:16")!!.label())
        assertEquals("1 Corinthians 13:4-7", ReferenceParser.parse("1 cor 13:4-7")!!.label())
        assertEquals("Psalms 23", ReferenceParser.parse("psalm 23")!!.label())
    }
}

class BooksTest {
    @Test fun canonHas66BooksAnd1189Chapters() {
        assertEquals(66, Books.all.size)
        assertEquals(1189, Books.all.sumOf { it.chapters })
        assertEquals(39, Books.all.count { it.number <= 39 })
    }

    @Test fun chapterNavigationCrossesBooks() {
        assertEquals(1 to 2, Books.next(1, 1))
        assertEquals(2 to 1, Books.next(1, 50))
        assertEquals(1 to 50, Books.previous(2, 1))
        assertNull(Books.previous(1, 1))
        assertNull(Books.next(66, 22))
    }

    @Test fun aliasesAreUnique() {
        val seen = mutableMapOf<String, String>()
        for (b in Books.all) for (a in b.aliases + b.code.lowercase()) {
            val previous = seen.put(a, b.name)
            assertTrue("Alias $a used by $previous and ${b.name}", previous == null || previous == b.name)
        }
    }
}

class SearchTextTest {
    @Test fun normalizeDropsPunctuationAndApostrophes() {
        assertEquals("for god so loved the world", SearchText.normalize("“For God so loved the world,"))
        assertEquals("dont marvel", SearchText.normalize("Don’t marvel"))
        assertEquals(" in the beginning ", SearchText.indexValue("In the beginning"))
    }

    @Test fun queryBuildsWordAndPhraseConditions() {
        val q = TextQuery.parse("Love one another")!!
        assertEquals(listOf("love", "one", "another"), q.terms)
        assertFalse(q.exactPhraseOnly)
        val sql = q.toSql("WEB", 50)
        assertEquals(3, Regex("searchText LIKE \\?").findAll(sql.sql.substringBefore("ORDER BY")).count())
        assertEquals(listOf("WEB", "% love%", "% one%", "% another%", "% love one another%", 50), sql.args)

        val exact = TextQuery.parse("\"born anew\"")!!
        assertTrue(exact.exactPhraseOnly)
        assertEquals(listOf("WEB", "% born anew %", "% born anew%", 10), exact.toSql("WEB", 10).args)
        assertNull(TextQuery.parse("  ?! "))
    }
}

class VerseOfDayTest {
    @Test fun allReferencesParseAndRotateDaily() {
        assertTrue(VerseOfDay.references.size >= 60)
        val d = LocalDate.of(2026, 11, 13)
        assertEquals(VerseOfDay.referenceFor(d), VerseOfDay.referenceFor(LocalDate.of(2026, 11, 13)))
        assertFalse(VerseOfDay.referenceFor(d) == VerseOfDay.referenceFor(d.plusDays(1)))
        // Works for any date, including far past/future.
        assertNotNull(VerseOfDay.referenceFor(LocalDate.of(1900, 1, 1)).verse)
    }
}

class ShareTextTest {
    @Test fun includesReferenceAndTranslation() {
        val text = ShareText.format("For God so loved the world…", "John 3:16", "World English Bible", "WEB")
        assertEquals("For God so loved the world…\n— John 3:16 (World English Bible, WEB)", text)
    }
}

/** Verifies the bundled Bible asset is complete and well-formed. */
class BundledBibleDataTest {
    private val rows: List<List<String>> by lazy {
        val file = File("src/main/assets/${Translations.WEB.assetPath}")
        GZIPInputStream(file.inputStream()).bufferedReader().readLines().map { it.split('\t', limit = 4) }
    }

    @Test fun verseCountMatchesTranslationMetadata() {
        assertEquals(Translations.WEB.expectedVerses, rows.size)
        assertTrue(rows.all { it.size == 4 && it[3].isNotBlank() })
    }

    @Test fun everyChapterOfEveryBookIsPresent() {
        val chapters = rows.groupBy { it[0].toInt() }.mapValues { (_, r) -> r.map { it[1].toInt() }.toSet() }
        for (book in Books.all) {
            assertEquals(book.name, (1..book.chapters).toSet(), chapters[book.number])
        }
    }

    @Test fun keyVersesHaveExpectedText() {
        fun text(b: Int, c: Int, v: Int) = rows.first { it[0] == "$b" && it[1] == "$c" && it[2] == "$v" }[3]
        assertTrue(text(43, 3, 16).startsWith("For God so loved the world"))
        assertTrue(text(19, 23, 1).startsWith("Yahweh is my shepherd"))
        assertTrue(text(1, 1, 1).startsWith("In the beginning"))
    }

    @Test fun everyVerseOfTheDayExistsInTheData() {
        val keys = rows.map { "${it[0]}:${it[1]}:${it[2]}" }.toSet()
        for (r in VerseOfDay.references) {
            val ref = ReferenceParser.parse(r)!!
            assertTrue(r, "${ref.book.number}:${ref.chapter}:${ref.verse}" in keys)
        }
    }
}
