package com.robertlangat.bibledaily.domain

/** Text normalization shared by the importer (stored search column) and the search query builder. */
object SearchText {
    /** Lowercases, drops apostrophes ("don’t" → "dont") and turns punctuation into single spaces. */
    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        var lastWasSpace = true
        for (ch in text.lowercase()) {
            when {
                ch == '\'' || ch == '’' || ch == '‘' || ch == 'ʼ' -> Unit
                ch.isLetterOrDigit() -> {
                    sb.append(ch)
                    lastWasSpace = false
                }
                !lastWasSpace -> {
                    sb.append(' ')
                    lastWasSpace = true
                }
            }
        }
        return sb.toString().trim()
    }

    /** Value stored in `verses.searchText`: padded with spaces so word-start matching works at the edges. */
    fun indexValue(text: String): String = " " + normalize(text) + " "
}

/**
 * A word/phrase query. Every term must start a word somewhere in the verse; verses that contain the
 * whole phrase in order are ranked first. Wrapping the query in quotes requires the exact phrase.
 */
data class TextQuery(val terms: List<String>, val phrase: String, val exactPhraseOnly: Boolean) {

    data class Sql(val sql: String, val args: List<Any>)

    fun toSql(translationId: String, limit: Int): Sql {
        val args = mutableListOf<Any>(translationId)
        val where = StringBuilder("translationId = ?")
        if (exactPhraseOnly) {
            where.append(" AND searchText LIKE ?")
            args += "% $phrase %"
        } else {
            for (term in terms) {
                where.append(" AND searchText LIKE ?")
                args += "% $term%"
            }
        }
        args += "% $phrase%"
        args += limit
        return Sql(
            "SELECT * FROM verses WHERE $where " +
                "ORDER BY CASE WHEN searchText LIKE ? THEN 0 ELSE 1 END, book, chapter, verse LIMIT ?",
            args,
        )
    }

    companion object {
        private val quotes = setOf('"', '“', '”')

        fun parse(raw: String): TextQuery? {
            val trimmed = raw.trim()
            val quoted = trimmed.length >= 2 && trimmed.first() in quotes && trimmed.last() in quotes
            val normalized = SearchText.normalize(trimmed)
            if (normalized.isBlank()) return null
            val terms = normalized.split(' ').filter { it.isNotBlank() }.distinct()
            return TextQuery(terms, normalized, exactPhraseOnly = quoted && terms.size > 1)
        }
    }
}
