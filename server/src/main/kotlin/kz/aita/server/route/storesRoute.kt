package kz.aita.server.route

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kz.aita.core.jsonBase
import kz.aita.model.dataModel.CompanyFormDataModel
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.dataModel.LocationDataModel
import kz.aita.model.dataModel.StoreDataModel
import kz.aita.server.db.Stores
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun Application.storesRoute() {
  routing {
    authenticate("auth-jwt") {
      get("/stores") {
        println("stores called 1")
        val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(UnauthorizedResponse())
        println("stores called 2")

        val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@get call.respond(UnauthorizedResponse())
        println("stores called 3")

        val stores = transaction {
          Stores
            .selectAll()
            .where {
              Stores.userId eq userId
            }.map {
              StoreDataModel(
                id = it[Stores.id].toString(),
                userId = it[Stores.userId].toString(),
                name = jsonBase.decodeFromString<List<LocalizedStringDataModel>>(it[Stores.name]),
                alias = it[Stores.alias]?.let { alias -> jsonBase.decodeFromString<List<LocalizedStringDataModel>>(alias) },
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

        println("stores called 5 $stores")

        call.respond(
          HttpStatusCode.OK,
          stores
        )
        println("stores called 6")

      }
    }
  }
}