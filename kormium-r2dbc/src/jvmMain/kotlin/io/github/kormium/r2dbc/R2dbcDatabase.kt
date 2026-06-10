package io.github.kormium.r2dbc

import io.github.kormium.Dialect
import io.github.kormium.KormiumConfig
import io.github.kormium.PostgresDialect
import io.github.kormium.StandardTypeMapper
import io.github.kormium.SuspendSqlExecutor
import io.github.kormium.TypeMapper
import io.github.kormium.WriteListeners
import io.github.kormium.database.SuspendDatabase
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext

/**
 * A truly async (non-blocking) Postgres [SuspendDatabase], backed by r2dbc-postgresql
 * over a reactive [ConnectionPool]. It implements ONLY the suspend hierarchy — there is
 * no blocking [io.github.kormium.database.Database] here — which is exactly why
 * SuspendDatabase is a sibling of Database, not a subtype.
 *
 * The phantom catalog tag is [Nothing], so by covariance it fits any
 * `SuspendDatabase<G>`; pin the tag at the call site (`val db: SuspendDatabase<MyCatalog>`).
 */
class R2dbcDatabase internal constructor(
    private val pool: ConnectionPool,
    private val dialect: Dialect,
    private val typeMapper: TypeMapper,
    override val config: KormiumConfig = KormiumConfig(),
) : SuspendDatabase<Nothing> {

    // Supports change observation (kormium-observe): writes through this database notify here.
    override val writeListeners: WriteListeners = WriteListeners()

    override suspend fun <R> useConnection(transactional: Boolean, block: suspend (SuspendSqlExecutor) -> R): R {
        val connection = pool.create().awaitSingle()
        try {
            if (transactional) connection.beginTransaction().awaitFirstOrNull()
            val exec = R2dbcExecutor(connection, dialect, typeMapper)
            return try {
                block(exec).also { if (transactional) connection.commitTransaction().awaitFirstOrNull() }
            } catch (e: Throwable) {
                if (transactional) {
                    withContext(NonCancellable) { runCatching { connection.rollbackTransaction().awaitFirstOrNull() } }
                }
                throw e
            }
        } finally {
            withContext(NonCancellable) { runCatching { connection.close().awaitFirstOrNull() } }
        }
    }

    override fun close() {
        pool.dispose()
    }
}

/**
 * Opens an async Postgres database over r2dbc with a reactive connection pool of
 * [poolSize]. Returns it tagged [Nothing] (covariance pins the catalog at the call site).
 */
fun createR2dbcDatabase(
    host: String,
    port: Int = 5432,
    database: String,
    user: String,
    password: String,
    poolSize: Int = 10,
    config: KormiumConfig = KormiumConfig(),
): R2dbcDatabase {
    val connectionFactory = PostgresqlConnectionFactory(
        PostgresqlConnectionConfiguration.builder()
            .host(host)
            .port(port)
            .database(database)
            .username(user)
            .password(password)
            .build(),
    )
    val poolConfiguration = ConnectionPoolConfiguration.builder(connectionFactory)
        .maxSize(poolSize)
        .build()
    return R2dbcDatabase(ConnectionPool(poolConfiguration), PostgresDialect, StandardTypeMapper, config)
}
