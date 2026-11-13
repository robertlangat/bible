package com.robertlangat.bibledaily.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val languageCode: String,
    val verseCount: Int,
    val dataVersion: Int,
)

/** One verse of one translation. [searchText] is a normalized, space-padded copy used for offline search. */
@Entity(
    tableName = "verses",
    primaryKeys = ["translationId", "book", "chapter", "verse"],
)
data class VerseEntity(
    val translationId: String,
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val searchText: String,
)

/**
 * The user's bookmark, highlight and note for a verse. Keyed by canonical reference only, so they
 * stay attached to the verse whichever translation is being read.
 */
@Entity(tableName = "verse_marks", primaryKeys = ["book", "chapter", "verse"])
data class VerseMark(
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val bookmarked: Boolean = false,
    val highlight: Int = 0,
    val note: String? = null,
    val updatedAt: Long = 0L,
)

fun VerseMark.hasNote(): Boolean = !note.isNullOrBlank()

fun VerseMark.isEmpty(): Boolean = !bookmarked && highlight == 0 && !hasNote()

data class MarkWithText(
    @Embedded val mark: VerseMark,
    @ColumnInfo(name = "verseText") val verseText: String?,
)

@Dao
interface BibleDao {
    @Query("SELECT * FROM translations WHERE id = :id")
    suspend fun translation(id: String): TranslationEntity?

    @Upsert
    suspend fun upsertTranslation(translation: TranslationEntity)

    @Query("DELETE FROM verses WHERE translationId = :id")
    suspend fun deleteVerses(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerses(verses: List<VerseEntity>)

    @Query("SELECT * FROM verses WHERE translationId = :t AND book = :book AND chapter = :chapter ORDER BY verse")
    suspend fun chapter(t: String, book: Int, chapter: Int): List<VerseEntity>

    @Query("SELECT * FROM verses WHERE translationId = :t AND book = :book AND chapter = :chapter AND verse = :verse")
    suspend fun verse(t: String, book: Int, chapter: Int, verse: Int): VerseEntity?

    @RawQuery
    suspend fun search(query: SupportSQLiteQuery): List<VerseEntity>
}

private const val MARK_JOIN = "SELECT m.*, v.text AS verseText FROM verse_marks m " +
    "LEFT JOIN verses v ON v.translationId = :t AND v.book = m.book AND v.chapter = m.chapter AND v.verse = m.verse "

@Dao
interface MarksDao {
    @Query("SELECT * FROM verse_marks WHERE book = :book AND chapter = :chapter")
    fun observeChapter(book: Int, chapter: Int): Flow<List<VerseMark>>

    @Query("SELECT * FROM verse_marks WHERE book = :book AND chapter = :chapter AND verse = :verse")
    suspend fun get(book: Int, chapter: Int, verse: Int): VerseMark?

    @Upsert
    suspend fun upsert(mark: VerseMark)

    @Query("DELETE FROM verse_marks WHERE book = :book AND chapter = :chapter AND verse = :verse")
    suspend fun delete(book: Int, chapter: Int, verse: Int)

    @Query(MARK_JOIN + "WHERE m.bookmarked = 1 ORDER BY m.updatedAt DESC")
    fun observeBookmarks(t: String): Flow<List<MarkWithText>>

    @Query(MARK_JOIN + "WHERE m.highlight != 0 ORDER BY m.updatedAt DESC")
    fun observeHighlights(t: String): Flow<List<MarkWithText>>

    @Query(MARK_JOIN + "WHERE m.note IS NOT NULL AND m.note != '' ORDER BY m.updatedAt DESC")
    fun observeNotes(t: String): Flow<List<MarkWithText>>
}

@Database(
    entities = [TranslationEntity::class, VerseEntity::class, VerseMark::class],
    version = 1,
    exportSchema = false,
)
abstract class BibleDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao
    abstract fun marksDao(): MarksDao

    companion object {
        fun create(context: Context): BibleDatabase =
            Room.databaseBuilder(context.applicationContext, BibleDatabase::class.java, "bible_daily.db")
                .build()
    }
}
