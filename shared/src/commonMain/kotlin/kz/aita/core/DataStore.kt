package kz.aita.core

import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.wrapper.TokenPair

interface DataStore<T> {
  suspend fun get(): T?
  suspend fun set(value: T?)
}

expect var tokenStore: DataStore<TokenPair>?
expect var userAccountStore: DataStore<UserAccountDataModel>?
