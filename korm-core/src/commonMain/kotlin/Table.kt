package io.github.knyazevs.korm

import io.github.knyazevs.korm.resultset.ResultSet
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * A table definition: its [Meta] (name/schema) and columns, tagged with the catalog
 * [G] it belongs to. A table holds no connection — operations run inside a
 * [transaction] / [autocommit] scope (or [Scope]) which supplies the pinned
 * [SqlExecutor], or inside their suspend counterparts via [SuspendScope]. The
 * operation methods are `internal`; call them through [Scope] / [SuspendScope].
 *
 * Each operation is built in two parts: a pure `*Sql` helper that renders SQL +
 * params (no I/O), and a thin runner. The blocking runner (taking [SqlExecutor])
 * and the suspend runner (taking [SuspendSqlExecutor]) share the same `*Sql`
 * helper and differ only in how they execute it.
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

    private fun getColumnNames(dialect: Dialect): List<String> {
        logger.trace { "get column names" }
        return fieldDisplayName.map { dialect.quoteIdentifier(it.value.name) }
    }

    private fun qualifiedTableName(dialect: Dialect): String = qualifiedName(dialect)

    private fun paramBuilder(dialect: Dialect, typeMapper: TypeMapper) = ParamBuilder(dialect, typeMapper)

    // find/findById/all SELECT every column in fieldDisplayName order, so the result columns
    // line up positionally — read them by index straight into the entity's field map, with no
    // per-row name→index map or intermediate allocations.
    private fun mapToDao(rs: ResultSet, typeMapper: TypeMapper): T {
        val fields = HashMap<String, Any?>(fieldDisplayName.size * 2)
        var index = 0
        for ((fieldName, column) in fieldDisplayName) {
            fields[fieldName] = typeMapper.fromResult(rs, index, column.columnType)
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

    // ---- pure SQL builders (no I/O) — shared by the blocking and suspend runners ----

    private fun selectByIdSql(id: Any, dialect: Dialect, typeMapper: TypeMapper): Pair<String, Map<String, Any?>> {
        val pk = primaryKey.singleOrNull()
            ?: throw IllegalStateException(
                "findById requires a single-column primary key on ${meta.tableName}; " +
                    "use find(...) for composite (or missing) keys",
            )
        val builder = paramBuilder(dialect, typeMapper)
        val idPlaceholder = builder.bind(id)
        val sql = "SELECT ${getColumnNames(dialect).joinToString(", ")} FROM ${qualifiedTableName(dialect)} WHERE ${dialect.quoteIdentifier(pk.name)} = $idPlaceholder"
        return sql.trimIndent() to builder.params
    }

    private fun selectSql(query: Query, dialect: Dialect, typeMapper: TypeMapper): Pair<String, Map<String, Any?>> {
        val builder = paramBuilder(dialect, typeMapper)
        val queryStr = query.toSql(builder)
        val sql = "SELECT ${getColumnNames(dialect).joinToString(", ")} FROM ${qualifiedTableName(dialect)} $queryStr"
        return sql.trimIndent() to builder.params
    }

    private fun selectAllSql(dialect: Dialect): String =
        "SELECT ${getColumnNames(dialect).joinToString(", ")} FROM ${qualifiedTableName(dialect)}".trimIndent()

    private fun insertSql(entity: T, dialect: Dialect, typeMapper: TypeMapper, returning: Boolean): Pair<String, Map<String, Any?>> {
        val builder = paramBuilder(dialect, typeMapper)
        val generatedFields = generateFieldToMap(entity)
        val columns = generatedFields.joinToString(", ") { dialect.quoteIdentifier(it.first) }
        val values = generatedFields.joinToString(", ") { builder.bind(it.second) }
        val base = "INSERT INTO ${qualifiedTableName(dialect)} ($columns) VALUES ($values)"
        val sql = if (returning) "$base RETURNING ${getColumnNames(dialect).joinToString(", ")}" else base
        return sql to builder.params
    }

    private fun insertAllSql(entities: List<T>, dialect: Dialect, typeMapper: TypeMapper, returning: Boolean): Pair<String, Map<String, Any?>> {
        val builder = paramBuilder(dialect, typeMapper)
        val columns = fieldDisplayName.values.joinToString(", ") { dialect.quoteIdentifier(it.name) }
        val tuples = entities.joinToString(", ") { entity ->
            "(${generateFieldToMap(entity).joinToString(", ") { builder.bind(it.second) }})"
        }
        val base = "INSERT INTO ${qualifiedTableName(dialect)} ($columns) VALUES $tuples"
        val sql = if (returning) "$base RETURNING ${getColumnNames(dialect).joinToString(", ")}" else base
        return sql to builder.params
    }

    private fun countSql(query: Query, dialect: Dialect, typeMapper: TypeMapper): Pair<String, Map<String, Any?>> {
        val builder = paramBuilder(dialect, typeMapper)
        val queryStr = query.toSql(builder)
        val sql = "SELECT COUNT(*) FROM ${qualifiedTableName(dialect)} $queryStr"
        return sql.trimIndent() to builder.params
    }

    private fun updateSql(query: Query, entity: T, dialect: Dialect, typeMapper: TypeMapper): Pair<String, Map<String, Any?>> {
        val builder = paramBuilder(dialect, typeMapper)
        val updateFields = generatePresentFields(entity)
        require(updateFields.isNotEmpty()) {
            "update() needs at least one field set on the entity to update ${qualifiedTableName(dialect)}"
        }
        val generatedUpdateFields = updateFields
            .joinToString(", ") { "${dialect.quoteIdentifier(it.first)}=${builder.bind(it.second)}" }
        val queryStr = query.toSql(builder)
        val sql = """
            UPDATE ${qualifiedTableName(dialect)}
            SET $generatedUpdateFields
           $queryStr
        """
        return sql.trimIndent() to builder.params
    }

    private fun deleteSql(query: Query, dialect: Dialect, typeMapper: TypeMapper): Pair<String, Map<String, Any?>> {
        val builder = paramBuilder(dialect, typeMapper)
        val queryStr = query.toSql(builder)
        val sql = "DELETE FROM ${qualifiedTableName(dialect)} $queryStr"
        return sql.trimIndent() to builder.params
    }

    internal fun createTableSql(dialect: Dialect, ifNotExists: Boolean): String {
        val columns = fieldDisplayName.values.joinToString(",\n    ") { col ->
            val nullability = if (col.nullable) "" else " NOT NULL"
            "${dialect.quoteIdentifier(col.name)} ${dialect.sqlType(col.columnType)}$nullability"
        }
        val pkClause = primaryKey
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { dialect.quoteIdentifier(it.name) }
            ?.let { ",\n    PRIMARY KEY ($it)" }
            .orEmpty()
        val exists = if (ifNotExists) "IF NOT EXISTS " else ""
        return "CREATE TABLE $exists${qualifiedTableName(dialect)} (\n    $columns$pkClause\n)"
    }

    internal fun dropTableSql(dialect: Dialect, ifExists: Boolean): String =
        "DROP TABLE ${if (ifExists) "IF EXISTS " else ""}${qualifiedTableName(dialect)}"

    // ---- blocking runners (called by Scope) ----

    internal fun runRaw(sql: String, exec: SqlExecutor) {
        exec.execute(sql = sql.trimIndent())
    }

    internal fun selectById(id: Any, exec: SqlExecutor): T? {
        val (sql, params) = selectByIdSql(id, exec.dialect, exec.typeMapper)
        return exec.execute(sql, params) { rs -> mapToDao(rs, exec.typeMapper) }.firstOrNull()
    }

    internal fun select(query: Query, exec: SqlExecutor): List<T> {
        val (sql, params) = selectSql(query, exec.dialect, exec.typeMapper)
        return exec.execute(sql, params) { rs -> mapToDao(rs, exec.typeMapper) }
    }

    internal fun selectAll(exec: SqlExecutor): List<T> =
        exec.execute(selectAllSql(exec.dialect)) { rs -> mapToDao(rs, exec.typeMapper) }

    internal fun insert(entity: T, exec: SqlExecutor, returning: Boolean): T? {
        val (sql, params) = insertSql(entity, exec.dialect, exec.typeMapper, returning)
        if (!returning) {
            exec.executeUpdate(sql = sql, namedParameters = params)
            return entity
        }
        return exec.execute(sql, params) { rs -> mapToDao(rs, exec.typeMapper) }.firstOrNull()
    }

    internal fun insertAll(entities: List<T>, exec: SqlExecutor, returning: Boolean): List<T> {
        if (entities.isEmpty()) return emptyList()
        val (sql, params) = insertAllSql(entities, exec.dialect, exec.typeMapper, returning)
        if (!returning) {
            exec.executeUpdate(sql = sql, namedParameters = params)
            return entities
        }
        return exec.execute(sql, params) { rs -> mapToDao(rs, exec.typeMapper) }
    }

    internal fun count(query: Query, exec: SqlExecutor): Long {
        val (sql, params) = countSql(query, exec.dialect, exec.typeMapper)
        return exec.execute(sql, params) { rs -> rs.getLong(0) ?: 0L }.firstOrNull() ?: 0L
    }

    internal fun updateRows(query: Query, entity: T, exec: SqlExecutor) {
        val (sql, params) = updateSql(query, entity, exec.dialect, exec.typeMapper)
        exec.executeUpdate(sql = sql, namedParameters = params)
    }

    internal fun deleteRows(query: Query, exec: SqlExecutor) {
        val (sql, params) = deleteSql(query, exec.dialect, exec.typeMapper)
        exec.executeUpdate(sql = sql, namedParameters = params)
    }

    // ---- suspend runners (called by SuspendScope) — same *Sql helpers, suspend execution ----

    internal suspend fun runRaw(sql: String, exec: SuspendSqlExecutor) {
        exec.execute(sql = sql.trimIndent())
    }

    internal suspend fun selectById(id: Any, exec: SuspendSqlExecutor): T? {
        val (sql, params) = selectByIdSql(id, exec.dialect, exec.typeMapper)
        return exec.execute(sql, params) { rs -> mapToDao(rs, exec.typeMapper) }.firstOrNull()
    }

    internal suspend fun select(query: Query, exec: SuspendSqlExecutor): List<T> {
        val (sql, params) = selectSql(query, exec.dialect, exec.typeMapper)
        return exec.execute(sql, params) { rs -> mapToDao(rs, exec.typeMapper) }
    }

    internal suspend fun selectAll(exec: SuspendSqlExecutor): List<T> =
        exec.execute(selectAllSql(exec.dialect)) { rs -> mapToDao(rs, exec.typeMapper) }

    internal suspend fun insert(entity: T, exec: SuspendSqlExecutor, returning: Boolean): T? {
        val (sql, params) = insertSql(entity, exec.dialect, exec.typeMapper, returning)
        if (!returning) {
            exec.executeUpdate(sql = sql, namedParameters = params)
            return entity
        }
        return exec.execute(sql, params) { rs -> mapToDao(rs, exec.typeMapper) }.firstOrNull()
    }

    internal suspend fun insertAll(entities: List<T>, exec: SuspendSqlExecutor, returning: Boolean): List<T> {
        if (entities.isEmpty()) return emptyList()
        val (sql, params) = insertAllSql(entities, exec.dialect, exec.typeMapper, returning)
        if (!returning) {
            exec.executeUpdate(sql = sql, namedParameters = params)
            return entities
        }
        return exec.execute(sql, params) { rs -> mapToDao(rs, exec.typeMapper) }
    }

    internal suspend fun count(query: Query, exec: SuspendSqlExecutor): Long {
        val (sql, params) = countSql(query, exec.dialect, exec.typeMapper)
        return exec.execute(sql, params) { rs -> rs.getLong(0) ?: 0L }.firstOrNull() ?: 0L
    }

    internal suspend fun updateRows(query: Query, entity: T, exec: SuspendSqlExecutor) {
        val (sql, params) = updateSql(query, entity, exec.dialect, exec.typeMapper)
        exec.executeUpdate(sql = sql, namedParameters = params)
    }

    internal suspend fun deleteRows(query: Query, exec: SuspendSqlExecutor) {
        val (sql, params) = deleteSql(query, exec.dialect, exec.typeMapper)
        exec.executeUpdate(sql = sql, namedParameters = params)
    }

    /**
     * Table identity. [schema] is optional and defaults to `null`: an unqualified table
     * name resolves through the connection's default schema (Postgres `search_path`,
     * SQLite `main`, ...). Set it explicitly to pin a schema (rendered as
     * `"schema"."table"`); backends without schemas (SQLite) should leave it unset.
     */
    class Meta(val tableName: String, val schema: String? = null)
}
