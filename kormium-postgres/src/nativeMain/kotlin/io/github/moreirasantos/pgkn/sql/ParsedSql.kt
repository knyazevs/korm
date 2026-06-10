package io.github.moreirasantos.pgkn.sql

import io.github.moreirasantos.pgkn.exception.InvalidDataAccessApiUsageException


/**
 * Heavily Based on:
 * https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/core/namedparam/NamedParameterUtils.html
 */
@Suppress(
    "ComplexCondition", "LoopWithTooManyJumpStatements",
    "CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth"
)
internal fun parseSql(sql: String): ParsedSql {

    val namedParameters: MutableSet<String> = HashSet()
    val sqlToUse = StringBuilder(sql)
    val parameterList: MutableList<ParameterHolder> = ArrayList()

    val statement: CharArray = sql.toCharArray()
    var namedParameterCount = 0
    var unnamedParameterCount = 0
    var totalParameterCount = 0

    var escapes = 0
    var i = 0
    while (i < statement.size) {
        var skipToPosition: Int
        while (i < statement.size) {
            skipToPosition = skipCommentsAndQuotes(statement, i)
            i = if (i == skipToPosition) {
                break
            } else {
                skipToPosition
            }
        }
        if (i >= statement.size) {
            break
        }
        val c = statement[i]
        if (c == ':' || c == '&') {
            var j = i + 1
            if (c == ':' && j < statement.size && statement[j] == ':') {
                // Postgres-style "::" casting operator should be skipped
                i += 2
                continue
            }
            var parameter: String?
            if (c == ':' && j < statement.size && statement[j] == '{') {
                // :{x} style parameter
                while (statement[j] != '}') {
                    j++
                    if (j >= statement.size) {
                        throw InvalidDataAccessApiUsageException(
                            "Non-terminated named parameter declaration at position $i in statement: $sql"
                        )
                    }
                    if (statement[j] == ':' || statement[j] == '{') {
                        throw InvalidDataAccessApiUsageException(
                            "Parameter name contains invalid character '${statement[j]}' " +
                                    "at position $i in statement: $sql"
                        )
                    }
                }
                if (j - i > 2) {
                    parameter = sql.substring(i + 2, j)
                    namedParameterCount = addNewNamedParameter(namedParameters, namedParameterCount, parameter)
                    totalParameterCount = addNamedParameter(
                        parameterList, totalParameterCount, escapes, i, j + 1, parameter
                    )
                }
                j++
            } else {
                while (j < statement.size && !isParameterSeparator(statement[j])) {
                    j++
                }
                if (j - i > 1) {
                    parameter = sql.substring(i + 1, j)
                    namedParameterCount = addNewNamedParameter(namedParameters, namedParameterCount, parameter)
                    totalParameterCount = addNamedParameter(
                        parameterList, totalParameterCount, escapes, i, j, parameter
                    )
                }
            }
            i = j - 1
        } else {
            if (c == '\\') {
                val j = i + 1
                if (j < statement.size && statement[j] == ':') {
                    // escaped ":" should be skipped
                    sqlToUse.deleteAt(i - escapes)
                    escapes++
                    i += 2
                    continue
                }
            }
            if (c == '?') {
                val j = i + 1
                if (j < statement.size && (statement[j] == '?' || statement[j] == '|' || statement[j] == '&')) {
                    // Postgres-style "??", "?|", "?&" operator should be skipped
                    i += 2
                    continue
                }
                unnamedParameterCount++
                totalParameterCount++
            }
        }
        i++
    }
    val parsedSql = ParsedSql(sqlToUse.toString())
    for (ph in parameterList) {
        parsedSql.addNamedParameter(ph.parameterName, ph.startIndex, ph.endIndex)
    }
    parsedSql.namedParameterCount = namedParameterCount
    parsedSql.unnamedParameterCount = unnamedParameterCount
    parsedSql.totalParameterCount = totalParameterCount
    return parsedSql
}


/**
 * Holds information about a parsed SQL statement.
 */
internal class ParsedSql(val originalSql: String) {

