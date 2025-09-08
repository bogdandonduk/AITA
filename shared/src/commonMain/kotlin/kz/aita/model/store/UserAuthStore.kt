package kz.aita.model.store

import kz.aita.core.async.coroutines.io
import kz.aita.model.store.base.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kz.aita.core.di.userRepository
import kz.aita.model.dataModel.UserAuthAccountDataModel
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.wrapper.DataState

object UserStore: Store() {

  private val _userAuthState = MutableStateFlow<DataState<UserAuthAccountDataModel>>(DataState.Empty())
  val userAuthState = _userAuthState.asStateFlow()

  fun logIn(userAuthLogIn: UserAuthLogInDataModel) {
    coroutineScope.launch(Dispatchers.io) {
      userRepository
        .logIn(userAuthLogIn)
        .collect {
          _userAuthState.emit(it)
        }
    }
  }

  override fun initialize() {

  }
}
