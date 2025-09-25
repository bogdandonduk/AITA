package kz.aita.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import kz.aita.AppUIConfiguration
import kz.aita.core.getFullDrawableResourceUrl
import kz.aita.widget.genericTextField
import kz.aita.widget.LargeIconWithTitleWidget

@Composable
fun AppUIConfiguration.UserAuthLogInScreenNarrow() {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      item {
        Column(
          modifier = Modifier
            .width(stateValues.screenWidth / 4),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          LargeIconWithTitleWidget(
            imageUrl = getFullDrawableResourceUrl(stateValues.drawablePathAITALogo),
            title = stateValues.stringLogIn
          )

          val loginTextFieldContext =
            genericTextField(
              titleText = stateValues.stringPhoneNumber
            )
        }
      }
    }
}
