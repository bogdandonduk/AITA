package kz.aita.compose.screen.userAuth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import kz.aita.compose.widget.GenericTextFieldContent
import kz.aita.compose.widget.LargeIconWithTitleWidget
import kz.aita.compose.widget.DomainSelectionTextFieldContent
import kz.aita.compose.widget.SelectableDomain
import kz.aita.compose.widget.TabContent
import kz.aita.compose.widget.actionButton
import kz.aita.compose.widget.countrySelectionTextField
import kz.aita.compose.widget.emailTextField
import kz.aita.compose.widget.passwordTextField
import kz.aita.compose.widget.tabRowWidget
import kz.aita.compose.widget.errorText
import kz.aita.compose.widget.domainSelectionTextField
import kz.aita.core.userRepository
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.wrapper.DataState

@Composable
fun AppConfiguration.UserAuthLogInScreen() {
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
            countrySelectionTextField()
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
          false
        }

        Spacer(modifier = Modifier.height(outerSpace))

        actionButton(
          text = stateValues.stringLogIn,
          enabled = stateValues.userAccountState !is DataState.Progress,
          icon = if (stateValues.userAccountState is DataState.Progress) {
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

        if (stateValues.isNarrowScreen) {
          Spacer(modifier = Modifier.height(2.dp))

          actionButton(
            text = stateValues.stringSignUp,
            enabled = stateValues.userAccountState !is DataState.Progress
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
