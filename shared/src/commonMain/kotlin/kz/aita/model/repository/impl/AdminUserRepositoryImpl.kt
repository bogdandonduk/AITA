package kz.aita.model.repository.impl

import io.ktor.http.HttpMethod
import kz.aita.model.dataModel.AdminUserAccountDataModel
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.dataModel.UserAuthSignUpDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.repository.AdminUserRepository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.MutableDataStateFlow

class AdminUserRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val configurationRepository: ConfigurationRepository
) : Repository(), AdminUserRepository {

  private val _userAccountState = MutableDataStateFlow<AdminUserAccountDataModel>(
    this,
//    initial = UserAccountDataModel(
//      0,
//      "bogdan.donduk@gmail.com",
//      "7714047737",
//      "Bogdan",
//      "Donduk",
//      "kz",
////      UserSettingsDataModel(
////        1759232357,
////        "en",
////        0,
////        0
////      )
//
//    )
  )
  override val userAccountState = _userAccountState.asDataStateFlow()

  override suspend fun logIn(userAuthLogIn: UserAuthLogInDataModel) {

  }

  override suspend fun signUp(userAuthSignUp: UserAuthSignUpDataModel) {

    val response = genericRemoteService
      .request<AdminUserAccountDataModel, UserAuthSignUpDataModel>(
        HttpMethod.Post,
        configurationRepository.globalAppConfigurationState.payloadValue.serverUrl,
        configurationRepository.globalAppConfigurationState.payloadValue.signUpPath,
        body = userAuthSignUp
      )

    println("response $response")
  }
}