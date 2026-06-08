package io.github.knyazevs.korm

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.knyazevs.korm.resultset.ResultSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

actual fun createSqliteDatabase(path: String, poolSize: Int, config: KormConfig): SqliteDriver =
    SqliteAndroidDriver(path, poolSize, config)

// Android can't use the Kotlin/Native sqlite3 cinterop (it runs on the JVM/ART), so it
// gets this driver on top of androidx.sqlite's bundled SQLite — which ships its own native
// library, so it works on-device without depending on the framework's sqlite. The shape
// mirrors the native driver: a fixed pool of connections handed out one-at-a-time via a
// Channel that doubles as a blocking "free connection" queue.
private class SqliteAndroidDriver(path: String, private val poolSize: Int, override val config: KormConfig) : SqliteDriver {

    init {
        require(poolSize >= 1) { "poolSize must be >= 1, was $poolSize" }
    }

    override val dialect: Dialect = SqliteDialect
    override val typeMapper: TypeMapper = StandardTypeMapper

    private val isMemory = path == ":memory:"
    private val driver = BundledSQLiteDriver()

    // NOTE: a `:memory:` database is private to its connection here (no shared-cache URI),
    // so for an in-memory database use the default poolSize = 1 — a pool of >1 would give
    // each caller its own empty database. File-backed databases pool freely (WAL below).
    private val connections: List<SQLiteConnection> = List(poolSize) {
        driver.open(path).also { initPragmas(it, file = !isMemory) }
    }

    private val pool = Channel<SQLiteConnection>(poolSize).also { channel ->
        connections.forEach { channel.trySend(it) }
    }

    private val closeLock = Mutex()

    private inline fun <T> withConnection(block: (SQLiteConnection) -> T): T {
        val connection = pool.tryReceive().getOrNull() ?: try {
            runBlocking { pool.receive() }
        } catch (_: ClosedReceiveChannelException) {
            throw QueryException("SQLite connection pool is closed")
        }
        try {
            return block(connection)
        } finally {
            pool.trySend(connection)
        }
    }

    override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> =
        withConnection { conn -> query(conn, sql, mapBinder(namedParameters)).handleResults(handler) }

    override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> =
        withConnection { conn -> query(conn, sql, sourceBinder(paramSource)).handleResults(handler) }

    override fun execute(sql: String, namedParameters: Map<String, Any?>): Long =
        withConnection { conn -> updateOrCount(conn, sql, mapBinder(namedParameters)) }

