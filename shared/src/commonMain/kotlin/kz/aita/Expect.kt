package kz.aita

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

expect val Dispatchers.io: CoroutineDispatcher

expect var tokenStore: DataStore<TokenPair>?
expect var userAccountStore: DataStore<UserAccountDataModel>?
expect var cacheDirPath: String

expect fun getHttpClientEngine(): HttpClientEngine

expect fun getSystemLocaleLanguage(): String?

expect fun getPlatformName(): String

expect var sqlDelightDriver: SqlDriver?