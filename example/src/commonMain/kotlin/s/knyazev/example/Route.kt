package io.github.knyazevs.korm.example

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import io.github.knyazevs.korm.Query
import io.github.knyazevs.korm.autocommit
import io.github.knyazevs.korm.eq
import io.github.knyazevs.korm.transaction
import io.github.knyazevs.korm.example.product.ProductDTO
import io.github.knyazevs.korm.example.product.ProductTable
import io.github.knyazevs.korm.example.product.toDto

fun Application.configureRouting() {
    routing {
        get("/") {
            val result = Database.autocommit { ProductTable.all() }
            val resultDTO = result.map { it.toDto() }
            call.respond(resultDTO)
        }

        put("/create") {
            val createDto = call.receive<ProductDTO>()
            val create = createDto.toDomain().apply {
                id = Uuid.random()
            }
            // new() returns the stored row (via RETURNING), so no follow-up read is needed.
            val result = Database.transaction { ProductTable.new(create) }
            val resultDTO = result!!.toDto()
            call.respond(resultDTO)
        }

        post("/update") {
            val productId = Uuid.parse(call.parameters["id"].orEmpty())
            val createDto = call.receive<ProductDTO>()
            val update = createDto.toDomain()
            val result = Database.transaction {
                ProductTable.update(Query(ProductTable.id eq productId.toString()), update)
                ProductTable.findById(productId)
            }
            val resultDTO = result!!.toDto()
            call.respond(resultDTO)
        }

        delete("/delete") {
            val productId = Uuid.parse(call.parameters["id"].orEmpty())
            Database.transaction {
                ProductTable.deleteWhere(Query(ProductTable.id eq productId.toString()))
            }
            call.respond(true)
        }
    }
}
