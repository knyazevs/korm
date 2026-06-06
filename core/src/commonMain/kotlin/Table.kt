package io.github.knyazevs.korm

import io.github.knyazevs.korm.resultset.ResultSet
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * A table definition: its [Meta] (name/schema) and columns, tagged with the catalog
 * [G] it belongs to. A table holds no connection — operations run inside a
 * [transaction] / [autocommit] scope (or [Scope]) which supplies the pinned
 * [SqlExecutor]. The operation methods are `internal`; call them through [Scope].
 */
abstract class Table<G: Catalog, T: Entity>(val meta: Meta, val factory: (MutableMap<String, Any?>) -> T) {
    private val fieldDisplayName: MutableMap<String, Column<*, *, *>> = mutableMapOf()

    fun getFieldDisplayNames() = fieldDisplayName

    /**
     * The primary-key column(s): those declared with `primaryKey = true`, or the column
     * named "id" if none are marked.
     */
    val primaryKey: List<Column<*, *, *>>
        get() = fieldDisplayName.values.filter { it.isPrimaryKey }
            .ifEmpty { fieldDisplayName.values.filter { it.name == "id" } }
    internal fun addColumn(fieldName: String, column: Column<*, *, *>) {
        logger.trace { "add column/field ${column.name}/$fieldName" }
        fieldDisplayName[fieldName] = column
    }

    private fun getColumnNames(exec: SqlExecutor): List<String> {
        logger.trace { "get column names" }
        return fieldDisplayName.map { exec.dialect.quoteIdentifier(it.value.name) }
    }

    private fun qualifiedTableName(exec: SqlExecutor): String =
        "${exec.dialect.quoteIdentifier(meta.schema)}.${exec.dialect.quoteIdentifier(meta.tableName)}"

    private fun paramBuilder(exec: SqlExecutor) = ParamBuilder(exec.dialect, exec.typeMapper)

    // find/findById/all SELECT every column in fieldDisplayName order, so the result columns
    // line up positionally — read them by index straight into the entity's field map, with no
    // per-row name→index map or intermediate allocations.
    private fun mapToDao(rs: ResultSet, exec: SqlExecutor): T {
        val fields = HashMap<String, Any?>(fieldDisplayName.size * 2)
        var index = 0
        for ((fieldName, column) in fieldDisplayName) {
            fields[fieldName] = exec.typeMapper.fromResult(rs, index, column.columnType)
            index++
        }
        return factory(fields)
    }

    private fun generateFieldToMap(dao: T): List<Pair<String, Any?>> {
        return this.fieldDisplayName.map {
            it.value.name to dao.fields[it.key]
        }
    }

    // Only the columns the entity actually assigned (present in its fields map),
    // so update() can tell "leave untouched" (absent) from "set to NULL" (present and null).
    private fun generatePresentFields(dao: T): List<Pair<String, Any?>> {
        return this.fieldDisplayName.filter { dao.fields.containsKey(it.key) }.map {
            it.value.name to dao.fields[it.key]
        }
    }

    internal fun runRaw(sql: String, exec: SqlExecutor) {
        exec.execute(sql = sql.trimIndent())
    }

    internal fun selectById(id: Any, exec: SqlExecutor): T? {
        val pk = primaryKey.singleOrNull()
            ?: throw IllegalStateException(
                "findById requires a single-column primary key on ${meta.tableName}; " +
                    "use find(...) for composite (or missing) keys",
            )
        val builder = paramBuilder(exec)
        val idPlaceholder = builder.bind(id)
        val sql = "SELECT ${this.getColumnNames(exec).joinToString ( ", " )} FROM ${qualifiedTableName(exec)} WHERE ${exec.dialect.quoteIdentifier(pk.name)} = $idPlaceholder"
        return exec.execute<T>(sql = sql.trimIndent(), namedParameters = builder.params) { rs: ResultSet ->
            this.mapToDao(rs = rs, exec = exec)
        }.firstOrNull()
    }

    internal fun select(query: Query, exec: SqlExecutor): List<T> {
        val builder = paramBuilder(exec)
        val queryStr = query.toSql(builder)
        val sql = "SELECT ${this.getColumnNames(exec).joinToString ( ", " )} FROM ${qualifiedTableName(exec)} $queryStr"
        return exec.execute(sql = sql.trimIndent(), namedParameters = builder.params) { rs: ResultSet ->
            this.mapToDao(rs = rs, exec = exec)
        }
    }

