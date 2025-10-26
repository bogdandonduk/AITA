package kz.aita.server.route

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kz.aita.model.dataModel.GenericGoodsCategoryDataModel
import kz.aita.server.db.GenericGoodsCategories
import kz.aita.server.db.Users
import kz.aita.server.util.checkPrincipal
import kz.aita.server.util.genericResponse
import kz.aita.server.util.genericResponseNoPayload
import kz.aita.server.util.getResponse
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.genericGoodsCategoriesRoute() {
  routing {
    route("/generic") {
      authenticate("auth-jwt") {
        route("/goodsCategories") {
          get("/get") {
            val userId = call.checkPrincipal() ?: return@get

            val genericGoodsCategories: Pair<Int, List<GenericGoodsCategoryDataModel>?> =
              newSuspendedTransaction(Dispatchers.IO) {
                val noUser = Users.select(Users.id).where { Users.id eq userId }.empty()

                if (noUser)
                  return@newSuspendedTransaction 1 to null

                val matches = GenericGoodsCategories
                  .selectAll()
                  .map {
                    GenericGoodsCategoryDataModel(
                      id = it[GenericGoodsCategories.id].toString(),
                      typeIds = it[GenericGoodsCategories.typeIds],
                      name = it[GenericGoodsCategories.name],
                      quantityUnitId = it[GenericGoodsCategories.quantityUnitId],
                      imagePaths = it[GenericGoodsCategories.imagePaths]
                    )
                  }

                0 to matches
              }

            when {
              genericGoodsCategories.first == 1 -> call.respond(UnauthorizedResponse())
              genericGoodsCategories.second?.isNotEmpty() == true -> {
                call.genericResponse(
                  HttpStatusCode.OK,
                  payload = genericGoodsCategories.second
                )
              }

              else -> {
                call.genericResponseNoPayload(HttpStatusCode.InternalServerError, message = getResponse("3").message)
              }
            }
          }
        }
      }
    }
  }
}