package kz.aita.server.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kz.aita.core.jsonBase
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.dataModel.SupplierDataModel
import kz.aita.server.db.Suppliers
import kz.aita.server.db.Users
import kz.aita.server.util.genericResponse
import kz.aita.server.util.genericResponseNoPayload
import kz.aita.server.util.getResponse
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.suppliersRoute() {
  routing {
    route("/suppliers") {
      authenticate("auth-jwt") {
        get("/get") {
          val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(UnauthorizedResponse())
          val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@get call.respond(
            UnauthorizedResponse()
          )

          val suppliers: Pair<Int, List<SupplierDataModel>?> =
            newSuspendedTransaction(Dispatchers.IO) {
              val noUser = Users.select(Users.id).where { Users.id eq userId }.empty()

              if (noUser)
                return@newSuspendedTransaction 1 to null

              val matches = Suppliers
                .selectAll()
                .map {
                  SupplierDataModel(
                    id = it[Suppliers.id].toString(),
                    typeIds = it[Suppliers.typeIds]?.let { value -> jsonBase.decodeFromString<List<String>?>(value) },
                    name = jsonBase.decodeFromString<List<LocalizedStringDataModel>>(it[Suppliers.name]),
                    phoneNumbers = it[Suppliers.phoneNumbers]?.let { value -> jsonBase.decodeFromString<List<String>?>(value) },
                    emails = it[Suppliers.emails]?.let { value -> jsonBase.decodeFromString<List<String>?>(value) },
                    addedAt = it[Suppliers.addedAt].toEpochMilli(),
                    isActive = it[Suppliers.isActive]
                  )
                }

              0 to matches
            }

          when {
            suppliers.first == 1 -> call.respond(UnauthorizedResponse())
            suppliers.second?.isNotEmpty() == true ->
              call.genericResponse(
                HttpStatusCode.OK,
                payload = suppliers.second
              )

            else -> {
              call.genericResponseNoPayload(HttpStatusCode.InternalServerError, message = getResponse("3").message)
            }
          }
        }
      }
    }
  }
}