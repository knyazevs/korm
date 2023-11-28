package s.knyazev.example.product

import s.knyazev.Column
import s.knyazev.Table
import s.knyazev.example.Database

object ProductTable: Table<ProductEntity>(Meta("products"), ::ProductEntity, Database) {
    val id by Column.UUID()
    val price by Column.Int()
    val payload by Column.Json()

    init {
        id;price;payload
    }
}
