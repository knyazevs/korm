package io.github.knyazevs.korm.samples.crudsqlite

import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.Column
import io.github.knyazevs.korm.Entity
import io.github.knyazevs.korm.Migration
import io.github.knyazevs.korm.Query
import io.github.knyazevs.korm.Table
import io.github.knyazevs.korm.createSqliteDatabase
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.eq
import io.github.knyazevs.korm.gt
import io.github.knyazevs.korm.migrate
import io.github.knyazevs.korm.transaction
import io.github.knyazevs.korm.autocommit

object Shop : Catalog

object Users : Table<Shop, User>("users", ::User) {
    val id by Column.Int().primaryKey()
    val name by Column.Text()
    val age by Column.Int()
}

class User : Entity() {
    var id by Users.id
    var name by Users.name
    var age by Users.age
}

private fun user(id: Int, name: String, age: Int) = User().apply {
    this.id = id; this.name = name; this.age = age
}

fun main() {
    // ":memory:" needs no files; pass a path (e.g. "shop.db") to persist instead.
    val db: Database<Shop> = createSqliteDatabase()
    db.use {
        // Migrations are idempotent and recorded in korm_migrations — safe to run on every start.
        db.migrate(
            listOf(
                Migration("001-create-users") { Users.execSql(usersDdl) },
            ),
        )

        db.transaction {
            Users.insertAll(listOf(user(1, "Alice", 30), user(2, "Bob", 25), user(3, "Carol", 41)))
        }

        val carol = db.autocommit { Users.findById(3) }
        println("findById(3) = ${carol?.name}")

        val over28 = db.autocommit { Users.find(Query(Users.age gt 28)) }
        println("age > 28   = ${over28.map { it.name }}")

        db.transaction { Users.update(Query(Users.id eq 2), user(2, "Bob", 26)) }
        db.transaction { Users.deleteWhere(Query(Users.id eq 1)) }

        val remaining = db.autocommit { Users.all() }
        println("remaining  = ${remaining.map { "${it.name}(${it.age})" }}")
    }
}

// Schema is owned by the app (raw SQL / migrations), not Korm.
internal val usersDdl = """CREATE TABLE IF NOT EXISTS "users" ("id" INTEGER NOT NULL, "name" TEXT NOT NULL, "age" INTEGER NOT NULL, PRIMARY KEY ("id"))"""
