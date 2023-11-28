package io.github.moreirasantos.pgkn.exception

sealed class SQLException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

class InvalidDataAccessApiUsageException(message: String, cause: Throwable? = null) : SQLException(message, cause)

class AnonymousClassException : SQLException("Class must not be anonymous", null)

class GetColumnValueException(columnIndex: Int) : SQLException("Error getting column $columnIndex value", null)
