package kz.aita.core.di

import kz.aita.model.repository.UserRepository
import kz.aita.model.repository.impl.UserRepositoryImpl

val userRepository: UserRepository by lazy {
  UserRepositoryImpl()
}

