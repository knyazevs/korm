package com.github.knyazevs.korm.example.product

import com.github.knyazevs.korm.Entity

class ProductEntity(override var fields: MutableMap<String, Any?> = mutableMapOf()): Entity(fields) {
    var id by ProductTable.id
    var price by ProductTable.price
    var payload by ProductTable.payload
}
