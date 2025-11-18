package kz.aita.compose.screen.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.core.checkAsPersonName
import kz.aita.core.filterAsPersonName
import kz.aita.compose.widget.*
import kz.aita.compose.wrapper.ImeWithAction
import kz.aita.core.userRepository
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.dataModel.UserAccountUpdateDataModel

@Composable
fun AppConfiguration.MenuUserAccountScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringUserAccount,
      iconPath = stateValues.drawablePathIconUserAccount,
      trailingIcons = listOf(
        stateValues.drawablePathIconExit to {
          userRepository
            .logOut()
        },
      ),
      onBack = if (!Navigation.Menu.isVeryFirstScreen(stateValues.isNarrowScreen)) {
        {
          coroutineScope.launch {
            Navigation.Menu.pop(stateValues.isNarrowScreen)
          }
        }
      } else null
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxHeight()
        .fillMaxWidth(0.5f)
        .padding(vertical = 24.dp)
    ) {
      item {
        val outerSpace = 16.dp
        val innerSpace = 8.dp

        val phoneNumberTextFieldContent = countrySelectionPhoneNumberTextField(
          valueInitial = stateValues.userAccount?.phoneNumber,
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = "phone_number",
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val emailTextFieldContent = emailTextField(
          valueInitial = stateValues.userAccount?.email,
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = "email",
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val firstNameTextFieldContent = genericTextField(
          valueInitial = stateValues.userAccount?.firstName,
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = "first_name",
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
          valueInitial = stateValues.userAccount?.lastName,
          stateHost = NavigationScreenModel.Menu.UserAccount,
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

        Text(
          text = stateValues.stringChangePassword,
          color = stateValues.TextColor,
          fontSize = stateValues.titleTextSize,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val (passwordTextFieldContent, repeatedPasswordTextFieldContent) = repeatedPasswordTextFieldGroup(
          passwordTitleText = stateValues.stringNewPassword,
          passwordPlaceholderText = stateValues.stringEnterNewPassword,
          repeatPasswordTitleText = stateValues.stringRepeatNewPassword,
          repeatPasswordPlaceholderText = stateValues.stringRepeatNewPassword,
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = NavigationScreenModel.KEY_STATE_PASSWORD,
          repeatedStateKey = NavigationScreenModel.KEY_STATE_REPEATED_PASSWORD,
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

//        responseText(
//          stateValues.stringUserWithThisPhoneNumberIsAlreadyRegistered,
//          showIf = {
//            (stateValues.userAccountState as? DataState.Failure)?.message?.equals(
//              stateValues.exceptionMessageUserWithThisPhoneNumberIsAlreadyRegistered,
//              true
//            ) == true
//          }
//        )
//
//        responseText(
//          stateValues.stringUserWithThisEmailAddressIsAlreadyRegistered,
//          showIf = {
//            (stateValues.userAccountState as? DataState.Failure)?.message?.equals(
//              stateValues.exceptionMessageUserWithThisEmailAddressIsAlreadyRegistered,
//              true
//            ) == true
//          }
//        )
//
//        responseText(
//          stateValues.stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered,
//          showIf = {
//            (stateValues.userAccountState as? DataState.Failure)?.message?.equals(
//              stateValues.exceptionMessageUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered,
//              true
//            ) == true
//          }
//        )

        Spacer(modifier = Modifier.height(outerSpace))

        goAction = {
          softKeyboardController?.hide()

          phoneNumberTextFieldContent.checkContentValidity()
          emailTextFieldContent.checkContentValidity()

          firstNameTextFieldContent.checkContentValidity()
          lastNameTextFieldContent.checkContentValidity()

          if (passwordTextFieldContent.value.text.isNotEmpty())
            passwordTextFieldContent.checkContentValidity()

          if (passwordTextFieldContent.value.text.isNotEmpty())
            repeatedPasswordTextFieldContent.checkContentValidity()

          confirmationPasswordTextFieldContent.checkContentValidity()

          if (
            phoneNumberTextFieldContent.isContentValid
            && emailTextFieldContent.isContentValid
            && firstNameTextFieldContent.isContentValid
            && lastNameTextFieldContent.isContentValid
            && (passwordTextFieldContent.value.text.isEmpty() || passwordTextFieldContent.isContentValid)
            && (passwordTextFieldContent.value.text.isEmpty() || repeatedPasswordTextFieldContent.isContentValid)
            && confirmationPasswordTextFieldContent.isContentValid
          ) {
            userRepository
              .update(
                userAccountUpdate = UserAccountUpdateDataModel(
                  account = UserAccountDataModel(
                    id = "",
                    phoneNumber = stateValues.globalAppConfiguration.countries.run {
                      find { it.locale.equals(phoneNumberTextFieldContent.selectedId, true) } ?: first()
                    }.phoneNumberCode.lowercase() + phoneNumberTextFieldContent.value.text.trim().lowercase(),
                    email = emailTextFieldContent.value.text.trim().lowercase(),
                    firstName = firstNameTextFieldContent.value.text.trim(),
                    lastName = lastNameTextFieldContent.value.text.trim(),
                    countryLocale = phoneNumberTextFieldContent.selectedId,
                    workerAccountIds = stateValues.userAccount?.workerAccountIds,
                    supplierAccountIds = stateValues.userAccount?.supplierAccountIds,
                    createdAt = 0L,
                    isActive = true
                  ),
                  password = confirmationPasswordTextFieldContent.value.text,
                  newPassword = passwordTextFieldContent.takeIf { it.value.text.isNotEmpty() }?.value?.text
                )
              )

            confirmationPasswordTextFieldContent.reset()
            passwordTextFieldContent.reset()
            repeatedPasswordTextFieldContent.reset()
          }
        }

        actionButton(
          text = stateValues.stringEdit,
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