package io.github.knyazevs.korm

import kotlin.uuid.Uuid

/**
 * Collects bind values while an [Expression] or [Query] is rendered to SQL.
 * Instead of inlining values into the SQL string (which is open to SQL
 * injection), each value is registered under a generated name and replaced by a
 * `:name` placeholder that the database driver binds as a real parameter.
 */
class ParamBuilder {
    private var counter = 0
    private val collected = LinkedHashMap<String, Any?>()

    /** The bind values gathered so far, keyed by their generated placeholder name. */
    val params: Map<String, Any?> get() = collected

    /**
     * Registers [value] as a bind parameter and returns the placeholder to embed
     * in the SQL string. UUIDs additionally get a `::uuid` cast so the text-bound
     * value is interpreted with the right type by Postgres.
     */
    fun bind(value: Any?): String {
        val name = "p${counter++}"
        collected[name] = normalize(value)
        return if (value is Uuid) ":$name::uuid" else ":$name"
    }

    // Drivers bind values as text, so anything that is not a primitive/String is
    // rendered through toString() here (UUID, BigDecimal, Instant, JsonElement, ...).
    private fun normalize(value: Any?): Any? = when (value) {
        null, is Boolean, is Int, is Long, is Double, is String -> value
        else -> value.toString()
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

class EqOp(expr1: Expression, expr2: Expression) : ComparisonOp(expr1, expr2, "=")

infix fun <T : Expression, T2 : Expression> T.eq(other: T2): Expression = EqOp(this, other)
infix fun <T : Expression> T.eq(other: String): Expression = EqOp(this, Value(other))

/**
 * Represents an SQL operator that checks if [expr1] is not equals to [expr2].
 */
class NeqOp(expr1: Expression, expr2: Expression) : ComparisonOp(expr1, expr2, "<>")

infix fun <T : Expression, T2 : Expression> T.neq(other: T2): Expression = NeqOp(this, other)
infix fun <T : Expression> T.neq(other: String): Expression = NeqOp(this, Value(other))

/**
 * Represents an SQL operator that checks if [expr1] is less than [expr2].
 */
class LessOp(expr1: Expression, expr2: Expression) : ComparisonOp(expr1, expr2, "<")

infix fun <T : Expression, T2 : Expression> T.less(other: T2): Expression = LessOp(this, other)
infix fun <T : Expression> T.less(other: String): Expression = LessOp(this, Value(other))

/**
 * Represents an SQL operator that checks if [expr1] is less than or equal to [expr2].
 */
class LessEqOp(expr1: Expression, expr2: Expression) : ComparisonOp(expr1, expr2, "<=")

infix fun <T : Expression, T2 : Expression> T.lessEq(other: T2): Expression = LessEqOp(this, other)
infix fun <T : Expression> T.lessEq(other: String): Expression = LessEqOp(this, Value(other))

/**
 * Represents an SQL operator that checks if [expr1] is greater than [expr2].
 */
class GreaterOp(expr1: Expression, expr2: Expression) : ComparisonOp(expr1, expr2, ">")

infix fun <T : Expression, T2 : Expression> T.gt(other: T2): Expression = GreaterOp(this, other)
infix fun <T : Expression> T.gt(other: String): Expression = GreaterOp(this, Value(other))

/**
 * Represents an SQL operator that checks if [expr1] is greater than or equal to [expr2].
 */
class GreaterEqOp(expr1: Expression, expr2: Expression) : ComparisonOp(expr1, expr2, ">=")

infix fun <T : Expression, T2 : Expression> T.gtEq(other: T2): Expression = GreaterEqOp(this, other)
infix fun <T : Expression> T.gtEq(other: String): Expression = GreaterEqOp(this, Value(other))
