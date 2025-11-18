package kz.aita.compose.screen.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.widget.MessageText
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.compose.widget.StoreWidget
import kz.aita.compose.widget.searchTextField
import kz.aita.core.search
import kz.aita.core.storeRepository
import kz.aita.model.dataModel.StoreDataModel
import kz.aita.model.wrapper.DataState

@Composable
fun AppConfiguration.MenuStoresScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringStores,
      iconPath = stateValues.drawablePathIconStores,
//      leadingContent = stateValues.activeStoreId?.let { activeStoreId ->
//        stateValues.stores?.find { it.id == activeStoreId }?.let { activeStore ->
//          activeStore.name.extractLocalizedString(stateValues.appLanguage)?.let { name ->
//            {
//              Row(
//                horizontalArrangement = Arrangement.Center
//              ) {
//                Text(
//                  text = name,
//                  color = stateValues.TextColor,
//                  fontSize = stateValues.accentTextSize,
//                  fontWeight = FontWeight.Bold
//                )
//
//                Spacer(modifier = Modifier.width(8.dp))
//
//                actionButton(
//                  text = "",
//                  iconPath = stateValues.drawablePathIconSwitch
//                ) {
//
//                }
//              }
//            }
//          }
//        }
//      },
      trailingIcons = listOf(
        stateValues.drawablePathIconAdd to {
          coroutineScope.launch {
            Navigation.Menu.go(NavigationScreenModel.Menu.AddEditStore, stateValues.isNarrowScreen)
          }
        },
      ),
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )

    when (val state = stateValues.storesState) {
      is DataState.Success -> {
        if (state.payload.isEmpty()) {
          MessageText(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            stateValues.stringListEmpty
          )
        } else {
          val searchTextFieldContent =
            searchTextField(
              stateHost = NavigationScreenModel.Menu.Stores,
              stateKey = NavigationScreenModel.KEY_STATE_EMAIL,
              modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(top = 8.dp)
            )

          val items = searchTextFieldContent
            .value
            .text
            .takeIf {
              it.isNotEmpty()
            }?.let { query ->
              state.payload.search<StoreDataModel>(query).first
            } ?: state.payload

          if (items.isEmpty()) {
            MessageText(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
              stateValues.stringNoMatches
            )
          } else {
            LazyColumn(
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth(0.6f)
                .padding(top = 8.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              items(items) { store ->
                StoreWidget(
                  store = store,
                  isActive = stateValues.activeStoreId == store.id,
                  onDelete = {

                  },
                  onEdit = {
                    coroutineScope.launch {
                      NavigationScreenModel.Menu.AddEditStore.setState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID to store.id)
                      Navigation.Menu.go(NavigationScreenModel.Menu.AddEditStore)
                    }
                  },
                  onSetActive = {
                    storeRepository.setActiveStoreId(it.id)
                  }
                )
              }
            }
          }
        }
      }

      is DataState.Empty -> {
        MessageText(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          stateValues.stringListEmpty
        )
      }
    }
  }
}