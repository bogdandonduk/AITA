package kz.aita.server.route

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kz.aita.core.jsonBase
import kz.aita.model.dataModel.CompanyFormDataModel
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.dataModel.LocationDataModel
import kz.aita.model.dataModel.StoreDataModel
import kz.aita.server.db.Stores
import kz.aita.server.db.Users
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.postgresql.util.PSQLException
import java.time.Instant
import java.util.*

fun Application.storesRoute() {
  routing {
    route("/stores") {
      authenticate("auth-jwt") {
        get("/get") {
          val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(UnauthorizedResponse())

          val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@get call.respond(
            UnauthorizedResponse()
          )

          val stores = newSuspendedTransaction(Dispatchers.IO) {
            val noUser = Users
              .select(Users.id)
              .where { Users.id eq userId }
              .empty()

            if (noUser)
              call.respond(UnauthorizedResponse())

            Stores
              .selectAll()
              .where {
                Stores.userId eq userId
              }.map {
                StoreDataModel(
                  id = it[Stores.id].toString(),
                  userId = it[Stores.userId].toString(),
                  name = jsonBase.decodeFromString<List<LocalizedStringDataModel>>(it[Stores.name]),
                  alias = it[Stores.alias]?.let { alias ->
                    jsonBase.decodeFromString<List<LocalizedStringDataModel>>(
                      alias
                    )
                  },
                  description = it[Stores.description]?.let { description ->
                    jsonBase.decodeFromString<List<LocalizedStringDataModel>>(
                      description
                    )
                  },
                  companyForm = jsonBase.decodeFromString<CompanyFormDataModel>(it[Stores.companyForm]),
                  location = jsonBase.decodeFromString<LocationDataModel>(it[Stores.location]),
                  phoneNumbers = jsonBase.decodeFromString<List<String>>(it[Stores.phoneNumbers]),
                  emails = jsonBase.decodeFromString<List<String>>(it[Stores.emails]),
                  createdAt = it[Stores.createdAt].toEpochMilli(),
                  isActive = it[Stores.isActive]
                )
              }
          }

          call.respond(
            HttpStatusCode.OK,
            stores
          )
        }

        post("/add") {
          val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(UnauthorizedResponse())

          val userId = runCatching { UUID.fromString(principal.subject) }
            .getOrNull() ?: return@post call.respond(UnauthorizedResponse())

          val noUser = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .select(Users.id)
              .where {
                Users.id eq userId
              }
              .empty()
          }
          if (noUser) return@post call.respond(UnauthorizedResponse())

          val body = call.receive<StoreDataModel>()

          var state23505Reached: Boolean

          var id: UUID? = null
          var instant = Instant.now()

          do {
            state23505Reached = try {
              id = UUID.randomUUID()
              instant = Instant.now()

              newSuspendedTransaction(Dispatchers.IO) {
                Stores.insert {
                  it[Stores.id] = id
                  it[Stores.userId] = userId
                  it[Stores.name] = jsonBase.encodeToString(body.name)
                  it[Stores.alias] = body.alias?.let { alias -> jsonBase.encodeToString(alias) }
                  it[Stores.description] = body.description?.let { description -> jsonBase.encodeToString(description) }
                  it[Stores.companyForm] = jsonBase.encodeToString(body.companyForm)
                  it[Stores.location] = jsonBase.encodeToString(body.location)
                  it[Stores.phoneNumbers] = jsonBase.encodeToString(body.phoneNumbers)
                  it[Stores.emails] = jsonBase.encodeToString(body.emails)
                  it[Stores.createdAt] = instant
                  it[Stores.isActive] = body.isActive
                }
              }

              false
            } catch (exception: ExposedSQLException) {
              val constraint = (exception.cause as? PSQLException)?.serverErrorMessage?.constraint
              val isPkCollision = exception.sqlState == "23505" && constraint?.equals("stores_pkey", true) == true

              isPkCollision
            }
          } while (state23505Reached)

          id?.run {
            call.respond(
              HttpStatusCode.Created,
              body.copy(id = id.toString(), createdAt = instant.toEpochMilli())
            )
          } ?: call.respond(HttpStatusCode.InternalServerError)
        }


        put("/update") {
          val principal = call.principal<JWTPrincipal>() ?: return@put call.respond(UnauthorizedResponse())
          val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@put call.respond(
            UnauthorizedResponse()
          )

          val body = call.receive<StoreDataModel>()

          val updated = newSuspendedTransaction(Dispatchers.IO) {

            val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: return@newSuspendedTransaction 2

            Stores.update({ (Stores.id eq id) and (Stores.userId eq userId) }) {
              it[Stores.name] = jsonBase.encodeToString(body.name)
              it[Stores.alias] = body.alias?.let { alias -> jsonBase.encodeToString(alias) }
              it[Stores.description] = body.description?.let { description -> jsonBase.encodeToString(description) }
              it[Stores.companyForm] = jsonBase.encodeToString(body.companyForm)
              it[Stores.location] = jsonBase.encodeToString(body.location)
              it[Stores.phoneNumbers] = jsonBase.encodeToString(body.phoneNumbers)
              it[Stores.emails] = jsonBase.encodeToString(body.emails)
              it[Stores.isActive] = body.isActive
            }.run {
              if (this > 0)
                0
              else
                1
            }
          }

          return@put when (updated) {
            0 -> call.respond(
              HttpStatusCode.OK,
              body
            )

            1, 2 -> call.respond(UnauthorizedResponse())
            else -> call.respond(HttpStatusCode.InternalServerError)
          }
        }

        delete("/delete") {
          val principal = call.principal<JWTPrincipal>() ?: return@delete call.respond(UnauthorizedResponse())
          val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@delete call.respond(
            UnauthorizedResponse()
          )

          val body = call.receive<StoreDataModel>()

          val deleted = newSuspendedTransaction(Dispatchers.IO) {

            val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: return@newSuspendedTransaction 2

            if (Stores.deleteWhere { (Stores.id eq id) and (Stores.userId eq userId) } > 0)
              0
            else
              1
          }

          return@delete when (deleted) {
            0 -> call.respond(
              HttpStatusCode.OK,
              body
            )

            1, 2 -> call.respond(UnauthorizedResponse())
            else -> call.respond(HttpStatusCode.InternalServerError)
          }
        }
      }
    }
  }
}