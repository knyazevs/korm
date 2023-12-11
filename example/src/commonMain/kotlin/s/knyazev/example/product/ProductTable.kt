package com.github.knyazevs.korm.example.product

import com.github.knyazevs.korm.Column
import com.github.knyazevs.korm.Table
import com.github.knyazevs.korm.example.Database

object ProductTable: Table<ProductEntity>(Meta("products"), ::ProductEntity, Database) {
    val id by Column.UUID()
    val price by Column.Int()
    val payload by Column.Json()

    init {
        id;price;payload
    }
}
