package kz.aita.compose.screen.userAuth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.util.checkAsPersonName
import kz.aita.compose.util.filterAsPersonName
import kz.aita.compose.widget.*
import kz.aita.model.dataModel.UserAuthSignUpDataModel
import kz.aita.model.repository.UserRepository

@Composable
fun AppConfiguration.UserAuthSignUpScreen(
  userRepository: UserRepository
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
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

      val phoneNumberTextFieldContent = countrySelectionPhoneNumberTextField(
        stateHost = NavigationScreenModel.UserAuth.SignUp,
        stateKey = "phone_number",
      )

      Spacer(modifier = Modifier.height(innerSpace))

      val emailTextFieldContent = emailTextField(
        stateHost = NavigationScreenModel.UserAuth.SignUp,
        stateKey = "email",
      )

      Spacer(modifier = Modifier.height(innerSpace))

      val firstNameTextFieldContent = genericTextField(
        titleText = stateValues.stringFirstName,
        placeholderText = stateValues.stringEnterFirstName,
        leadingIconPath = stateValues.drawablePathIconPerson,
        contentInvalidText = stateValues.stringFirstNameCannotBeEmptyOrJustWhitespaces,
        stateHost = NavigationScreenModel.UserAuth.SignUp,
        stateKey = "first_name",
        onContentValidityCheck = {
          it.checkAsPersonName()
        },
        onFilterValue = {
          it.filterAsPersonName()
        }
      )

      Spacer(modifier = Modifier.height(innerSpace))

      val lastNameTextFieldContent = genericTextField(
        titleText = stateValues.stringLastName,
        placeholderText = stateValues.stringEnterLastName,
        leadingIconPath = stateValues.drawablePathIconPerson,
        contentInvalidText = stateValues.stringLastNameCannotBeEmptyOrJustWhitespaces,
        stateHost = NavigationScreenModel.UserAuth.SignUp,
        stateKey = "last_name",
        onContentValidityCheck = {
          it.checkAsPersonName()
        },
        onFilterValue = {
          it.filterAsPersonName()
        }
      )

      Spacer(modifier = Modifier.height(innerSpace))

      val (passwordTextFieldContent, repeatedPasswordTextFieldContent) = repeatedPasswordTextFieldGroup(
        stateHost = NavigationScreenModel.UserAuth.SignUp,
        stateKey = "new_password",
        repeatedStateKey = "repeated_password",
      )

      Spacer(modifier = Modifier.height(outerSpace))

      actionButton(
        text = stateValues.stringSignUp,
        enabled = stateValues.latestNotification?.message?.equals(stateValues.stringSignUp) != true,
        icon = if (stateValues.latestNotification?.message?.equals(stateValues.stringSignUp) == true) {
          {
            CircularProgressIndicator(
              color = stateValues.AccentTextColor,
              modifier = Modifier
                .padding(start = 20.dp)
                .size(20.dp)
            )
          }
        } else null
      ) {
        phoneNumberTextFieldContent.checkContentValidity()
        emailTextFieldContent.checkContentValidity()

        firstNameTextFieldContent.checkContentValidity()
        lastNameTextFieldContent.checkContentValidity()

        passwordTextFieldContent.checkContentValidity()
        repeatedPasswordTextFieldContent.checkContentValidity()

        if (
          phoneNumberTextFieldContent.isContentValid
          && emailTextFieldContent.isContentValid
          && firstNameTextFieldContent.isContentValid
          && lastNameTextFieldContent.isContentValid
          && passwordTextFieldContent.isContentValid
          && repeatedPasswordTextFieldContent.isContentValid
        )
          userRepository
            .signUp(
              UserAuthSignUpDataModel(
                phoneNumber = stateValues.globalAppConfiguration.countries.run {
                  find { it.locale.equals(phoneNumberTextFieldContent.selectedId, true) } ?: first()
                }.phoneNumberCode + phoneNumberTextFieldContent.value.text.trim(),
                email = emailTextFieldContent.value.text.trim(),
                firstName = firstNameTextFieldContent.value.text.trim(),
                lastName = lastNameTextFieldContent.value.text.trim(),
                countryLocale = phoneNumberTextFieldContent.selectedId,
                password = passwordTextFieldContent.value.text
              )
            )

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