    internal fun selectAll(exec: SqlExecutor): List<T> {
        val sql = "SELECT ${this.getColumnNames(exec).joinToString ( ", " )} FROM ${qualifiedTableName(exec)}"
        return exec.execute(sql = sql.trimIndent()) { rs: ResultSet ->
            this.mapToDao(rs = rs, exec = exec)
        }
    }

    internal fun insert(entity: T, exec: SqlExecutor): T? {
        val builder = paramBuilder(exec)
        val generatedFields = this.generateFieldToMap(entity)
        val columns = generatedFields.joinToString(", ") { exec.dialect.quoteIdentifier(it.first) }
        val values = generatedFields.joinToString(", ") { builder.bind(it.second) }

        val sql = """
            INSERT INTO ${qualifiedTableName(exec)}
            ($columns)
            VALUES($values)
            RETURNING ${getColumnNames(exec).joinToString(", ")};
        """
        return exec.execute(sql = sql.trimIndent(), namedParameters = builder.params) { rs ->
            this.mapToDao(rs = rs, exec = exec)
        }.firstOrNull()
    }

    internal fun insertAll(entities: List<T>, exec: SqlExecutor): List<T> {
        if (entities.isEmpty()) return emptyList()
        val builder = paramBuilder(exec)
        val columns = this.fieldDisplayName.values.joinToString(", ") { exec.dialect.quoteIdentifier(it.name) }
        val tuples = entities.joinToString(", ") { entity ->
            "(${generateFieldToMap(entity).joinToString(", ") { builder.bind(it.second) }})"
        }
        val sql = """
            INSERT INTO ${qualifiedTableName(exec)}
            ($columns)
            VALUES $tuples
            RETURNING ${getColumnNames(exec).joinToString(", ")};
        """
        return exec.execute(sql = sql.trimIndent(), namedParameters = builder.params) { rs ->
            this.mapToDao(rs = rs, exec = exec)
        }
    }

    internal fun count(query: Query, exec: SqlExecutor): Long {
        val builder = paramBuilder(exec)
        val queryStr = query.toSql(builder)
        val sql = "SELECT COUNT(*) FROM ${qualifiedTableName(exec)} $queryStr"
        return exec.execute(sql.trimIndent(), builder.params) { rs -> rs.getLong(0) ?: 0L }.firstOrNull() ?: 0L
    }

    internal fun createTableSql(exec: SqlExecutor, ifNotExists: Boolean): String {
        val columns = fieldDisplayName.values.joinToString(",\n    ") { col ->
            val nullability = if (col.nullable) "" else " NOT NULL"
            "${exec.dialect.quoteIdentifier(col.name)} ${exec.dialect.sqlType(col.columnType)}$nullability"
        }
        val pkClause = primaryKey
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { exec.dialect.quoteIdentifier(it.name) }
            ?.let { ",\n    PRIMARY KEY ($it)" }
            .orEmpty()
        val exists = if (ifNotExists) "IF NOT EXISTS " else ""
        return "CREATE TABLE $exists${qualifiedTableName(exec)} (\n    $columns$pkClause\n)"
    }

    internal fun dropTableSql(exec: SqlExecutor, ifExists: Boolean): String =
        "DROP TABLE ${if (ifExists) "IF EXISTS " else ""}${qualifiedTableName(exec)}"

    internal fun updateRows(query: Query, entity: T, exec: SqlExecutor) {
        val builder = paramBuilder(exec)
        val updateFields = this.generatePresentFields(entity)
        require(updateFields.isNotEmpty()) {
            "update() needs at least one field set on the entity to update ${qualifiedTableName(exec)}"
        }
        val generatedUpdateFields = updateFields
            .joinToString(", ") { "${exec.dialect.quoteIdentifier(it.first)}=${builder.bind(it.second)}" }
        val queryStr = query.toSql(builder)
        val sql = """
            UPDATE ${qualifiedTableName(exec)}
            SET $generatedUpdateFields
           $queryStr
        """
        exec.executeUpdate(sql = sql.trimIndent(), namedParameters = builder.params)
    }

    internal fun deleteRows(query: Query, exec: SqlExecutor) {
        val builder = paramBuilder(exec)
        val queryStr = query.toSql(builder)
        val sql = "DELETE FROM ${qualifiedTableName(exec)} $queryStr"
        exec.executeUpdate(sql = sql.trimIndent(), namedParameters = builder.params)
    }

    class Meta(val tableName: String, val schema: String = "public")
}
