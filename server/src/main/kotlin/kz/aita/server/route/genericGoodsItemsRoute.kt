package kz.aita.server.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kz.aita.core.jsonBase
import kz.aita.model.dataModel.GenericGoodsItemDataModel
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.server.db.GenericGoodsItems
import kz.aita.server.db.Users
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

fun Application.genericGoodsItemsRoute() {
  routing {
    route("/generic") {
      authenticate("auth-jwt") {
        route("/goodsItems") {
          get("/get") {
            val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(UnauthorizedResponse())
            val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@get call.respond(
              UnauthorizedResponse()
            )

            val genericGoodsItems = newSuspendedTransaction(Dispatchers.IO) {
              val noUser = Users.select(Users.id).where { Users.id eq userId }.empty()

              if (noUser)
                return@newSuspendedTransaction call.respond(UnauthorizedResponse())

              GenericGoodsItems
                .selectAll()
                .map {
                  GenericGoodsItemDataModel(
                    id = it[GenericGoodsItems.id].toString(),
                    barcode = it[GenericGoodsItems.barcode]?.let { value ->
                      jsonBase.decodeFromString<List<String>>(
                        value
                      )
                    },
                    name = it[GenericGoodsItems.name].let { value ->
                      jsonBase.decodeFromString<List<LocalizedStringDataModel>>(
                        value
                      )
                    },
                    typeIds = it[GenericGoodsItems.typeIds]?.let { value ->
                      jsonBase.decodeFromString<List<String>>(
                        value
                      )
                    },
                    categoryIds = it[GenericGoodsItems.categoryIds]?.let { value ->
                      jsonBase.decodeFromString<List<String>>(
                        value
                      )
                    },
                    supplierIds = it[GenericGoodsItems.supplierIds]?.let { value ->
                      jsonBase.decodeFromString<List<String>>(
                        value
                      )
                    },
                    manufacturerIds = it[GenericGoodsItems.manufacturerIds]?.let { value ->
                      jsonBase.decodeFromString<List<String>>(
                        value
                      )
                    },
                  )
                }
            }

            call.respond(
              HttpStatusCode.OK,
              genericGoodsItems
            )
          }
        }
      }
    }
  }
}