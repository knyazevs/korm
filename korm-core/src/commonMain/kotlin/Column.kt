package io.github.kormium

import io.github.kormium.resultset.ResultSet
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonElement
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private val logger = KotlinLogging.logger {}

/**
 * A typed column on table [T]. A column is the SQL identifier (an [Expression]/[Selectable])
 * and, through its concrete [NotNullColumn] / [NullableColumn] subtype, the property delegate
 * used inside the entity [N].
 *
 * Nullability is encoded in the type, not a runtime flag:
 *
 * ```kotlin
 * object Users : Table<App, User>("users", ::User) {
 *     val id by Column.UUID().primaryKey()          // non-null primary key
 *     val name by Column.Text()                      // non-null
 *     val note by Column.Text().nullable()           // nullable -> String?
 *     val createdAt by Column.Instant(name = "created_at")
 * }
 * ```
 *
 * [fieldKey] (the Kotlin property name) keys the value in [Entity.fields]; [name] is the
 * rendered SQL identifier. They differ only when a custom `name = ...` is given, so custom
 * SQL names never leak into entity internals or absent/null tracking.
 */
sealed class Column<Z, T: Table<*, N>, N: Entity>(
    private val table: T,
    /** Key under which the value is stored in [Entity.fields]; follows the Kotlin property name. */
    val fieldKey: String,
    /** Rendered SQL column identifier. Equals [fieldKey] unless a custom name was supplied. */
    open var name: String,
    open var nullable: kotlin.Boolean,
    val columnType: ColumnNameEnum,
) : Expression, Selectable<Z> {

    open fun init() {
        logger.trace { "init column $fieldKey (sql: $name)" }
        table.addColumn(fieldKey, this)
    }

    override fun toString(): String = name

    // A column renders to its (quoted) identifier in SQL, never as a bind parameter.
    // Inside a join it is qualified by its table so `users.id` and `orders.id` differ.
    override fun toSql(builder: ParamBuilder): String =
        if (builder.qualifyColumns) "${builder.dialect.quoteIdentifier(table.tableName)}.${builder.dialect.quoteIdentifier(name)}"
        else builder.dialect.quoteIdentifier(name)

    internal val tableRef: Table<*, *> get() = table

    /** Whether this column is part of the table's primary key. */
    internal var isPrimaryKey: kotlin.Boolean = false

    // As a selectable, read its value via the type mapper (its toSql is the SELECT SQL).
    @Suppress("UNCHECKED_CAST")
    override fun read(rs: ResultSet, index: kotlin.Int, typeMapper: TypeMapper): Z? =
        typeMapper.fromResult(rs, index, columnType) as Z?

    /**
     * A non-null column. Its entity property is `Z`: assigning `null` is a compile error, and
     * reading a field that was never assigned (or that the database returned as `NULL`) throws.
     */
    class NotNullColumn<Z, T: Table<*, N>, N: Entity>(table: T, fieldKey: String, name: String, columnType: ColumnNameEnum)
        : Column<Z, T, N>(table, fieldKey, name, nullable = false, columnType) {
        operator fun getValue(n: N, property: KProperty<*>): Z {
            logger.trace { "Get value $fieldKey" }
            if (!n.fields.containsKey(fieldKey)) error("Field '$fieldKey' is not present on ${tableRef.tableName}")
            @Suppress("UNCHECKED_CAST")
            return (n.fields[fieldKey] ?: error("Field '$fieldKey' is null but column '$name' is non-null")) as Z
        }

        operator fun setValue(n: N, property: KProperty<*>, z: Z) {
            logger.trace { "Set value $fieldKey" }
            n.fields[fieldKey] = z
        }
    }

    /** A nullable column. Its entity property is `Z?`: an absent field reads back as `null`. */
    class NullableColumn<Z, T: Table<*, N>, N: Entity>(table: T, fieldKey: String, name: String, columnType: ColumnNameEnum)
        : Column<Z, T, N>(table, fieldKey, name, nullable = true, columnType) {
        @Suppress("UNCHECKED_CAST")
        operator fun getValue(n: N, property: KProperty<*>): Z? {
            logger.trace { "Get value $fieldKey" }
            return n.fields[fieldKey] as Z?
        }

        operator fun setValue(n: N, property: KProperty<*>, z: Z?) {
            logger.trace { "Set value $fieldKey" }
            n.fields[fieldKey] = z
        }
    }

    enum class ColumnNameEnum {
        BigDecimal,
        UUID,
        Double,
        Int,
        Boolean,
        String,
        Instant,
        Json,
        Long,
        Float,
        Short,
        LocalDate,
        LocalTime,
        LocalDateTime
    }

    // ---- column specs (the public declaration builders) ----

    /**
     * Entry-point spec for a non-null column. Resolves to a [NotNullColumn]; refine with
     * [nullable] or [primaryKey] (mutually exclusive — a nullable primary key cannot be
     * expressed).
     */
    sealed class Spec<Z>(private val name: String?, private val columnType: ColumnNameEnum) {
        operator fun <T: Table<*, N>, N: Entity> provideDelegate(table: T, property: KProperty<*>): ReadOnlyProperty<T, NotNullColumn<Z, T, N>> {
            val column = NotNullColumn<Z, T, N>(table, property.name, name ?: property.name, columnType).also { it.init() }
            return ReadOnlyProperty { _, _ -> column }
        }

        /** Makes the column nullable; its entity property becomes `Z?`. */
        fun nullable(): NullableSpec<Z> = NullableSpec(name, columnType)

        /** Marks the column as the (or part of the) primary key. Non-null by construction. */
        fun primaryKey(): PrimaryKeySpec<Z> = PrimaryKeySpec(name, columnType)
    }

    /** Spec for a nullable column. Has no [primaryKey] — nullable primary keys are not allowed. */
    class NullableSpec<Z> internal constructor(private val name: String?, private val columnType: ColumnNameEnum) {
        operator fun <T: Table<*, N>, N: Entity> provideDelegate(table: T, property: KProperty<*>): ReadOnlyProperty<T, NullableColumn<Z, T, N>> {
            val column = NullableColumn<Z, T, N>(table, property.name, name ?: property.name, columnType).also { it.init() }
            return ReadOnlyProperty { _, _ -> column }
        }
    }

    /** Spec for a primary-key column. Has no [nullable] — nullable primary keys are not allowed. */
    class PrimaryKeySpec<Z> internal constructor(private val name: String?, private val columnType: ColumnNameEnum) {
        operator fun <T: Table<*, N>, N: Entity> provideDelegate(table: T, property: KProperty<*>): ReadOnlyProperty<T, NotNullColumn<Z, T, N>> {
            val column = NotNullColumn<Z, T, N>(table, property.name, name ?: property.name, columnType)
                .also { it.isPrimaryKey = true; it.init() }
            return ReadOnlyProperty { _, _ -> column }
        }
    }

    // ---- the 14 typed column declarations ----

    class UUID(name: String? = null) : Spec<kotlin.uuid.Uuid>(name, ColumnNameEnum.UUID)
    class BigDecimal(name: String? = null) : Spec<com.ionspin.kotlin.bignum.decimal.BigDecimal>(name, ColumnNameEnum.BigDecimal)
    class Double(name: String? = null) : Spec<kotlin.Double>(name, ColumnNameEnum.Double)
    class Int(name: String? = null) : Spec<kotlin.Int>(name, ColumnNameEnum.Int)
    class Boolean(name: String? = null) : Spec<kotlin.Boolean>(name, ColumnNameEnum.Boolean)
    class Text(name: String? = null) : Spec<kotlin.String>(name, ColumnNameEnum.String)
    class Instant(name: String? = null) : Spec<kotlinx.datetime.Instant>(name, ColumnNameEnum.Instant)
    class Json(name: String? = null) : Spec<JsonElement>(name, ColumnNameEnum.Json)
    class Long(name: String? = null) : Spec<kotlin.Long>(name, ColumnNameEnum.Long)
    class Float(name: String? = null) : Spec<kotlin.Float>(name, ColumnNameEnum.Float)
    class Short(name: String? = null) : Spec<kotlin.Short>(name, ColumnNameEnum.Short)
    class LocalDate(name: String? = null) : Spec<kotlinx.datetime.LocalDate>(name, ColumnNameEnum.LocalDate)
    class LocalTime(name: String? = null) : Spec<kotlinx.datetime.LocalTime>(name, ColumnNameEnum.LocalTime)
    class LocalDateTime(name: String? = null) : Spec<kotlinx.datetime.LocalDateTime>(name, ColumnNameEnum.LocalDateTime)
}
