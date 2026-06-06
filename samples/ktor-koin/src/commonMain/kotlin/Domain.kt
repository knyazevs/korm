@file:OptIn(ExperimentalUuidApi::class)

package io.github.knyazevs.korm.samples.ktorkoin

import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.Column
import io.github.knyazevs.korm.Entity
import io.github.knyazevs.korm.Table
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object AppCatalog : Catalog

object ProductTable : Table<AppCatalog, ProductEntity>(Meta("products"), ::ProductEntity) {
    val id by Column.UUID(primaryKey = true)
    val price by Column.Int()
    val payload by Column.Json()

    init { id; price; payload }
}

class ProductEntity(override var fields: MutableMap<String, Any?> = mutableMapOf()) : Entity(fields) {
    var id by ProductTable.id
    var price by ProductTable.price
    var payload by ProductTable.payload
}

@Serializable
data class ProductDTO(val id: Uuid? = null, val price: Int? = null, val payload: JsonElement? = null) {
    fun toDomain(): ProductEntity = ProductEntity().apply {
        id = this@ProductDTO.id
        price = this@ProductDTO.price
        payload = this@ProductDTO.payload
    }
}

fun ProductEntity.toDto() = ProductDTO(id, price, payload)
