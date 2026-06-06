package io.github.knyazevs.korm

/** Base type for all korm errors. */
open class KormException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A SQL statement failed on the server. [sqlState] is the 5-character SQLSTATE code when
 * the backend reports one (e.g. "23505"); subtypes cover the common constraint violations.
 */
open class QueryException(message: String, val sqlState: String? = null, cause: Throwable? = null) :
    KormException(message, cause)

/** Unique / primary-key constraint violation (SQLSTATE 23505). */
class UniqueViolationException(message: String, sqlState: String?, cause: Throwable? = null) :
    QueryException(message, sqlState, cause)

/** Foreign-key constraint violation (SQLSTATE 23503). */
class ForeignKeyViolationException(message: String, sqlState: String?, cause: Throwable? = null) :
    QueryException(message, sqlState, cause)

/** NOT NULL constraint violation (SQLSTATE 23502). */
class NotNullViolationException(message: String, sqlState: String?, cause: Throwable? = null) :
    QueryException(message, sqlState, cause)

/** CHECK constraint violation (SQLSTATE 23514). */
class CheckViolationException(message: String, sqlState: String?, cause: Throwable? = null) :
    QueryException(message, sqlState, cause)

/** Maps a SQLSTATE to the most specific [QueryException] subtype. */
fun sqlException(message: String, sqlState: String?, cause: Throwable? = null): QueryException = when (sqlState) {
    "23505" -> UniqueViolationException(message, sqlState, cause)
    "23503" -> ForeignKeyViolationException(message, sqlState, cause)
    "23502" -> NotNullViolationException(message, sqlState, cause)
    "23514" -> CheckViolationException(message, sqlState, cause)
    else -> QueryException(message, sqlState, cause)
}
