package io.github.knyazevs.korm.example

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import io.github.knyazevs.korm.Query
import io.github.knyazevs.korm.eq
import io.github.knyazevs.korm.suspendAutocommit
import io.github.knyazevs.korm.suspendTransaction
import io.github.knyazevs.korm.example.product.ProductDTO
import io.github.knyazevs.korm.example.product.ProductTable
import io.github.knyazevs.korm.example.product.toDto

fun Application.configureRouting() {
    routing {
        get("/") {
            val result = Database.suspendAutocommit { ProductTable.all() }
            val resultDTO = result.map { it.toDto() }
            call.respond(resultDTO)
        }

        put("/create") {
            val createDto = call.receive<ProductDTO>()
            val create = createDto.toDomain().apply {
                id = Uuid.random()
            }
            // returning = true fetches the stored row back (RETURNING), so no follow-up read.
            val result = Database.suspendTransaction { ProductTable.new(create, returning = true) }
            val resultDTO = result!!.toDto()
            call.respond(resultDTO)
        }

        post("/update") {
            val productId = Uuid.parse(call.parameters["id"].orEmpty())
            val createDto = call.receive<ProductDTO>()
            val update = createDto.toDomain()
            val result = Database.suspendTransaction {
                ProductTable.update(Query(ProductTable.id eq productId), update)
                ProductTable.findById(productId)
            }
            val resultDTO = result!!.toDto()
            call.respond(resultDTO)
        }

        delete("/delete") {
            val productId = Uuid.parse(call.parameters["id"].orEmpty())
            Database.suspendTransaction {
                ProductTable.deleteWhere(Query(ProductTable.id eq productId))
            }
            call.respond(true)
        }
    }
}
