@file:OptIn(ExperimentalUuidApi::class)

package io.github.kormium.samples.ktordi

import io.github.kormium.Catalog
import io.github.kormium.Column
import io.github.kormium.Entity
import io.github.kormium.Table
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A [Catalog] is a marker type for a logical database; it pins tables and the database together. */
object AppCatalog : Catalog

object ProductTable : Table<AppCatalog, ProductEntity>("products", ::ProductEntity) {
    val id by Column.UUID().primaryKey()
    val price by Column.Int()
    val payload by Column.Json()
}

class ProductEntity : Entity() {
    var id by ProductTable.id
    var price by ProductTable.price
    var payload by ProductTable.payload
}

@Serializable
data class ProductDTO(val id: Uuid? = null, val price: Int? = null, val payload: JsonElement? = null) {
    fun toDomain(): ProductEntity = ProductEntity().apply {
        this@ProductDTO.id?.let { id = it }
        this@ProductDTO.price?.let { price = it }
        this@ProductDTO.payload?.let { payload = it }
    }
}

fun ProductEntity.toDto() = ProductDTO(id, price, payload)

internal val productTableDdl = """CREATE TABLE IF NOT EXISTS "products" ("id" uuid NOT NULL, "price" integer NOT NULL, "payload" jsonb NOT NULL, PRIMARY KEY ("id"))"""
