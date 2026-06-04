package io.github.moreirasantos.pgkn.exception

sealed class SQLException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

class InvalidDataAccessApiUsageException(message: String, cause: Throwable? = null) : SQLException(message, cause)

class AnonymousClassException : SQLException("Class must not be anonymous", null)

class GetColumnValueException(columnIndex: Int) : SQLException("Error getting column $columnIndex value", null)

/** Raised when a statement (or the initial connection) fails on the server. */
class QueryExecutionException(message: String, cause: Throwable? = null) : SQLException(message, cause)

/** Raised when the driver is used after [io.github.moreirasantos.pgkn.PostgresDriver] was closed. */
class ConnectionClosedException : SQLException("PostgresDriver is closed", null)
