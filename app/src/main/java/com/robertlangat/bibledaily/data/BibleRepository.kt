package com.robertlangat.bibledaily.data

import android.content.Context
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.robertlangat.bibledaily.data.db.BibleDatabase
import com.robertlangat.bibledaily.data.db.MarkWithText
import com.robertlangat.bibledaily.data.db.TranslationEntity
import com.robertlangat.bibledaily.data.db.VerseEntity
import com.robertlangat.bibledaily.data.db.VerseMark
import com.robertlangat.bibledaily.data.db.isEmpty
import com.robertlangat.bibledaily.domain.BibleReference
import com.robertlangat.bibledaily.domain.ReferenceParser
import com.robertlangat.bibledaily.domain.SearchText
import com.robertlangat.bibledaily.domain.TextQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.zip.GZIPInputStream

data class ReferenceHit(val reference: BibleReference, val label: String, val preview: String)

data class SearchResults(
    val query: String,
    val reference: ReferenceHit? = null,
    val verses: List<VerseEntity> = emptyList(),
    val terms: List<String> = emptyList(),
    val truncated: Boolean = false,
)

class BibleRepository(private val context: Context, private val db: BibleDatabase) {

    private val dao = db.bibleDao()
    private val marks = db.marksDao()
    private val importMutex = Mutex()
    private val markMutex = Mutex()

    /** Imports the bundled translation into Room on first launch (or when the bundled data changes). */
    suspend fun ensureImported(translation: Translation) = importMutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = dao.translation(translation.id)
            if (existing != null &&
                existing.dataVersion == translation.dataVersion &&
                existing.verseCount == translation.expectedVerses
            ) return@withContext

            db.withTransaction {
                dao.deleteVerses(translation.id)
                var count = 0
                val batch = ArrayList<VerseEntity>(BATCH)
                context.assets.open(translation.assetPath).use { raw ->
                    GZIPInputStream(raw).bufferedReader(Charsets.UTF_8).useLines { lines ->
                        for (line in lines) {
                            if (line.isBlank()) continue
                            val parts = line.split('\t', limit = 4)
                            if (parts.size < 4) continue
                            val text = parts[3]
                            batch += VerseEntity(
                                translationId = translation.id,
                                book = parts[0].toInt(),
                                chapter = parts[1].toInt(),
                                verse = parts[2].toInt(),
                                text = text,
                                searchText = SearchText.indexValue(text),
                            )
                            if (batch.size >= BATCH) {
                                dao.insertVerses(batch.toList())
                                count += batch.size
                                batch.clear()
                            }
                        }
                    }
                }
                if (batch.isNotEmpty()) {
                    dao.insertVerses(batch.toList())
                    count += batch.size
                }
                check(count > 0) { "No verses found in ${translation.assetPath}" }
                dao.upsertTranslation(
                    TranslationEntity(
                        id = translation.id,
                        name = translation.name,
                        languageCode = translation.languageCode,
                        verseCount = count,
                        dataVersion = translation.dataVersion,
                    ),
                )
            }
        }
    }

    suspend fun chapter(translationId: String, book: Int, chapter: Int): List<VerseEntity> =
        dao.chapter(translationId, book, chapter)

    suspend fun verse(translationId: String, book: Int, chapter: Int, verse: Int): VerseEntity? =
        dao.verse(translationId, book, chapter, verse)

    suspend fun search(translation: Translation, rawQuery: String, limit: Int = 500): SearchResults {
        val query = rawQuery.trim()
        if (query.isEmpty()) return SearchResults(query)

        val ref = ReferenceParser.parse(query)
        var refHit: ReferenceHit? = null
        if (ref != null) {
            val label = ref.label(translation.bookName(ref.book))
            val preview = if (ref.verse != null) {
                val end = ref.verseEnd ?: ref.verse
                dao.chapter(translation.id, ref.book.number, ref.chapter)
                    .filter { it.verse in ref.verse..end }
                    .joinToString(" ") { it.text }
            } else {
                dao.chapter(translation.id, ref.book.number, ref.chapter).take(2).joinToString(" ") { it.text }
            }
            if (preview.isNotEmpty()) refHit = ReferenceHit(ref, label, preview)
            // A full reference ("John 3:16", "Psalm 23") is answered by the reference alone.
            if (!ref.bookOnly && refHit != null) return SearchResults(query, refHit)
        }

        val textQuery = TextQuery.parse(query) ?: return SearchResults(query, refHit)
        val sql = textQuery.toSql(translation.id, limit + 1)
        val rows = dao.search(SimpleSQLiteQuery(sql.sql, sql.args.toTypedArray()))
        return SearchResults(
            query = query,
            reference = refHit,
            verses = rows.take(limit),
            terms = textQuery.terms,
            truncated = rows.size > limit,
        )
    }

    fun observeChapterMarks(book: Int, chapter: Int): Flow<List<VerseMark>> = marks.observeChapter(book, chapter)
    fun observeBookmarks(translationId: String): Flow<List<MarkWithText>> = marks.observeBookmarks(translationId)
    fun observeHighlights(translationId: String): Flow<List<MarkWithText>> = marks.observeHighlights(translationId)
    fun observeNotes(translationId: String): Flow<List<MarkWithText>> = marks.observeNotes(translationId)

    suspend fun updateMark(book: Int, chapter: Int, verse: Int, transform: (VerseMark) -> VerseMark) =
        markMutex.withLock {
            val current = marks.get(book, chapter, verse) ?: VerseMark(book, chapter, verse)
            val updated = transform(current).copy(updatedAt = System.currentTimeMillis())
            if (updated.isEmpty()) marks.delete(book, chapter, verse) else marks.upsert(updated)
        }

    private companion object {
        const val BATCH = 2000
    }
}
