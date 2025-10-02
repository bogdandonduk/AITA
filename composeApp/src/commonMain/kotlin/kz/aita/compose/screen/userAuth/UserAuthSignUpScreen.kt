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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kz.aita.AppUIConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.widget.LargeIconWithTitleWidget
import kz.aita.compose.widget.actionButton
import kz.aita.compose.widget.emailTextField
import kz.aita.compose.widget.errorText
import kz.aita.compose.widget.genericTextField
import kz.aita.compose.widget.phoneNumberWithCountrySelectionTextField
import kz.aita.compose.widget.repeatedPasswordTextFieldGroup
import kz.aita.core.io
import kz.aita.model.dataModel.UserAuthSignUpDataModel
import kz.aita.model.repository.UserRepository

@Composable
fun AppUIConfiguration.UserAuthSignUpScreen(
  userRepository: UserRepository
) {
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
            title = stateValues.stringSignUp
          )

        if (!stateValues.isNarrowScreen)
          Text(
            text = stateValues.stringSignUp,
            style = TextStyle(
              color = stateValues.TextColor,
              fontSize = stateValues.titleTextSize,
              fontWeight = FontWeight.Bold
            )
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

        errorText(stateValues.stringUserWithThisPhoneNumberIsAlreadyRegistered) {
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

          if (phoneNumberTextFieldContent.isContentValid && emailTextFieldContent.isContentValid && passwordTextFieldContent.isContentValid && repeatedPasswordTextFieldContent.isContentValid)
            coroutineScope.launch(Dispatchers.io) {
              userRepository
                .signUp(
                  UserAuthSignUpDataModel(
                    phoneNumberTextFieldContent.value.text,
                    emailTextFieldContent.value.text,
                    passwordTextFieldContent.value.text,
                    firstNameTextFieldContent.value.text,
                    lastNameTextFieldContent.value.text,
                    phoneNumberTextFieldContent.selectedCountryLocale
                  )
                )
            }
        }

        if (stateValues.isNarrowScreen) {
          Spacer(modifier = Modifier.height(2.dp))

          actionButton(
            text = stateValues.stringCancel,
            enabledColor = stateValues.DisabledColor
          ) {
            coroutineScope.launch {
              Navigation.UserAuth.popLeft()
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
}
