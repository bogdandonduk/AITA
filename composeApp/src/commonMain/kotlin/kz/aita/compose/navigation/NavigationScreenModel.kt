package kz.aita.compose.navigation

import kz.aita.AppConfiguration
import kz.aita.core.StateHost


sealed class NavigationScreenModel(
  val route: String,
  open val name: String = route,
  open val iconPath: String = ""
): StateHost() {

  companion object {
    const val KEY_STATE_SEARCH_QUERY: String = "keyState_searchQuery"
    const val KEY_STATE_NAME: String = "keyState_name"
    const val KEY_STATE_PHONE_NUMBER: String = "keyState_phoneNumber"
    const val KEY_STATE_EMAIL: String = "keyState_email"
    const val KEY_STATE_FIRST_NAME: String = "keyState_firstName"
    const val KEY_STATE_LAST_NAME: String = "keyState_lastName"
    const val KEY_STATE_PASSWORD: String = "keyState_password"
    const val KEY_STATE_REPEATED_PASSWORD: String = "keyState_repeatedPassword"
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

    data object Payment: Transaction("TransactionPaymentNavigationScreenModelRoute")

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

    data object AddEditGoodsItem: Stock("StockAddGoodsItemNavigationScreenModelRoute") {
      const val KEY_STATE_EDITED_GOODS_ITEM_ID: String = "keyState_editedGoodsItemId"
      const val KEY_STATE_BARCODE: String = "keyState_barcode"
      const val KEY_STATE_SALE_DATA: String = "keyState_saleData"
      const val KEY_STATE_RETURN_DATA: String = "keyState_returnData"
      const val KEY_STATE_SUPPLY_DATA: String = "keyState_supplyData"
    }
  }

  sealed class Menu(route: String): NavigationScreenModel(route) {

    data object Main: Menu("MenuNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconMenu
      override val name: String
        get() = AppConfiguration.stateValues.stringMenu
    }

    data object List: Menu("MenuListNavigationScreenModelRoute")

    data object UserAccount: Menu("MenuUserAccountNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconUserAccount
      override val name: String
        get() = AppConfiguration.stateValues.stringUserAccount

      const val KEY_STATE_CONFIRMATION_PASSWORD: String = "keyState_confirmationPassword"
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
    data object AddEditStore: Menu("MenuAddEditStoreNavigationScreenModelRoute") {
      const val KEY_STATE_EDITED_STORE_ID: String = "keyState_editedStoreId"
      const val KEY_STATE_ALIAS: String = "keyState_alias"
      const val KEY_STATE_DESCRIPTION: String = "keyState_description"
    }

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
