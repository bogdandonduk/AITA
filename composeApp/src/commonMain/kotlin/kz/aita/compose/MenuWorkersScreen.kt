package kz.aita.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
fun AppConfiguration.MenuWorkersScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringWorkers,
      iconPath = stateValues.drawablePathIconWorkers,
      trailingIcons = listOf(
        Triple(
          stateValues.drawablePathIconPerson,
          stateValues.drawableResIconPerson.value
        ) {
          coroutineScope.launch {
            Navigation.Menu.go(NavigationScreenModel.Menu.AddEditWorker, stateValues.isNarrowScreen)
          }
        },
        Triple(
          stateValues.drawablePathIconAdd,
          stateValues.drawableResIconAdd.value
        ) {
          coroutineScope.launch {
            Navigation.Menu.go(NavigationScreenModel.Menu.AddEditWorker, stateValues.isNarrowScreen)
          }
        }
      ),
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )
  }
}