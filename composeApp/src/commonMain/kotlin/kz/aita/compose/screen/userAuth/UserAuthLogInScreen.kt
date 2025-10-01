package kz.aita.compose.screen.userAuth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.AppUIConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.widget.GenericTextFieldContent
import kz.aita.compose.widget.LargeIconWithTitleWidget
import kz.aita.compose.widget.PhoneNumberWithCountrySelectionTextFieldContent
import kz.aita.compose.widget.TabContent
import kz.aita.compose.widget.actionButton
import kz.aita.compose.widget.emailTextField
import kz.aita.compose.widget.passwordTextField
import kz.aita.compose.widget.tabRowWidget
import kz.aita.compose.widget.errorText
import kz.aita.compose.widget.phoneNumberWithCountrySelectionTextField

@Composable
fun AppUIConfiguration.UserAuthLogInScreen() {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    item {
      if (stateValues.isNarrowScreen)
        Spacer(
          modifier = Modifier
            .height(stateValues.screenHeight / 6)
        )

      Column(
        modifier = Modifier
          .width(stateValues.boundWidgetWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        if (stateValues.isNarrowScreen)
          LargeIconWithTitleWidget(
            imageUrl = stateValues.drawablePathAITALogo,
            title = stateValues.stringLogIn
          )

        if (!stateValues.isNarrowScreen)
          Text(
            text = stateValues.stringLogIn,
            style = TextStyle(
              color = stateValues.TextColor,
              fontSize = stateValues.titleTextSize,
              fontWeight = FontWeight.Bold
            )
          )

        val outerSpace = 16.dp
        val innerSpace = 8.dp

        Spacer(modifier = Modifier.height(outerSpace))

        val loginMethodTabRowContent = tabRowWidget(
          modifier = Modifier,
          tabs = listOf(
            TabContent(stateValues.stringPhoneNumber),
            TabContent(stateValues.stringEmail)
          )
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val loginTextFieldContent = when (loginMethodTabRowContent.index) {
          0 -> {
            phoneNumberWithCountrySelectionTextField(
              countries = stateValues.globalAppConfiguration.countries
            )
          }

          else -> {
            emailTextField()
          }
        }

        Spacer(modifier = Modifier.height(innerSpace))

        val passwordTextFieldContent = passwordTextField()

        errorText(
          stateValues.stringLoginAndOrPasswordIncorrect
        ) {
          true
        }

        Spacer(modifier = Modifier.height(outerSpace))

        actionButton(
          text = stateValues.stringLogIn
        ) {
          (loginTextFieldContent as? PhoneNumberWithCountrySelectionTextFieldContent)
            ?.checkContentValidity() ?: (loginTextFieldContent as GenericTextFieldContent).checkContentValidity()

          passwordTextFieldContent.checkContentValidity()
        }

        if (stateValues.isNarrowScreen) {
          Spacer(modifier = Modifier.height(2.dp))

          actionButton(
            text = stateValues.stringSignUp,
            enabledColor = stateValues.DisabledColor
          ) {
            coroutineScope.launch {
              Navigation.UserAuth.goLeft(NavigationScreenModel.UserAuth.SignUp)
            }
          }
        }

      }

      Spacer(
        modifier = Modifier
          .height(stateValues.screenHeight / 10)
      )
    }
  }
}
