package kz.aita.compose.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kz.aita.AppUIConfiguration
import kz.aita.compose.widget.LargeIconWithTitleWidget
import kz.aita.compose.widget.actionButton
import kz.aita.compose.widget.emailTextField
import kz.aita.compose.widget.errorText
import kz.aita.compose.widget.genericTextField
import kz.aita.compose.widget.phoneNumberWithCountrySelectionTextField
import kz.aita.compose.widget.repeatedPasswordTextFieldGroup

@Composable
fun AppUIConfiguration.UserAuthSignUpScreenNarrow() {
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
          title = stateValues.stringSignUp
        )

        val outerSpace = 16.dp
        val innerSpace = 8.dp

        Spacer(modifier = Modifier.height(outerSpace))

        val phoneNumberTextFieldContent = phoneNumberWithCountrySelectionTextField(
          countries = stateValues.globalAppConfiguration.countries
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val emailTextFieldContent = emailTextField()

        Spacer(modifier = Modifier.height(innerSpace))

        val firstNameTextFieldContent = genericTextField(
          titleText = stateValues.stringFirstName,
          placeholderText = stateValues.stringEnterFirstName,
          leadingIconPath = stateValues.drawablePathIconPerson
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val lastNameTextFieldContent = genericTextField(
          titleText = stateValues.stringLastName,
          placeholderText = stateValues.stringEnterLastName,
          leadingIconPath = stateValues.drawablePathIconPerson
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val (passwordTextFieldContent, repeatedPasswordTextFieldContent) = repeatedPasswordTextFieldGroup()

        errorText(
          stateValues.stringUserWithThisPhoneNumberIsAlreadyRegistered
        ) {
          true
        }

        Spacer(modifier = Modifier.height(outerSpace))

        actionButton(
          text = stateValues.stringSignUp
        ) {
          phoneNumberTextFieldContent.checkContentValidity()
          emailTextFieldContent.checkContentValidity()

          passwordTextFieldContent.checkContentValidity()
          repeatedPasswordTextFieldContent.checkContentValidity()
        }

        Spacer(
          modifier = Modifier
            .height(stateValues.screenHeight / 10)
        )
      }
    }
  }
}
