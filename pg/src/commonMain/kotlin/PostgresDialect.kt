package io.github.knyazevs.korm

import kotlin.uuid.Uuid

/**
 * Postgres dialect: standard SQL rendering plus a `::uuid` cast on UUID binds, so a
 * text-bound UUID is interpreted with the right type by the server.
 */
object PostgresDialect : Dialect by StandardDialect {
    override fun renderBind(name: String, value: Any?): String =
        if (value is Uuid) ":$name::uuid" else StandardDialect.renderBind(name, value)
}
