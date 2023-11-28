package s.knyazev.sql

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.uuid.UUID
import s.knyazev.resultset.ResultSet

fun ResultSet.getUUID(columnIndex: Int): UUID? {
    return getString(columnIndex)?.let { UUID(it) }
}

fun ResultSet.getBigDecimal(columnIndex: Int): BigDecimal? {
    return getString(columnIndex)?.let { BigDecimal.parseString(it) }
}

fun ResultSet.getInstant(columnIndex: Int): Instant? {
    return getString(columnIndex)?.let { it.toInstant() }
}

fun ResultSet.getJson(columnIndex: Int): JsonElement? {
    return getString(columnIndex)?.let { Json.parseToJsonElement(it) }
}
