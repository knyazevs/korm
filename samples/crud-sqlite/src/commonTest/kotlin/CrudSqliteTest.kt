package io.github.knyazevs.korm.samples.crudsqlite

import io.github.knyazevs.korm.Migration
import io.github.knyazevs.korm.Query
import io.github.knyazevs.korm.autocommit
import io.github.knyazevs.korm.createSqliteDatabase
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.eq
import io.github.knyazevs.korm.gt
import io.github.knyazevs.korm.migrate
import io.github.knyazevs.korm.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs on both JVM and native (SQLite is self-contained, no external database). */
class CrudSqliteTest {

    private fun user(id: Int, name: String, age: Int) = User().apply {
        this.id = id; this.name = name; this.age = age
    }

    @Test
    fun migrateThenCrud() {
        val db: Database<Shop> = createSqliteDatabase()
        db.use {
            db.migrate(listOf(Migration("001-create-users") { Users.createTable() }))

            db.transaction {
                Users.insertAll(listOf(user(1, "Alice", 30), user(2, "Bob", 25), user(3, "Carol", 41)))
            }

            assertEquals("Carol", db.autocommit { Users.findById(3) }?.name)
            assertEquals(
                listOf("Alice", "Carol"),
                db.autocommit { Users.find(Query(Users.age gt 28)) }.mapNotNull { it.name },
            )

            db.transaction { Users.update(Query(Users.id eq 2), user(2, "Bob", 26)) }
            db.transaction { Users.deleteWhere(Query(Users.id eq 1)) }

            val remaining = db.autocommit { Users.all() }.map { "${it.name}:${it.age}" }.sorted()
            assertEquals(listOf("Bob:26", "Carol:41"), remaining)
        }
    }
}
