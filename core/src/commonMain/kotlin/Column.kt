package io.github.knyazevs.korm

import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KProperty


sealed class Column<Z, T: Table<N>, N: Entity>(private val table: T, open var name: String, open var nullable: kotlin.Boolean = false, val columnType: ColumnNameEnum):
    Expression {
    operator fun getValue(n: N, property: KProperty<*>): Z? {
        println("Get value ${name}")
        return n.fields[name] as Z?
    }

    operator fun setValue(n: N, property: KProperty<*>, z: Z?){
        println("Set value ${name}")
        n.fields[name] = z
    }

    open fun init() {
        println("init column ${this.name}")
        table.addColumn(this.name, this)
    }
    override fun toString(): String {
        return name
    }

    class UUIDType<T: Table<N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<kotlinx.uuid.UUID, T, N>(table, name, nullable, ColumnNameEnum.UUID)
    class DoubleType<T: Table<N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<kotlin.Double, T, N>(table, name, nullable, ColumnNameEnum.Double)
    class IntType<T: Table<N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<kotlin.Int, T, N>(table, name, nullable, ColumnNameEnum.Int)
    class BooleanType<T: Table<N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<kotlin.Boolean, T, N>(table, name, nullable, ColumnNameEnum.Boolean)
    class TextType<T: Table<N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<kotlin.String, T, N>(table, name, nullable, ColumnNameEnum.String)
    class InstantType<T: Table<N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<kotlinx.datetime.Instant, T, N>(table, name, nullable, ColumnNameEnum.Instant)
    class JsonType<T: Table<N>, N: Entity>(table: T, override var name: String, override var nullable: kotlin.Boolean = false) : Column<JsonElement, T, N>(table, name, nullable, ColumnNameEnum.Json)

    enum class ColumnNameEnum {
        UUID,
        Double,
        Int,
        Boolean,
        String,
        Instant,
        Json
    }

    private companion object {
        fun <T: Table<N>, N: Entity> double(table: T, name: String, nullable: kotlin.Boolean): DoubleType<T, N>  = Column.DoubleType(table, name, nullable).also { it.init() }
        fun <T: Table<N>, N: Entity> uuid(table: T, name: String, nullable: kotlin.Boolean): UUIDType<T, N> = Column.UUIDType(table, name, nullable).also { it.init() }
        fun <T: Table<N>, N: Entity> int(table: T, name: String, nullable: kotlin.Boolean): IntType<T, N> = Column.IntType(table, name, nullable).also { it.init() }
        fun <T: Table<N>, N: Entity> boolean(table: T, name: String, nullable: kotlin.Boolean): BooleanType<T, N> = Column.BooleanType(table, name, nullable).also { it.init() }
        fun <T: Table<N>, N: Entity> text(table: T, name: String, nullable: kotlin.Boolean): TextType<T, N> = Column.TextType(table, name, nullable).also { it.init() }
        fun <T: Table<N>, N: Entity> instant(table: T, name: String, nullable: kotlin.Boolean): InstantType<T, N> = Column.InstantType(table, name, nullable).also { it.init() }
        fun <T: Table<N>, N: Entity> json(table: T, name: String, nullable: kotlin.Boolean): JsonType<T, N> = Column.JsonType(table, name, nullable).also { it.init() }

        fun getColumnName(property: KProperty<*>): String {
            //val columnNameAnnotation: Annotation? = property.annotations.firstOrNull { it is ColumnName }
            //return (columnNameAnnotation as ColumnName?)?.name ?: property.name
            return property.name
        }
    }

    class Double(val nullable: kotlin.Boolean = false) {
        operator fun <T : Table<N>, N : Entity> getValue(table: T, property: KProperty<*>): DoubleType<T, N> = Column.double(table, getColumnName(property), nullable)
    }

    class UUID(val nullable: kotlin.Boolean = false)  {
        operator fun <T : Table<N>, N : Entity> getValue(table: T, property: KProperty<*>): UUIDType<T, N> {
            return Column.uuid(table, getColumnName(property), nullable)
        }
    }

    class Int(val nullable: kotlin.Boolean = false) {
        operator fun <T : Table<N>, N : Entity> getValue(table: T, property: KProperty<*>) = Column.int(table, getColumnName(property), nullable)
    }

    class Boolean(val nullable: kotlin.Boolean = false) {
        operator fun <T : Table<N>, N : Entity> getValue(table: T, property: KProperty<*>) = Column.boolean(table, getColumnName(property), nullable)
    }
    class Text(val nullable: kotlin.Boolean = false) {
        operator fun <T : Table<N>, N : Entity> getValue(table: T, property: KProperty<*>) = Column.text(table, getColumnName(property), nullable)
    }
    class Instant(val nullable: kotlin.Boolean = false) {
        operator fun <T : Table<N>, N : Entity> getValue(table: T, property: KProperty<*>) = Column.instant(table, getColumnName(property), nullable)
    }

    class Json(val nullable: kotlin.Boolean = false) {
        operator fun <T : Table<N>, N : Entity> getValue(table: T, property: KProperty<*>) = Column.json(table, getColumnName(property), nullable)
    }
}

annotation class ColumnName(val name: String)
