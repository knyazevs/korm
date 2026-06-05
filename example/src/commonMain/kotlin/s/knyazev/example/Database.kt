package io.github.knyazevs.korm.example

import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.SqlParameterSource
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.database.createDatabase
import io.github.knyazevs.korm.resultset.ResultSet

object AppCatalog : Catalog

object Database: Database<AppCatalog> {
    private val driver = createDatabase(
        host = "localhost",
        port = 5432,
        user = "postgres",
        database = "postgres",
        password = "password",
    )

    override val dialect get() = driver.dialect
    override val typeMapper get() = driver.typeMapper

    override fun <T> execute(
        sql: String,
        namedParameters: Map<String, Any?>,
        handler: (ResultSet) -> T,
    ): List<T> = driver.execute(sql, namedParameters, handler)

    override fun <T> execute(
        sql: String,
        paramSource: SqlParameterSource,
        handler: (ResultSet) -> T,
    ): List<T> = driver.execute(sql, paramSource, handler)

    override fun execute(sql: String, namedParameters: Map<String, Any?>): Long = driver.execute(sql, namedParameters)

    override fun execute(sql: String, paramSource: SqlParameterSource): Long = driver.execute(sql, paramSource)

    override fun executeUpdate(
        sql: String,
        namedParameters: Map<String, Any?>,
    ) = driver.executeUpdate(sql, namedParameters)
}
