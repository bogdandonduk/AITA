package kz.aita

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.config.LocalKamelConfig
import io.kamel.image.asyncPainterResource
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.round

@Stable
internal class StoreAddressPickerState(
    initialLocation: LocationDataModel?
) {
    var selectedLocation: LocationDataModel? by mutableStateOf(
        initialLocation?.takeIf { it.isResolvedAddress() }
    )
    var suggestions: List<AddressSuggestionDataModel> by mutableStateOf(emptyList())
    var isSuggesting: Boolean by mutableStateOf(false)
    var isResolving: Boolean by mutableStateOf(false)
    var providerMessage: String? by mutableStateOf(null)
    var validationMessage: String? by mutableStateOf(null)
    var mapPreview: AddressMapPreviewDataModel? by mutableStateOf(null)
    var mapPreviewFailed: Boolean by mutableStateOf(false)

    fun locationForSave(rawText: String, language: String): LocationDataModel? =
        selectedLocation?.takeIf { location ->
            location.isResolvedAddress() && location.matchesDisplayedAddress(rawText, language)
        }

    fun requireSuggestionSelection(message: String) {
        validationMessage = message
    }

    fun clearValidation() {
        validationMessage = null
    }
}

internal data class StoreAddressPickerContent(
    val textField: GenericTextFieldContent,
    val state: StoreAddressPickerState
) {
    fun locationForSave(language: String): LocationDataModel? =
        state.locationForSave(textField.value.text, language)
}

@Composable
private fun AppConfiguration.AddressSuggestionRow(
    suggestion: AddressSuggestionDataModel,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .aitaClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CpImage(
            modifier = Modifier.size(28.dp),
            url = "svg/135_${normalizeAppThemePreference(stateValues.appThemeId)}.svg",
            fallbackRes = null,
            contentDescription = suggestion.title
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.title,
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val supporting = suggestion.formattedAddress
                .ifBlank { suggestion.subtitle }
                .takeIf { it.isNotBlank() && !it.equals(suggestion.title, ignoreCase = true) }
            if (supporting != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = supporting,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AppConfiguration.VerifiedAddressCard(
    location: LocationDataModel,
    preview: AddressMapPreviewDataModel?,
    previewFailed: Boolean,
    onPreviewFailure: () -> Unit,
    onOpenMap: (String) -> Unit
) {
    val shape = RoundedCornerShape(stateValues.cornerRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape)
            .clip(shape)
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                stateValues.AccentColor.copy(alpha = 0.72f),
                shape
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CpImage(
                modifier = Modifier.size(34.dp),
                url = "svg/135_${normalizeAppThemePreference(stateValues.appThemeId)}.svg",
                fallbackRes = null,
                contentDescription = localizedStringResource(2300, "Verified address")
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedStringResource(2300, "Verified address"),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = location.displayAddress(stateValues.appLanguage),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val latitudeText = (round(location.latitude * 1_000_000.0) / 1_000_000.0).toString()
        val longitudeText = (round(location.longitude * 1_000_000.0) / 1_000_000.0).toString()
        Text(
            text = "$latitudeText, $longitudeText",
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )

        if (preview != null) {
            Spacer(modifier = Modifier.height(10.dp))
            CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
                KamelImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape((stateValues.cornerRadius.value * 0.75f).dp)),
                    resource = { asyncPainterResource(Url(preview.url)) },
                    contentDescription = localizedStringResource(2301, "Address map preview"),
                    contentScale = ContentScale.Crop,
                    onFailure = { onPreviewFailure() }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = preview.attribution,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
            Spacer(modifier = Modifier.height(8.dp))
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(2302, "Open on map"),
                iconPath = "svg/135_${normalizeAppThemePreference(stateValues.appThemeId)}.svg",
                confirmationRequired = false,
                onClick = { onOpenMap(preview.openMapUrl) }
            )
        } else if (previewFailed) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = localizedStringResource(2303, "Map preview is temporarily unavailable. The verified coordinates are still saved."),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }
    }
}

