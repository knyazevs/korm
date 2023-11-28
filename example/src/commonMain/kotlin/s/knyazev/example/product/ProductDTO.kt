package s.knyazev.example.product

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.uuid.UUID

@Serializable
data class ProductDTO(val id: UUID? = null, val price: Int? = null, val payload: JsonElement?) {
    fun toDomain(): ProductEntity {
        val dto = this
        return ProductEntity().apply {
            id = dto.id
            price = dto.price
            payload = dto.payload
        }
    }
}

fun ProductEntity.toDto() = ProductDTO(this.id, this.price, this.payload)
