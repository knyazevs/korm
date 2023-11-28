package io.github.moreirasantos.pgkn.sql

import s.knyazev.SqlParameterSource


/**
 * Heavily Based on:
 * https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/core/namedparam/NamedParameterUtils.html
 * Parse the SQL statement and locate any placeholders or named parameters. Named
 * parameters are substituted for a placeholder, and any select list is expanded
 * to the required number of placeholders. Select lists may contain an array of
 * objects, and in that case the placeholders will be grouped and enclosed with
 * parentheses. This allows for the use of "expression lists" in the SQL statement
 * like: <br></br><br></br>
 * `select id, name, state from table where (name, age) in (('John', 35), ('Ann', 50))`
 *
 * The parameter values passed in are used to determine the number of placeholders to
 * be used for a select list. Select lists should be limited to 100 or fewer elements.
 * A larger number of elements is not guaranteed to be supported by the database and
 * is strictly vendor-dependent.
 * @param parsedSql the parsed representation of the SQL statement
 * @param paramSource the source for named parameters
 * @return the SQL statement with substituted parameters
 */
@Suppress("NestedBlockDepth")
internal fun substituteNamedParameters(parsedSql: ParsedSql, paramSource: SqlParameterSource?): String {
    val originalSql: String = parsedSql.originalSql
    val paramNames: List<String> = parsedSql.parameterNames
    if (paramNames.isEmpty()) {
        return originalSql
    }
    val actualSql = StringBuilder(originalSql.length)
    var lastIndex = 0
    var parameterNumber = 1
    for (i in paramNames.indices) {
        val paramName = paramNames[i]
        val indexes: IntArray = parsedSql.parameterIndexes[i]
        val startIndex = indexes[0]
        val endIndex = indexes[1]
        actualSql.append(originalSql, lastIndex, startIndex)
        if (paramSource != null && paramSource.hasValue(paramName)) {
            val value: Any = paramSource.getValue(paramName)!!
            /*
            if (value is SqlParameterValue) {
                value = (value as SqlParameterValue).getValue()
            }
             */
            if (value is Iterable<*>) {
                val entryIter = value.iterator()
                var k = 0
                while (entryIter.hasNext()) {
                    if (k > 0) {
                        actualSql.append(", ")
                    }
                    k++
                    val entryItem = entryIter.next()!!
                    if (entryItem is Array<*>) {
                        actualSql.append('(')
                        for (m in entryItem.indices) {
                            if (m > 0) {
                                actualSql.append(", ")
                            }
                            actualSql.append('?')
                        }
                        actualSql.append(')')
                    } else {
                        actualSql.append('?')
                    }
                }
            } else {
                // actualSql.append('?')
                actualSql.append("\$$parameterNumber")
                parameterNumber++
            }
        } else {
            // actualSql.append('?')
            actualSql.append("\$$parameterNumber")
            parameterNumber++
        }
        lastIndex = endIndex
    }
    actualSql.append(originalSql, lastIndex, originalSql.length)
    return actualSql.toString()
}
