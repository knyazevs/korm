package io.github.knyazevs.korm

import io.github.knyazevs.korm.resultset.ResultSet
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KProperty

private val logger = KotlinLogging.logger {}


sealed class Column<Z, T: Table<*, N>, N: Entity>(private val table: T, open var name: String, open var nullable: kotlin.Boolean = false, val columnType: ColumnNameEnum):
    Expression, Selectable<Z> {
    operator fun getValue(n: N, property: KProperty<*>): Z? {
        logger.trace { "Get value $name" }
        return n.fields[name] as Z?
    }

    operator fun setValue(n: N, property: KProperty<*>, z: Z?){
        logger.trace { "Set value $name" }
        n.fields[name] = z
    }

    open fun init() {
        logger.trace { "init column ${this.name}" }
        table.addColumn(this.name, this)
    }
    override fun toString(): String {
        return name
    }

    // A column renders to its (quoted) identifier in SQL, never as a bind parameter.
    // Inside a join it is qualified by its table so `users.id` and `orders.id` differ.
    override fun toSql(builder: ParamBuilder): String =
        if (builder.qualifyColumns) "${builder.dialect.quoteIdentifier(table.meta.tableName)}.${builder.dialect.quoteIdentifier(name)}"
        else builder.dialect.quoteIdentifier(name)

    internal val tableRef: Table<*, *> get() = table

    /** Whether this column is part of the table's primary key. */
    internal var isPrimaryKey: kotlin.Boolean = false

    // As a selectable, read its value via the type mapper (its toSql is the SELECT SQL).
    @Suppress("UNCHECKED_CAST")
    override fun read(rs: ResultSet, index: kotlin.Int, typeMapper: TypeMapper): Z? =
        typeMapper.fromResult(rs, index, columnType) as Z?

    class UUIDType<T: Table<*, N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<kotlin.uuid.Uuid, T, N>(table, name, nullable, ColumnNameEnum.UUID)
    class BigDecimalType<T: Table<*, N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<com.ionspin.kotlin.bignum.decimal.BigDecimal, T, N>(table, name, nullable, ColumnNameEnum.BigDecimal)
    class DoubleType<T: Table<*, N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<kotlin.Double, T, N>(table, name, nullable, ColumnNameEnum.Double)
    class IntType<T: Table<*, N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<kotlin.Int, T, N>(table, name, nullable, ColumnNameEnum.Int)
    class BooleanType<T: Table<*, N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<kotlin.Boolean, T, N>(table, name, nullable, ColumnNameEnum.Boolean)
    class TextType<T: Table<*, N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<kotlin.String, T, N>(table, name, nullable, ColumnNameEnum.String)
    class InstantType<T: Table<*, N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<kotlinx.datetime.Instant, T, N>(table, name, nullable, ColumnNameEnum.Instant)
    class JsonType<T: Table<*, N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<JsonElement, T, N>(table, name, nullable, ColumnNameEnum.Json)

    enum class ColumnNameEnum {
        BigDecimal,
        UUID,
        Double,
        Int,
        Boolean,
        String,
        Instant,
        Json
    }

    private companion object {
        fun <T: Table<*, N>, N: Entity> double(table: T, name: String, nullable: kotlin.Boolean, primaryKey: kotlin.Boolean): DoubleType<T, N>  = Column.DoubleType(table, name, nullable).also { it.isPrimaryKey = primaryKey; it.init() }
        fun <T: Table<*, N>, N: Entity> bigDecimal(table: T, name: String, nullable: kotlin.Boolean, primaryKey: kotlin.Boolean): BigDecimalType<T, N> = Column.BigDecimalType(table, name, nullable).also { it.isPrimaryKey = primaryKey; it.init() }
        fun <T: Table<*, N>, N: Entity> uuid(table: T, name: String, nullable: kotlin.Boolean, primaryKey: kotlin.Boolean): UUIDType<T, N> = Column.UUIDType(table, name, nullable).also { it.isPrimaryKey = primaryKey; it.init() }
        fun <T: Table<*, N>, N: Entity> int(table: T, name: String, nullable: kotlin.Boolean, primaryKey: kotlin.Boolean): IntType<T, N> = Column.IntType(table, name, nullable).also { it.isPrimaryKey = primaryKey; it.init() }
        fun <T: Table<*, N>, N: Entity> boolean(table: T, name: String, nullable: kotlin.Boolean, primaryKey: kotlin.Boolean): BooleanType<T, N> = Column.BooleanType(table, name, nullable).also { it.isPrimaryKey = primaryKey; it.init() }
        fun <T: Table<*, N>, N: Entity> text(table: T, name: String, nullable: kotlin.Boolean, primaryKey: kotlin.Boolean): TextType<T, N> = Column.TextType(table, name, nullable).also { it.isPrimaryKey = primaryKey; it.init() }
        fun <T: Table<*, N>, N: Entity> instant(table: T, name: String, nullable: kotlin.Boolean, primaryKey: kotlin.Boolean): InstantType<T, N> = Column.InstantType(table, name, nullable).also { it.isPrimaryKey = primaryKey; it.init() }
        fun <T: Table<*, N>, N: Entity> json(table: T, name: String, nullable: kotlin.Boolean, primaryKey: kotlin.Boolean): JsonType<T, N> = Column.JsonType(table, name, nullable).also { it.isPrimaryKey = primaryKey; it.init() }

        fun getColumnName(property: KProperty<*>): String {
            //val columnNameAnnotation: Annotation? = property.annotations.firstOrNull { it is ColumnName }
            //return (columnNameAnnotation as ColumnName?)?.name ?: property.name
            return property.name
        }
    }

    class Double(val nullable: kotlin.Boolean = false, val primaryKey: kotlin.Boolean = false) {
        operator fun <T : Table<*, N>, N : Entity> getValue(table: T, property: KProperty<*>): DoubleType<T, N> = Column.double(table, getColumnName(property), nullable, primaryKey)
    }

    class BigDecimal(val nullable: kotlin.Boolean = false, val primaryKey: kotlin.Boolean = false)  {
        operator fun <T : Table<*, N>, N : Entity> getValue(table: T, property: KProperty<*>): BigDecimalType<T, N> {
            return Column.bigDecimal(table, getColumnName(property), nullable, primaryKey)
        }
    }

    class UUID(val nullable: kotlin.Boolean = false, val primaryKey: kotlin.Boolean = false)  {
        operator fun <T : Table<*, N>, N : Entity> getValue(table: T, property: KProperty<*>): UUIDType<T, N> {
            return Column.uuid(table, getColumnName(property), nullable, primaryKey)
        }
    }

    class Int(val nullable: kotlin.Boolean = false, val primaryKey: kotlin.Boolean = false) {
        operator fun <T : Table<*, N>, N : Entity> getValue(table: T, property: KProperty<*>) = Column.int(table, getColumnName(property), nullable, primaryKey)
    }

    class Boolean(val nullable: kotlin.Boolean = false, val primaryKey: kotlin.Boolean = false) {
        operator fun <T : Table<*, N>, N : Entity> getValue(table: T, property: KProperty<*>) = Column.boolean(table, getColumnName(property), nullable, primaryKey)
    }
    class Text(val nullable: kotlin.Boolean = false, val primaryKey: kotlin.Boolean = false) {
        operator fun <T : Table<*, N>, N : Entity> getValue(table: T, property: KProperty<*>) = Column.text(table, getColumnName(property), nullable, primaryKey)
    }
    class Instant(val nullable: kotlin.Boolean = false, val primaryKey: kotlin.Boolean = false) {
        operator fun <T : Table<*, N>, N : Entity> getValue(table: T, property: KProperty<*>) = Column.instant(table, getColumnName(property), nullable, primaryKey)
    }

    class Json(val nullable: kotlin.Boolean = false, val primaryKey: kotlin.Boolean = false) {
        operator fun <T : Table<*, N>, N : Entity> getValue(table: T, property: KProperty<*>) = Column.json(table, getColumnName(property), nullable, primaryKey)
    }
}

annotation class ColumnName(val name: String)
