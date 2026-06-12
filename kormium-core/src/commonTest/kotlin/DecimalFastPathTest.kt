import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.github.kormium.sql.bigDecimalToParamString
import io.github.kormium.sql.parseBigDecimalFast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecimalFastPathTest {

    private val plainDecimals = listOf(
        "0", "0.0", "0.00",
        "1", "-1", "5", "-5",
        "10", "100", "1000000",
        "0.5", "0.50", "-0.5", "0.0050", "-0.0050",
        "123.45", "-123.45", "1.0", "-1.0",
        "999999999999999999",            // 18 digits — the fast-path limit
        "99999999999999999.9",
        "0.000000000000000001",
        "42", "1234.5678", "-1234.5678",
        "007", "00.50", "0.000", "10.50", "1.50",
    )

    private val fallbackOnly = listOf(
        "1000000000000000000",           // 19 digits — beyond the fast path
        "-1000000000000000000",
        "123456789012345678901234567890.5",
        "1E5", "1.5e-3", "-2.5E+10",     // scientific input never comes from PG but must still parse
    )

    /** The fast path must be indistinguishable from parseString: same value, same representation. */
    @Test
    fun fastParseMatchesParseStringExactly() {
        for (s in plainDecimals + fallbackOnly) {
            val reference = BigDecimal.parseString(s)
            val fast = parseBigDecimalFast(s)
            assertEquals(0, reference.compareTo(fast), "value mismatch for '$s': $reference vs $fast")
            assertEquals(reference.significand, fast.significand, "significand mismatch for '$s'")
            assertEquals(reference.exponent, fast.exponent, "exponent mismatch for '$s'")
            assertEquals(reference.toString(), fast.toString(), "toString mismatch for '$s'")
        }
    }

    /** Serialized form must parse back (via ionspin's own parser) to the same value. */
    @Test
    fun paramStringRoundTrips() {
        for (s in plainDecimals + fallbackOnly) {
            val value = BigDecimal.parseString(s)
            val rendered = bigDecimalToParamString(value)
            assertEquals(
                0,
                value.compareTo(BigDecimal.parseString(rendered)),
                "round-trip mismatch for '$s': rendered as '$rendered'",
            )
        }
    }

    /** Whole numbers in Long range render as plain integers (no E suffix to surprise logs/tests). */
    @Test
    fun wholeNumbersRenderPlain() {
        assertEquals("42", bigDecimalToParamString(BigDecimal.fromInt(42)))
        assertEquals("-42", bigDecimalToParamString(BigDecimal.fromInt(-42)))
        assertEquals("0", bigDecimalToParamString(BigDecimal.ZERO))
    }

    /** Values whose significand exceeds Long must take the toString fallback unchanged. */
    @Test
    fun hugeSignificandFallsBackToToString() {
        val huge = BigDecimal.parseString("123456789012345678901234567890.5")
        assertEquals(huge.toString(), bigDecimalToParamString(huge))
        assertTrue(huge.significand.bitLength() > 62)
    }
}
