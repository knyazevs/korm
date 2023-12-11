package io.github.knyazevs.korm.example.product

import io.github.knyazevs.korm.Column
import io.github.knyazevs.korm.Table
import io.github.knyazevs.korm.example.Database

object ProductTable: Table<ProductEntity>(Meta("products"), ::ProductEntity, Database) {
    val id by Column.UUID()
    val price by Column.Int()
    val payload by Column.Json()

    init {
        id;price;payload
    }
}
