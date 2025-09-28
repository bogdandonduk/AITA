package kz.aita.compose.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kz.aita.AppUIConfiguration
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
fun AppUIConfiguration.UserAuthLogInScreenNarrow() {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    item {
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
        LargeIconWithTitleWidget(
          imageUrl = stateValues.drawablePathAITALogo,
          title = stateValues.stringLogIn
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
      }

      Spacer(
        modifier = Modifier
          .height(stateValues.screenHeight / 10)
      )
    }
  }
}
