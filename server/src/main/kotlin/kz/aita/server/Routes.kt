package kz.aita.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticFiles
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kz.aita.GenericGoodsCategoryDataModel
import kz.aita.GenericGoodsItemDataModel
import kz.aita.GoodsBatchDataModel
import kz.aita.GoodsItemDataModel
import kz.aita.LocalizedStringDataModel
import kz.aita.StoreDataModel
import kz.aita.SupplierDataModel
import kz.aita.TokenPair
import kz.aita.UserAccountDataModel
import kz.aita.UserAccountUpdateDataModel
import kz.aita.UserAuthLogInDataModel
import kz.aita.UserAuthSignUpDataModel
import kz.aita.UserBalanceDataModel
import kz.aita.jsonBase
import kz.aita.server.db.GenericGoodsCategories
import kz.aita.server.db.GenericGoodsItems
import kz.aita.server.db.Stock
import kz.aita.server.db.StockBatches
import kz.aita.server.db.StoreUsers
import kz.aita.server.db.Stores
import kz.aita.server.db.Suppliers
import kz.aita.server.db.UserBalances
import kz.aita.server.db.Users
import kz.aita.server.encrypt.Pw
import kz.aita.server.jwt.TokenService
import kz.aita.server.util.checkPrincipal
import kz.aita.server.util.genericResponse
import kz.aita.server.util.genericResponseNoPayload
import kz.aita.server.util.getResponse
import kz.aita.server.util.metaFrom
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.postgresql.util.PSQLException
import java.io.File
import java.time.Instant
import java.util.UUID

