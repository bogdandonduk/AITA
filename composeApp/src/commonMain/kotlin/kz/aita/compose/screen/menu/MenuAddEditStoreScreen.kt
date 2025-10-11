package kz.aita.compose.screen.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.compose.widget.SelectableDomain
import kz.aita.compose.widget.actionButton
import kz.aita.compose.widget.countrySelectionPhoneNumberTextField
import kz.aita.compose.widget.dropdownListWidget
import kz.aita.compose.widget.emailTextField
import kz.aita.compose.widget.genericTextField
import kz.aita.core.extractLocalizedString
import kz.aita.core.storeRepository
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.dataModel.LocationDataModel
import kz.aita.model.dataModel.StoreDataModel
import kz.aita.model.wrapper.DataState

@Composable
fun AppConfiguration.MenuAddEditStoreScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringAddStore,
      iconPath = stateValues.drawablePathIconAdd,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth(
          if (stateValues.isNarrowScreen) 1f else 0.6f
        )
        .weight(1f)
        .padding(start = 8.dp, top = 24.dp, end = 8.dp)
    ) {
      item {
        val innerSpace = 8.dp
        val outerSpace = 16.dp

        val nameTextFieldContent =
          genericTextField(
            titleText = stateValues.stringName,
            placeholderText = stateValues.stringEnterName,
          )

        Spacer(
          modifier = Modifier
            .height(innerSpace)
        )

        val aliasTextFieldContent =
          genericTextField(
            titleText = stateValues.stringAlias,
            placeholderText = stateValues.stringOptional,
          )

        Spacer(
          modifier = Modifier
            .height(innerSpace)
        )

        val descriptionTextFieldContent =
          genericTextField(
            titleText = stateValues.stringDescription,
            placeholderText = stateValues.stringOptional
          )

        Spacer(
          modifier = Modifier
            .height(innerSpace)
        )

        val phoneNumberTextFieldContent = countrySelectionPhoneNumberTextField(
          valueInitial = stateValues.userAccount?.phoneNumber
        )

        Spacer(modifier = Modifier.height(innerSpace))

        var goAction: (() -> Unit)? = null
        val emailTextFieldContent = emailTextField(valueInitial = stateValues.userAccount?.email)

        Spacer(modifier = Modifier.height(innerSpace))

        val companyFormDropdownListContent = dropdownListWidget(
          titleText = "Company form",
          domains = stateValues.globalAppConfiguration.companyForms.map {
            SelectableDomain(
              id = it.id,
              name = it.name,
              iconPath = null
            )
          }
        )

        Spacer(modifier = Modifier.height(outerSpace))

        goAction = {
          softKeyboardController?.hide()

          nameTextFieldContent.checkContentValidity()

          phoneNumberTextFieldContent.checkContentValidity()
          emailTextFieldContent.checkContentValidity()

          if (
            nameTextFieldContent.isContentValid
            && phoneNumberTextFieldContent.isContentValid
            && emailTextFieldContent.isContentValid
          ) {
            storeRepository
              .addStore(
                store = StoreDataModel(
                  id = "",
                  userId = "",
                  name = listOf(
                    LocalizedStringDataModel(
                      language = stateValues.appLanguage,
                      value = nameTextFieldContent.value.text.trim()
                    )
                  ),
                  alias = if (aliasTextFieldContent.value.text.isNotEmpty()) {
                    listOf(
                      LocalizedStringDataModel(
                        language = stateValues.appLanguage,
                        value = aliasTextFieldContent.value.text.trim()
                      )
                    )
                  } else null,
                  description = if (descriptionTextFieldContent.value.text.isNotEmpty()) {
                    listOf(
                      LocalizedStringDataModel(
                        language = stateValues.appLanguage,
                        value = descriptionTextFieldContent.value.text.trim()
                      )
                    )
                  } else null,
                  companyForms = stateValues.globalAppConfiguration.companyForms.find { it.id == companyFormDropdownListContent.selectedId }
                    ?.run { listOf(this) },
                  location = stateValues.globalAppConfiguration.countries.first().cities.first().run {
                    LocationDataModel(
                      name = name.extractLocalizedString(stateValues.appLanguage) ?: "Some location",
                      postalIndex = "020000",
                      latitude = centerLatitude,
                      longitude = centerLongitude
                    )
                  },
                  phoneNumbers = listOf(
                    stateValues.globalAppConfiguration.countries.run {
                      find { it.locale.equals(phoneNumberTextFieldContent.selectedId, true) } ?: first()
                    }.phoneNumberCode.lowercase() + phoneNumberTextFieldContent.value.text.trim().lowercase()
                  ),
                  emails = listOf(emailTextFieldContent.value.text.trim().lowercase()),

                  createdAt = 0L,
                  isActive = true
                )
              )
          }
        }

        actionButton(
          text = stateValues.stringAddStore,
          enabled = stateValues.storesState !is DataState.Progress
        ) {
          goAction.invoke()
        }
      }

      item {
        Spacer(
          modifier = Modifier
            .height(200.dp)
        )
      }
    }
  }
}