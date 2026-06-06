package io.github.knyazevs.korm.database

import io.github.knyazevs.korm.PgResultSetWrapper
import io.github.knyazevs.korm.PostgresDialect
import io.github.knyazevs.korm.PostgresDriver
import io.github.knyazevs.korm.StandardTypeMapper
import io.github.knyazevs.korm.jdbc.JdbcDatabase

/**
 * A Postgres [PostgresDriver] backed by the shared [JdbcDatabase] (HikariCP pool).
 *
 * stringtype=unspecified lets the server infer a parameter's type from its column
 * context, so text-bound values (BigDecimal, uuid, timestamps, ...) are accepted by
 * numeric/uuid/timestamp columns — matching the native (libpq) driver, which sends
 * all parameters as untyped text. prepareThreshold=1 makes pgjdbc use a server-side
 * prepared statement from the first execution (default 5), so repeated statements skip
 * re-parsing. (The MySQL-style cachePrepStmts/prepStmtCacheSize properties are ignored
 * by pgjdbc.)
 */
private class PostgresJdbcDriver(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    poolSize: Int,
) : JdbcDatabase(
    jdbcUrl = "jdbc:postgresql://$host:$port/$database?stringtype=unspecified&prepareThreshold=1",
    username = user,
    password = password,
    poolSize = poolSize,
    dialect = PostgresDialect,
    typeMapper = StandardTypeMapper,
    wrap = ::PgResultSetWrapper,
), PostgresDriver

actual fun createDatabase(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    poolSize: Int,
): PostgresDriver = PostgresJdbcDriver(host, port, database, user, password, poolSize)
