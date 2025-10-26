package kz.aita.server.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.request.receive
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
import kz.aita.server.util.checkPrincipal
import kz.aita.server.util.genericResponse
import kz.aita.server.util.genericResponseNoPayload
import kz.aita.server.util.getResponse
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

fun Application.genericGoodsItemsRoute() {
  routing {
    route("/generic") {
      authenticate("auth-jwt") {
        route("/goodsItems") {
          get("/get") {
            val userId = call.checkPrincipal() ?: return@get

            val barcode = call.request.header("barcode")

            val genericGoodsItems: Pair<Int, List<GenericGoodsItemDataModel>?> =
              newSuspendedTransaction(Dispatchers.IO) {
                val noUser = Users.select(Users.id).where { Users.id eq userId }.empty()

                if (noUser)
                  return@newSuspendedTransaction 1 to null

                val matches = GenericGoodsItems
                  .selectAll()
                  .where { GenericGoodsItems.barcode.contains(listOf(barcode)) }
                  .map {

                    GenericGoodsItemDataModel(
                      id = it[GenericGoodsItems.id].toString(),
                      barcode = it[GenericGoodsItems.barcode],
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

                0 to matches
              }

            when {
              genericGoodsItems.first == 1 -> call.respond(UnauthorizedResponse())
              genericGoodsItems.second?.isNotEmpty() == true -> {
                call.genericResponse(
                  HttpStatusCode.OK,
                  payload = genericGoodsItems.second
                )
              }

              else -> {
                call.genericResponseNoPayload(HttpStatusCode.NotFound, message = getResponse("13").message)
              }
            }
          }
        }
      }
    }
  }
}