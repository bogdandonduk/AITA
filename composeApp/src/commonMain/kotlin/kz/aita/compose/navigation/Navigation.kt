package kz.aita.compose.navigation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object Navigation {
  val bottomNavBarScreens = listOf(
    NavigationScreenModel.Transaction.MainSale,
    NavigationScreenModel.Transaction.MainReturn,
    NavigationScreenModel.Transaction.MainSupply,
    NavigationScreenModel.Stock.Main,
    NavigationScreenModel.Menu.Main
  )

  private val _Main =
    MutableStateFlow<List<NavigationScreenModel>>(
      listOf(
        NavigationScreenModel.Splash
      )
    )
  val Main = _Main.asStateFlow()

  suspend fun goMain(model: NavigationScreenModel) {
    if (model::class != _Main.value.last()::class)
      _Main.emit(listOf(model))
  }

  object TransactionSale {
    fun isVeryFirstScreen(isNarrowScreen: Boolean, clientId: Int): Boolean {
      return (
          if (isNarrowScreen)
            when (clientId) {
              0 -> _LeftClient1
              1 -> _LeftClient2
              2 -> _LeftClient3
              3 -> _LeftClient4
              else -> _LeftClient5
            }
          else
            when (clientId) {
              0 -> _RightClient1
              1 -> _RightClient2
              2 -> _RightClient3
              3 -> _RightClient4
              else -> _RightClient5
            }
          ).value.size == 1
    }

    fun isVeryFirstScreenLeft(clientId: Int): Boolean {
      return (when (clientId) {
        0 -> _LeftClient1
        1 -> _LeftClient2
        2 -> _LeftClient3
        3 -> _LeftClient4
        else -> _LeftClient5
      }).value.size == 1
    }

    fun isVeryFirstScreenRight(clientId: Int): Boolean {
      return (when (clientId) {
        0 -> _RightClient1
        1 -> _RightClient2
        2 -> _RightClient3
        3 -> _RightClient4
        else -> _RightClient5
      }).value.size == 1
    }

    private val _ClientId =
      MutableStateFlow(0)
    val ClientId =
      _ClientId.asStateFlow()

    suspend fun setClientId(id: Int) {
      _ClientId.emit(id)
    }

    private val _LeftClient1 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient1 =
      _LeftClient1.asStateFlow()

    private val _LeftClient2 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient2 =
      _LeftClient2.asStateFlow()

    private val _LeftClient3 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient3 =
      _LeftClient3.asStateFlow()

    private val _LeftClient4 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient4 =
      _LeftClient4.asStateFlow()

    private val _LeftClient5 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient5 =
      _LeftClient5.asStateFlow()

    private val _RightClient1 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient1 =
      _RightClient1.asStateFlow()

    private val _RightClient2 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient2 =
      _RightClient2.asStateFlow()

    private val _RightClient3 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient3 =
      _RightClient3.asStateFlow()

    private val _RightClient4 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient4 =
      _RightClient4.asStateFlow()

    private val _RightClient5 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient5 =
      _RightClient5.asStateFlow()

    suspend fun goLeftClient1(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient1.value.last()::class)
        _LeftClient1.emit(
          _LeftClient1
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient1.value.last()::class == _LeftClient1.value.last()::class)
        popRightClient1()

      val oldSize = _LeftClient1.value.size

      _LeftClient1.emit(
        _LeftClient1.value.toMutableList()
          .apply {
            if (_LeftClient1.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient1.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearLeftClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient1.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient2(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient2.value.last()::class)
        _LeftClient2.emit(
          _LeftClient2
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient2.value.last()::class == _LeftClient2.value.last()::class)
        popRightClient2()

      val oldSize = _RightClient2.value.size

      _LeftClient2.emit(
        _LeftClient2.value.toMutableList()
          .apply {
            if (_LeftClient2.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient2.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearLeftClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient1.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient3(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient3.value.last()::class)
        _LeftClient3.emit(
          _LeftClient3
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient3.value.last()::class == _LeftClient3.value.last()::class)
        popRightClient3()

      val oldSize = _LeftClient3.value.size

      _LeftClient3.emit(
        _LeftClient3.value.toMutableList()
          .apply {
            if (_LeftClient3.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient3.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearLeftClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient3.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient4(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient4.value.last()::class)
        _LeftClient4.emit(
          _LeftClient4
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient4.value.last()::class == _LeftClient4.value.last()::class)
        popRightClient4()

      val oldSize = _LeftClient4.value.size

      _LeftClient4.emit(
        _LeftClient4.value.toMutableList()
          .apply {
            if (_LeftClient4.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient4.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient4(this@run)
      }
    }

    suspend fun clearLeftClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient1.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient5(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient5.value.last()::class)
        _LeftClient5.emit(
          _LeftClient5
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient5.value.last()::class == _LeftClient5.value.last()::class)
        popRightClient5()

      val oldSize = _LeftClient5.value.size

      _LeftClient5.emit(
        _LeftClient5.value.toMutableList()
          .apply {
            if (_LeftClient5.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient5.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient5(this@run)
      }
    }

    suspend fun clearLeftClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient5.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient1(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient1.value.last()::class)
        _RightClient1.emit(
          _RightClient1
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient1.value.size

      _RightClient1.emit(
        _RightClient1.value.toMutableList()
          .apply {
            if (_RightClient1.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient1.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient1(this@run)
      }
    }

    suspend fun clearRightClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient1.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient2(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient2.value.last()::class)
        _RightClient2.emit(
          _RightClient2
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient2.value.size

      _RightClient2.emit(
        _RightClient2.value.toMutableList()
          .apply {
            if (_RightClient2.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient2.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearRightClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient1.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient3(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient3.value.last()::class)
        _RightClient3.emit(
          _RightClient3
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient3.value.size

      _RightClient3.emit(
        _RightClient3.value.toMutableList()
          .apply {
            if (_RightClient3.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient3.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearRightClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient3.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient4(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient4.value.last()::class)
        _RightClient4.emit(
          _RightClient4
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient4.value.size

      _RightClient4.emit(
        _RightClient4.value.toMutableList()
          .apply {
            if (_RightClient4.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient4.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient4(this@run)
      }
    }

    suspend fun clearRightClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient4.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient5(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient5.value.last()::class)
        _RightClient5.emit(
          _RightClient5
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient5.value.size

      _RightClient5.emit(
        _RightClient5.value.toMutableList()
          .apply {
            if (_RightClient5.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient5.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient5(this@run)
      }
    }

    suspend fun clearRightClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient5.emit(
        listOf(model)
      )
    }

    suspend fun init(isNarrowScreen: Boolean) {
      TransactionSale.run {
        if (isNarrowScreen) {
          if (RightClient1.value.size > 1) {
            _LeftClient1.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient1.value.subList(1, RightClient1.value.size)
                )
              }
            )
          }
          clearRightClient1()

          if (RightClient2.value.size > 1) {
            _LeftClient2.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient2.value.subList(1, RightClient2.value.size)
                )
              }
            )
          }
          clearRightClient2()

          if (RightClient3.value.size > 1) {
            _LeftClient3.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient3.value.subList(1, RightClient3.value.size)
                )
              }
            )
          }
          clearRightClient3()

          if (RightClient4.value.size > 1) {
            _LeftClient4.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient4.value.subList(1, RightClient4.value.size)
                )
              }
            )
          }
          clearRightClient4()

          if (RightClient5.value.size > 1) {
            _LeftClient5.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient5.value.subList(1, RightClient5.value.size)
                )
              }
            )
          }
          clearRightClient5()

        } else {
          if (LeftClient1.value.size > 1) {
            _RightClient1.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient1.value.subList(1, LeftClient1.value.size)
                )
              }
            )
          }
          clearLeftClient1()

          if (LeftClient2.value.size > 1) {
            _RightClient2.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient2.value.subList(1, LeftClient2.value.size)
                )
              }
            )
          }
          clearLeftClient2()

          if (LeftClient3.value.size > 1) {
            _RightClient3.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient3.value.subList(1, LeftClient3.value.size)
                )
              }
            )
          }
          clearLeftClient3()

          if (LeftClient4.value.size > 1) {
            _RightClient4.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient4.value.subList(1, LeftClient4.value.size)
                )
              }
            )
          }
          clearLeftClient4()

          if (LeftClient5.value.size > 1) {
            _RightClient5.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient5.value.subList(1, LeftClient5.value.size)
                )
              }
            )
          }
          clearLeftClient5()
        }
      }
    }
  }

  object TransactionReturn {

    fun isVeryFirstScreen(isNarrowScreen: Boolean, clientId: Int): Boolean {
      return (
          if (isNarrowScreen)
            when (clientId) {
              0 -> _LeftClient1
              1 -> _LeftClient2
              2 -> _LeftClient3
              3 -> _LeftClient4
              else -> _LeftClient5
            }
          else
            when (clientId) {
              0 -> _RightClient1
              1 -> _RightClient2
              2 -> _RightClient3
              3 -> _RightClient4
              else -> _RightClient5
            }
          ).value.size == 1
    }

    fun isVeryFirstScreenLeft(clientId: Int): Boolean {
      return (when (clientId) {
        0 -> _LeftClient1
        1 -> _LeftClient2
        2 -> _LeftClient3
        3 -> _LeftClient4
        else -> _LeftClient5
      }).value.size == 1
    }

    fun isVeryFirstScreenRight(clientId: Int): Boolean {
      return (when (clientId) {
        0 -> _RightClient1
        1 -> _RightClient2
        2 -> _RightClient3
        3 -> _RightClient4
        else -> _RightClient5
      }).value.size == 1
    }

    private val _ClientId =
      MutableStateFlow(0)
    val ClientId =
      _ClientId.asStateFlow()

    suspend fun setClientId(id: Int) {
      _ClientId.emit(id)
    }

    private val _LeftClient1 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient1 =
      _LeftClient1.asStateFlow()

    private val _LeftClient2 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient2 =
      _LeftClient2.asStateFlow()

    private val _LeftClient3 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient3 =
      _LeftClient3.asStateFlow()

    private val _LeftClient4 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient4 =
      _LeftClient4.asStateFlow()

    private val _LeftClient5 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient5 =
      _LeftClient5.asStateFlow()

    private val _RightClient1 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient1 =
      _RightClient1.asStateFlow()

    private val _RightClient2 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient2 =
      _RightClient2.asStateFlow()

    private val _RightClient3 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient3 =
      _RightClient3.asStateFlow()

    private val _RightClient4 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient4 =
      _RightClient4.asStateFlow()

    private val _RightClient5 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient5 =
      _RightClient5.asStateFlow()

    suspend fun goLeftClient1(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient1.value.last()::class)
        _LeftClient1.emit(
          _LeftClient1
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient1.value.last()::class == _LeftClient1.value.last()::class)
        popRightClient1()

      val oldSize = _LeftClient1.value.size

      _LeftClient1.emit(
        _LeftClient1.value.toMutableList()
          .apply {
            if (_LeftClient1.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient1.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearLeftClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient1.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient2(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient2.value.last()::class)
        _LeftClient2.emit(
          _LeftClient2
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient2.value.last()::class == _LeftClient2.value.last()::class)
        popRightClient2()

      val oldSize = _RightClient2.value.size

      _LeftClient2.emit(
        _LeftClient2.value.toMutableList()
          .apply {
            if (_LeftClient2.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient2.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearLeftClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient1.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient3(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient3.value.last()::class)
        _LeftClient3.emit(
          _LeftClient3
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient3.value.last()::class == _LeftClient3.value.last()::class)
        popRightClient3()

      val oldSize = _LeftClient3.value.size

      _LeftClient3.emit(
        _LeftClient3.value.toMutableList()
          .apply {
            if (_LeftClient3.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient3.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearLeftClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient3.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient4(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient4.value.last()::class)
        _LeftClient4.emit(
          _LeftClient4
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient4.value.last()::class == _LeftClient4.value.last()::class)
        popRightClient4()

      val oldSize = _LeftClient4.value.size

      _LeftClient4.emit(
        _LeftClient4.value.toMutableList()
          .apply {
            if (_LeftClient4.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient4.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient4(this@run)
      }
    }

    suspend fun clearLeftClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient1.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient5(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient5.value.last()::class)
        _LeftClient5.emit(
          _LeftClient5
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient5.value.last()::class == _LeftClient5.value.last()::class)
        popRightClient5()

      val oldSize = _LeftClient5.value.size

      _LeftClient5.emit(
        _LeftClient5.value.toMutableList()
          .apply {
            if (_LeftClient5.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient5.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient5(this@run)
      }
    }

    suspend fun clearLeftClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient5.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient1(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient1.value.last()::class)
        _RightClient1.emit(
          _RightClient1
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient1.value.size

      _RightClient1.emit(
        _RightClient1.value.toMutableList()
          .apply {
            if (_RightClient1.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient1.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient1(this@run)
      }
    }

    suspend fun clearRightClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient1.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient2(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient2.value.last()::class)
        _RightClient2.emit(
          _RightClient2
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient2.value.size

      _RightClient2.emit(
        _RightClient2.value.toMutableList()
          .apply {
            if (_RightClient2.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient2.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearRightClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient1.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient3(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient3.value.last()::class)
        _RightClient3.emit(
          _RightClient3
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient3.value.size

      _RightClient3.emit(
        _RightClient3.value.toMutableList()
          .apply {
            if (_RightClient3.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient3.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearRightClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient3.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient4(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient4.value.last()::class)
        _RightClient4.emit(
          _RightClient4
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient4.value.size

      _RightClient4.emit(
        _RightClient4.value.toMutableList()
          .apply {
            if (_RightClient4.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient4.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient4(this@run)
      }
    }

    suspend fun clearRightClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient4.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient5(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient5.value.last()::class)
        _RightClient5.emit(
          _RightClient5
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient5.value.size

      _RightClient5.emit(
        _RightClient5.value.toMutableList()
          .apply {
            if (_RightClient5.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient5.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient5(this@run)
      }
    }

    suspend fun clearRightClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient5.emit(
        listOf(model)
      )
    }

    suspend fun init(isNarrowScreen: Boolean) {
      TransactionSupply.run {
        if (isNarrowScreen) {
          if (RightClient1.value.size > 1) {
            _LeftClient1.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient1.value.subList(1, RightClient1.value.size)
                )
              }
            )
          }
          clearRightClient1()

          if (RightClient2.value.size > 1) {
            _LeftClient2.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient2.value.subList(1, RightClient2.value.size)
                )
              }
            )
          }
          clearRightClient2()

          if (RightClient3.value.size > 1) {
            _LeftClient3.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient3.value.subList(1, RightClient3.value.size)
                )
              }
            )
          }
          clearRightClient3()

          if (RightClient4.value.size > 1) {
            _LeftClient4.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient4.value.subList(1, RightClient4.value.size)
                )
              }
            )
          }
          clearRightClient4()

          if (RightClient5.value.size > 1) {
            _LeftClient5.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient5.value.subList(1, RightClient5.value.size)
                )
              }
            )
          }
          clearRightClient5()

        } else {
          if (LeftClient1.value.size > 1) {
            _RightClient1.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient1.value.subList(1, LeftClient1.value.size)
                )
              }
            )
          }
          clearLeftClient1()

          if (LeftClient2.value.size > 1) {
            _RightClient2.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient2.value.subList(1, LeftClient2.value.size)
                )
              }
            )
          }
          clearLeftClient2()

          if (LeftClient3.value.size > 1) {
            _RightClient3.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient3.value.subList(1, LeftClient3.value.size)
                )
              }
            )
          }
          clearLeftClient3()

          if (LeftClient4.value.size > 1) {
            _RightClient4.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient4.value.subList(1, LeftClient4.value.size)
                )
              }
            )
          }
          clearLeftClient4()

          if (LeftClient5.value.size > 1) {
            _RightClient5.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient5.value.subList(1, LeftClient5.value.size)
                )
              }
            )
          }
          clearLeftClient5()
        }
      }
    }
  }

  object TransactionSupply {

    fun isVeryFirstScreen(isNarrowScreen: Boolean, clientId: Int): Boolean {
      return (
          if (isNarrowScreen)
            when (clientId) {
              0 -> _LeftClient1
              1 -> _LeftClient2
              2 -> _LeftClient3
              3 -> _LeftClient4
              else -> _LeftClient5
            }
          else
            when (clientId) {
              0 -> _RightClient1
              1 -> _RightClient2
              2 -> _RightClient3
              3 -> _RightClient4
              else -> _RightClient5
            }
          ).value.size == 1
    }

    fun isVeryFirstScreenLeft(clientId: Int): Boolean {
      return (when (clientId) {
        0 -> _LeftClient1
        1 -> _LeftClient2
        2 -> _LeftClient3
        3 -> _LeftClient4
        else -> _LeftClient5
      }).value.size == 1
    }

    fun isVeryFirstScreenRight(clientId: Int): Boolean {
      return (when (clientId) {
        0 -> _RightClient1
        1 -> _RightClient2
        2 -> _RightClient3
        3 -> _RightClient4
        else -> _RightClient5
      }).value.size == 1
    }

    private val _ClientId =
      MutableStateFlow(0)
    val ClientId =
      _ClientId.asStateFlow()

    suspend fun setClientId(id: Int) {
      _ClientId.emit(id)
    }

    private val _LeftClient1 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient1 =
      _LeftClient1.asStateFlow()

    private val _LeftClient2 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient2 =
      _LeftClient2.asStateFlow()

    private val _LeftClient3 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient3 =
      _LeftClient3.asStateFlow()

    private val _LeftClient4 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient4 =
      _LeftClient4.asStateFlow()

    private val _LeftClient5 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient5 =
      _LeftClient5.asStateFlow()

    private val _RightClient1 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient1 =
      _RightClient1.asStateFlow()

    private val _RightClient2 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient2 =
      _RightClient2.asStateFlow()

    private val _RightClient3 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient3 =
      _RightClient3.asStateFlow()

    private val _RightClient4 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient4 =
      _RightClient4.asStateFlow()

    private val _RightClient5 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient5 =
      _RightClient5.asStateFlow()

    suspend fun goLeftClient1(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient1.value.last()::class)
        _LeftClient1.emit(
          _LeftClient1
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient1.value.last()::class == _LeftClient1.value.last()::class)
        popRightClient1()

      val oldSize = _LeftClient1.value.size

      _LeftClient1.emit(
        _LeftClient1.value.toMutableList()
          .apply {
            if (_LeftClient1.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient1.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearLeftClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient1.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient2(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient2.value.last()::class)
        _LeftClient2.emit(
          _LeftClient2
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient2.value.last()::class == _LeftClient2.value.last()::class)
        popRightClient2()

      val oldSize = _RightClient2.value.size

      _LeftClient2.emit(
        _LeftClient2.value.toMutableList()
          .apply {
            if (_LeftClient2.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient2.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearLeftClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient1.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient3(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient3.value.last()::class)
        _LeftClient3.emit(
          _LeftClient3
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient3.value.last()::class == _LeftClient3.value.last()::class)
        popRightClient3()

      val oldSize = _LeftClient3.value.size

      _LeftClient3.emit(
        _LeftClient3.value.toMutableList()
          .apply {
            if (_LeftClient3.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient3.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearLeftClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient3.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient4(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient4.value.last()::class)
        _LeftClient4.emit(
          _LeftClient4
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient4.value.last()::class == _LeftClient4.value.last()::class)
        popRightClient4()

      val oldSize = _LeftClient4.value.size

      _LeftClient4.emit(
        _LeftClient4.value.toMutableList()
          .apply {
            if (_LeftClient4.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient4.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient4(this@run)
      }
    }

    suspend fun clearLeftClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient1.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient5(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient5.value.last()::class)
        _LeftClient5.emit(
          _LeftClient5
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient5.value.last()::class == _LeftClient5.value.last()::class)
        popRightClient5()

      val oldSize = _LeftClient5.value.size

      _LeftClient5.emit(
        _LeftClient5.value.toMutableList()
          .apply {
            if (_LeftClient5.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient5.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient5(this@run)
      }
    }

    suspend fun clearLeftClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient5.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient1(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient1.value.last()::class)
        _RightClient1.emit(
          _RightClient1
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient1.value.size

      _RightClient1.emit(
        _RightClient1.value.toMutableList()
          .apply {
            if (_RightClient1.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient1.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient1(this@run)
      }
    }

    suspend fun clearRightClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient1.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient2(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient2.value.last()::class)
        _RightClient2.emit(
          _RightClient2
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient2.value.size

      _RightClient2.emit(
        _RightClient2.value.toMutableList()
          .apply {
            if (_RightClient2.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient2.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearRightClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient1.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient3(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient3.value.last()::class)
        _RightClient3.emit(
          _RightClient3
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient3.value.size

      _RightClient3.emit(
        _RightClient3.value.toMutableList()
          .apply {
            if (_RightClient3.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient3.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearRightClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient3.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient4(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient4.value.last()::class)
        _RightClient4.emit(
          _RightClient4
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient4.value.size

      _RightClient4.emit(
        _RightClient4.value.toMutableList()
          .apply {
            if (_RightClient4.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient4.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient4(this@run)
      }
    }

    suspend fun clearRightClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient4.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient5(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient5.value.last()::class)
        _RightClient5.emit(
          _RightClient5
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient5.value.size

      _RightClient5.emit(
        _RightClient5.value.toMutableList()
          .apply {
            if (_RightClient5.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient5.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient5(this@run)
      }
    }

    suspend fun clearRightClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient5.emit(
        listOf(model)
      )
    }

    suspend fun init(isNarrowScreen: Boolean) {
      TransactionSupply.run {
        if (isNarrowScreen) {
          if (RightClient1.value.size > 1) {
            _LeftClient1.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient1.value.subList(1, RightClient1.value.size)
                )
              }
            )
          }
          clearRightClient1()

          if (RightClient2.value.size > 1) {
            _LeftClient2.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient2.value.subList(1, RightClient2.value.size)
                )
              }
            )
          }
          clearRightClient2()

          if (RightClient3.value.size > 1) {
            _LeftClient3.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient3.value.subList(1, RightClient3.value.size)
                )
              }
            )
          }
          clearRightClient3()

          if (RightClient4.value.size > 1) {
            _LeftClient4.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient4.value.subList(1, RightClient4.value.size)
                )
              }
            )
          }
          clearRightClient4()

          if (RightClient5.value.size > 1) {
            _LeftClient5.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient5.value.subList(1, RightClient5.value.size)
                )
              }
            )
          }
          clearRightClient5()

        } else {
          if (LeftClient1.value.size > 1) {
            _RightClient1.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient1.value.subList(1, LeftClient1.value.size)
                )
              }
            )
          }
          clearLeftClient1()

          if (LeftClient2.value.size > 1) {
            _RightClient2.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient2.value.subList(1, LeftClient2.value.size)
                )
              }
            )
          }
          clearLeftClient2()

          if (LeftClient3.value.size > 1) {
            _RightClient3.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient3.value.subList(1, LeftClient3.value.size)
                )
              }
            )
          }
          clearLeftClient3()

          if (LeftClient4.value.size > 1) {
            _RightClient4.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient4.value.subList(1, LeftClient4.value.size)
                )
              }
            )
          }
          clearLeftClient4()

          if (LeftClient5.value.size > 1) {
            _RightClient5.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient5.value.subList(1, LeftClient5.value.size)
                )
              }
            )
          }
          clearLeftClient5()
        }
      }
    }
  }

  object Stock {

    fun isVeryFirstScreen(isNarrowScreen: Boolean): Boolean {
      return (if (isNarrowScreen) _Left else _Right).value.size == 1
    }

    fun isVeryFirstScreenLeft(): Boolean {
      return _Left.value.size == 1
    }

    fun isVeryFirstScreenRight(): Boolean {
      return _Right.value.size == 1
    }

    private val _Left =
      MutableStateFlow<List<NavigationScreenModel.Stock>>(listOf(NavigationScreenModel.Stock.Warehouse))
    val Left =
      _Left.asStateFlow()

    private val _Right =
      MutableStateFlow<List<NavigationScreenModel.Stock>>(listOf(NavigationScreenModel.Stock.AddEditGoodsItem))
    val Right =
      _Right.asStateFlow()

    suspend fun goLeft(
      model: NavigationScreenModel.Stock,
      remove: Boolean = false
    ) {
      if (model::class != _Left.value.last()::class)
        _Left.emit(
          _Left
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeft(navigateAfterwards: NavigationScreenModel.Stock? = null) {
      if (_Right.value.last()::class == _Left.value.last()::class)
        popRight()

      val oldSize = _Left.value.size

      _Left.emit(
        _Left.value.toMutableList()
          .apply {
            if (_Left.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_Left.value.size == oldSize)
          delay(30)

        delay(300)

        goLeft(this@run)
      }
    }

    suspend fun clearLeft(model: NavigationScreenModel.Stock = NavigationScreenModel.Stock.Warehouse) {
      _Left.emit(
        listOf(model)
      )
    }

    suspend fun goRight(
      model: NavigationScreenModel.Stock,
      remove: Boolean = false
    ) {
      if (model::class != _Right.value.last()::class)
        _Right.emit(
          _Right
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRight(navigateAfterwards: NavigationScreenModel.Stock? = null) {
      val oldSize = _Right.value.size

      _Right.emit(
        _Right.value.toMutableList()
          .apply {
            if (_Right.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_Right.value.size == oldSize)
          delay(30)

        delay(300)

        goLeft(this@run)
      }
    }

    suspend fun clearRight(model: NavigationScreenModel.Stock = NavigationScreenModel.Stock.AddEditGoodsItem) {
      _Right.emit(
        listOf(model)
      )
    }

    suspend fun init(isNarrowScreen: Boolean) {
      Stock.run {
        if (isNarrowScreen) {
          if (Right.value.size > 1) {
            _Left.emit(
              mutableListOf<NavigationScreenModel.Stock>().apply {
                add(NavigationScreenModel.Stock.Warehouse)
                addAll(Right.value.subList(1, Right.value.size))
              }
            )
          }
          clearRight()
        } else {
          if (_Left.value.size > 1) {
            _Right.emit(
              mutableListOf<NavigationScreenModel.Stock>().apply {
                add(NavigationScreenModel.Stock.AddEditGoodsItem)
                addAll(_Left.value.subList(1, _Left.value.size))
              }
            )
          }
          clearLeft()
        }
      }
    }
  }

  object Menu {

    val listScreens = listOf(
      NavigationScreenModel.Menu.UserAccount,
      NavigationScreenModel.Menu.GoodsCategories,
      NavigationScreenModel.Menu.Stores,
      NavigationScreenModel.Menu.TransactionHistory,
      NavigationScreenModel.Menu.Analytics,
      NavigationScreenModel.Menu.Workers,
      NavigationScreenModel.Menu.Suppliers,
      NavigationScreenModel.Menu.Debtors,
      NavigationScreenModel.Menu.Devices,
      NavigationScreenModel.Menu.AppLanguage,
      NavigationScreenModel.Menu.AppTheme
    )

    private val _Left =
      MutableStateFlow<List<NavigationScreenModel.Menu>>(listOf(NavigationScreenModel.Menu.List))
    val Left =
      _Left.asStateFlow()

    fun isVeryFirstScreen(isNarrowScreen: Boolean): Boolean {
      return (if (isNarrowScreen) _Left else _Right).value.size == 1
    }

    fun isVeryFirstScreenLeft(): Boolean {
      return _Left.value.size == 1
    }

    fun isVeryFirstScreenRight(): Boolean {
      return _Right.value.size == 1
    }

    private val _Right =
      MutableStateFlow<List<NavigationScreenModel.Menu>>(listOf(NavigationScreenModel.Menu.UserAccount))
    val Right =
      _Right.asStateFlow()

    suspend fun go(model: NavigationScreenModel.Menu, isNarrowScreen: Boolean) {
      if (isNarrowScreen)
        goLeft(model)
      else
        goRight(model)
    }

    suspend fun pop(isNarrowScreen: Boolean, navigateAfterwards: NavigationScreenModel.Menu? = null) {
      if (isNarrowScreen)
        popLeft(navigateAfterwards)
      else
        popRight(navigateAfterwards)
    }

    suspend fun goLeft(
      model: NavigationScreenModel.Menu,
      remove: Boolean = false
    ) {
      if (model::class != _Left.value.last()::class)
        _Left.emit(
          _Left
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeft(navigateAfterwards: NavigationScreenModel.Menu? = null) {
      if (_Right.value.last()::class == _Left.value.last()::class)
        popRight()

      val oldSize = _Left.value.size

      _Left.emit(
        _Left.value.toMutableList()
          .apply {
            if (_Left.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_Left.value.size == oldSize)
          delay(30)

        delay(300)

        goLeft(this@run)
      }
    }

    suspend fun clearLeft(model: NavigationScreenModel.Menu = NavigationScreenModel.Menu.List) {
      _Left.emit(
        listOf(model)
      )
    }

    suspend fun goRight(
      model: NavigationScreenModel.Menu,
      remove: Boolean = false
    ) {
      if (model::class != _Right.value.last()::class)
        _Right.emit(
          _Right
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRight(navigateAfterwards: NavigationScreenModel.Menu? = null) {
      val oldSize = _Right.value.size

      _Right.emit(
        _Right.value.toMutableList()
          .apply {
            if (_Right.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_Right.value.size == oldSize)
          delay(30)

        delay(300)

        goLeft(this@run)
      }
    }

    suspend fun clearRight(model: NavigationScreenModel.Menu = NavigationScreenModel.Menu.UserAccount) {
      _Right.emit(
        listOf(model)
      )
    }

    suspend fun init(isNarrowScreen: Boolean) {
      Menu.run {
        if (isNarrowScreen) {
          if (Right.value.size > 1) {
            _Left.emit(
              mutableListOf<NavigationScreenModel.Menu>().apply {
                add(NavigationScreenModel.Menu.List)
                addAll(Right.value.subList(1, Right.value.size))
              }
            )
          }
          clearRight()
        } else {
          if (_Left.value.size > 1) {
            _Right.emit(
              mutableListOf<NavigationScreenModel.Menu>().apply {
                add(NavigationScreenModel.Menu.UserAccount)
                addAll(_Left.value.subList(1, _Left.value.size))
              }
            )
          }
          clearLeft()
        }
      }
    }
  }

  object UserAuth {

    fun isVeryFirstScreen(isNarrowScreen: Boolean): Boolean {
      return (if (isNarrowScreen) _Left else _Right).value.size == 1
    }

    fun isVeryFirstScreenLeft(): Boolean {
      return _Left.value.size == 1
    }

    fun isVeryFirstScreenRight(): Boolean {
      return _Right.value.size == 1
    }

    private val _Left =
      MutableStateFlow<List<NavigationScreenModel.UserAuth>>(listOf(NavigationScreenModel.UserAuth.LogIn))
    val Left =
      _Left.asStateFlow()

    private val _Right =
      MutableStateFlow<List<NavigationScreenModel.UserAuth>>(listOf(NavigationScreenModel.UserAuth.SignUp))
    val Right =
      _Right.asStateFlow()

    suspend fun goLeft(
      model: NavigationScreenModel.UserAuth,
      remove: Boolean = false
    ) {
      if (model::class != _Left.value.last()::class)
        _Left.emit(
          _Left
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeft(navigateAfterwards: NavigationScreenModel.UserAuth? = null) {
      if (_Right.value.last()::class == _Left.value.last()::class)
        popRight()

      val oldSize = _Left.value.size

      _Left.emit(
        _Left.value.toMutableList()
          .apply {
            if (_Left.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_Left.value.size == oldSize)
          delay(30)

        delay(300)

        goLeft(this@run)
      }
    }

    suspend fun clearLeft(model: NavigationScreenModel.UserAuth = NavigationScreenModel.UserAuth.LogIn) {
      _Left.emit(
        listOf(model)
      )
    }

    suspend fun goRight(
      model: NavigationScreenModel.UserAuth,
      remove: Boolean = false
    ) {
      if (model::class != _Right.value.last()::class)
        _Right.emit(
          _Right
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRight(navigateAfterwards: NavigationScreenModel.UserAuth? = null) {
      val oldSize = _Right.value.size

      _Right.emit(
        _Right.value.toMutableList()
          .apply {
            if (_Right.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_Right.value.size == oldSize)
          delay(30)

        delay(300)

        goLeft(this@run)
      }
    }

    suspend fun clearRight(model: NavigationScreenModel.UserAuth = NavigationScreenModel.UserAuth.SignUp) {
      _Right.emit(
        listOf(model)
      )
    }

    suspend fun init(isNarrowScreen: Boolean) {
      UserAuth.run {
        if (isNarrowScreen) {
          if (Right.value.size > 1) {
            _Left.emit(
              mutableListOf<NavigationScreenModel.UserAuth>().apply {
                add(NavigationScreenModel.UserAuth.LogIn)
                addAll(Right.value.subList(1, Right.value.size))
              }
            )
          }
          clearRight()
        } else {
          if (Left.value.size > 1) {
            _Right.emit(
              mutableListOf<NavigationScreenModel.UserAuth>().apply {
                add(NavigationScreenModel.UserAuth.SignUp)
                addAll(_Left.value.subList(1, _Left.value.size))
              }
            )
          }
          clearLeft()
        }
      }
    }
  }
}