@Composable
internal fun AppConfiguration.storeVerifiedAddressPicker(
    initialLocation: LocationDataModel?,
    initialAddress: String,
    countryCode: String
): StoreAddressPickerContent {
    val scope = rememberCoroutineScope()
    val state = remember(initialLocation?.providerObjectId, initialLocation?.providerRevision) {
        StoreAddressPickerState(initialLocation)
    }
    val trailingContent: (@Composable () -> Unit)? = if (state.isSuggesting || state.isResolving) {
        {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = stateValues.AccentColor
            )
        }
    } else null

    val textField = genericTextField(
        titleText = localizedStringResource(520, "Address"),
        placeholderText = localizedStringResource(2304, "Start typing and select a verified address"),
        valueInitial = initialLocation
            ?.takeIf { it.isResolvedAddress() }
            ?.displayAddress(stateValues.appLanguage)
            ?.takeIf(String::isNotBlank)
            ?: initialAddress,
        stateHost = NavigationScreenModel.Menu.AddEditStore,
        stateKey = NavigationScreenModel.Menu.AddEditStore.KEY_STATE_ADDRESS,
        leadingIconPath = "svg/135_${normalizeAppThemePreference(stateValues.appThemeId)}.svg",
        contentInvalidText = localizedStringResource(2305, "Select an address from suggestions"),
        onContentValidityCheck = { raw -> state.locationForSave(raw, stateValues.appLanguage) != null },
        trailingIcon = trailingContent,
        onValueChange = { value, apply ->
            apply()
            state.providerMessage = null
            state.validationMessage = null
            val selected = state.selectedLocation
            if (selected != null && !selected.matchesDisplayedAddress(value, stateValues.appLanguage)) {
                state.selectedLocation = null
                state.mapPreview = null
                state.mapPreviewFailed = false
            }
        }
    )

    val query = textField.value.text.trim().replace(Regex("\\s+"), " ")
    val selectedMatchesQuery = state.selectedLocation?.matchesDisplayedAddress(query, stateValues.appLanguage) == true
    val addressCountryChangedText = localizedStringResource(2311, "The country changed. Select the address again.")

    LaunchedEffect(countryCode, state.selectedLocation?.countryCode) {
        val selectedCountry = state.selectedLocation?.countryCode?.trim()?.uppercase()
        val currentCountry = countryCode.trim().uppercase()
        if (selectedCountry?.length == 2 && currentCountry.length == 2 && selectedCountry != currentCountry) {
            state.selectedLocation = null
            state.suggestions = emptyList()
            state.mapPreview = null
            state.mapPreviewFailed = false
            state.providerMessage = addressCountryChangedText
        }
    }

    val aitaLatestAddressOwner0 = rememberAitaLatestUiRequestOwner()
    LaunchedEffect(query, countryCode, stateValues.appLanguage, selectedMatchesQuery) {        val aitaLatestAddressTicket0 = aitaLatestAddressOwner0.begin()

        if (selectedMatchesQuery || query.length < 3) {
            state.suggestions = emptyList()
            state.isSuggesting = false
            return@LaunchedEffect
        }
        delay(340)
        if (aitaLatestAddressOwner0.owns(aitaLatestAddressTicket0)) state.isSuggesting = true
        state.providerMessage = null
        try {
            val response = suggestStoreAddresses(
                query = query,
                language = stateValues.appLanguage,
                countryCodes = listOf(countryCode).filter(String::isNotBlank),
                limit = 8
            )
            if (query == textField.value.text.trim().replace(Regex("\\s+"), " ")) {
                if (aitaLatestAddressOwner0.owns(aitaLatestAddressTicket0)) state.suggestions = response.payload.orEmpty()
                state.providerMessage = if (response.negative) {
                    response.message?.extractLocalizedString(stateValues.appLanguage)
                        ?: localizedStringResource(2306, "Could not load address suggestions")
                } else if (response.payload.orEmpty().isEmpty()) {
                    localizedStringResource(2307, "No matching addresses found")
                } else null
                if (aitaLatestAddressOwner0.owns(aitaLatestAddressTicket0)) state.isSuggesting = false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            if (_ is kotlinx.coroutines.CancellationException) throw _
            if (query == textField.value.text.trim().replace(Regex("\\s+"), " ")) {
                if (aitaLatestAddressOwner0.owns(aitaLatestAddressTicket0)) state.suggestions = emptyList()
                state.providerMessage = localizedStringResource(2306, "Could not load address suggestions")
                if (aitaLatestAddressOwner0.owns(aitaLatestAddressTicket0)) state.isSuggesting = false
            }
        }
    }

    val selectedLocation = state.selectedLocation
    val aitaLatestAddressOwner1 = rememberAitaLatestUiRequestOwner()
    LaunchedEffect(
        selectedLocation?.providerObjectId,
        selectedLocation?.providerRevision,
        stateValues.appLanguage,
        stateValues.appThemeId
    ) {        val aitaLatestAddressTicket1 = aitaLatestAddressOwner1.begin()

        val location = selectedLocation ?: run {
            state.mapPreview = null
            state.mapPreviewFailed = false
            return@LaunchedEffect
        }
        state.mapPreviewFailed = false
        try {
            val response = requestStoreAddressMapPreview(
                location = location,
                language = stateValues.appLanguage,
                darkTheme = normalizeAppThemePreference(stateValues.appThemeId) == 1L
            )
            if (aitaLatestAddressOwner1.owns(aitaLatestAddressTicket1)) state.mapPreview = response.payload
            if (aitaLatestAddressOwner1.owns(aitaLatestAddressTicket1)) state.mapPreviewFailed = response.negative || response.payload == null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            if (_ is kotlinx.coroutines.CancellationException) throw _
            if (aitaLatestAddressOwner1.owns(aitaLatestAddressTicket1)) state.mapPreview = null
            if (aitaLatestAddressOwner1.owns(aitaLatestAddressTicket1)) state.mapPreviewFailed = true
        }
    }

    AnimatedVisibility(
        visible = state.suggestions.isNotEmpty() && !selectedMatchesQuery,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val shape = RoundedCornerShape(stateValues.cornerRadius)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .shadow(4.dp, shape)
                .clip(shape)
                .background(stateValues.BackgroundColor)
                .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.55f), shape)
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
        ) {
            state.suggestions.forEachIndexed { index, suggestion ->
                AddressSuggestionRow(
                    suggestion = suggestion,
                    enabled = !state.isResolving,
                    onClick = {
                        scope.launch {
                            state.isResolving = true
                            state.providerMessage = null
                            state.validationMessage = null
                            try {
                                val response = resolveStoreAddressSuggestion(suggestion, stateValues.appLanguage)
                                val location = response.payload
                                if (!response.negative && location != null && location.isResolvedAddress()) {
                                    state.selectedLocation = location
                                    state.suggestions = emptyList()
                                    textField.replaceText(location.displayAddress(stateValues.appLanguage))
                                } else {
                                    state.providerMessage = response.message?.extractLocalizedString(stateValues.appLanguage)
                                        ?: localizedStringResource(2308, "Could not verify this address. Select another suggestion or try again.")
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                state.providerMessage = localizedStringResource(
                                    2308,
                                    "Could not verify this address. Select another suggestion or try again."
                                )
                            } finally {
                                state.isResolving = false
                            }
                        }
                    }
                )
                if (index != state.suggestions.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(stateValues.PlaceholderTextColor.copy(alpha = 0.18f))
                    )
                }
            }
        }
    }

    state.providerMessage?.let { message ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            modifier = Modifier.fillMaxWidth()
        )
    }

    val openingMapsUnavailableText = localizedStringResource(2309, "Opening maps is unavailable on this device")
    state.selectedLocation?.let { location ->
        Spacer(modifier = Modifier.height(10.dp))
        VerifiedAddressCard(
            location = location,
            preview = state.mapPreview,
            previewFailed = state.mapPreviewFailed,
            onPreviewFailure = {
                state.mapPreview = null
                state.mapPreviewFailed = true
            },
            onOpenMap = { url ->
                scope.launch {
                    val result = openExternalUrlPlatformAction?.invoke(url)
                        ?: ReceiptPlatformActionResult(false, openingMapsUnavailableText)
                    if (!result.success) {
                        postInAppNotification(
                            listOf(LocalizedStringDataModel("main", result.message)),
                            NotificationType.Negative,
                            transient = true
                        )
                    }
                }
            }
        )
    }

    return StoreAddressPickerContent(textField, state)
}

@Composable
internal fun AppConfiguration.StoreAddressInlineValidationMessage(
    state: StoreAddressPickerState
) {
    state.validationMessage?.let { message ->
        Text(
            text = message,
            color = stateValues.ErrorColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )
    }
}
