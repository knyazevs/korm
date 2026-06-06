package io.github.knyazevs.korm.sql

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.uuid.Uuid
import io.github.knyazevs.korm.resultset.ResultSet

fun ResultSet.getUUID(columnIndex: Int): Uuid? {
    return getString(columnIndex)?.let { Uuid.parse(it) }
}

fun ResultSet.getBigDecimal(columnIndex: Int): BigDecimal? {
    return getString(columnIndex)?.let { BigDecimal.parseString(it) }
}

// Note: ResultSet already provides getInstant() as a member (with the required
// Postgres "space -> T" ISO-8601 fix), so no extension is defined here.

fun ResultSet.getJson(columnIndex: Int): JsonElement? {
    return getString(columnIndex)?.let { Json.parseToJsonElement(it) }
}
