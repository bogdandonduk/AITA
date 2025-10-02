package kz.aita.compose.navigation

import kz.aita.AppUIConfiguration

sealed class NavigationScreenModel(
  val route: String,
  open val name: String = route,
  open val iconPath: String = ""
) {

  sealed class Transaction(route: String): NavigationScreenModel(route) {

    data object MainSale: Transaction("TransactionMainSaleNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconTransactionSale
      override val name: String
        get() = AppUIConfiguration.stateValues.stringSale
    }

    data object MainReturn: Transaction("TransactionMainReturnNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconTransactionReturn
      override val name: String
        get() = AppUIConfiguration.stateValues.stringReturn
    }

    data object MainSupply: Transaction("TransactionMainSupplyNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconTransactionSupply
      override val name: String
        get() = AppUIConfiguration.stateValues.stringSupply
    }

    data object Cart: Transaction("TransactionCartScreenNavigationScreenModelRoute")

    data object Selection: Transaction("TransactionSelectionNavigationScreenModelRoute")
    data object QuickItems: Transaction("TransactionQuickItemsNavigationScreenModelRoute")

    data object Checkout: Transaction("TransactionCheckoutNavigationScreenModelRoute")

    data object ReceiptPreview: Transaction("TransactionReceiptPreviewNavigationScreenModelRoute")

    companion object {
      const val PARENT_GRAPH_ROUTE = "CashRegisterScreenParentGraphRoute"
    }
  }

  sealed class Stock(route: String): NavigationScreenModel(route) {

    data object Main: Stock("StockNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconStock
      override val name: String
        get() = AppUIConfiguration.stateValues.stringStock
    }

    data object Warehouse: Stock("StockWarehouseNavigationScreenModelRoute")

    data object AddEditGoodsItem: Stock("StockAddGoodsItemNavigationScreenModelRoute")
  }

  sealed class Menu(route: String): NavigationScreenModel(route) {

    data object Main: Menu("MenuNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconMenu
      override val name: String
        get() = AppUIConfiguration.stateValues.stringMenu
    }

    data object List: Menu("MenuListNavigationScreenModelRoute")

    data object UserAccount: Menu("MenuMapNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconUserAccount
      override val name: String
        get() = AppUIConfiguration.stateValues.stringUserAccount
    }

    data object StoreSubscription: Menu("MenuStoreSubscriptionNavigationScreenModelRoute")
    data object StoreSubscriptionPlans: Menu("MenuStoreSubscriptionPlansNavigationScreenModelRoute")

    data object Workers: Menu("MenuWorkersNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconWorkers
      override val name: String
        get() = AppUIConfiguration.stateValues.stringWorkers
    }
    data object AddEditWorker: Menu("MenuAddEditWorkerNavigationScreenModelRoute")

    data object Stores: Menu("MenuStoresNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconStores
      override val name: String
        get() = AppUIConfiguration.stateValues.stringStores
    }
    data object AddEditStore: Menu("MenuAddEditStoreNavigationScreenModelRoute")

    data object Analytics: Menu("MenuAnalyticsNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconAnalytics
      override val name: String
        get() = AppUIConfiguration.stateValues.stringAnalytics
    }

    data object TransactionHistory: Menu("MenuTransactionHistoryNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconTransactionHistory
      override val name: String
        get() = AppUIConfiguration.stateValues.stringTransactionHistory
    }
    data object TransactionHistoryReceiptPreview: Menu("MenuTransactionHistoryReceiptPreviewNavigationScreenModelRoute")

    data object Debtors: Menu("MenuDebtorsNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconDebtors
      override val name: String
        get() = AppUIConfiguration.stateValues.stringDebtors
    }
    data object CloseDebt: Menu("MenuCloseDebtNavigationScreenModelRoute")

    data object Suppliers: Menu("MenuSuppliersNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconSuppliers
      override val name: String
        get() = AppUIConfiguration.stateValues.stringSuppliers
    }
    data object AddEditSupplier: Menu("MenuAddEditSupplierNavigationScreenModelRoute")

    data object GoodsCategories: Menu("MenuGoodsCategoriesNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconGoodsCategories
      override val name: String
        get() = AppUIConfiguration.stateValues.stringGoodsCategories
    }
    data object AddEditGoodsCategory: Menu("MenuAddEditGoodsCategoryNavigationScreenModelRoute")

    data object Devices: Menu("MenuDevicesNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconDevices
      override val name: String
        get() = AppUIConfiguration.stateValues.stringDevices
    }
    data object AppLanguage: Menu("MenuAppLanguageNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconAppLanguage
      override val name: String
        get() = AppUIConfiguration.stateValues.stringAppLanguage
    }
    data object AppTheme: Menu("MenuAppThemeNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconAppTheme
      override val name: String
        get() = AppUIConfiguration.stateValues.stringAppTheme
    }
  }

  sealed class UserAuth(route: String): NavigationScreenModel(route) {

    data object Main: UserAuth("UserAuthNavigationScreenModelRoute")

    data object LogIn: UserAuth("UserAuthLogInNavigationScreenModelRoute")
    data object SignUp: UserAuth("UserAuthLogInNavigationScreenModelRoute")
  }

  object Splash: NavigationScreenModel("SplashNavigationScreenModelRoute")
}
