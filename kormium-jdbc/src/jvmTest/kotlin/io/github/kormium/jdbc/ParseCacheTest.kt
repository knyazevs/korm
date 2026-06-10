package io.github.kormium.jdbc

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The `:name` parse cache must stay bounded: an LRU capped at
 * [NamedParamStatement.MAX_CACHE_ENTRIES], with SQL longer than
 * [NamedParamStatement.MAX_CACHEABLE_SQL_LENGTH] (batch INSERTs, large IN-lists — a
 * distinct string per call) bypassing it entirely.
 */
class ParseCacheTest {

    @Test
    fun cacheNeverExceedsItsCap() {
        repeat(NamedParamStatement.MAX_CACHE_ENTRIES * 2) { i ->
            NamedParamStatement.parseCached("SELECT * FROM t WHERE id = :p$i")
        }
        assertTrue(
            NamedParamStatement.parseCacheSize <= NamedParamStatement.MAX_CACHE_ENTRIES,
            "parse cache grew past its cap: ${NamedParamStatement.parseCacheSize}",
        )
    }

    @Test
    fun oversizedSqlBypassesTheCache() {
        val before = NamedParamStatement.parseCacheSize
        val hugeInList = (0 until 2000).joinToString(", ") { ":p$it" }
        NamedParamStatement.parseCached("INSERT INTO t (c) VALUES ($hugeInList)")
        assertTrue(
            NamedParamStatement.parseCacheSize == before,
            "oversized SQL must not be cached",
        )
    }
}
