import io.github.moreirasantos.pgkn.resultset.parsePgLong
import kotlinx.cinterop.*
import kotlin.test.Test
import kotlin.test.assertEquals

/** Allocation-free integer parsing straight from a libpq C string. No database needed. */
@OptIn(ExperimentalForeignApi::class)
class PgNumericTest {

    private fun parse(text: String): Long = memScoped { parsePgLong(text.cstr.getPointer(this)) }

    @Test
    fun parsesIntegers() {
        assertEquals(0L, parse("0"))
        assertEquals(7L, parse("7"))
        assertEquals(123456789L, parse("123456789"))
    }

    @Test
    fun parsesSigns() {
        assertEquals(-42L, parse("-42"))
        assertEquals(42L, parse("+42"))
    }

    @Test
    fun parsesLongBounds() {
        assertEquals(Long.MAX_VALUE, parse("9223372036854775807"))
        // Long.MIN_VALUE has no positive counterpart — the negative accumulator must still get it.
        assertEquals(Long.MIN_VALUE, parse("-9223372036854775808"))
    }
}
