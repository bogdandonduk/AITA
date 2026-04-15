package kz.aita.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.DataState
import kz.aita.StoreDataModel
import kz.aita.search
import kz.aita.storeRepository

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
        Triple(
          stateValues.drawablePathIconAdd,
          stateValues.drawableResIconAdd.value
        ) {
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

    Column(
      modifier = Modifier
        .fillMaxWidth(0.6f)
        .fillMaxHeight()
    ) {
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
                  .padding(top = 8.dp)
              )

            stateValues.activeStoreId?.run {
              stateValues.stores?.find {
                stateValues.activeStoreId == it.id
              }?.let { store ->
                StoreWidget(
                  modifier = Modifier
                    .padding(top = 8.dp),
                  store = store,
                  onDelete = {

                  },
                  onEdit = {
                    coroutineScope.launch {
                      NavigationScreenModel.Menu.AddEditStore.setState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID to store.id)
                      Navigation.Menu.go(NavigationScreenModel.Menu.AddEditStore)
                    }
                  },
                  onSetInactive = {
                    storeRepository.setActiveStoreId(null)
                  }
                )
              }
            }

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
                  .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                items(items) { store ->
                  val isActive = store.id == stateValues.activeStoreId

                  if (!isActive)
                    StoreWidget(
                      store = store,
                      onDelete = {

                      },
                      onEdit = {
                        coroutineScope.launch {
                          NavigationScreenModel.Menu.AddEditStore.setState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID to store.id)
                          Navigation.Menu.go(NavigationScreenModel.Menu.AddEditStore)
                        }
                      },
                      onSetActive = if (isActive) null else {
                        {
                          storeRepository.setActiveStoreId(it.id)
                        }
                      }
                    )
                }

                item {
                  Spacer(
                    modifier = Modifier
                      .height(stateValues.screenHeight / 4)
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
}