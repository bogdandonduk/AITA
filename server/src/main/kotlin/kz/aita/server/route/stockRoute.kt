package kz.aita.server.route

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kz.aita.model.dataModel.GoodsItemDataModel
import kz.aita.server.db.Stock
import kz.aita.server.db.Users
import kz.aita.server.util.genericResponse
import kz.aita.server.util.genericResponseNoPayload
import kz.aita.server.util.getResponse
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.postgresql.util.PSQLException
import java.time.Instant
import java.util.*

fun Application.stockRoute() {
  routing {
    route("/stock") {
      authenticate("auth-jwt") {
        get("/get") {
          val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(UnauthorizedResponse())

          val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@get call.respond(
            UnauthorizedResponse()
          )

          val goodsItems = newSuspendedTransaction(Dispatchers.IO) {
            val noUser = Users
              .select(Users.id)
              .where { Users.id eq userId }
              .empty()

            if (noUser)
              call.respond(UnauthorizedResponse())

            val storeId = UUID.fromString(call.request.headers["store_id"])

            Stock
              .selectAll()
              .where { (Stock.userId eq userId) and (Stock.storeId eq storeId) }
              .map {
                GoodsItemDataModel(
                  id = it[Stock.id].toString(),
                  userId = it[Stock.userId].toString(),
                  storeId = it[Stock.storeId].toString(),
                  barcode = it[Stock.barcode],
                  name = it[Stock.name],
                  quantity = it[Stock.quantity],
                  categoryIds = it[Stock.categoryIds],
                  salePrices = it[Stock.salePrices],
                  returnPrices = it[Stock.returnPrices],
                  supplyPrices = it[Stock.supplyPrices],
                  createdAt = it[Stock.createdAt].toEpochMilli(),
                  isActive = it[Stock.isActive]
                )
              }
          }

          call.genericResponse(
            HttpStatusCode.OK,
            goodsItems
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

          val body = call.receive<GoodsItemDataModel>()

          var state23505Reached: Boolean

          var id: UUID? = null
          var instant = Instant.now()

          do {
            state23505Reached = try {
              id = UUID.randomUUID()
              instant = Instant.now()

              newSuspendedTransaction(Dispatchers.IO) {
                Stock.insert {
                  it[Stock.id] = id
                  it[Stock.userId] = userId
                  it[Stock.storeId] = UUID.fromString(body.storeId)
                  it[Stock.name] = body.name
                  it[Stock.barcode] = body.barcode
                  it[Stock.quantity] = body.quantity
                  it[Stock.categoryIds] = body.categoryIds
                  it[Stock.salePrices] = body.salePrices
                  it[Stock.returnPrices] = body.returnPrices
                  it[Stock.supplyPrices] = body.supplyPrices
                  it[Stock.createdAt] = instant
                  it[Stock.isActive] = body.isActive
                }
              }

              false
            } catch (exception: ExposedSQLException) {
              val constraint = (exception.cause as? PSQLException)?.serverErrorMessage?.constraint
              val isPkCollision = exception.sqlState == "23505" && constraint?.equals("stock_pkey", true) == true

              isPkCollision
            }
          } while (state23505Reached)

          id?.run {
            call.genericResponse(
              HttpStatusCode.Created,
              payload = body.copy(id = id.toString(), createdAt = instant.toEpochMilli()),
              message = getResponse("14").message
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
        }

        put("/update") {
          val principal = call.principal<JWTPrincipal>() ?: return@put call.respond(UnauthorizedResponse())
          val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@put call.respond(
            UnauthorizedResponse()
          )

          val noUser = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .select(Users.id)
              .where {
                Users.id eq userId
              }
              .empty()
          }

          if (noUser) return@put call.respond(UnauthorizedResponse())

          val body = call.receive<GoodsItemDataModel>()
          val id = UUID.fromString(body.id)

          val doesntExist = newSuspendedTransaction(Dispatchers.IO) {
            Stock
              .select(Stock.id)
              .where {
                Stock.id eq id
              }
              .empty()
          }

          if (doesntExist) return@put call.respond(UnauthorizedResponse())

          val storeId = UUID.fromString(body.storeId)

          val updated = newSuspendedTransaction(Dispatchers.IO) {

            val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: return@newSuspendedTransaction 2

            Stock.update({
              (Stock.id eq id) and (Stock.userId eq userId) and (Stock.storeId eq storeId)
            }) {
              it[Stock.id] = id
              it[Stock.userId] = userId
              it[Stock.storeId] = UUID.fromString(body.storeId)
              it[Stock.name] = body.name
              it[Stock.barcode] = body.barcode
              it[Stock.quantity] = body.quantity
              it[Stock.categoryIds] = body.categoryIds
              it[Stock.salePrices] = body.salePrices
              it[Stock.returnPrices] = body.returnPrices
              it[Stock.supplyPrices] = body.supplyPrices
            }.run {
              if (this > 0)
                0
              else
                1
            }
          }

          return@put when (updated) {
            0 -> call.genericResponse(
              HttpStatusCode.OK,
              payload = body,
              getResponse("15").message
            )

            1, 2 -> call.respond(UnauthorizedResponse())
            else -> call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )
          }
        }

        delete("/delete") {
          val principal = call.principal<JWTPrincipal>() ?: return@delete call.respond(UnauthorizedResponse())
          val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@delete call.respond(
            UnauthorizedResponse()
          )

          val body = call.receive<String>()
          val storeId = UUID.fromString(call.request.header("store_id"))
          val deleted = newSuspendedTransaction(Dispatchers.IO) {

            val id = runCatching { UUID.fromString(body) }.getOrNull() ?: return@newSuspendedTransaction 2

            if (Stock.deleteWhere { (Stock.id eq id) and (Stock.userId eq userId) and (Stock.storeId eq storeId) } > 0)
              0
            else
              1
          }

          return@delete when (deleted) {
            0 -> call.genericResponseNoPayload(
              HttpStatusCode.OK,
              message = getResponse("16").message
            )

            1, 2 -> call.respond(UnauthorizedResponse())
            else -> call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )
          }
        }
      }
    }
  }
}