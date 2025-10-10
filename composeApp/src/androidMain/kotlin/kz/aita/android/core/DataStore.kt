package kz.aita.android.core

import kz.aita.android.app.system.core.EncryptedDataStore
import kz.aita.core.DataStore
import kz.aita.core.jsonBase
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.wrapper.TokenPair

class TokenStore: DataStore<TokenPair> {
  private val key = "key_auth_tokens"

  override suspend fun get(): TokenPair? {
    return EncryptedDataStore.get(key)?.run { jsonBase.decodeFromString<TokenPair>(this) }
  }

  override suspend fun set(value: TokenPair?) {
    EncryptedDataStore.set(key, value?.run { jsonBase.encodeToString(this) })
  }
}

class UserAccountStore: DataStore<UserAccountDataModel> {
  private val key = "key_user_account"

  override suspend fun get(): UserAccountDataModel? {
    return EncryptedDataStore.get(key)?.run { jsonBase.decodeFromString<UserAccountDataModel>(this) }
  }

  override suspend fun set(value: UserAccountDataModel?) {
    EncryptedDataStore.set(key, value?.run { jsonBase.encodeToString(this) })
  }
}