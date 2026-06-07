package io.github.knyazevs.korm

/**
 * Collects bind values while an [Expression] or [Query] is rendered to SQL.
 * Instead of inlining values into the SQL string (which is open to SQL
 * injection), each value is registered under a generated name and replaced by a
 * placeholder that the database driver binds as a real parameter. Identifier
 * quoting and placeholder rendering are delegated to [dialect]; value conversion
 * to [typeMapper].
 */
class ParamBuilder(
    val dialect: Dialect,
    private val typeMapper: TypeMapper,
    /** When true, a [Column] renders as `"table"."col"` (needed to disambiguate joins). */
    val qualifyColumns: Boolean = false,
) {
    private var counter = 0
    private val collected = LinkedHashMap<String, Any?>()

    /** The bind values gathered so far, keyed by their generated placeholder name. */
    val params: Map<String, Any?> get() = collected

    /** Registers [value] as a bind parameter and returns the placeholder to embed in the SQL. */
    fun bind(value: Any?): String {
        val name = "p${counter++}"
        collected[name] = typeMapper.toParameter(value)
        return dialect.renderBind(name, value)
    }
}

interface Expression {
    fun toSql(builder: ParamBuilder): String
}

class Value(private val value: Any?) : Expression {
    override fun toSql(builder: ParamBuilder): String = builder.bind(value)
}

/**
 * Raw, unparameterized SQL fragment embedded verbatim. Unsafe with untrusted
 * input — prefer [Value] and the typed operators below. Use only for SQL you
 * fully control.
 */
class RawExpression(val expression: String) : Expression {
    override fun toSql(builder: ParamBuilder): String = expression
}

sealed class CompoundBooleanOp(
    private val operator: String,
    private val first: Expression,
    private val second: Expression,
) : Expression {
    override fun toSql(builder: ParamBuilder): String =
        "${first.toSql(builder)}$operator${second.toSql(builder)}"
}

class AndOp(first: Expression, second: Expression) : CompoundBooleanOp(" AND ", first, second)

infix fun <T : Expression, T2 : Expression> T.and(other: T2): Expression = AndOp(this, other)

/**
 * Represents a logical operator that performs an `or` operation between all the specified [expressions].
 */
class OrOp(first: Expression, second: Expression) : CompoundBooleanOp(" OR ", first, second)

infix fun <T : Expression, T2 : Expression> T.or(other: T2): Expression = OrOp(this, other)


abstract class ComparisonOp(
    /** Returns the left-hand side operand. */
    val first: Expression,
    /** Returns the right-hand side operand. */
    val second: Expression,
    /** Returns the symbol of the comparison operation. */
    val opSign: String,
) : Expression {
    override fun toSql(builder: ParamBuilder): String =
        "${first.toSql(builder)} $opSign ${second.toSql(builder)}"
}

// Comparison operators come in two forms: column-to-expression (e.g. column to column)
// and the common column-to-value form, which is typed — `Users.age eq 18`, not a string —
// so the value type must match the column's.

class EqOp(expr1: Expression, expr2: Expression) : ComparisonOp(expr1, expr2, "=")

infix fun <T : Expression, T2 : Expression> T.eq(other: T2): Expression = EqOp(this, other)
infix fun <Z> Column<Z, *, *>.eq(value: Z): Expression = EqOp(this, Value(value))

/** Checks that the operands are not equal. */
class NeqOp(expr1: Expression, expr2: Expression) : ComparisonOp(expr1, expr2, "<>")

infix fun <T : Expression, T2 : Expression> T.neq(other: T2): Expression = NeqOp(this, other)
infix fun <Z> Column<Z, *, *>.neq(value: Z): Expression = NeqOp(this, Value(value))

/** Checks that the left operand is less than the right. */
class LessOp(expr1: Expression, expr2: Expression) : ComparisonOp(expr1, expr2, "<")

infix fun <T : Expression, T2 : Expression> T.less(other: T2): Expression = LessOp(this, other)
infix fun <Z> Column<Z, *, *>.less(value: Z): Expression = LessOp(this, Value(value))

/** Checks that the left operand is less than or equal to the right. */
class LessEqOp(expr1: Expression, expr2: Expression) : ComparisonOp(expr1, expr2, "<=")

infix fun <T : Expression, T2 : Expression> T.lessEq(other: T2): Expression = LessEqOp(this, other)
infix fun <Z> Column<Z, *, *>.lessEq(value: Z): Expression = LessEqOp(this, Value(value))

/** Checks that the left operand is greater than the right. */
class GreaterOp(expr1: Expression, expr2: Expression) : ComparisonOp(expr1, expr2, ">")

infix fun <T : Expression, T2 : Expression> T.gt(other: T2): Expression = GreaterOp(this, other)
infix fun <Z> Column<Z, *, *>.gt(value: Z): Expression = GreaterOp(this, Value(value))

/** Checks that the left operand is greater than or equal to the right. */
class GreaterEqOp(expr1: Expression, expr2: Expression) : ComparisonOp(expr1, expr2, ">=")

infix fun <T : Expression, T2 : Expression> T.gtEq(other: T2): Expression = GreaterEqOp(this, other)
infix fun <Z> Column<Z, *, *>.gtEq(value: Z): Expression = GreaterEqOp(this, Value(value))

/** `column IN (v1, v2, ...)`. An empty list renders to `FALSE` (matches nothing). */
class InListOp(private val column: Expression, private val values: List<*>) : Expression {
    override fun toSql(builder: ParamBuilder): String =
        if (values.isEmpty()) "FALSE"
        else "${column.toSql(builder)} IN (${values.joinToString(", ") { builder.bind(it) }})"
}

infix fun <Z> Column<Z, *, *>.inList(values: List<Z>): Expression = InListOp(this, values)

/** `column LIKE pattern` (text columns only). */
class LikeOp(expr1: Expression, expr2: Expression) : ComparisonOp(expr1, expr2, "LIKE")

infix fun Column<String, *, *>.like(pattern: String): Expression = LikeOp(this, Value(pattern))

/** `column IS NULL` / `column IS NOT NULL`. */
class IsNullOp(private val column: Expression, private val negated: Boolean) : Expression {
    override fun toSql(builder: ParamBuilder): String =
        "${column.toSql(builder)} IS ${if (negated) "NOT " else ""}NULL"
}

fun Column<*, *, *>.isNull(): Expression = IsNullOp(this, false)
fun Column<*, *, *>.isNotNull(): Expression = IsNullOp(this, true)

/** Negates an expression: `NOT (expr)`. */
class NotOp(private val expr: Expression) : Expression {
    override fun toSql(builder: ParamBuilder): String = "NOT (${expr.toSql(builder)})"
}

fun not(expr: Expression): Expression = NotOp(expr)