fun Application.routes(tokenService: TokenService) {
  routing {
    route("/auth") {
      post("/signUp") {
        try {
          val body = call.receive<UserAuthSignUpDataModel>()
          val phoneNumber = body.phoneNumber.trim().lowercase()
          val email = body.email.trim().lowercase()

          val conflictResult = newSuspendedTransaction(Dispatchers.IO) {
            val userWithPhoneNumberExists = Users
              .select(Users.phoneNumber)
              .where { Users.phoneNumber eq phoneNumber }
              .empty()
              .not()

            val userWithEmailExists = Users
              .select(Users.email)
              .where { Users.email eq email }
              .empty()
              .not()

            if (userWithPhoneNumberExists && userWithEmailExists)
              return@newSuspendedTransaction 1
            else if (userWithPhoneNumberExists)
              return@newSuspendedTransaction 2
            else if (userWithEmailExists)
              return@newSuspendedTransaction 3

            return@newSuspendedTransaction 0
          }

          when (conflictResult) {
            1 -> {
              return@post call.genericResponseNoPayload(
                HttpStatusCode.Conflict,
                message = getResponse("2").message
              )
            }

            2 -> {
              return@post call.genericResponseNoPayload(
                HttpStatusCode.Conflict,
                message = getResponse("0").message
              )
            }

            3 -> {
              return@post call.genericResponseNoPayload(
                HttpStatusCode.Conflict,
                message = getResponse("1").message
              )
            }
          }

          val firstName = body.firstName.trim()
          val lastName = body.lastName.trim()
          val countryLocale = body.countryLocale.trim().lowercase()

          var id = UUID.randomUUID()

          val hash = Pw.hash(body.password.toCharArray())
          val instant = Instant.now()

          var state23505Reached: Boolean

          do {
            state23505Reached = try {
              id = UUID.randomUUID()

              newSuspendedTransaction(Dispatchers.IO) {
                Users.insert {
                  it[Users.id] = id
                  it[Users.phoneNumber] = phoneNumber
                  it[Users.email] = email
                  it[Users.firstName] = firstName
                  it[Users.lastName] = lastName
                  it[Users.countryLocale] = countryLocale
                  it[Users.workerIds] = null
                  it[Users.supplierIds] = null
                  it[Users.passwordHash] = hash
                  it[Users.createdAt] = instant
                  it[Users.isActive] = true
                }

                false
              }
            } catch (exception: ExposedSQLException) {
              val constraint = (exception.cause as? PSQLException)?.serverErrorMessage?.constraint
              val isPkCollision = exception.sqlState == "23505" && constraint?.equals("users_pkey", true) == true

              isPkCollision
            }
          } while (state23505Reached)

          id?.run {
            val tokenPair: TokenPair = tokenService.newPair(this, metaFrom(call))

            call.genericResponse(
              status = HttpStatusCode.Created,
              tokenPair
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
        } catch (throwable: Throwable) {
          call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
          throwable.printStackTrace()
        }
      }

      post("/logIn") {
        val body = call.receive<UserAuthLogInDataModel>()

        val login = body.login.trim().lowercase()

        val user = newSuspendedTransaction(Dispatchers.IO) {
          Users.selectAll().where { (Users.phoneNumber eq login) or (Users.email eq login) }.singleOrNull()
        } ?: return@post call.respond(UnauthorizedResponse())

        val ok = Pw.verify(body.password.toCharArray(), user[Users.passwordHash])

        if (!ok)
          return@post call.respond(UnauthorizedResponse())

        val tokenPair: TokenPair = tokenService.newPair(user[Users.id], metaFrom(call))

        call.genericResponse<TokenPair>(HttpStatusCode.OK, tokenPair)
      }

      delete("/logOut") {
        val body = call.receive<String>()

        try {
          tokenService.revoke(body)
        } catch (throwable: Throwable) {
          throwable.printStackTrace()
        }

        call.genericResponseNoPayload(HttpStatusCode.OK, message = getResponse("8").message)
      }

      post("/refresh") {
        val body = call.receive<String>()
        try {
          val newTokens = tokenService.rotate(body, metaFrom(call))
          call.genericResponse(HttpStatusCode.OK, newTokens)
        } catch (throwable: Throwable) {
          call.respond(UnauthorizedResponse())
          throwable.printStackTrace()
        }
      }
    }

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

    staticFiles("config/global", File("AITA/server/config/app/global.json"))

    staticFiles("res/string", File("AITA/server/assets/values/strings.json"))
    staticFiles("res/dimension", File("AITA/server/assets/values/dimensions.json"))
    staticFiles("res/color", File("AITA/server/assets/values/colors.json"))
    staticFiles("res/drawableConfig", File("AITA/server/assets/drawable/drawables.json"))
    staticFiles("res/drawable", File("AITA/server/assets/drawable"))

    route("/stockBatches") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get

          val batches = newSuspendedTransaction(Dispatchers.IO) {
            val noUser = Users
              .select(Users.id)
              .where { Users.id eq userId }
              .empty()

            if (noUser)
              call.respond(UnauthorizedResponse())

            val storeId = UUID.fromString(call.request.headers["store_id"])

            StockBatches
              .selectAll()
              .where { (StockBatches.userId eq userId) and (StockBatches.storeId eq storeId) }
              .map {
                GoodsBatchDataModel(
                  id = it[StockBatches.id].toString(),
                  goodsItemId = it[StockBatches.goodsItemId].toString(),

                  userId = it[StockBatches.userId].toString(),
                  storeId = it[StockBatches.storeId].toString(),
                  supplierId = it[StockBatches.storeId].toString(),

                  salePrice = it[StockBatches.salePrice],
                  returnPrice = it[StockBatches.returnPrice],
                  supplyPrice = it[StockBatches.supplyPrice],

                  quantity = it[StockBatches.quantity],

                  supplyTime = it[StockBatches.supplyTime].toEpochMilli(),
                  expirationTime = it[StockBatches.expirationTime].toEpochMilli(),

                  shelfQueue = it[StockBatches.shelfQueue],
                  createdAt = it[StockBatches.createdAt].toEpochMilli(),
                  createdByUserId = it[StockBatches.createdByUserId].toString(),
                  isActive = it[StockBatches.isActive]
                )
              }
          }

          call.genericResponse(
            HttpStatusCode.OK,
            batches
          )
        }

        post("/add") {
          val userId = call.checkPrincipal() ?: return@post

          val noUser = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .select(Users.id)
              .where {
                Users.id eq userId
              }
              .empty()
          }

          if (noUser) return@post call.respond(UnauthorizedResponse())

          val body = call.receive<GoodsBatchDataModel>()

          var state23505Reached: Boolean

          var id: UUID? = null
          var instant = Instant.now()

          do {
            state23505Reached = try {
              id = UUID.randomUUID()
              instant = Instant.now()

              newSuspendedTransaction(Dispatchers.IO) {
                StockBatches.insert {
                  it[StockBatches.id] = id
                  it[StockBatches.goodsItemId] = UUID.fromString(body.goodsItemId)

                  it[StockBatches.userId] = userId
                  it[StockBatches.storeId] = UUID.fromString(body.storeId)
                  it[StockBatches.supplierId] = UUID.fromString(body.supplierId)

                  it[StockBatches.salePrice] = body.salePrice
                  it[StockBatches.returnPrice] = body.returnPrice
                  it[StockBatches.supplyPrice] = body.supplyPrice
                  it[StockBatches.quantity] = body.quantity

                  it[StockBatches.supplyTime] = Instant.ofEpochMilli(body.supplyTime)
                  it[StockBatches.expirationTime] = Instant.ofEpochMilli(body.expirationTime)

                  it[StockBatches.shelfQueue] = body.shelfQueue

                  it[StockBatches.createdAt] = Instant.now()
                  it[StockBatches.createdByUserId] = userId

                  it[StockBatches.isActive] = body.isActive
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
              message = getResponse("17").message
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
        }

        put("/update") {
          val userId = call.checkPrincipal() ?: return@put

          val noUser = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .select(Users.id)
              .where {
                Users.id eq userId
              }
              .empty()
          }

          if (noUser) return@put call.respond(UnauthorizedResponse())

          val body = call.receive<GoodsBatchDataModel>()
          val id = UUID.fromString(body.id)

          val doesntExist = newSuspendedTransaction(Dispatchers.IO) {
            Stock
              .select(StockBatches.id)
              .where {
                StockBatches.id eq id
              }
              .empty()
          }

          if (doesntExist) return@put call.respond(UnauthorizedResponse())

          val storeId = UUID.fromString(body.storeId)

          val updated = newSuspendedTransaction(Dispatchers.IO) {

            val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: return@newSuspendedTransaction 2

            StockBatches.update({
              (StockBatches.id eq id) and (StockBatches.userId eq userId) and (StockBatches.storeId eq storeId)
            }) {
              it[StockBatches.userId] = userId
              it[StockBatches.storeId] = UUID.fromString(body.storeId)
              it[StockBatches.supplierId] = UUID.fromString(body.supplierId)

              it[StockBatches.salePrice] = body.salePrice
              it[StockBatches.returnPrice] = body.returnPrice
              it[StockBatches.supplyPrice] = body.supplyPrice
              it[StockBatches.quantity] = body.quantity

              it[StockBatches.supplyTime] = Instant.ofEpochMilli(body.supplyTime)
              it[StockBatches.expirationTime] = Instant.ofEpochMilli(body.expirationTime)

              it[StockBatches.shelfQueue] = body.shelfQueue

              it[StockBatches.isActive] = body.isActive
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
              getResponse("18").message
            )

            1, 2 -> call.respond(UnauthorizedResponse())
            else -> call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )
          }
        }

        delete("/delete") {
          val userId = call.checkPrincipal() ?: return@delete

          val body = call.receive<String>()
          val storeId = UUID.fromString(call.request.header("store_id"))
          val deleted = newSuspendedTransaction(Dispatchers.IO) {

            val id = runCatching { UUID.fromString(body) }.getOrNull() ?: return@newSuspendedTransaction 2

            if (StockBatches.deleteWhere { (StockBatches.id eq id) and (StockBatches.userId eq userId) and (StockBatches.storeId eq storeId) } > 0)
              0
            else
              1
          }

          return@delete when (deleted) {
            0 -> call.genericResponseNoPayload(
              HttpStatusCode.OK,
              message = getResponse("19").message
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

    route("/stock") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get

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
                  measurementUnitId = it[Stock.measurementUnitId].toString(),
                  categoryIds = it[Stock.categoryIds],
                  salePrices = it[Stock.salePrices],
                  returnPrices = it[Stock.returnPrices],
                  supplyPrices = it[Stock.supplyPrices],
                  isQuickItem = it[Stock.isQuickItem],
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
          val userId = call.checkPrincipal() ?: return@post

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
                  it[Stock.measurementUnitId] = body.measurementUnitId
                  it[Stock.barcode] = body.barcode
                  it[Stock.categoryIds] = body.categoryIds
                  it[Stock.salePrices] = body.salePrices
                  it[Stock.returnPrices] = body.returnPrices
                  it[Stock.supplyPrices] = body.supplyPrices
                  it[Stock.isQuickItem] = body.isQuickItem
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
          val userId = call.checkPrincipal() ?: return@put

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
              it[Stock.measurementUnitId] = body.measurementUnitId
              it[Stock.barcode] = body.barcode
              it[Stock.categoryIds] = body.categoryIds
              it[Stock.salePrices] = body.salePrices
              it[Stock.returnPrices] = body.returnPrices
              it[Stock.supplyPrices] = body.supplyPrices
              it[Stock.isQuickItem] = body.isQuickItem
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
          val userId = call.checkPrincipal() ?: return@delete

          val noUser = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .select(Users.id)
              .where {
                Users.id eq userId
              }
              .empty()
          }

          if (noUser) return@delete call.respond(UnauthorizedResponse())

          val body = call.receive<String>()
          val storeId = UUID.fromString(call.request.header("store_id"))
          val id = runCatching { UUID.fromString(body) }.getOrNull()

          if (id == null) {
            call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )

            return@delete
          }

          val deleted = newSuspendedTransaction(Dispatchers.IO) {
            if (Stock.deleteWhere { (Stock.id eq id) and (Stock.userId eq userId) and (Stock.storeId eq storeId) } > 0)
              0
            else
              1
          }

          return@delete when (deleted) {
            0 -> call.genericResponse(
              HttpStatusCode.OK,
              message = getResponse("16").message,
              payload = id.toString()
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

    route("/stores") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get

          val stores = newSuspendedTransaction(Dispatchers.IO) {
            val noUser = Users
              .select(Users.id)
              .where { Users.id eq userId }
              .empty()

            if (noUser)
              call.respond(UnauthorizedResponse())

            Stores
              .innerJoin(StoreUsers, { Stores.id }, { StoreUsers.storeId })
              .selectAll()
              .where { StoreUsers.userId eq userId }
              .map {
                StoreDataModel(
                  id = it[Stores.id].toString(),
                  userIds = it[Stores.ownerUserIds],
                  storeTypeIds = it[Stores.storeTypeIds],
                  name = it[Stores.name],
                  alias = it[Stores.alias],
                  description = it[Stores.description],
                  companyForms = it[Stores.companyForms],
                  location = it[Stores.location],
                  phoneNumbers = it[Stores.phoneNumbers],
                  emails = it[Stores.emails],
                  countryLocales = it[Stores.countryLocales],
                  createdAt = it[Stores.createdAt].toEpochMilli()
                )
              }
          }

          call.genericResponse(
            HttpStatusCode.OK,
            stores
          )
        }

        post("/add") {
          val userId = call.checkPrincipal() ?: return@post

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
                  it[Stores.ownerUserIds] = listOf(userId.toString())
                  it[Stores.storeTypeIds] = body.storeTypeIds
                  it[Stores.name] = body.name
                  it[Stores.alias] = body.alias
                  it[Stores.description] = body.description
                  it[Stores.companyForms] = body.companyForms
                  it[Stores.location] = body.location
                  it[Stores.phoneNumbers] = body.phoneNumbers
                  it[Stores.emails] = body.emails
                  it[Stores.countryLocales] = body.countryLocales
                  it[Stores.createdAt] = instant
                }

                StoreUsers.insertIgnore {           // composite PK avoids dup (store_id,user_id)
                  it[StoreUsers.storeId] = id
                  it[StoreUsers.userId] = userId // from JWT principal
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
            call.genericResponse(
              HttpStatusCode.Created,
              payload = body.copy(id = id.toString(), createdAt = instant.toEpochMilli()),
              message = getResponse("10").message
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
        }

        put("/update") {
          val userId = call.checkPrincipal() ?: return@put

          val body = call.receive<StoreDataModel>()

          val updated = newSuspendedTransaction(Dispatchers.IO) {

            val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: return@newSuspendedTransaction 2

            Stores.update({
              (Stores.id eq id) and exists(
                StoreUsers.selectAll().where { (StoreUsers.storeId eq id) and (StoreUsers.userId eq userId) })
            }) {
              it[Stores.storeTypeIds] = body.storeTypeIds
              it[Stores.name] = body.name
              it[Stores.alias] = body.alias
              it[Stores.description] = body.description
              it[Stores.companyForms] = body.companyForms
              it[Stores.location] = body.location
              it[Stores.phoneNumbers] = body.phoneNumbers
              it[Stores.emails] = body.emails
              it[Stores.countryLocales] = body.countryLocales
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
              getResponse("11").message
            )

            1, 2 -> call.respond(UnauthorizedResponse())
            else -> call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )
          }
        }

        delete("/delete") {
          val userId = call.checkPrincipal() ?: return@delete

          val body = call.receive<String>()

          val deleted = newSuspendedTransaction(Dispatchers.IO) {

            val id = runCatching { UUID.fromString(body) }.getOrNull() ?: return@newSuspendedTransaction 2

            if (Stores.deleteWhere { (Stores.id eq id) and (Stores.ownerUserIds.contains(userId.toString())) } > 0)
              0
            else
              1
          }

          return@delete when (deleted) {
            0 -> call.genericResponseNoPayload(
              HttpStatusCode.OK,
              message = getResponse("12").message
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

    route("/suppliers") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get

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

    route("/balance") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get

          val balance = newSuspendedTransaction(Dispatchers.IO) {
            val noUser = Users
              .select(Users.id)
              .where { Users.id eq userId }
              .empty()

            if (noUser)
              call.respond(UnauthorizedResponse())

            UserBalances
              .selectAll()
              .where { UserBalances.userId eq userId }
              .singleOrNull()
              ?.let {
                UserBalanceDataModel(
                  it[UserBalances.value],
                  it[UserBalances.currencyCode],
                  it[UserBalances.history]
                )
              }
          }

          balance?.let {
            call.genericResponse(
              HttpStatusCode.OK,
              balance
            )
          } ?: call.genericResponseNoPayload(
            HttpStatusCode.NotFound,
            getResponse("13").message
          )

        }

//        put("/topUp") {
//          val userId = call.checkPrincipal() ?: return@put
//
//          val body = call.receive<StoreDataModel>()
//
//          val updated = newSuspendedTransaction(Dispatchers.IO) {
//
//            val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: return@newSuspendedTransaction 2
//
//            Stores.update({
//              (Stores.id eq id) and exists(
//                StoreUsers.selectAll().where { (StoreUsers.storeId eq id) and (StoreUsers.userId eq userId) })
//            }) {
//              it[Stores.storeTypeIds] = body.storeTypeIds
//              it[Stores.name] = body.name
//              it[Stores.alias] = body.alias
//              it[Stores.description] = body.description
//              it[Stores.companyForms] = body.companyForms
//              it[Stores.location] = body.location
//              it[Stores.phoneNumbers] = body.phoneNumbers
//              it[Stores.emails] = body.emails
//              it[Stores.countryLocales] = body.countryLocales
//            }.run {
//              if (this > 0)
//                0
//              else
//                1
//            }
//          }
//
//          return@put when (updated) {
//            0 -> call.genericResponse(
//              HttpStatusCode.OK,
//              payload = body,
//              getResponse("11").message
//            )
//
//            1, 2 -> call.respond(UnauthorizedResponse())
//            else -> call.genericResponseNoPayload(
//              status = HttpStatusCode.InternalServerError,
//              message = getResponse("3").message
//            )
//          }
//        }
      }
    }

    route("/user") {
      authenticate("auth-jwt") {
        get("/get") {
          val uuid = call.checkPrincipal() ?: return@get

          val user = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .selectAll()
              .where {
                Users.id eq uuid
              }
              .limit(1)
              .singleOrNull()
          } ?: return@get call.respond(UnauthorizedResponse())

          call.genericResponse(
            HttpStatusCode.OK,
            UserAccountDataModel(
              id = user[Users.id].toString(),
              phoneNumber = user[Users.phoneNumber],
              email = user[Users.email],
              firstName = user[Users.firstName],
              lastName = user[Users.lastName],
              countryLocale = user[Users.countryLocale],
              workerAccountIds = user[Users.workerIds],
              supplierAccountIds = user[Users.supplierIds],
              createdAt = user[Users.createdAt].toEpochMilli(),
              isActive = user[Users.isActive]
            )
          )
        }

        put("/update") {
          val uuid = call.checkPrincipal() ?: return@put

          val body = call.receive<UserAccountUpdateDataModel>()
          val newAccount = body.account

          val phoneNumber = newAccount.phoneNumber.trim().lowercase()
          val email = newAccount.email.trim().lowercase()
          val firstName = newAccount.firstName.trim()
          val lastName = newAccount.lastName.trim()
          val countryLocale = newAccount.countryLocale.trim().lowercase()
          val isActive = newAccount.isActive

          val updated = newSuspendedTransaction(Dispatchers.IO) {
            val existingUser =
              Users
                .selectAll()
                .where {
                  Users.id eq uuid
                }
                .forUpdate()
                .limit(1)
                .singleOrNull() ?: return@newSuspendedTransaction "unauthorized"

            if (!Pw.verify(body.password.toCharArray(), existingUser[Users.passwordHash]))
              return@newSuspendedTransaction "password_mismatch"

            val phoneNumberClash = Users
              .select(Users.id, Users.phoneNumber)
              .where {
                (Users.phoneNumber eq newAccount.phoneNumber) and (Users.id neq uuid)
              }
              .empty()
              .not()


            val emailClash = Users
              .select(Users.id, Users.email)
              .where {
                (Users.email eq newAccount.email) and (Users.id neq uuid)
              }
              .empty()
              .not()

            if (phoneNumberClash && emailClash)
              return@newSuspendedTransaction "phone_number_and_email_clash"
            else if (phoneNumberClash)
              return@newSuspendedTransaction "phone_number_clash"
            else if (emailClash)
              return@newSuspendedTransaction "email_clash"

            val newHash = body.newPassword
              ?.takeIf {
                it.isNotEmpty()
                    && it.isNotBlank()
                    && !Pw.verify(it.toCharArray(), existingUser[Users.passwordHash])
              }?.let {
                Pw.hash(it.toCharArray())
              }

            Users.update({ Users.id eq uuid }) {
              if (existingUser[Users.phoneNumber] != phoneNumber)
                it[Users.phoneNumber] = phoneNumber

              if (existingUser[Users.email] != email)
                it[Users.email] = email

              if (existingUser[Users.firstName] != firstName)
                it[Users.firstName] = firstName

              if (existingUser[Users.lastName] != lastName)
                it[Users.lastName] = lastName

              if (existingUser[Users.countryLocale] != countryLocale)
                it[Users.countryLocale] = countryLocale

              newHash?.run {
                it[Users.passwordHash] = this
              }

              if (existingUser[Users.isActive] != isActive)
                it[Users.isActive] = isActive
            }

            "ok"
          }

          when (updated) {
            "ok" -> call.genericResponse(
              HttpStatusCode.OK,
              payload = body.account,
              message = getResponse("9").message
            )

            "unauthorized", "password_mismatch" -> call.respond(UnauthorizedResponse())

            "phone_number_and_email_clash" -> call.genericResponseNoPayload(
              HttpStatusCode.Conflict,
              message = getResponse("2").message
            )

            "phone_number_clash" -> call.genericResponseNoPayload(
              HttpStatusCode.Conflict,
              message = getResponse("0").message
            )

            "email_clash" -> call.genericResponseNoPayload(
              HttpStatusCode.Conflict,
              message = getResponse("1").message
            )

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