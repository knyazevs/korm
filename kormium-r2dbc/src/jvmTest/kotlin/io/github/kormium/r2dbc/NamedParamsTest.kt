package io.github.kormium.r2dbc

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the `:name` -> positional-marker rewriter. No database needed — they assert the
 * parsed SQL text and the recovered parameter order directly. This parser is structurally shared
 * with the JDBC and Android SQLite parsers, so the comment/quote/cast cases below cover the same
 * logic in each. Most cases use [PostgresParamMarker] (`$N`); [rewritesToMysqlQuestionMarks] covers
 * the r2dbc-mysql `?` marker.
 */
class NamedParamsTest {

    @Test
    fun rewritesNamedParamsByOccurrence() {
        val parsed = parseNamedParams("SELECT * FROM t WHERE id = :id AND name = :name", PostgresParamMarker)
        assertEquals("SELECT * FROM t WHERE id = \$1 AND name = \$2", parsed.sql)
        assertEquals(listOf("id", "name"), parsed.names)
    }

    @Test
    fun rewritesToMysqlQuestionMarks() {
        val parsed = parseNamedParams("SELECT * FROM t WHERE id = :id AND name = :name", QuestionMarkParamMarker)
        assertEquals("SELECT * FROM t WHERE id = ? AND name = ?", parsed.sql)
        assertEquals(listOf("id", "name"), parsed.names)
    }

    @Test
    fun ignoresPlaceholderInLineComment() {
        // Regression (#7): ":debug" inside a -- comment must not become a parameter.
        val parsed = parseNamedParams("SELECT 1 -- TODO: remove :debug later\nWHERE id = :id", PostgresParamMarker)
        assertEquals(listOf("id"), parsed.names)
        assertEquals("SELECT 1 -- TODO: remove :debug later\nWHERE id = \$1", parsed.sql)
    }

    @Test
    fun ignoresPlaceholderInBlockComment() {
        // Regression (#7): ":skip" inside a /* */ comment must not become a parameter.
        val parsed = parseNamedParams("SELECT /* :skip me */ :id FROM t", PostgresParamMarker)
        assertEquals(listOf("id"), parsed.names)
        assertEquals("SELECT /* :skip me */ \$1 FROM t", parsed.sql)
    }

    @Test
    fun ignoresPlaceholderInQuotesAndCasts() {
        val parsed = parseNamedParams("SELECT ':notparam', x::text, :real FROM t", PostgresParamMarker)
        assertEquals(listOf("real"), parsed.names)
        assertEquals("SELECT ':notparam', x::text, \$1 FROM t", parsed.sql)
    }

    @Test
    fun ignoresPlaceholderAfterEscapedQuoteInLiteral() {
        // Regression (#47): a doubled '' is an escaped quote inside the string, so ':not_param'
        // stays part of the literal. Quote-toggling handles this correctly.
        val parsed = parseNamedParams("SELECT 'it''s :not_param' AS v", PostgresParamMarker)
        assertEquals(emptyList(), parsed.names)
        assertEquals("SELECT 'it''s :not_param' AS v", parsed.sql)
    }

    @Test
    fun handlesMultipleEscapedQuotesThenRealParam() {
        val parsed = parseNamedParams("SELECT 'a''b''c :x' AS v, :real FROM t", PostgresParamMarker)
        assertEquals(listOf("real"), parsed.names)
        assertEquals("SELECT 'a''b''c :x' AS v, \$1 FROM t", parsed.sql)
    }
}
