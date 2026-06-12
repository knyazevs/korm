package io.github.kormium.sql

import com.ionspin.kotlin.bignum.decimal.BigDecimal

// 18 digits always fit in a Long (Long.MAX_VALUE has 19 digits).
private const val MAX_FAST_DIGITS = 18

/**
 * Parses plain decimal text — the only form Postgres emits for `numeric` — straight into
 * ionspin's (significand, exponent) representation. BigDecimal.parseString runs per-digit
 * bignum arithmetic and dominates row-materialization profiles; this path is a single Long
 * accumulation. Anything that isn't a plain decimal with at most [MAX_FAST_DIGITS] digits
 * (scientific notation, NaN, huge values) falls back to parseString.
 */
internal fun parseBigDecimalFast(text: String): BigDecimal {
    val negative = text.startsWith('-')
    val start = if (negative) 1 else 0
    var unscaled = 0L
    var scale = 0
    var digitChars = 0
    var seenDot = false
    var fracNonZero = false
    for (i in start until text.length) {
        val c = text[i]
        when {
            c in '0'..'9' -> {
                if (digitChars == MAX_FAST_DIGITS) return BigDecimal.parseString(text)
                unscaled = unscaled * 10 + (c - '0')
                digitChars++
                if (seenDot) {
                    scale++
                    if (c != '0') fracNonZero = true
                }
            }
            // A '.' needs digits on both sides ("1.", ".5" take the reference path).
            c == '.' && !seenDot && digitChars > 0 -> seenDot = true
            else -> return BigDecimal.parseString(text)
        }
    }
    if (digitChars == 0 || (seenDot && scale == 0)) return BigDecimal.parseString(text)
    if (unscaled == 0L) return BigDecimal.ZERO
    // Mirror parseString's representation quirk: fractional trailing zeros are dropped
    // ("10.50" -> significand 105) unless the whole fraction is zeros, which is kept
    // verbatim ("1.0" -> 10). The exponent is unaffected either way (each dropped zero
    // lowers the digit count and the scale together).
    if (fracNonZero) {
        while (unscaled % 10L == 0L && scale > 0) {
            unscaled /= 10L
            scale--
        }
    }
    if (negative) unscaled = -unscaled
    // value = unscaled * 10^-scale; ionspin stores the scientific exponent (d.ddd * 10^e).
    val exponent = (digitsOf(unscaled) - 1 - scale).toLong()
    return BigDecimal.fromLongWithExponent(unscaled, exponent)
}

/**
 * Renders a BigDecimal as a parameter string without ionspin's toString (repeated bignum
 * division by ten). Produces `<significand>E<exponent>` in standard scientific notation —
 * accepted by every backend that takes decimal text — or a plain integer string when the
 * exponent works out to zero. Significands beyond Long fall back to toString().
 */
internal fun bigDecimalToParamString(value: BigDecimal): String {
    if (value.isZero()) return "0"
    val significand = value.significand
    if (significand.bitLength() > 62) return value.toString()
    val sigLong = significand.longValue(exactRequired = false)
    // ionspin's exponent is scientific (d.ddd * 10^e); standard E-notation is mantissa * 10^e.
    val standardExponent = value.exponent - (digitsOf(sigLong) - 1)
    return if (standardExponent == 0L) sigLong.toString() else "${sigLong}E$standardExponent"
}

private fun digitsOf(value: Long): Int {
    var v = if (value < 0) -value else value
    var digits = 1
    while (v >= 10) {
        v /= 10
        digits++
    }
    return digits
}
