package s.knyazev.example

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import s.knyazev.Query
import s.knyazev.eq
import s.knyazev.example.product.ProductDTO
import s.knyazev.example.product.ProductTable
import s.knyazev.example.product.toDto

fun Application.configureRouting() {
    routing {
        get("/") {
            val result = ProductTable.all()
            val resultDTO = result.map { it.toDto() }
            call.respond(resultDTO)
        }

        put("/create") {
            val createDto = call.receive<ProductDTO>()
            val create = createDto.toDomain().apply {
                id = UUID.generateUUID()
            }
            ProductTable.new(create)
            val result = ProductTable.findById(create.id!!)
            val resultDTO = result!!.toDto()
            call.respond(resultDTO)
        }

        post("/update") {
            val productId = UUID(call.parameters["id"].orEmpty())
            val createDto = call.receive<ProductDTO>()
            val update = createDto.toDomain()
            ProductTable.update(Query(ProductTable.id eq productId.toString()), update)
            val result = ProductTable.findById(productId)
            val resultDTO = result!!.toDto()
            call.respond(resultDTO)
        }

        delete("/delete") {
            val productId = UUID(call.parameters["id"].orEmpty())
            ProductTable.deleteWhere(Query(ProductTable.id eq productId.toString()))
            call.respond(true)
        }
    }
}
