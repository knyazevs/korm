package io.github.kormium.database

import io.github.kormium.KormConfig
import io.github.kormium.PgResultSetWrapper
import io.github.kormium.PostgresDialect
import io.github.kormium.PostgresDriver
import io.github.kormium.StandardTypeMapper
import io.github.kormium.jdbc.JdbcDatabase

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
    config: KormConfig,
) : JdbcDatabase(
    jdbcUrl = "jdbc:postgresql://$host:$port/$database?stringtype=unspecified&prepareThreshold=1",
    username = user,
    password = password,
    poolSize = poolSize,
    dialect = PostgresDialect,
    typeMapper = StandardTypeMapper,
    wrap = ::PgResultSetWrapper,
    config = config,
), PostgresDriver

actual fun createDatabase(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    poolSize: Int,
    config: KormConfig,
): PostgresDriver = PostgresJdbcDriver(host, port, database, user, password, poolSize, config)
