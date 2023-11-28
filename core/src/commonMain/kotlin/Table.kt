package s.knyazev

import s.knyazev.database.Database
import s.knyazev.resultset.ResultSet
import s.knyazev.sql.getJson
import s.knyazev.sql.getUUID


abstract class Table<T: Entity>(val meta: Meta, val factory: (MutableMap<String, Any?>) -> T, private val database: Database) {
    private val fieldDisplayName: MutableMap<String, Column<*, *, *>> = mutableMapOf()

    fun getFieldDisplayNames() = fieldDisplayName
    internal fun addColumn(fieldName: String, column: Column<*, *, *>) {
        println("add column/field ${column.name}/$fieldName")
        fieldDisplayName[fieldName] = column
    }

    private fun getColumnNames(): List<String> {
        println("get column names")
        return fieldDisplayName.map { it.value.name }
    }

    private fun columnMapToValue(rs: ResultSet): Map<String, Pair<Any, Any?>> {
        println("Column list: ${rs.columns.joinToString(", ")}")
        val columnMap = rs.columns.mapIndexed { index: Int, s: String -> s to index }.toMap()

        return fieldDisplayName.map {
            val columnNumber = columnMap[it.value.name]
            if (columnNumber == null  && !it.value.nullable) {
                throw Exception("Not nullable column \"${it.value.name}\" not found")
            }
            val strictColumnNumber = columnNumber!!


            println("Column[$columnNumber]: ${it.value.name}")
            val columnValue: Any? = when(it.value.columnType) {
                Column.ColumnNameEnum.UUID      ->  rs.getUUID(strictColumnNumber)
                Column.ColumnNameEnum.Double    ->  rs.getDouble(strictColumnNumber)
                Column.ColumnNameEnum.Int       ->  rs.getInt(strictColumnNumber)
                Column.ColumnNameEnum.Boolean   ->  rs.getBoolean(strictColumnNumber)
                Column.ColumnNameEnum.String    ->  rs.getString(strictColumnNumber)
                Column.ColumnNameEnum.Instant   ->  rs.getInstant(strictColumnNumber)
                Column.ColumnNameEnum.Json      ->  rs.getJson(strictColumnNumber)
            }
            it.key to Pair<Any, Any?>(it.value, columnValue)
        }.associateBy( {it.first}, {it.second})
    }

    private fun mapToDao(rs: ResultSet): T {
        val columnMapWithValue = columnMapToValue(rs)
        val fieldToValue: MutableMap<String, Any?> = columnMapWithValue.mapValues { v -> v.value.second }.toMutableMap()
        println("Map to dao done: ${fieldToValue.toMap().toString()}")
        return this.factory(fieldToValue)
    }

    private fun generateFieldToMap(dao: T): List<Pair<String, String>> {
        return this.fieldDisplayName.map {
            it.value.name to dao.fields[it.key].toString()
        }
    }

    fun execSql(sql: String) {
        database.execute(sql = sql.trimIndent())
    }

    fun findById(id: Any): T? {
        val query = Query(whereExpression = RawExpression("id='$id'"))
        val sql = "SELECT ${this.getColumnNames().joinToString ( ", " )} FROM ${this.meta.schema}.${this.meta.tableName} $query"
        return database.execute<T>(sql = sql.trimIndent()) { rs: ResultSet ->
            this.mapToDao(rs = rs)
        }.firstOrNull()
    }


    fun find(query: Query): List<T> {
        val sql = "SELECT ${this.getColumnNames().joinToString ( ", " )} FROM ${this.meta.schema}.${this.meta.tableName} $query"
        return database.execute(sql = sql.trimIndent()) { rs: ResultSet ->
            this.mapToDao(rs = rs)
        }
    }

    /*
    fun count(query: Query): List<T> {
        return PGDatabase.execute(sql = "SELECT COUNT(*) FROM ${this.meta.schema}.${this.meta.tableName}$query") { rs: ResultSet ->
            this.mapToDao(rs = rs)
        }
    }
     */

    fun all(): List<T> {
        val sql = "SELECT ${this.getColumnNames().joinToString ( ", " )} FROM ${this.meta.schema}.${this.meta.tableName}"
        return database.execute(sql = sql.trimIndent()) { rs: ResultSet ->
            this.mapToDao(rs = rs)
        }
    }

    fun new(entity: T) {
        val generatedFields = this.generateFieldToMap(entity)
        val columns = generatedFields.joinToString(", ") { "\"${it.first}\"" }
        val values = generatedFields.joinToString(", ") { "'${it.second}'" }

        val sql = """
            INSERT INTO ${this.meta.schema}.${this.meta.tableName}
            ($columns)
            VALUES($values);
        """
        println(sql)
        database.executeUpdate(sql = sql.trimIndent())
    }

    fun update(query: Query, entity: T) {
        val generatedUpdateFields = this.generateFieldToMap(entity).filter{ it.second != "null" }.joinToString(", ") {
            "\"${it.first}\"='${it.second}'"
        }
        val sql = """
            UPDATE ${this.meta.schema}.${this.meta.tableName}
            SET $generatedUpdateFields
           $query
        """
        database.executeUpdate(sql = sql.trimIndent())
    }

    fun deleteWhere(query: Query) {
        val sql = "DELETE FROM ${this.meta.schema}.${this.meta.tableName} $query"
        database.executeUpdate(sql = sql.trimIndent())
    }

    class Meta(val tableName: String, val schema: String = "public")
}
