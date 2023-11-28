package io.github.moreirasantos.pgkn.sql

import io.github.moreirasantos.pgkn.exception.InvalidDataAccessApiUsageException
import s.knyazev.SqlParameterSource

/**
 * Convert a Map of named parameter values to a corresponding array.
 * @param parsedSql the parsed SQL statement
 * @param paramSource the source for named parameters
 * @return the array of values
 */
@Suppress("NestedBlockDepth", "SwallowedException")
internal fun buildValueArray(
    parsedSql: ParsedSql,
    paramSource: SqlParameterSource
): Array<Any?> {
    if (parsedSql.namedParameterCount > 0 && parsedSql.unnamedParameterCount > 0) {
        throw InvalidDataAccessApiUsageException(
            "Not allowed to mix named and traditional ? placeholders. You have " +
                    parsedSql.namedParameterCount + " named parameter(s) and " +
                    parsedSql.unnamedParameterCount + " traditional placeholder(s) in statement: " +
                    parsedSql.originalSql
        )
    }
    val paramArray = arrayOfNulls<Any>(parsedSql.totalParameterCount)
    parsedSql.parameterNames.forEachIndexed { index, paramName ->
        try {
            paramArray[index] = paramSource.getValue(paramName)
        } catch (ex: IllegalArgumentException) {
            throw InvalidDataAccessApiUsageException("No value supplied for the SQL parameter '$paramName'", ex)
        }
    }
    return paramArray
}
