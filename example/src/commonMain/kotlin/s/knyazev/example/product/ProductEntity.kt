package s.knyazev.example.product

import s.knyazev.Entity

class ProductEntity(override var fields: MutableMap<String, Any?> = mutableMapOf()): Entity(fields) {
    var id by ProductTable.id
    var price by ProductTable.price
    var payload by ProductTable.payload
}
