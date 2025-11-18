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
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.compose.widget.SelectableDomain
import kz.aita.compose.widget.actionButton
import kz.aita.compose.widget.countrySelectionPhoneNumberTextField
import kz.aita.compose.widget.domainSelectionTextFieldGroupWidget
import kz.aita.compose.widget.dropdownListWidget
import kz.aita.compose.widget.emailTextField
import kz.aita.compose.widget.genericTextField
import kz.aita.core.extractLocalizedString
import kz.aita.core.storeRepository
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.dataModel.LocationDataModel
import kz.aita.model.dataModel.StoreDataModel
import kotlin.collections.emptyList

@Composable
fun AppConfiguration.MenuAddEditStoreScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val editedStore = NavigationScreenModel.Menu.AddEditStore.state.value[NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID]?.run { stateValues.stores?.find { store -> store.id == this } }

    ScreenAppBarWidget(
      title = if (editedStore != null) stateValues.stringEditStore else stateValues.stringAddStore,
      iconPath = stateValues.drawablePathIconAdd,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
          if (editedStore != null)
            NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID)
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
        val outerSpace = 16.dp

        val nameData = domainSelectionTextFieldGroupWidget(
          titleText = stateValues.stringName,
          placeholderText = stateValues.stringEnterName,
          stateHost = NavigationScreenModel.Menu.AddEditStore,
          stateKey = NavigationScreenModel.KEY_STATE_NAME,
          domains = emptyList(),
          secondaryDomains = mutableListOf<SelectableDomain>().apply {
            add(
              SelectableDomain(
                id = "main",
                displayId = stateValues.stringMain,
                name = stateValues.stringMain,
                iconPath = null
              )
            )

            stateValues.globalAppConfiguration.languages.forEach { language ->
              add(
                SelectableDomain(
                  id = language.language,
                  displayId = language.name,
                  name = language.name,
                  iconPath = language.flagDrawablePath
                )
              )
            }
          },
          secondaryDomainsShowName = false,
          addDomainActionButtonText = "",
          addSecondaryDomainActionButtonText = stateValues.stringAddName,
        )

        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextFieldGroup)
        )

        val aliasData = domainSelectionTextFieldGroupWidget(
          titleText = stateValues.stringAlias,
          placeholderText = stateValues.stringEnterAlias,
          stateHost = NavigationScreenModel.Menu.AddEditStore,
          stateKey = NavigationScreenModel.Menu.AddEditStore.KEY_STATE_ALIAS,
          domains = emptyList(),
          secondaryDomains = mutableListOf<SelectableDomain>().apply {
            add(
              SelectableDomain(
                id = "main",
                displayId = stateValues.stringMain,
                name = stateValues.stringMain,
                iconPath = null
              )
            )

            stateValues.globalAppConfiguration.languages.forEach { language ->
              add(
                SelectableDomain(
                  id = language.language,
                  displayId = language.name,
                  name = language.name,
                  iconPath = language.flagDrawablePath
                )
              )
            }
          },
          secondaryDomainsShowName = false,
          addDomainActionButtonText = "",
          addSecondaryDomainActionButtonText = stateValues.stringAddTranslation,
        )

        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextFieldGroup)
        )

        val descriptionData = domainSelectionTextFieldGroupWidget(
          titleText = stateValues.stringDescription,
          placeholderText = stateValues.stringEnterDescription,
          stateHost = NavigationScreenModel.Menu.AddEditStore,
          stateKey = NavigationScreenModel.Menu.AddEditStore.KEY_STATE_DESCRIPTION,
          domains = emptyList(),
          secondaryDomains = mutableListOf<SelectableDomain>().apply {
            add(
              SelectableDomain(
                id = "main",
                displayId = stateValues.stringMain,
                name = stateValues.stringMain,
                iconPath = null
              )
            )

            stateValues.globalAppConfiguration.languages.forEach { language ->
              add(
                SelectableDomain(
                  id = language.language,
                  displayId = language.name,
                  name = language.name,
                  iconPath = language.flagDrawablePath
                )
              )
            }
          },
          secondaryDomainsShowName = false,
          addDomainActionButtonText = "",
          addSecondaryDomainActionButtonText = stateValues.stringAddTranslation
        )

        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextFieldGroup)
        )

        val phoneNumberTextFieldContent = countrySelectionPhoneNumberTextField(
          valueInitial = editedStore?.phoneNumbers?.takeIf { it.isNotEmpty() }?.first(),
          stateHost = NavigationScreenModel.Menu.AddEditStore,
          stateKey = NavigationScreenModel.KEY_STATE_PHONE_NUMBER
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        var goAction: (() -> Unit)? = null
        val emailTextFieldContent = emailTextField(
          valueInitial = editedStore?.emails?.takeIf { it.isNotEmpty() }?.first(),
          stateHost = NavigationScreenModel.Menu.AddEditStore,
          stateKey = NavigationScreenModel.KEY_STATE_EMAIL
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        val companyFormDropdownListContent = dropdownListWidget(
          titleText = stateValues.stringCompanyForm,
          domains = stateValues.globalAppConfiguration.companyForms.map {
            SelectableDomain(
              id = it.id,
              displayId = it.name,
              name = it.name,
              iconPath = null
            )
          },
          showName = false,
          selectedInitial = editedStore?.companyForms?.takeIf { it.isNotEmpty() }?.first()?.id
        )

        Spacer(modifier = Modifier.height(outerSpace))

        goAction = {
          softKeyboardController?.hide()

//          nameTextFieldContent.checkContentValidity()

          phoneNumberTextFieldContent.checkContentValidity()
          emailTextFieldContent.checkContentValidity()

          if (
//            nameTextFieldContent.isContentValid &&
            phoneNumberTextFieldContent.isContentValid
            && emailTextFieldContent.isContentValid
          ) {
            if (editedStore != null) {
              storeRepository
                .updateStore(
                  store = StoreDataModel(
                    id = editedStore.id,
                    userIds = emptyList(),
                    storeTypeIds = emptyList(),
                    name = nameData.data.map {
                      LocalizedStringDataModel(
                        it.selectedSecondaryDomainId,
                        it.value.text
                      )
                    },
                    alias = aliasData.data.map {
                      LocalizedStringDataModel(
                        it.selectedSecondaryDomainId,
                        it.value.text
                      )
                    },
                    description = descriptionData.data.map {
                      LocalizedStringDataModel(
                        it.selectedSecondaryDomainId,
                        it.value.text
                      )
                    },
                    companyForms = stateValues.globalAppConfiguration.companyForms.find { it.id == companyFormDropdownListContent.selectedId }!!.run { listOf(this) },
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
                    countryLocales = listOf(),
                    createdAt = 0L,
                    isActive = true
                  )
                ) {
                  coroutineScope.launch {
                    Navigation.Menu.pop()
                    NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID)
                  }
                }
            } else {
              storeRepository
                .addStore(
                  store = StoreDataModel(
                    id = "",
                    userIds = emptyList(),
                    storeTypeIds = emptyList(),
                    name = nameData.data.map {
                      LocalizedStringDataModel(
                        it.selectedSecondaryDomainId,
                        it.value.text
                      )
                    },
                    alias = aliasData.data.map {
                      LocalizedStringDataModel(
                        it.selectedSecondaryDomainId,
                        it.value.text
                      )
                    },
                    description = descriptionData.data.map {
                      LocalizedStringDataModel(
                        it.selectedSecondaryDomainId,
                        it.value.text
                      )
                    },
                    companyForms = stateValues.globalAppConfiguration.companyForms.find { it.id == companyFormDropdownListContent.selectedId }
                      !!.run { listOf(this) },
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
                    countryLocales = emptyList(),
                    createdAt = 0L,
                    isActive = true
                  )
                ) {
                  coroutineScope.launch {
                    Navigation.Menu.pop()
                  }
                }
            }
          }
        }

        actionButton(
          text = if (editedStore != null) stateValues.stringEditStore else stateValues.stringAddStore,
          enabled = stateValues.latestNotification == null
        ) {
          goAction.invoke()
        }
      }

      item {
        Spacer(
          modifier = Modifier
            .height(stateValues.screenHeight / 4)
        )
      }
    }
  }
}