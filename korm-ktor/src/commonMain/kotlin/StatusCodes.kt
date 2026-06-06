package io.github.knyazevs.korm.ktor

import io.github.knyazevs.korm.CheckViolationException
import io.github.knyazevs.korm.ForeignKeyViolationException
import io.github.knyazevs.korm.KormException
import io.github.knyazevs.korm.NotNullViolationException
import io.github.knyazevs.korm.QueryException
import io.github.knyazevs.korm.UniqueViolationException
import io.ktor.http.HttpStatusCode

/**
 * Maps a [KormException] to a sensible HTTP status code:
 * - [UniqueViolationException] -> 409 Conflict
 * - [ForeignKeyViolationException] / [NotNullViolationException] / [CheckViolationException] -> 400 Bad Request
 * - any other [QueryException] / [KormException] -> 500 Internal Server Error
 *
 * This is a pure function on purpose — korm-ktor does not install a `StatusPages` handler or
 * dictate a response body. Plug it into your own handler so you control the body format:
 * ```
 * install(StatusPages) {
 *     exception<KormException> { call, e -> call.respond(e.httpStatusCode(), ErrorDto(e.message)) }
 * }
 * ```
 */
fun KormException.httpStatusCode(): HttpStatusCode = when (this) {
    is UniqueViolationException -> HttpStatusCode.Conflict
    is ForeignKeyViolationException -> HttpStatusCode.BadRequest
    is NotNullViolationException -> HttpStatusCode.BadRequest
    is CheckViolationException -> HttpStatusCode.BadRequest
    is QueryException -> HttpStatusCode.InternalServerError
    else -> HttpStatusCode.InternalServerError
}
