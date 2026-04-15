package kz.aita.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.UserAuthLogInDataModel
import kz.aita.userRepository

@Composable
fun AppConfiguration.UserAuthLogInScreen() {
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
      if (stateValues.isNarrowScreen) {
        val drawableResAITALogo by stateValues.drawableResAITALogo.collectAsState()

        LargeIconWithTitleWidget(
          imageUrl = stateValues.drawablePathAITALogo,
          imageRes = drawableResAITALogo,
          title = stateValues.stringLogIn
        )
      }

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
          TabContent("0", stateValues.stringPhoneNumber),
          TabContent("1", stateValues.stringEmail)
        )
      )

      Spacer(modifier = Modifier.height(innerSpace))

      val loginTextFieldContent = when (loginMethodTabRowContent.id) {
        "0" -> {
          countrySelectionPhoneNumberTextField(
            imeWithAction = ImeWithAction(ime = ImeAction.Next),
            stateHost = NavigationScreenModel.UserAuth.LogIn,
            stateKey = NavigationScreenModel.KEY_STATE_PHONE_NUMBER,
            lockedId = stateValues.globalAppConfiguration.countries.find { it.locale == "kz" }?.phoneNumberCode
          )
        }

        else -> {
          emailTextField(
            stateHost = NavigationScreenModel.UserAuth.LogIn,
            stateKey = NavigationScreenModel.KEY_STATE_EMAIL,
          )
        }
      }

      Spacer(modifier = Modifier.height(innerSpace))

      var goAction: (() -> Unit)? = null

      val passwordTextFieldContent = passwordTextField(
        imeWithAction = ImeWithAction(ImeAction.Go) {
          goAction?.invoke()

        },
        stateHost = NavigationScreenModel.UserAuth.LogIn,
        stateKey = "password",
      )

      Spacer(modifier = Modifier.height(outerSpace))

      goAction = {
        softKeyboardController?.hide()
        passwordTextFieldContent.checkContentValidity()

        val loginEmail = loginTextFieldContent !is DomainSelectionTextFieldContent

        if (loginEmail) {
          (loginTextFieldContent as GenericTextFieldContent).run {
            checkContentValidity()

            if (isContentValid && passwordTextFieldContent.isContentValid)
              userRepository
                .logIn(
                  UserAuthLogInDataModel(
                    login = this.value.text,
                    password = passwordTextFieldContent.value.text
                  )
                )
          }
        } else {
          loginTextFieldContent.run {
            checkContentValidity()

            if (isContentValid && passwordTextFieldContent.isContentValid)
              userRepository
                .logIn(
                  UserAuthLogInDataModel(
                    login = stateValues.globalAppConfiguration.countries.run {
                      find { it.locale.equals(loginTextFieldContent.selectedId, true) } ?: first()
                    }.phoneNumberCode + loginTextFieldContent.value.text.trim(),
                    password = passwordTextFieldContent.value.text
                  )
                )
          }
        }
      }

      actionButton(
        text = stateValues.stringLogIn,
        enabled = stateValues.latestNotification?.message?.equals(stateValues.stringLoggingIn) != true,
        icon = if (stateValues.latestNotification?.message?.equals(stateValues.stringLoggingIn) == true) {
          {
            CircularProgressIndicator(
              color = stateValues.AccentTextColor,
              modifier = Modifier
                .padding(start = 20.dp)
                .size(20.dp)
            )
          }
        } else null,
        onClick = goAction
      )

      if (stateValues.isNarrowScreen) {
        Spacer(modifier = Modifier.height(2.dp))

        actionButton(
          text = stateValues.stringSignUp,
          enabled = stateValues.latestNotification == null
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
