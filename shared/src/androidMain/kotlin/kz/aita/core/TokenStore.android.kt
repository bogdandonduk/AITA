package kz.aita.core

import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.wrapper.TokenPair

actual var tokenStore: DataStore<TokenPair>? = null
actual var userAccountStore: DataStore<UserAccountDataModel>? = null
