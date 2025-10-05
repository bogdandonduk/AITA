package kz.aita.compose.navigation

import kz.aita.AppConfiguration

sealed class NavigationScreenModel(
  val route: String,
  open val name: String = route,
  open val iconPath: String = ""
) {

  private val _state = mutableMapOf<String, String>()
  val state = _state as Map<String, String>

  fun setState(pair: Pair<String, String>) {
    _state[pair.first] = pair.second
  }

  fun removeStockWarehouse(key: String) {
    _state.remove(key)
  }

  fun clearStockWarehouse() {
    _state.clear()
  }

  sealed class Transaction(route: String): NavigationScreenModel(route) {

    data object MainSale: Transaction("TransactionMainSaleNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconTransactionSale
      override val name: String
        get() = AppConfiguration.stateValues.stringSale
    }

    data object MainReturn: Transaction("TransactionMainReturnNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconTransactionReturn
      override val name: String
        get() = AppConfiguration.stateValues.stringReturn
    }

    data object MainSupply: Transaction("TransactionMainSupplyNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconTransactionSupply
      override val name: String
        get() = AppConfiguration.stateValues.stringSupply
    }

    data object Cart: Transaction("TransactionCartScreenNavigationScreenModelRoute")

    data object Selection: Transaction("TransactionSelectionNavigationScreenModelRoute")

    data object Checkout: Transaction("TransactionCheckoutNavigationScreenModelRoute")

    data object ReceiptPreview: Transaction("TransactionReceiptPreviewNavigationScreenModelRoute")
  }

  sealed class Stock(route: String): NavigationScreenModel(route) {

    data object Main: Stock("StockNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconStock
      override val name: String
        get() = AppConfiguration.stateValues.stringStock
    }

    data object Warehouse: Stock("StockWarehouseNavigationScreenModelRoute")

    data object AddEditGoodsItem: Stock("StockAddGoodsItemNavigationScreenModelRoute")
  }

  sealed class Menu(route: String): NavigationScreenModel(route) {

    data object Main: Menu("MenuNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconMenu
      override val name: String
        get() = AppConfiguration.stateValues.stringMenu
    }

    data object List: Menu("MenuListNavigationScreenModelRoute")

    data object UserAccount: Menu("MenuMapNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconUserAccount
      override val name: String
        get() = AppConfiguration.stateValues.stringUserAccount
    }

    data object StoreSubscription: Menu("MenuStoreSubscriptionNavigationScreenModelRoute")
    data object StoreSubscriptionPlans: Menu("MenuStoreSubscriptionPlansNavigationScreenModelRoute")

    data object Workers: Menu("MenuWorkersNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconWorkers
      override val name: String
        get() = AppConfiguration.stateValues.stringWorkers
    }
    data object AddEditWorker: Menu("MenuAddEditWorkerNavigationScreenModelRoute")

    data object Stores: Menu("MenuStoresNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconStores
      override val name: String
        get() = AppConfiguration.stateValues.stringStores
    }
    data object AddEditStore: Menu("MenuAddEditStoreNavigationScreenModelRoute")

    data object Analytics: Menu("MenuAnalyticsNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconAnalytics
      override val name: String
        get() = AppConfiguration.stateValues.stringAnalytics
    }

    data object TransactionHistory: Menu("MenuTransactionHistoryNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconTransactionHistory
      override val name: String
        get() = AppConfiguration.stateValues.stringTransactionHistory
    }
    data object TransactionHistoryReceiptPreview: Menu("MenuTransactionHistoryReceiptPreviewNavigationScreenModelRoute")

    data object Debtors: Menu("MenuDebtorsNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconDebtors
      override val name: String
        get() = AppConfiguration.stateValues.stringDebtors
    }
    data object CloseDebt: Menu("MenuCloseDebtNavigationScreenModelRoute")

    data object Suppliers: Menu("MenuSuppliersNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconSuppliers
      override val name: String
        get() = AppConfiguration.stateValues.stringSuppliers
    }
    data object AddEditSupplier: Menu("MenuAddEditSupplierNavigationScreenModelRoute")

    data object GoodsCategories: Menu("MenuGoodsCategoriesNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconGoodsCategories
      override val name: String
        get() = AppConfiguration.stateValues.stringGoodsCategories
    }
    data object AddEditGoodsCategory: Menu("MenuAddEditGoodsCategoryNavigationScreenModelRoute")

    data object Devices: Menu("MenuDevicesNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconDevices
      override val name: String
        get() = AppConfiguration.stateValues.stringDevices
    }
    data object AppLanguage: Menu("MenuAppLanguageNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconAppLanguage
      override val name: String
        get() = AppConfiguration.stateValues.stringAppLanguage
    }
    data object AppTheme: Menu("MenuAppThemeNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconAppTheme
      override val name: String
        get() = AppConfiguration.stateValues.stringAppTheme
    }
  }

  sealed class UserAuth(route: String): NavigationScreenModel(route) {

    data object Main: UserAuth("UserAuthNavigationScreenModelRoute")

    data object LogIn: UserAuth("UserAuthLogInNavigationScreenModelRoute")
    data object SignUp: UserAuth("UserAuthLogInNavigationScreenModelRoute")
  }

  object Splash: NavigationScreenModel("SplashNavigationScreenModelRoute")
}
