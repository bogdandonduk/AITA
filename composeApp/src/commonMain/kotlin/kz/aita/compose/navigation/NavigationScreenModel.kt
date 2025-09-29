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

    data object AddGoodsItem: Stock("StockAddGoodsItemNavigationScreenModelRoute")
    data object EditGoodsItem: Stock("StockEditGoodsItemNavigationScreenModelRoute")

    data object Map: Stock("StockMapNavigationScreenModelRoute")
  }

  sealed class Menu(route: String): NavigationScreenModel(route) {

    data object Main: Menu("MenuNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppUIConfiguration.stateValues.drawablePathIconMenu
      override val name: String
        get() = AppUIConfiguration.stateValues.stringMenu
    }

    data object List: Menu("MenuListNavigationScreenModelRoute")

    data object UserAccount: Menu("MenuMapNavigationScreenModelRoute")

    data object Subscription: Menu("MenuSubscriptionNavigationScreenModelRoute")
    data object SubscriptionPlans: Menu("MenuSubscriptionPlansNavigationScreenModelRoute")

    data object Employees: Menu("MenuEmployeesNavigationScreenModelRoute")
    data object AddEmployee: Menu("MenuAddEmployeeNavigationScreenModelRoute")

    data object Stores: Menu("MenuStoresNavigationScreenModelRoute")
    data object AddStore: Menu("MenuAddStoreNavigationScreenModelRoute")

    data object Map: Menu("MenuMapNavigationScreenModelRoute")
    data object Analytics: Menu("MenuAnalyticsNavigationScreenModelRoute")

    data object TransactionHistory: Menu("MenuTransactionHistoryNavigationScreenModelRoute")
    data object TransactionHistoryReceiptPreview: Menu("MenuTransactionHistoryReceiptPreviewNavigationScreenModelRoute")

    data object Debtors: Menu("MenuDebtorsNavigationScreenModelRoute")
    data object CheckoutDebtor: Menu("MenuCheckoutDebtorNavigationScreenModelRoute")

    data object Suppliers: Menu("MenuSuppliersNavigationScreenModelRoute")

    data object Categories: Menu("MenuCategoriesNavigationScreenModelRoute")
    data object GoodsCategories: Menu("MenuGoodsCategoriesCategoriesNavigationScreenModelRoute")

    data object Accessories: Menu("MenuAccessoriesNavigationScreenModelRoute")
    data object AppLanguage: Menu("MenuAppLanguageNavigationScreenModelRoute")
    data object AppTheme: Menu("MenuAppThemeNavigationScreenModelRoute")
  }

  sealed class UserAuth(route: String): NavigationScreenModel(route) {

    data object Main: UserAuth("UserAuthNavigationScreenModelRoute")

    data object LogIn: UserAuth("UserAuthLogInNavigationScreenModelRoute")
    data object SignUp: UserAuth("UserAuthLogInNavigationScreenModelRoute")
  }

  object Splash: NavigationScreenModel("SplashNavigationScreenModelRoute")
}
