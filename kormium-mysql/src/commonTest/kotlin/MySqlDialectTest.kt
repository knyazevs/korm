import io.github.kormium.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Pure-function tests of [MySqlDialect]'s rendering — no database needed. These pin the
 * MySQL-specific SQL the write path depends on (backtick quoting, LIMIT/OFFSET, the INSERT family),
 * so a regression shows up here rather than only in the Testcontainers/native integration suites.
 */
class MySqlDialectTest {

    @Test
    fun quotesWithBackticks() {
        assertEquals("`name`", MySqlDialect.quoteIdentifier("name"))
        assertEquals("`a``b`", MySqlDialect.quoteIdentifier("a`b"))
    }

    @Test
    fun limitOffsetMysqlSemantics() {
        assertEquals("", MySqlDialect.renderLimitOffset(UInt.MAX_VALUE, 0u))
        assertEquals("LIMIT 10 ", MySqlDialect.renderLimitOffset(10u, 0u))
        assertEquals("LIMIT 10 OFFSET 5 ", MySqlDialect.renderLimitOffset(10u, 5u))
        // Offset without a real limit needs a sentinel LIMIT (a bare OFFSET is a syntax error).
        assertEquals("LIMIT 18446744073709551615 OFFSET 5 ", MySqlDialect.renderLimitOffset(UInt.MAX_VALUE, 5u))
    }

    @Test
    fun hasNoReturning() {
        assertFalse(MySqlDialect.supportsReturning)
    }

    @Test
    fun insertDefaultValuesMysqlForm() {
        assertEquals("INSERT INTO `t` () VALUES ()", MySqlDialect.renderInsertDefaultValues("`t`"))
    }

    @Test
    fun upsertUsesOnDuplicateKeyUpdate() {
        assertEquals(
            "ON DUPLICATE KEY UPDATE `qty` = :q",
            MySqlDialect.renderUpsertSuffix(listOf("`id`"), "`qty` = :q"),
        )
    }

    @Test
    fun insertOrIgnoreIsNoOpUpsert() {
        assertEquals(
            "ON DUPLICATE KEY UPDATE `id` = `id`",
            MySqlDialect.renderInsertOrIgnoreSuffix(listOf("`id`")),
        )
    }

    @Test
    fun noAdvisoryLock() {
        assertEquals(null, MySqlDialect.advisoryLockSql(42L))
    }
}
