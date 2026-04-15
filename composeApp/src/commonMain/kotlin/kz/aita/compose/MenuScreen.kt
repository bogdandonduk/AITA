package kz.aita.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppConfiguration.MenuScreen() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {

    if (stateValues.isNarrowScreen) {
      AnimatedContent(
        modifier = Modifier
          .weight(1f),
        targetState = stateValues.navigationScreensMenuLeft.last()
      ) { model ->
        when (model) {
          is NavigationScreenModel.Menu.List -> {
            MenuListScreen()
          }
          is NavigationScreenModel.Menu.AppMode -> {
            MenuAppModeScreen()
          }
          is NavigationScreenModel.Menu.UserAccount -> {
            MenuUserAccountScreen()
          }
          is NavigationScreenModel.Menu.Finances -> {
            MenuFinancesScreen()
          }
          is NavigationScreenModel.Menu.GoodsCategories -> {
            MenuGoodsCategoriesScreen()
          }
          is NavigationScreenModel.Menu.AddEditGoodsCategory -> {
            MenuAddEditGoodsCategoryScreen()
          }
          is NavigationScreenModel.Menu.Stores -> {
            MenuStoresScreen()
          }
          is NavigationScreenModel.Menu.AddEditStore -> {
            MenuAddEditStoreScreen()
          }
          is NavigationScreenModel.Menu.StoreSubscription -> {
            MenuStoreSubscriptionScreen()
          }
          is NavigationScreenModel.Menu.StoreSubscriptionPlans -> {
            MenuStoreSubscriptionPlansScreen()
          }
          is NavigationScreenModel.Menu.TransactionHistory -> {
            MenuTransactionHistoryScreen()
          }
          is NavigationScreenModel.Menu.TransactionHistoryReceiptPreview -> {
            MenuTransactionHistoryReceiptPreviewScreen()
          }
          is NavigationScreenModel.Menu.Analytics -> {
            MenuAnalyticsScreen()
          }
          is NavigationScreenModel.Menu.Workers -> {
            MenuWorkersScreen()
          }
          is NavigationScreenModel.Menu.AddEditWorker -> {
            MenuAddEditWorkerScreen()
          }
          is NavigationScreenModel.Menu.Suppliers -> {
            MenuSuppliersScreen()
          }
          is NavigationScreenModel.Menu.AddEditSupplier -> {
            MenuAddEditSupplierScreen()
          }
          is NavigationScreenModel.Menu.Debtors -> {
            MenuDebtorsScreen()
          }
          is NavigationScreenModel.Menu.CloseDebt -> {
            MenuCloseDebtScreen()
          }
          is NavigationScreenModel.Menu.Devices -> {
            MenuDevicesScreen()
          }
          is NavigationScreenModel.Menu.AppLanguage -> {
            MenuAppLanguageScreen()
          }
          is NavigationScreenModel.Menu.AppTheme -> {
            MenuAppThemeScreen()
          }

          else -> {}
        }
      }
    } else {
      Row(
        modifier = Modifier
          .weight(1f)
      ) {
        AnimatedContent(
          modifier = Modifier
            .weight(0.2f),
          targetState = stateValues.navigationScreensMenuLeft.last()
        ) { model ->
          when (model) {
            is NavigationScreenModel.Menu.List -> {
              MenuListScreen()
            }
            is NavigationScreenModel.Menu.AppMode -> {
              MenuAppModeScreen()
            }
            is NavigationScreenModel.Menu.UserAccount -> {
              MenuUserAccountScreen()
            }
            is NavigationScreenModel.Menu.Finances -> {
              MenuFinancesScreen()
            }
            is NavigationScreenModel.Menu.GoodsCategories -> {
              MenuGoodsCategoriesScreen()
            }
            is NavigationScreenModel.Menu.AddEditGoodsCategory -> {
              MenuAddEditGoodsCategoryScreen()
            }
            is NavigationScreenModel.Menu.Stores -> {
              MenuStoresScreen()
            }
            is NavigationScreenModel.Menu.AddEditStore -> {
              MenuAddEditStoreScreen()
            }
            is NavigationScreenModel.Menu.StoreSubscription -> {
              MenuStoreSubscriptionScreen()
            }
            is NavigationScreenModel.Menu.StoreSubscriptionPlans -> {
              MenuStoreSubscriptionPlansScreen()
            }
            is NavigationScreenModel.Menu.TransactionHistory -> {
              MenuTransactionHistoryScreen()
            }
            is NavigationScreenModel.Menu.TransactionHistoryReceiptPreview -> {
              MenuTransactionHistoryReceiptPreviewScreen()
            }
            is NavigationScreenModel.Menu.Analytics -> {
              MenuAnalyticsScreen()
            }
            is NavigationScreenModel.Menu.Workers -> {
              MenuWorkersScreen()
            }
            is NavigationScreenModel.Menu.AddEditWorker -> {
              MenuAddEditWorkerScreen()
            }
            is NavigationScreenModel.Menu.Suppliers -> {
              MenuSuppliersScreen()
            }
            is NavigationScreenModel.Menu.AddEditSupplier -> {
              MenuAddEditSupplierScreen()
            }
            is NavigationScreenModel.Menu.Debtors -> {
              MenuDebtorsScreen()
            }
            is NavigationScreenModel.Menu.CloseDebt -> {
              MenuCloseDebtScreen()
            }
            is NavigationScreenModel.Menu.Devices -> {
              MenuDevicesScreen()
            }
            is NavigationScreenModel.Menu.AppLanguage -> {
              MenuAppLanguageScreen()
            }
            is NavigationScreenModel.Menu.AppTheme -> {
              MenuAppThemeScreen()
            }

            else -> {}
          }
        }

        AnimatedContent(
          modifier = Modifier
            .weight(1f),
          targetState = stateValues.navigationScreensMenuRight.last()
        ) { model ->
          when (model) {
            is NavigationScreenModel.Menu.List -> {
              MenuListScreen()
            }
            is NavigationScreenModel.Menu.AppMode -> {
              MenuAppModeScreen()
            }
            is NavigationScreenModel.Menu.UserAccount -> {
              MenuUserAccountScreen()
            }
            is NavigationScreenModel.Menu.Finances -> {
              MenuFinancesScreen()
            }
            is NavigationScreenModel.Menu.GoodsCategories -> {
              MenuGoodsCategoriesScreen()
            }
            is NavigationScreenModel.Menu.AddEditGoodsCategory -> {
              MenuAddEditGoodsCategoryScreen()
            }
            is NavigationScreenModel.Menu.Stores -> {
              MenuStoresScreen()
            }
            is NavigationScreenModel.Menu.AddEditStore -> {
              MenuAddEditStoreScreen()
            }
            is NavigationScreenModel.Menu.StoreSubscription -> {
              MenuStoreSubscriptionScreen()
            }
            is NavigationScreenModel.Menu.StoreSubscriptionPlans -> {
              MenuStoreSubscriptionPlansScreen()
            }
            is NavigationScreenModel.Menu.TransactionHistory -> {
              MenuTransactionHistoryScreen()
            }
            is NavigationScreenModel.Menu.TransactionHistoryReceiptPreview -> {
              MenuTransactionHistoryReceiptPreviewScreen()
            }
            is NavigationScreenModel.Menu.Analytics -> {
              MenuAnalyticsScreen()
            }
            is NavigationScreenModel.Menu.Workers -> {
              MenuWorkersScreen()
            }
            is NavigationScreenModel.Menu.AddEditWorker -> {
              MenuAddEditWorkerScreen()
            }
            is NavigationScreenModel.Menu.Suppliers -> {
              MenuSuppliersScreen()
            }
            is NavigationScreenModel.Menu.AddEditSupplier -> {
              MenuAddEditSupplierScreen()
            }
            is NavigationScreenModel.Menu.Debtors -> {
              MenuDebtorsScreen()
            }
            is NavigationScreenModel.Menu.CloseDebt -> {
              MenuCloseDebtScreen()
            }
            is NavigationScreenModel.Menu.Devices -> {
              MenuDevicesScreen()
            }
            is NavigationScreenModel.Menu.AppLanguage -> {
              MenuAppLanguageScreen()
            }
            is NavigationScreenModel.Menu.AppTheme -> {
              MenuAppThemeScreen()
            }

            else -> {}
          }
        }

//        LazyColumn(
//          modifier = Modifier
//            .weight(0.5f)
//            .background(stateValues.DisabledColor)
//            .fillMaxHeight()
//        ) {
//
//        }
      }
    }
  }
}
