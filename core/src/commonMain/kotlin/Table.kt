package io.github.knyazevs.korm

import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.resultset.ResultSet
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

abstract class Table<T: Entity>(val meta: Meta, val factory: (MutableMap<String, Any?>) -> T, private val database: Database) {
    private val fieldDisplayName: MutableMap<String, Column<*, *, *>> = mutableMapOf()

    fun getFieldDisplayNames() = fieldDisplayName
    internal fun addColumn(fieldName: String, column: Column<*, *, *>) {
        logger.trace { "add column/field ${column.name}/$fieldName" }
        fieldDisplayName[fieldName] = column
    }

    private fun getColumnNames(): List<String> {
        logger.trace { "get column names" }
        return fieldDisplayName.map { database.dialect.quoteIdentifier(it.value.name) }
    }

    private fun qualifiedTableName(): String =
        "${database.dialect.quoteIdentifier(meta.schema)}.${database.dialect.quoteIdentifier(meta.tableName)}"

    private fun paramBuilder() = ParamBuilder(database.dialect, database.typeMapper)

    private fun columnMapToValue(rs: ResultSet): Map<String, Pair<Any, Any?>> {
        logger.trace { "Column list: ${rs.columns.joinToString(", ")}" }
        val columnMap = rs.columns.mapIndexed { index: Int, s: String -> s to index }.toMap()

        return fieldDisplayName.map {
            val columnNumber = columnMap[it.value.name]
            logger.trace { "Column[$columnNumber]: ${it.value.name}" }
            val columnValue: Any? = if (columnNumber == null) {
                if (!it.value.nullable) {
                    throw Exception("Not nullable column \"${it.value.name}\" not found")
                }
                null
            } else {
                database.typeMapper.fromResult(rs, columnNumber, it.value.columnType)
            }
            it.key to Pair<Any, Any?>(it.value, columnValue)
        }.associateBy( {it.first}, {it.second})
    }

    private fun mapToDao(rs: ResultSet): T {
        val columnMapWithValue = columnMapToValue(rs)
        val fieldToValue: MutableMap<String, Any?> = columnMapWithValue.mapValues { v -> v.value.second }.toMutableMap()
        logger.trace { "Map to dao done: ${fieldToValue.toMap()}" }
        return this.factory(fieldToValue)
    }

    private fun generateFieldToMap(dao: T): List<Pair<String, Any?>> {
        return this.fieldDisplayName.map {
            it.value.name to dao.fields[it.key]
        }
    }

    fun execSql(sql: String) {
        database.execute(sql = sql.trimIndent())
    }

    fun findById(id: Any): T? {
        val builder = paramBuilder()
        val idPlaceholder = builder.bind(id)
        val sql = "SELECT ${this.getColumnNames().joinToString ( ", " )} FROM ${qualifiedTableName()} WHERE ${database.dialect.quoteIdentifier("id")} = $idPlaceholder"
        return database.execute<T>(sql = sql.trimIndent(), namedParameters = builder.params) { rs: ResultSet ->
            this.mapToDao(rs = rs)
        }.firstOrNull()
    }


    fun find(query: Query): List<T> {
        val builder = paramBuilder()
        val queryStr = query.toSql(builder)
        val sql = "SELECT ${this.getColumnNames().joinToString ( ", " )} FROM ${qualifiedTableName()} $queryStr"
        return database.execute(sql = sql.trimIndent(), namedParameters = builder.params) { rs: ResultSet ->
            this.mapToDao(rs = rs)
        }
    }

    fun all(): List<T> {
        val sql = "SELECT ${this.getColumnNames().joinToString ( ", " )} FROM ${qualifiedTableName()}"
        return database.execute(sql = sql.trimIndent()) { rs: ResultSet ->
            this.mapToDao(rs = rs)
        }
    }

    fun new(entity: T) {
        val builder = paramBuilder()
        val generatedFields = this.generateFieldToMap(entity)
        val columns = generatedFields.joinToString(", ") { database.dialect.quoteIdentifier(it.first) }
        val values = generatedFields.joinToString(", ") { builder.bind(it.second) }

        val sql = """
            INSERT INTO ${qualifiedTableName()}
            ($columns)
            VALUES($values);
        """
        database.executeUpdate(sql = sql.trimIndent(), namedParameters = builder.params)
    }

    fun update(query: Query, entity: T) {
        val builder = paramBuilder()
        val updateFields = this.generateFieldToMap(entity).filter { it.second != null }
        require(updateFields.isNotEmpty()) {
            "update() needs at least one non-null field to set on ${qualifiedTableName()}"
        }
        val generatedUpdateFields = updateFields
            .joinToString(", ") { "${database.dialect.quoteIdentifier(it.first)}=${builder.bind(it.second)}" }
        val queryStr = query.toSql(builder)
        val sql = """
            UPDATE ${qualifiedTableName()}
            SET $generatedUpdateFields
           $queryStr
        """
        database.executeUpdate(sql = sql.trimIndent(), namedParameters = builder.params)
    }

    fun deleteWhere(query: Query) {
        val builder = paramBuilder()
        val queryStr = query.toSql(builder)
        val sql = "DELETE FROM ${qualifiedTableName()} $queryStr"
        database.executeUpdate(sql = sql.trimIndent(), namedParameters = builder.params)
    }

    class Meta(val tableName: String, val schema: String = "public")
}