    /**
     * Return all the parameters (bind variables) in the parsed SQL statement.
     * Repeated occurrences of the same parameter name are included here.
     */
    val parameterNames: MutableList<String> = ArrayList()
    val parameterIndexes: MutableList<IntArray> = ArrayList()
    /**
     * Return the count of named parameters in the SQL statement.
     * Each parameter name counts once; repeated occurrences do not count here.
     */
    /**
     * Set the count of named parameters in the SQL statement.
     * Each parameter name counts once; repeated occurrences do not count here.
     */
    var namedParameterCount = 0
    /**
     * Return the count of all the unnamed parameters in the SQL statement.
     */
    /**
     * Set the count of all the unnamed parameters in the SQL statement.
     */
    var unnamedParameterCount = 0
    /**
     * Return the total count of all the parameters in the SQL statement.
     * Repeated occurrences of the same parameter name do count here.
     */
    /**
     * Set the total count of all the parameters in the SQL statement.
     * Repeated occurrences of the same parameter name do count here.
     */
    var totalParameterCount = 0

    /**
     * Add a named parameter parsed from this SQL statement.
     * @param parameterName the name of the parameter
     * @param startIndex the start index in the original SQL String
     * @param endIndex the end index in the original SQL String
     */
    fun addNamedParameter(parameterName: String, startIndex: Int, endIndex: Int) {
        parameterNames.add(parameterName)
        parameterIndexes.add(intArrayOf(startIndex, endIndex))
    }

    /**
     * Exposes the original SQL String.
     */
    override fun toString() = originalSql
}


private class ParameterHolder(val parameterName: String, val startIndex: Int, val endIndex: Int)

/**
 * Skip over comments and quoted names present in an SQL statement.
 * @param statement character array containing SQL statement
 * @param position current position of statement
 * @return next position to process after any comments or quotes are skipped
 */
@Suppress("NestedBlockDepth", "ReturnCount")
private fun skipCommentsAndQuotes(statement: CharArray, position: Int): Int {
    for (i in START_SKIP.indices) {
        if (statement[position] == START_SKIP[i][0]) {
            var match = true
            for (j in 1 until START_SKIP[i].length) {
                if (statement[position + j] != START_SKIP[i][j]) {
                    match = false
                    break
                }
            }
            if (match) {
                val offset: Int = START_SKIP[i].length
                for (m in position + offset until statement.size) {
                    if (statement[m] == STOP_SKIP[i][0]) {
                        var endMatch = true
                        var endPos = m
                        for (n in 1 until STOP_SKIP[i].length) {
                            if (m + n >= statement.size) {
                                // last comment not closed properly
                                return statement.size
                            }
                            if (statement[m + n] != STOP_SKIP[i][n]) {
                                endMatch = false
                                break
                            }
                            endPos = m + n
                        }
                        if (endMatch) {
                            // found character sequence ending comment or quote
                            return endPos + 1
                        }
                    }
                }
                // character sequence ending comment or quote not found
                return statement.size
            }
        }
    }
    return position
}

@Suppress("LongParameterList")
private fun addNamedParameter(
    parameterList: MutableList<ParameterHolder>,
    totalParameterCount: Int,
    escapes: Int,
    i: Int,
    j: Int,
    parameter: String
): Int {
    var count = totalParameterCount
    parameterList.add(ParameterHolder(parameter, i - escapes, j - escapes))
    count++
    return count
}

private fun addNewNamedParameter(
    namedParameters: MutableSet<String>,
    namedParameterCount: Int,
    parameter: String
): Int {
    var count = namedParameterCount
    if (!namedParameters.contains(parameter)) {
        namedParameters.add(parameter)
        count++
    }
    return count
}

/**
 * Determine whether a parameter name ends at the current position,
 * that is, whether the given character qualifies as a separator.
 */
@Suppress("MagicNumber")
private fun isParameterSeparator(c: Char) = c.code < 128 && separatorIndex[c.code] || c.isWhitespace()


/**
 * Set of characters that qualify as comment or quotes starting characters.
 */
private val START_SKIP = arrayOf("'", "\"", "--", "/*")

/**
 * Set of characters that at are the corresponding comment or quotes ending characters.
 */
private val STOP_SKIP = arrayOf("'", "\"", "\n", "*/")

/**
 * Set of characters that qualify as parameter separators,
 * indicating that a parameter name in an SQL String has ended.
 */
private const val PARAMETER_SEPARATORS = "\"':&,;()|=+-*%/\\<>^"

/**
 * An index with separator flags per character code.
 * Technically only needed between 34 and 124 at this point.
 */
@Suppress("MagicNumber")
private val separatorIndex = BooleanArray(128).apply {
    PARAMETER_SEPARATORS.toCharArray().forEach { this[it.code] = true }
}
