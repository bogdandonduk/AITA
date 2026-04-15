package kz.aita.compose

import kz.aita.StateHost
import org.jetbrains.compose.resources.DrawableResource


sealed class NavigationScreenModel(
  val route: String,
  open val name: String = route,
  open val iconPath: String = "",
  open val iconRes: DrawableResource? = null
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
    const val KEY_STATE_QUANTITY: String = "keyState_quantity"
  }

  sealed class Buyer(route: String): NavigationScreenModel(route) {

    sealed class Main(route: String): Buyer(route) {
      data object Home: Main("BuyerMainHomeNavigationScreenModelRoute") {
        override val name: String
          get() = AppConfiguration.stateValues.stringMain // TODO
        override val iconPath: String
          get() {
            return AppConfiguration.stateValues.drawablePathIconMenu // TODO
          }
        override val iconRes: DrawableResource
          get() {
            return AppConfiguration.stateValues.drawableResIconMenu.value
          }
      }
      data object Search: Main("BuyerMainSearchNavigationScreenModelRoute")
    }

    sealed class Cart(route: String): Buyer(route) {
      data object Main: Cart("BuyerCartMainNavigationScreenModelRoute") {
        override val name: String
          get() = AppConfiguration.stateValues.stringCart
        override val iconPath: String
          get() {
            return AppConfiguration.stateValues.drawablePathIconTransactionSale
          }
        override val iconRes: DrawableResource
          get() {
            return AppConfiguration.stateValues.drawableResIconTransactionSale.value
          }
      }
    }

    sealed class Orders(route: String): Buyer(route) {
      data object Main: Orders("BuyerOrdersMainNavigationScreenModelRoute") {
        override val name: String
          get() = "Orders" // TODO
        override val iconPath: String
          get() {
            return AppConfiguration.stateValues.drawablePathIconTransactionHistory
          }
        override val iconRes: DrawableResource
          get() {
            return AppConfiguration.stateValues.drawableResIconTransactionHistory.value
          }
      }
    }
  }

  sealed class Transaction(route: String): NavigationScreenModel(route) {

    data object MainSale: Transaction("TransactionMainSaleNavigationScreenModelRoute") {
      override val iconPath: String
        get() {
          return AppConfiguration.stateValues.drawablePathIconTransactionSale
        }
      override val iconRes: DrawableResource
        get() {
          return AppConfiguration.stateValues.drawableResIconTransactionSale.value
        }
      override val name: String
        get() = AppConfiguration.stateValues.stringSale
    }

    data object MainReturn: Transaction("TransactionMainReturnNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconTransactionReturn
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconTransactionReturn.value
      override val name: String
        get() = AppConfiguration.stateValues.stringReturn
    }

    data object MainSupply: Transaction("TransactionMainSupplyNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconTransactionSupply
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconTransactionSupply.value
      override val name: String
        get() = AppConfiguration.stateValues.stringSupply
    }

    data object Cart: Transaction("TransactionCartScreenNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconCart
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconCart.value
      override val name: String
        get() = AppConfiguration.stateValues.stringCart
    }

    data object Selection: Transaction("TransactionSelectionNavigationScreenModelRoute") {
      override val iconPath: String
        get() = ""
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconCart.value
      override val name: String
        get() = AppConfiguration.stateValues.stringSelect
    }

    data object Payment: Transaction("TransactionPaymentNavigationScreenModelRoute") {
      override val iconPath: String
        get() = ""
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconCart.value
      override val name: String
        get() = AppConfiguration.stateValues.stringPayment
    }

    data object ReceiptPreview: Transaction("TransactionReceiptPreviewNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconReceipt
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconReceipt.value
      override val name: String
        get() = AppConfiguration.stateValues.stringReceipt
    }
  }

  sealed class Stock(route: String): NavigationScreenModel(route) {

    data object Main: Stock("StockNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconStock
      override val name: String
        get() = AppConfiguration.stateValues.stringStock
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconStock.value
    }

    data object Warehouse: Stock("StockWarehouseNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object AddEditGoodsItem: Stock("StockAddGoodsItemNavigationScreenModelRoute") {
      const val KEY_STATE_EDITED_GOODS_ITEM_ID: String = "keyState_editedGoodsItemId"
      const val KEY_STATE_BARCODE: String = "keyState_barcode"
      const val KEY_STATE_SALE_DATA: String = "keyState_saleData"
      const val KEY_STATE_RETURN_DATA: String = "keyState_returnData"
      const val KEY_STATE_SUPPLY_DATA: String = "keyState_supplyData"

      const val KEY_STATE_QUANTITY_DATA: String = "keyState_quantityData"
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }
  }

  sealed class Menu(route: String): NavigationScreenModel(route) {

    data object Main: Menu("MenuNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconMenu
      override val name: String
        get() = AppConfiguration.stateValues.stringMenu
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconMenu.value
    }

    data object List: Menu("MenuListNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object UserAccount: Menu("MenuUserAccountNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconUserAccount
      override val name: String
        get() = AppConfiguration.stateValues.stringUserAccount
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconUserAccount.value
      const val KEY_STATE_CONFIRMATION_PASSWORD: String = "keyState_confirmationPassword"
    }

    data object Finances: Menu("MenuFinancesNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconFinances
      override val name: String
        get() = AppConfiguration.stateValues.stringFinances
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconFinances.value
    }

    data object AppMode: Menu("MenuAppModeNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconSwitch
      override val name: String
        get() = AppConfiguration.stateValues.stringAppMode
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconSwitch.value
    }

    data object StoreSubscription: Menu("MenuStoreSubscriptionNavigationScreenModelRoute") {
      override val iconPath: String
        get() = ""
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconStores.value
      override val name: String
        get() = AppConfiguration.stateValues.stringSubscription
    }

    data object StoreSubscriptionPlans: Menu("MenuStoreSubscriptionPlansNavigationScreenModelRoute") {
      override val iconPath: String
        get() = ""
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconStores.value
      override val name: String
        get() = AppConfiguration.stateValues.stringSubscriptionPlans
    }

    data object Workers: Menu("MenuWorkersNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconWorkers
      override val name: String
        get() = AppConfiguration.stateValues.stringWorkers
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconWorkers.value
    }
    data object AddEditWorker: Menu("MenuAddEditWorkerNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconWorkers
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconWorkers.value
      override val name: String
        get() = AppConfiguration.stateValues.stringAddWorker
    }

    data object Stores: Menu("MenuStoresNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconStores
      override val name: String
        get() = AppConfiguration.stateValues.stringStores
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconStores.value
    }
    data object AddEditStore: Menu("MenuAddEditStoreNavigationScreenModelRoute") {
      const val KEY_STATE_EDITED_STORE_ID: String = "keyState_editedStoreId"
      const val KEY_STATE_ALIAS: String = "keyState_alias"
      const val KEY_STATE_DESCRIPTION: String = "keyState_description"

      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object Analytics: Menu("MenuAnalyticsNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconAnalytics
      override val name: String
        get() = AppConfiguration.stateValues.stringAnalytics
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconAnalytics.value
    }

    data object TransactionHistory: Menu("MenuTransactionHistoryNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconTransactionHistory
      override val name: String
        get() = AppConfiguration.stateValues.stringTransactionHistory
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconTransactionHistory.value
    }
    data object TransactionHistoryReceiptPreview: Menu("MenuTransactionHistoryReceiptPreviewNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object Debtors: Menu("MenuDebtorsNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconDebtors
      override val name: String
        get() = AppConfiguration.stateValues.stringDebtors
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconDebtors.value
    }
    data object CloseDebt: Menu("MenuCloseDebtNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object Suppliers: Menu("MenuSuppliersNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconSuppliers
      override val name: String
        get() = AppConfiguration.stateValues.stringSuppliers
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconSuppliers.value
    }
    data object AddEditSupplier: Menu("MenuAddEditSupplierNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object GoodsCategories: Menu("MenuGoodsCategoriesNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconGoodsCategories
      override val name: String
        get() = AppConfiguration.stateValues.stringGoodsCategories
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconGoodsCategories.value
    }
    data object AddEditGoodsCategory: Menu("MenuAddEditGoodsCategoryNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconGoodsCategories
      override val name: String
        get() = AppConfiguration.stateValues.stringGoodsCategories
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconGoodsCategories.value
    }

    data object Devices: Menu("MenuDevicesNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconDevices
      override val name: String
        get() = AppConfiguration.stateValues.stringDevices
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconDevices.value
    }
    data object AppLanguage: Menu("MenuAppLanguageNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconAppLanguage
      override val name: String
        get() = AppConfiguration.stateValues.stringAppLanguage
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconAppLanguage.value
    }
    data object AppTheme: Menu("MenuAppThemeNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconAppTheme
      override val name: String
        get() = AppConfiguration.stateValues.stringAppTheme
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconAppTheme.value
    }
  }

  sealed class UserAuth(route: String): NavigationScreenModel(route) {

    data object Main: UserAuth("UserAuthNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object LogIn: UserAuth("UserAuthLogInNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object SignUp: UserAuth("UserAuthLogInNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }
  }

  object Splash: NavigationScreenModel("SplashNavigationScreenModelRoute") {
    override val iconRes: DrawableResource
      get() = TODO("Not yet implemented")
  }
}
