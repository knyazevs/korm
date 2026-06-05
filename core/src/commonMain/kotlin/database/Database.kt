package io.github.knyazevs.korm.database

import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.Dialect
import io.github.knyazevs.korm.SqlParameterSource
import io.github.knyazevs.korm.TypeMapper
import io.github.knyazevs.korm.resultset.ResultSet


/**
 * A database handle, tagged with the [Catalog] [G] it connects to. The tag is
 * phantom (it appears in no member), so a backend driver implements
 * `Database<Nothing>` and — by covariance — fits any `Database<G>`; a caller pins
 * the tag by assigning it to a `Database<MyCatalog>`. A [io.github.knyazevs.korm.Table]
 * tagged with the same catalog can then be bound to it.
 */
interface Database<out G : Catalog> {
    /** How this database renders SQL (identifier quoting, bind placeholders, ...). */
    val dialect: Dialect

    /** How this database converts column values to/from the driver's wire form. */
    val typeMapper: TypeMapper

    fun <T> execute(sql: String, namedParameters: Map<String, Any?> = emptyMap(), handler: (ResultSet) -> T): List<T>
    fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T>
    fun execute(sql: String, namedParameters: Map<String, Any?> = emptyMap()): Long
    fun execute(sql: String, paramSource: SqlParameterSource): Long

    fun executeUpdate(sql: String, namedParameters: Map<String, Any?> = emptyMap())
}