    override fun execute(sql: String, paramSource: SqlParameterSource): Long =
        withConnection { conn -> updateOrCount(conn, sql, sourceBinder(paramSource)) }

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>): Long =
        withConnection { conn -> updateOrCount(conn, sql, mapBinder(namedParameters)) }

    override fun <R> usePinned(transactional: Boolean, block: (SqlExecutor) -> R): R =
        withConnection { conn ->
            val executor = AndroidExecutor(conn)
            if (!transactional) return@withConnection block(executor)
            rawExec(conn, "BEGIN")
            try {
                val result = block(executor)
                rawExec(conn, "COMMIT")
                result
            } catch (e: Throwable) {
                runCatching { rawExec(conn, "ROLLBACK") }
                throw e
            }
        }

    // An SqlExecutor bound to the pinned connection for the duration of usePinned.
    private inner class AndroidExecutor(private val conn: SQLiteConnection) : SqlExecutor {
        override val dialect = SqliteDialect
        override val typeMapper = StandardTypeMapper

        override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T) =
            query(conn, sql, mapBinder(namedParameters)).handleResults(handler)

        override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T) =
            query(conn, sql, sourceBinder(paramSource)).handleResults(handler)

        override fun execute(sql: String, namedParameters: Map<String, Any?>) =
            updateOrCount(conn, sql, mapBinder(namedParameters))

        override fun execute(sql: String, paramSource: SqlParameterSource) =
            updateOrCount(conn, sql, sourceBinder(paramSource))

        override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>): Long =
            updateOrCount(conn, sql, mapBinder(namedParameters))
    }

    override fun close() {
        if (!closeLock.tryLock()) return
        runBlocking { repeat(poolSize) { pool.receive().close() } }
        pool.close()
    }

    // ---- statement execution -------------------------------------------------------

    // A Binder binds the positional placeholders of a prepared statement. androidx.sqlite
    // exposes no parameter-name introspection, so the SQL's `:name` placeholders are parsed
    // to positional `?` (recording the name order) and each is bound by its 1-based index.
    private fun interface Binder {
        fun bind(stmt: SQLiteStatement, fields: List<String>)
    }

    private fun mapBinder(params: Map<String, Any?>) = Binder { stmt, fields ->
        bindByName(stmt, fields, params::containsKey) { params[it] }
    }

    private fun sourceBinder(source: SqlParameterSource) = Binder { stmt, fields ->
        bindByName(stmt, fields, source::hasValue, source::getValue)
    }

    private fun bindByName(
        stmt: SQLiteStatement,
        fields: List<String>,
        hasValue: (String) -> Boolean,
        getValue: (String) -> Any?,
    ) {
        fields.forEachIndexed { index, name ->
            if (!hasValue(name)) return@forEachIndexed // unbound placeholders default to NULL
            bindValue(stmt, index + 1, getValue(name))
        }
    }

    private fun bindValue(stmt: SQLiteStatement, i: Int, value: Any?) {
        when (value) {
            null -> stmt.bindNull(i)
            is Boolean -> stmt.bindLong(i, if (value) 1L else 0L)
            is Int -> stmt.bindLong(i, value.toLong())
            is Short -> stmt.bindLong(i, value.toLong())
            is Long -> stmt.bindLong(i, value)
            is Float -> stmt.bindDouble(i, value.toDouble())
            is Double -> stmt.bindDouble(i, value)
            is ByteArray -> stmt.bindBlob(i, value)
            else -> stmt.bindText(i, value.toString())
        }
    }

    private fun query(conn: SQLiteConnection, sql: String, binder: Binder): SqliteResultSet {
        val parsed = ParsedSql.of(sql)
        val stmt = conn.prepare(parsed.sql)
        try {
            binder.bind(stmt, parsed.fields)
            val colCount = stmt.getColumnCount()
            val names = Array(colCount) { stmt.getColumnName(it) }
            val rows = ArrayList<Array<Any?>>()
            while (stmt.step()) {
                rows.add(Array(colCount) { readColumn(stmt, it) })
            }
            return SqliteResultSet(names, rows)
        } catch (e: SQLiteException) {
            throw sqliteException(e.message ?: "SQLite error", cause = e)
        } finally {
            stmt.close()
        }
    }

    private fun updateOrCount(conn: SQLiteConnection, sql: String, binder: Binder): Long {
        val parsed = ParsedSql.of(sql)
        val stmt = conn.prepare(parsed.sql)
        try {
            binder.bind(stmt, parsed.fields)
            if (stmt.getColumnCount() > 0) {
                // A query: report the number of rows it would yield.
                var n = 0L
                while (stmt.step()) n++
                return n
            }
            // A statement (INSERT/UPDATE/DELETE): execute it and report the rows it changed.
            stmt.step()
            return changes(conn)
        } catch (e: SQLiteException) {
            throw sqliteException(e.message ?: "SQLite error", cause = e)
        } finally {
            stmt.close()
        }
    }

    // changes() reports the rows modified by the most recent INSERT/UPDATE/DELETE; a plain
    // SELECT (like this one) does not reset it, so it is safe to read it back this way.
    private fun changes(conn: SQLiteConnection): Long =
        conn.prepare("SELECT changes()").use { stmt ->
            stmt.step()
            stmt.getLong(0)
        }

    private fun readColumn(stmt: SQLiteStatement, i: Int): Any? = when (stmt.getColumnType(i)) {
        SQLITE_DATA_NULL -> null
        SQLITE_DATA_INTEGER -> stmt.getLong(i)
        SQLITE_DATA_FLOAT -> stmt.getDouble(i)
        SQLITE_DATA_TEXT -> stmt.getText(i)
        SQLITE_DATA_BLOB -> stmt.getBlob(i)
        else -> null
    }

    private fun rawExec(conn: SQLiteConnection, sql: String) {
        conn.prepare(sql).use { it.step() }
    }

    private fun <T> SqliteResultSet.handleResults(handler: (ResultSet) -> T): List<T> {
        val list = ArrayList<T>()
        while (next()) list.add(handler(this))
        return list
    }
}

// foreign_keys are OFF by default in SQLite; WAL only applies to a real file. busy_timeout
// lets a blocked writer wait instead of failing immediately with SQLITE_BUSY.
private fun initPragmas(conn: SQLiteConnection, file: Boolean) {
    if (file) conn.prepare("PRAGMA journal_mode=WAL").use { it.step() }
    conn.prepare("PRAGMA foreign_keys=ON").use { it.step() }
    conn.prepare("PRAGMA busy_timeout=5000").use { it.step() }
}
