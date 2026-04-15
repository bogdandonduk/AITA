package kz.aita.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.checkAsPersonName
import kz.aita.filterAsPersonName
import kz.aita.workerRepository

@Composable
fun AppConfiguration.MenuAddEditWorkerScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringAddWorker,
      iconPath = stateValues.drawablePathIconAdd,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxHeight()
        .fillMaxWidth(0.5f)
        .padding(vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      item {
        val outerSpace = 16.dp
        val innerSpace = 8.dp

        val phoneNumberTextFieldContent = countrySelectionPhoneNumberTextField(
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = NavigationScreenModel.KEY_STATE_PHONE_NUMBER
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val emailTextFieldContent = emailTextField(
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = NavigationScreenModel.KEY_STATE_EMAIL
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val firstNameTextFieldContent = genericTextField(
          stateHost = NavigationScreenModel.Menu.AddEditWorker,
          stateKey = NavigationScreenModel.KEY_STATE_FIRST_NAME,
          titleText = stateValues.stringFirstName,
          placeholderText = stateValues.stringEnterFirstName,
          leadingIconPath = stateValues.drawablePathIconPerson,
          contentInvalidText = stateValues.stringFirstNameCannotBeEmptyOrJustWhitespaces,
          onContentValidityCheck = {
            it.checkAsPersonName()
          },
          onFilterValue = {
            it.filterAsPersonName()
          }
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val lastNameTextFieldContent = genericTextField(
          stateHost = NavigationScreenModel.Menu.AddEditWorker,
          stateKey = NavigationScreenModel.KEY_STATE_LAST_NAME,
          titleText = stateValues.stringLastName,
          placeholderText = stateValues.stringEnterLastName,
          leadingIconPath = stateValues.drawablePathIconPerson,
          contentInvalidText = stateValues.stringLastNameCannotBeEmptyOrJustWhitespaces,
          onContentValidityCheck = {
            it.checkAsPersonName()
          },
          onFilterValue = {
            it.filterAsPersonName()
          }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        var goAction: (() -> Unit)? = null

        val confirmationPasswordTextFieldContent = passwordTextField(
          titleText = stateValues.stringConfirmationPassword,
          placeholderText = stateValues.stringRequiredToEditAccount,
          contentInvalidText = stateValues.stringRequiredToEditAccount + ". \n" + stateValues.stringPasswordMustBe,
          imeWithAction = ImeWithAction(ImeAction.Go) {
            goAction?.invoke()
          },
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = NavigationScreenModel.Menu.UserAccount.KEY_STATE_CONFIRMATION_PASSWORD
        )

        Spacer(modifier = Modifier.height(outerSpace))

        Text(
          text = "If everything is correct the user will be invited", // TODO
          fontSize = stateValues.textSize,
          color = stateValues.TextColor,
          modifier = Modifier
            .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(outerSpace))

        goAction = {
          softKeyboardController?.hide()

          phoneNumberTextFieldContent.checkContentValidity()
          emailTextFieldContent.checkContentValidity()

          firstNameTextFieldContent.checkContentValidity()
          lastNameTextFieldContent.checkContentValidity()

          confirmationPasswordTextFieldContent.checkContentValidity()

          if (
            phoneNumberTextFieldContent.isContentValid
            && emailTextFieldContent.isContentValid
            && firstNameTextFieldContent.isContentValid
            && lastNameTextFieldContent.isContentValid
            && confirmationPasswordTextFieldContent.isContentValid
          ) {
            workerRepository
              .addStoreWorker(
                phoneNumber = stateValues.globalAppConfiguration.countries.run {
                  find { it.locale.equals(phoneNumberTextFieldContent.selectedId, true) } ?: first()
                }.phoneNumberCode.lowercase() + phoneNumberTextFieldContent.value.text.trim().lowercase(),
                email = emailTextFieldContent.value.text.trim().lowercase(),
                firstName = firstNameTextFieldContent.value.text.trim(),
                lastName = lastNameTextFieldContent.value.text.trim(),
                password = confirmationPasswordTextFieldContent.value.text
              )

            confirmationPasswordTextFieldContent.reset()
          }
        }

        actionButton(
          text = stateValues.stringAddWorker,
          enabled = stateValues.latestNotification == null
        ) {
          goAction.invoke()
        }

        Spacer(
          modifier = Modifier
            .height(stateValues.screenHeight / 10)
        )
      }
    }
  }
}