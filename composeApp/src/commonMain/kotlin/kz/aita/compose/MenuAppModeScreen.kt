package kz.aita.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.configurationRepository

@Composable
fun AppConfiguration.MenuAppModeScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ){
    ScreenAppBarWidget(
      title = stateValues.stringAppMode,
      iconPath = stateValues.drawablePathIconSwitch,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )

    @Composable
    fun Card(
      text: String,
      onClick: (String) -> Unit
    ) {
      Column(
        modifier = Modifier
          .clip(RoundedCornerShape(stateValues.cornerRadius))
          .border(stateValues.unfocusedBorderWidth, color = stateValues.TextColor, RoundedCornerShape(stateValues.cornerRadius))
          .clickable(
            interactionSource = remember {
              MutableInteractionSource()
            },
            indication = ripple(color = stateValues.TextColor)
          ) {
            onClick(text)
          }
      ) {
        Text(
          modifier = Modifier
            .padding(16.dp),
          text = text,
          color = stateValues.TextColor,
          fontWeight = FontWeight.Bold
        )
      }
    }

    if (AppConfiguration.stateValues.isNarrowScreen) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxHeight(0.6f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Column {
          Card(
            "Store"
          ) {
            configurationRepository.setAppMode(0)
          }

          Spacer(modifier = Modifier.height(4.dp))

          Card(
            "Buyer"
          ) {
            configurationRepository.setAppMode(1)
          }

          Spacer(modifier = Modifier.height(4.dp))

          Card(
            "Supplier"
          ) {
            configurationRepository.setAppMode(2)
          }

          Spacer(modifier = Modifier.height(4.dp))

          Card(
            "Producer"
          ) {
            configurationRepository.setAppMode(3)
          }

          Spacer(modifier = Modifier.height(4.dp))
        }
      }
    } else {
      Row(
        modifier = Modifier
          .fillMaxWidth(0.6f)
          .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Card(
          "Store"
        ) {
          configurationRepository.setAppMode(0)
        }

        Spacer(modifier = Modifier.width(4.dp))

        Card(
          "Buyer"
        ) {
          configurationRepository.setAppMode(1)
        }

        Spacer(modifier = Modifier.width(4.dp))

        Card(
          "Supplier"
        ) {
          configurationRepository.setAppMode(2)
        }

        Spacer(modifier = Modifier.width(4.dp))

        Card(
          "Producer"
        ) {
          configurationRepository.setAppMode(3)
        }

        Spacer(modifier = Modifier.width(4.dp))
      }
    }
  }
}