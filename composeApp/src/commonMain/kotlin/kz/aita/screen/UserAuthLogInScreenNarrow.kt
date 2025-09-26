package kz.aita.screen

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
import kz.aita.widget.LargeIconWithTitleWidget
import kz.aita.widget.actionButton
import kz.aita.widget.emailTextField
import kz.aita.widget.genericTextField
import kz.aita.widget.passwordTextField
import kz.aita.widget.tabRowWidget
import kz.aita.model.TabContent

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
            .width(stateValues.boundWidgetWidth),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          LargeIconWithTitleWidget(
            imageUrl = stateValues.drawablePathAITALogo,
            title = stateValues.stringLogIn
          )

          val loginMethodTabRowContent =
            tabRowWidget(
              modifier = Modifier
                .padding(top = 16.dp),
              tabs = listOf(
                TabContent(stateValues.stringPhoneNumber),
                TabContent(stateValues.stringEmail)
              )
            )

          Spacer(modifier = Modifier.height(12.dp))

          val loginTextFieldContent = when (loginMethodTabRowContent.index) {
            0 -> {
              genericTextField()
            }
            else -> {
              emailTextField()
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          val passwordTextFieldContent =
            passwordTextField()

          Spacer(modifier = Modifier.height(12.dp))

          val proceedButtonContent =
            actionButton(
              text = stateValues.stringLogIn
            ) {
              loginTextFieldContent.checkContentValidity()
              passwordTextFieldContent.checkContentValidity()
            }
        }
      }
    }
}
