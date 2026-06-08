package io.github.knyazevs.korm.r2dbc

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the `:name` -> `$N` rewriter. No database needed — they assert the parsed
 * SQL text and the recovered parameter order directly. This parser is structurally shared
 * with the JDBC and Android SQLite parsers, so the comment/quote/cast cases below cover the
 * same logic in each.
 */
class NamedParamsTest {

    @Test
    fun rewritesNamedParamsByOccurrence() {
        val parsed = parseNamedParams("SELECT * FROM t WHERE id = :id AND name = :name")
        assertEquals("SELECT * FROM t WHERE id = \$1 AND name = \$2", parsed.sql)
        assertEquals(listOf("id", "name"), parsed.names)
    }

    @Test
    fun ignoresPlaceholderInLineComment() {
        // Regression (#7): ":debug" inside a -- comment must not become a parameter.
        val parsed = parseNamedParams("SELECT 1 -- TODO: remove :debug later\nWHERE id = :id")
        assertEquals(listOf("id"), parsed.names)
        assertEquals("SELECT 1 -- TODO: remove :debug later\nWHERE id = \$1", parsed.sql)
    }

    @Test
    fun ignoresPlaceholderInBlockComment() {
        // Regression (#7): ":skip" inside a /* */ comment must not become a parameter.
        val parsed = parseNamedParams("SELECT /* :skip me */ :id FROM t")
        assertEquals(listOf("id"), parsed.names)
        assertEquals("SELECT /* :skip me */ \$1 FROM t", parsed.sql)
    }

    @Test
    fun ignoresPlaceholderInQuotesAndCasts() {
        val parsed = parseNamedParams("SELECT ':notparam', x::text, :real FROM t")
        assertEquals(listOf("real"), parsed.names)
        assertEquals("SELECT ':notparam', x::text, \$1 FROM t", parsed.sql)
    }
}
