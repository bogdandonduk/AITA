package kz.aita.compose.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MenuNavContainerScreen() {
  if (isWideScreen && isLandscape) {
    ConstraintLayout(
      modifier = Modifier.weight(1f)
    ) {
      AnimatedContent(targetState = currentScreenModel1!!.last(), label = "") { screenModel ->
        when (screenModel) {
          is MenuScreenNavigationScreenModel.MenuScreen ->
            MenuScreen(activity = activity)

          else -> {
          }
        }
      }
    }

    Spacer(
      modifier = Modifier
        .width(notFocusedBorderWidth.dp)
        .fillMaxHeight()
        .background(GrayColor)
    )

    ConstraintLayout(
      modifier = Modifier.weight(1f)
    ) {
      AnimatedContent(targetState = currentScreenModel2!!.last(), label = "") { screenModel ->
        when (screenModel) {
          is MenuScreenNavigationScreenModel.AccountScreen ->
            MenuAccountScreen(activity = activity, viewModel = activity.menuScreenViewModel)

          is MenuScreenNavigationScreenModel.AnalyticsScreen ->
            MenuAnalyticsScreen(activity = activity, viewModel = activity.menuScreenViewModel)

          is MenuScreenNavigationScreenModel.HistoryScreen ->
            MenuHistoryScreen(activity = activity, viewModel = activity.menuScreenViewModel)

          is MenuScreenNavigationScreenModel.HistoryItemReceiptPreviewScreen ->
            MenuHistoryItemReceiptPreviewScreen(activity = activity, viewModel = activity.menuScreenViewModel)

          is MenuScreenNavigationScreenModel.BalanceScreen ->
            MenuBalanceScreen(activity = activity, viewModel = activity.menuScreenViewModel)

          is MenuScreenNavigationScreenModel.SubscriptionPlans ->
            MenuSubscriptionPlansScreen(activity = activity)

          is MenuScreenNavigationScreenModel.EmployeesScreen ->
            MenuEmployeesScreen(activity = activity)

          is MenuScreenNavigationScreenModel.AddEmployeeScreen ->
            MenuAddEmployeeScreen(activity = activity)

          is MenuScreenNavigationScreenModel.StoresScreen ->
            MenuStoresScreen(activity = activity)

          is MenuScreenNavigationScreenModel.AddStoreScreen ->
            MenuAddStoreScreen(activity = activity)

          is MenuScreenNavigationScreenModel.Maps -> {
            MapsScreenActions.sourceMenuScreen = true
            MapsScreen()
          }

          is MenuScreenNavigationScreenModel.DebtorsScreen ->
            MenuDebtorsScreen(activity = activity)

          is MenuScreenNavigationScreenModel.CheckoutDebtor ->
            CashRegisterCheckoutScreen(
              activity = activity,
              cashRegisterScreenViewModel = activity.cashRegisterScreenViewModel,
              menuScreenViewModel = viewModel
            )

          is MenuScreenNavigationScreenModel.LanguageScreen ->
            MenuLanguageScreen(activity = activity)

          is MenuScreenNavigationScreenModel.AccessoriesScreen ->
            MenuAccessoriesScreen(activity = activity)

          is MenuScreenNavigationScreenModel.SuppliersScreen ->
            MenuSuppliesScreen(activity = activity, activity.menuScreenViewModel)

          is MenuScreenNavigationScreenModel.GoodsCategoriesScreen ->
            MenuGoodsCategoriesScreen(activity = activity, activity.stockScreenViewModel)

          is MenuScreenNavigationScreenModel.ThemesScreen ->
            MenuThemesScreen(activity = activity)

          else -> {
          }
        }
      }
    }
  }
} else {
  AnimatedContent(targetState = currentScreenModel1!!.last(), label = "") { screenModel ->
    when (screenModel) {
      is MenuScreenNavigationScreenModel.MenuScreen ->
        MenuScreen(activity = activity)

      is MenuScreenNavigationScreenModel.AccountScreen ->
        MenuAccountScreen(activity = activity, viewModel = activity.menuScreenViewModel)

      is MenuScreenNavigationScreenModel.HistoryScreen ->
        MenuHistoryScreen(activity = activity, viewModel = activity.menuScreenViewModel)

      is MenuScreenNavigationScreenModel.HistoryItemReceiptPreviewScreen ->
        MenuHistoryItemReceiptPreviewScreen(activity = activity, viewModel = activity.menuScreenViewModel)

      is MenuScreenNavigationScreenModel.BalanceScreen ->
        MenuBalanceScreen(activity = activity, viewModel = activity.menuScreenViewModel)

      is MenuScreenNavigationScreenModel.SubscriptionPlans ->
        MenuSubscriptionPlansScreen(activity = activity)

      is MenuScreenNavigationScreenModel.EmployeesScreen ->
        MenuEmployeesScreen(activity = activity)

      is MenuScreenNavigationScreenModel.AddEmployeeScreen ->
        MenuAddEmployeeScreen(activity = activity)

      is MenuScreenNavigationScreenModel.StoresScreen ->
        MenuStoresScreen(activity = activity)

      is MenuScreenNavigationScreenModel.AddStoreScreen ->
        MenuAddStoreScreen(activity = activity)

      is MenuScreenNavigationScreenModel.Maps -> {
        MapsScreenActions.sourceMenuScreen = true
        MapsScreen()
      }

      is MenuScreenNavigationScreenModel.DebtorsScreen ->
        MenuDebtorsScreen(activity = activity)

      is MenuScreenNavigationScreenModel.CheckoutDebtor ->
        CashRegisterCheckoutScreen(
          activity = activity,
          cashRegisterScreenViewModel = activity.cashRegisterScreenViewModel,
          menuScreenViewModel = viewModel
        )

      is MenuScreenNavigationScreenModel.LanguageScreen ->
        MenuLanguageScreen(activity = activity)

      is MenuScreenNavigationScreenModel.AccessoriesScreen ->
        MenuAccessoriesScreen(activity = activity)

      is MenuScreenNavigationScreenModel.SuppliersScreen ->
        MenuSuppliesScreen(activity = activity, activity.menuScreenViewModel)

      is MenuScreenNavigationScreenModel.GoodsCategoriesScreen ->
        MenuGoodsCategoriesScreen(activity = activity, activity.stockScreenViewModel)

      is MenuScreenNavigationScreenModel.ThemesScreen ->
        MenuThemesScreen(activity = activity)

      else -> {
      }
    }
  }
}
}
