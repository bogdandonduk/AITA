package kz.aita.core

import kz.aita.model.wrapper.TokenPair

interface TokenStore {
  suspend fun get(): TokenPair?
  suspend fun set(tokens: TokenPair?)
}

expect var tokenStore: TokenStore?
