package com.github.knyazevs.korm


interface Expression

class Value(private val str: String) : Expression {
    override fun toString(): String {
        return "'$str'"
    }
}

class RawExpression(val expression: String): Expression {
    override fun toString(): String = expression
}

sealed class CompoundBooleanOp(
    private val operator: String,
    private val first: Expression,
    private val second: Expression,
) : Expression {
    override fun toString(): String {
        return "$first $operator $second"
    }
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
    override fun toString(): String {
        return "$first $opSign $second"
    }
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
