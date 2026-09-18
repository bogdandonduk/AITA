// THIS IS CommonMainCompose.kt split slice: AuthTransaction
@file:OptIn(ExperimentalTime::class, ExperimentalFoundationApi::class)
package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.ExperimentalTime

@Composable
internal fun AppConfiguration.PagingControls(
    modifier: Modifier = Modifier,
    page: Int,
    totalItems: Int,
    pageSize: Int,
    onPageChange: (Int) -> Unit
) {
    val totalPages = totalItems.totalClientPages(pageSize)
    if (totalPages <= 1) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        actionButton(
            text = "", iconContentDescription = localizedStringResource(577, "Previous"),
            enabled = page > 0,
            iconPath = stateValues.drawablePathIconBackArrow,
            confirmationRequired = false,
            onClick = { onPageChange((page - 1).coerceAtLeast(0)) }
        )
        Text(
            modifier = Modifier.weight(1f),
            text = localizedStringResource(578, "Page") + " ${page + 1} / $totalPages · $totalItems",
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        actionButton(
            text = "", iconContentDescription = localizedStringResource(579, "Next"),
            enabled = page + 1 < totalPages,
            iconPath = nextPageIconPath(),
            iconRes = nextPageIconFallback(),
            iconAfterText = true,
            confirmationRequired = false,
            onClick = { onPageChange((page + 1).coerceAtMost(totalPages - 1)) }
        )
    }
}

@Composable
internal fun AppConfiguration.AitaRoundCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    blocked: Boolean = false,
    borderColor: Color? = null,
    containerSize: Dp = 48.dp,
    circleSize: Dp = 20.dp
) {
    val shape = RoundedCornerShape(999.dp)
    val targetBorderColor = borderColor ?: when {
        blocked -> stateValues.ErrorColor
        checked -> stateValues.AccentColor
        else -> stateValues.PlaceholderTextColor
    }
    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS),
        label = "aitaCheckboxBorder"
    )
    val animatedContainerColor by animateColorAsState(
        targetValue = if (checked) stateValues.AccentColor else Color.Transparent,
        animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS),
        label = "aitaCheckboxContainer"
    )
    val clickModifier = if (enabled && onCheckedChange != null) {
        Modifier.aitaClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = if (checked) stateValues.AccentColor else animatedBorderColor),
            pressScale = 0.91f,
            onClick = { onCheckedChange(!checked) }
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(containerSize)
            .clip(shape)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .aitaSelectionMotion(selected = checked, selectedScale = 1.13f)
                .clip(shape)
                .background(animatedContainerColor)
                .border(stateValues.unfocusedBorderWidth, animatedBorderColor, shape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = when {
                    checked -> "checked"
                    blocked -> "blocked"
                    else -> "empty"
                },
                transitionSpec = {
                    (fadeIn(animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS)) +
                            scaleIn(initialScale = 0.62f, animationSpec = spring(stiffness = Spring.StiffnessMedium)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS)) +
                                    scaleOut(targetScale = 0.72f, animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS))
                        )
                },
                label = "aitaCheckboxMark"
            ) { markState ->
                when (markState) {
                    "checked" -> Text(
                        text = "✓",
                        color = stateValues.AccentTextColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    "blocked" -> Text(
                        text = "!",
                        color = stateValues.ErrorColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    else -> Spacer(modifier = Modifier.size(1.dp))
                }
            }
        }
    }
}

internal fun Modifier.foregroundTactileShadow(
    cornerRadius: Dp,
    elevated: Boolean = false
): Modifier {
    val shadowColor = AppConfiguration.stateValues.TextColor.copy(alpha = if (elevated) 0.28f else 0.17f)
    return shadow(
        elevation = if (elevated) 14.dp else 5.dp,
        shape = RoundedCornerShape(cornerRadius),
        clip = false,
        ambientColor = shadowColor,
        spotColor = shadowColor
    )
}

internal fun Modifier.foregroundSubtleShadow(
    cornerRadius: Dp
): Modifier {
    val shadowColor = AppConfiguration.stateValues.TextColor.copy(alpha = 0.13f)
    return shadow(
        elevation = 2.dp,
        shape = RoundedCornerShape(cornerRadius),
        clip = false,
        ambientColor = shadowColor,
        spotColor = shadowColor
    )
}

@Suppress("UNUSED_PARAMETER")
internal fun accentTextShadow(
    color: Color,
    accentColor: Color
): Shadow? = null

internal fun accentTextWeight(
    color: Color,
    accentColor: Color,
    fallback: FontWeight = FontWeight.Normal
): FontWeight {
    return if (color == accentColor) FontWeight.Bold else fallback
}

@Composable
internal fun AppConfiguration.AuthTinyChoiceChip(
    selected: Boolean,
    label: String,
    iconPath: String? = null,
    iconRes: DrawableResource? = null,
    contentDescription: String = label,
    onClick: () -> Unit
) {
    val borderColor = if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor.copy(alpha = 0.65f)
    val backgroundColor = if (selected) stateValues.AccentColor.copy(alpha = 0.14f) else stateValues.BackgroundColor
    val textColor = if (selected) stateValues.AccentColor else stateValues.TextColor

    Row(
        modifier = Modifier
            .heightIn(min = stateValues.textFieldHeight * 0.86f)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(backgroundColor)
            .border(stateValues.unfocusedBorderWidth, borderColor, RoundedCornerShape(stateValues.cornerRadius))
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.AccentColor),
                onClick = onClick
            )
            .padding(horizontal = stateValues.textFieldIconPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stateValues.textFieldIconPadding * 0.55f)
    ) {
        iconPath?.let {
            CpImage(
                modifier = Modifier.size(stateValues.iconSize * 0.72f),
                url = it,
                fallbackRes = iconRes,
                contentDescription = contentDescription,
                tintColor = null
            )
        }

        Text(
            text = label,
            color = textColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AppConfiguration.AuthPreferencesChooser(
    modifier: Modifier = Modifier
) {
    val languages = stateValues.globalAppConfiguration.languages.withBundledAppLanguages().sortedBy {
        when (it.language.lowercase()) {
            "ru" -> 0
            "kk" -> 1
            "en" -> 2
            else -> 3
        }
    }

    val themes = availableAppThemes(stateValues.globalAppConfiguration.themes)

    val sizeModeChoices = listOf(
        0L to localizedStringResource(911, "Default"),
        1L to localizedStringResource(912, "Big"),
        2L to storePeopleText("large")
    )

    if (languages.isEmpty() && themes.isEmpty() && sizeModeChoices.isEmpty()) return

    val languageScrollState = rememberScrollState()
    val themeScrollState = rememberScrollState()
    val sizeScrollState = rememberScrollState()
    val rowWidth = stateValues.boundWidgetWidth * (if (stateValues.isNarrowScreen) 1f else 1.85f)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (languages.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .widthIn(max = rowWidth)
                    .horizontalScroll(languageScrollState)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AuthTinyChoiceChip(
                    selected = stateValues.appLanguagePreference == "system",
                    label = stateValues.stringSystemLanguage,
                    contentDescription = stateValues.stringSystemLanguage
                ) { setAuthScreenAppLocale("system") }
                languages.forEach { language ->
                    AuthTinyChoiceChip(
                        selected = stateValues.appLanguagePreference == language.language,
                        label = language.language.uppercase(),
                        iconPath = language.flagDrawablePath,
                        iconRes = language.mapIconRes(),
                        contentDescription = language.name.visibleLocalizedString(stateValues.appLanguage, language.language.uppercase())
                    ) {
                        setAuthScreenAppLocale(language.language)
                    }
                }
            }
        }

        if (themes.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .widthIn(max = rowWidth)
                    .horizontalScroll(themeScrollState)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                themes.forEach { theme ->
                    val selected = stateValues.appThemeId == theme.id
                    val darkThemeChoice = isDarkAppTheme(theme.id)
                    AuthTinyChoiceChip(
                        selected = selected,
                        label = theme.name.visibleLocalizedString(stateValues.appLanguage, theme.id.toString()),
                        iconPath = if (darkThemeChoice) stateValues.drawablePathIconThemeDark else stateValues.drawablePathIconThemeLight,
                        iconRes = if (darkThemeChoice) stateValues.drawableResIconThemeDark.value else stateValues.drawableResIconThemeLight.value,
                        contentDescription = theme.name.visibleLocalizedString(stateValues.appLanguage, theme.id.toString())
                    ) {
                        setAuthScreenAppTheme(theme.id)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .widthIn(max = rowWidth)
                .horizontalScroll(sizeScrollState)
                .padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sizeModeChoices.forEach { (id, label) ->
                AuthTinyChoiceChip(
                    selected = stateValues.appSizeModeId == id,
                    label = label,
                    iconPath = stateValues.drawablePathIconAppScale,
                    iconRes = stateValues.drawableResIconAppScale.value,
                    contentDescription = label
                ) {
                    setAuthScreenAppSizeMode(id)
                }
            }
        }
    }
}


@Composable
fun AppConfiguration.UserAuthSignUpScreen(
) {
    // Credentials and personally identifying sign-up data must live only for this visible form.
    // AITA normally restores form drafts, but restoring an abandoned registration after relaunch
    // would expose phone/email/password data on a shared device.
    val transientSignUpState = remember { object : StateHost() {} }
    var pendingRegistration by remember { mutableStateOf<UserAuthSignUpDataModel?>(null) }
    val pending = pendingRegistration
    if (pending != null) {
        RegistrationEmailConfirmationScreen(pending) { pendingRegistration = null }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (stateValues.isNarrowScreen)
            Spacer(
                modifier = Modifier
                    .height(stateValues.screenHeight / 8)
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
                    title = stateValues.stringSignUp,
                    iconSize = stateValues.boundWidgetWidth * 0.48f
                )
            }

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

            if (stateValues.isNarrowScreen) {
                AuthPreferencesChooser()
                Spacer(modifier = Modifier.height(innerSpace))
            }

            val phoneNumberTextFieldContent = countrySelectionPhoneNumberTextField(
                stateHost = transientSignUpState,
                stateKey = NavigationScreenModel.KEY_STATE_PHONE_NUMBER,
                identityKey = "user-auth-sign-up-phone",
                retainTextAcrossRecreation = false,
                persistTextDraft = false,
                retainSelectionAcrossRecreation = false,
                persistSelectionDraft = false
            )

            Spacer(modifier = Modifier.height(innerSpace))

            val emailTextFieldContent = emailTextField(
                stateHost = transientSignUpState,
                stateKey = NavigationScreenModel.KEY_STATE_EMAIL,
                identityKey = "user-auth-sign-up-email",
                retainTextAcrossRecreation = false,
                persistTextDraft = false
            )

            Spacer(modifier = Modifier.height(innerSpace))

            val firstNameTextFieldContent = genericTextField(
                titleText = stateValues.stringFirstName,
                placeholderText = stateValues.stringEnterFirstName,
                leadingIconPath = stateValues.drawablePathIconPerson,
                contentInvalidText = stateValues.stringFirstNameCannotBeEmptyOrJustWhitespaces,
                stateHost = transientSignUpState,
                stateKey = NavigationScreenModel.KEY_STATE_FIRST_NAME,
                identityKey = "user-auth-sign-up-first-name",
                retainTextAcrossRecreation = false,
                persistTextDraft = false,
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
                stateHost = transientSignUpState,
                stateKey = NavigationScreenModel.KEY_STATE_LAST_NAME,
                identityKey = "user-auth-sign-up-last-name",
                retainTextAcrossRecreation = false,
                persistTextDraft = false,
                onContentValidityCheck = {
                    it.checkAsPersonName()
                },
                onFilterValue = {
                    it.filterAsPersonName()
                }
            )

            Spacer(modifier = Modifier.height(innerSpace))

            val (passwordTextFieldContent, repeatedPasswordTextFieldContent) = repeatedPasswordTextFieldGroup(
                stateHost = transientSignUpState,
                stateKey = "new_password",
                repeatedStateKey = "repeated_password",
                retainTextAcrossRecreation = false,
                persistTextDraft = false
            )

            Spacer(modifier = Modifier.height(outerSpace))

            val signingUp = stateValues.signUpInProgress

            actionButton(
                text = contactText("registration_continue"),
                enabled = !signingUp,
                loading = signingUp,
                loadingText = stateValues.stringSigningUp,
                onClick = signUpAction@{
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
                ) {
                    val selectedPhoneCountry = stateValues.globalAppConfiguration.countries
                        .countryByPhoneSelection(phoneNumberTextFieldContent.selectedSecondaryId)
                        ?: stateValues.globalAppConfiguration.countries.first()

                    pendingRegistration = (
                        UserAuthSignUpDataModel(
                            phoneNumber = selectedPhoneCountry.phoneNumberCode + phoneNumberTextFieldContent.value.text.trim(),
                            email = emailTextFieldContent.value.text.trim(),
                            firstName = firstNameTextFieldContent.value.text.trim(),
                            lastName = lastNameTextFieldContent.value.text.trim(),
                            countryLocale = selectedPhoneCountry.locale,
                            password = passwordTextFieldContent.value.text
                        )
                    )
                }

                }
            )

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

internal var userAuthScreenScrollFirstVisibleItemIndex: Int = 0
internal var userAuthScreenScrollFirstVisibleItemScrollOffset: Int = 0

@Composable
fun AppConfiguration.UserAuthScreen() {
    val publicDestination = (if (stateValues.isNarrowScreen) stateValues.navigationScreensUserAuthLeft
        else stateValues.navigationScreensUserAuthRight).lastOrNull()
    val openPublicPage: (NavigationScreenModel.UserAuth) -> Unit = { destination ->
        coroutineScope.launch {
            if (stateValues.isNarrowScreen) Navigation.UserAuth.goLeft(destination)
            else Navigation.UserAuth.goRight(destination)
        }
    }
    val closePublicPage: () -> Unit = {
        coroutineScope.launch {
            if (stateValues.isNarrowScreen) Navigation.UserAuth.popLeft() else Navigation.UserAuth.popRight()
        }
    }
    if (publicDestination == NavigationScreenModel.UserAuth.Downloads) {
        DownloadsScreen(onBack = closePublicPage,
            onOpenFolderSettings = { openPublicPage(NavigationScreenModel.UserAuth.DownloadSettings) })
        return
    }
    if (publicDestination == NavigationScreenModel.UserAuth.DownloadSettings) {
        AitaScreenColumn(Modifier.fillMaxSize(), appBar = {
            ScreenAppBarWidget(title = downloadsText("folder"), iconPath = downloadsIconPath(), onBack = closePublicPage)
        }) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    DownloadFolderSettings()
                }
            }
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val authListState = rememberLazyListState(
            initialFirstVisibleItemIndex = userAuthScreenScrollFirstVisibleItemIndex.coerceAtLeast(0),
            initialFirstVisibleItemScrollOffset = userAuthScreenScrollFirstVisibleItemScrollOffset.coerceAtLeast(0)
        )

        LaunchedEffect(authListState) {
            snapshotFlow { authListState.firstVisibleItemIndex to authListState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    userAuthScreenScrollFirstVisibleItemIndex = index
                    userAuthScreenScrollFirstVisibleItemScrollOffset = offset
                }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f).fillMaxWidth(),
            state = authListState,
            contentPadding = PaddingValues(
                top = if (stateValues.isNarrowScreen) 0.dp else stateValues.screenHeight / 18,
                bottom = 32.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                if (stateValues.isNarrowScreen) {
                    AnimatedContent(
                        targetState = stateValues.navigationScreensUserAuthLeft,
                        transitionSpec = aitaStackTransitionSpec(),
                        label = "userAuthNavigationNarrow"
                    ) { navigationStack ->
                        val model = navigationStack.last()
                        when (model) {
                            is NavigationScreenModel.UserAuth.LogIn -> {
                                UserAuthLogInScreen()
                            }

                            else -> {
                                UserAuthSignUpScreen()
                            }
                        }
                    }
                } else {
                    val drawableResAITALogo by stateValues.drawableResAITALogo.collectAsState()

                    LargeIconWithTitleWidget(
                        modifier = Modifier
                            .width(stateValues.boundWidgetWidth)
                            .padding(bottom = stateValues.screenHeight / 56),
                        imageUrl = stateValues.drawablePathAITALogo,
                        imageRes = drawableResAITALogo,
                        iconSize = stateValues.boundWidgetWidth * 0.52f
                    )

                    AuthPreferencesChooser(
                        modifier = Modifier.padding(bottom = stateValues.screenHeight / 42)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        AnimatedContent(
                            modifier = Modifier
                                .weight(1f),
                            targetState = stateValues.navigationScreensUserAuthLeft,
                            transitionSpec = aitaStackTransitionSpec(),
                            label = "userAuthNavigationLeft"
                        ) { navigationStack ->
                            val model = navigationStack.last()
                            when (model) {
                                is NavigationScreenModel.UserAuth.LogIn -> {
                                    UserAuthLogInScreen()
                                }

                                else -> {
                                    UserAuthSignUpScreen()
                                }
                            }
                        }

                        AnimatedContent(
                            modifier = Modifier
                                .weight(1f),
                            targetState = stateValues.navigationScreensUserAuthRight,
                            transitionSpec = aitaStackTransitionSpec(),
                            label = "userAuthNavigationRight"
                        ) { navigationStack ->
                            val model = navigationStack.last()
                            when (model) {
                                is NavigationScreenModel.UserAuth.SignUp -> {
                                    UserAuthSignUpScreen()
                                }
                                else -> { }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), contentAlignment = Alignment.Center) {
            AuthQuietAction(downloadsText("auth_link")) { openPublicPage(NavigationScreenModel.UserAuth.Downloads) }
        }
    }
}

@Composable
fun AppConfiguration.UserAuthLogInScreen() {
    AdvancedAuthenticationLoginScreen()
}

internal data class TransactionUiContext(
    val transactionTypeIndex: Int,
    val clientId: Int,
    val stateHost: StateHost
)

internal data class QuickStockAddSheetRequest(
    val barcode: String,
    val transactionTypeIndex: Int,
    val clientId: Int
)

internal val quickStockAddSheetRequestState = MutableStateFlow<QuickStockAddSheetRequest?>(null)

internal fun AppConfiguration.openQuickStockAddSheet(
    barcode: String = "",
    transactionTypeIndex: Int,
    clientId: Int
) {
    coroutineScope.launch {
        quickStockAddSheetRequestState.emit(
            QuickStockAddSheetRequest(
                barcode = barcode.transactionBarcodeCandidate() ?: barcode.trim(),
                transactionTypeIndex = transactionTypeIndex,
                clientId = clientId
            )
        )
    }
}

internal fun closeQuickStockAddSheet() {
    AppConfiguration.coroutineScope.launch {
        quickStockAddSheetRequestState.emit(null)
        delay(120)
        requestTransactionBarcodeFocus()
    }
}

internal var activeTransactionBarcodeHandler: ((String) -> Boolean)? = null

internal fun String.transactionBarcodeCandidate(): String? {
    val compact = trim()
        .replace("\r", "")
        .replace("\n", "")
        .replace("\t", "")
        .replace(" ", "")

    if (compact.isBlank()) return null

    val digitRun = Regex("""\d{4,32}""").findAll(this)
        .map { it.value }
        .maxByOrNull { it.length }

    if (digitRun != null) return digitRun

    return compact.takeIf { value ->
        value.length in 4..32 && value.all { char -> char.isLetterOrDigit() || char in "-_." }
    }
}

internal fun String.normalizedTransactionBarcode(): String {
    return filter { it.isLetterOrDigit() }.uppercase()
}

internal fun String.isKnownRegionalBarcodePrefix(): Boolean {
    if (length < 3 || !all { it.isDigit() }) return false
    val prefix3 = take(3).toIntOrNull() ?: return false
    val prefix2 = take(2).toIntOrNull() ?: return false

    return prefix3 in 460..469 || // Russia / GS1 Russia range
            prefix3 == 487 ||         // Kazakhstan
            prefix3 == 470 ||         // Kyrgyzstan
            prefix3 == 478 ||         // Uzbekistan
            prefix3 == 488 ||         // Tajikistan is often serviced through nearby GS1/import ranges in practice
            prefix2 in 20..29         // common in-store weighted/internal barcodes
}

internal fun String.hasValidGtinChecksum(): Boolean {
    if (!all { it.isDigit() } || length !in setOf(8, 12, 13, 14)) return false

    val digits = map { it.digitToInt() }
    val check = digits.last()
    val body = digits.dropLast(1)
    var sum = 0
    var weight = 3

    for (index in body.size - 1 downTo 0) {
        val digit = body[index]
        sum += digit * weight
        weight = if (weight == 3) 1 else 3
    }

    return ((10 - (sum % 10)) % 10) == check
}

internal fun String.looksLikeCompleteRetailBarcodeInput(): Boolean {
    val candidate = transactionBarcodeCandidate() ?: return false
    val scannerEnded = any { it == '\n' || it == '\r' || it == '\t' }

    if (scannerEnded && candidate.length >= 4) return true
    if (!candidate.all { it.isDigit() }) return false

    return (candidate.length in setOf(8, 12, 13, 14) && candidate.hasValidGtinChecksum()) ||
            (candidate.length == 13 && candidate.isKnownRegionalBarcodePrefix()) ||
            candidate.length >= 16
}

internal fun String.compactTransactionBarcodeInput(): String = trim()
    .replace("\r", "")
    .replace("\n", "")
    .replace("\t", "")
    .replace(" ", "")

internal fun GoodsItemDataModel.matchesScannedBarcode(candidate: String): Boolean {
    return allBarcodeValues().any { barcode ->
        storedBarcodeMatchesScannedTransactionBarcode(
            storedBarcode = barcode,
            scannedBarcode = candidate
        )
    }
}

internal fun GoodsItemDataModel.matchesTransactionBarcode(candidate: String): Boolean {
    val embeddedWeightBarcodes = candidate.parseEmbeddedWeightBarcodeFormats()
    return matchesScannedBarcode(candidate) ||
            embeddedWeightBarcodes.any { matchesEmbeddedWeightBarcode(it) }
}

internal fun AppConfiguration.weightQuantityFromBarcode(
    goodsItem: GoodsItemDataModel,
    embeddedWeightBarcode: EmbeddedWeightBarcodeDataModel?
): QuantityDataModel? {
    if (embeddedWeightBarcode == null)
        return null

    if (!goodsItem.isWeightMeasurementUnit(stateValues.globalAppConfiguration))
        return null

    if (!goodsItem.matchesEmbeddedWeightBarcode(embeddedWeightBarcode) && !goodsItem.matchesScannedBarcode(embeddedWeightBarcode.rawBarcode))
        return null

    return goodsItem
        .defaultCartQuantity(stateValues.globalAppConfiguration)
        .copy(roundTotal = false)
        .withTotalValue(embeddedWeightBarcode.weightKilograms)
}

internal fun AppConfiguration.batchBelongsToInventoryStoreForUi(batchStoreId: String?, activeStoreId: String?): Boolean {
    val cleanActiveStoreId = activeStoreId?.takeIf { it.isNotBlank() } ?: return true
    val cleanBatchStoreId = batchStoreId?.takeIf { it.isNotBlank() } ?: return false
    return cleanBatchStoreId == cleanActiveStoreId || sameInventoryStoreGroupForUi(cleanActiveStoreId, cleanBatchStoreId)
}

internal fun AppConfiguration.availableSaleQuantityFor(goodsItem: GoodsItemDataModel): Double {
    val activeStoreId = stateValues.activeStoreId

    return stateValues.stockBatches.orEmpty()
        .filter { batch ->
            batch.goodsItemId == goodsItem.id &&
                    batchBelongsToInventoryStoreForUi(batch.storeId, activeStoreId) &&
                    batch.isActive &&
                    batch.status != StockBatchStatusDataModel.Ordered &&
                    batch.status != StockBatchStatusDataModel.Reserved &&
                    batch.status != StockBatchStatusDataModel.Deleted &&
                    batch.status != StockBatchStatusDataModel.WrittenOff &&
                    batch.status != StockBatchStatusDataModel.SoldOut &&
                    batch.status != StockBatchStatusDataModel.InTransit
        }
        .sumOf { it.quantity.total.coerceAtLeast(0.0) }
}

internal fun AppConfiguration.hasSaleStock(goodsItem: GoodsItemDataModel): Boolean {
    return availableSaleQuantityFor(goodsItem) > 0.0
}

internal fun AppConfiguration.cartQuantityLimitMessage(goodsItem: GoodsItemDataModel, availableQuantity: Double = availableSaleQuantityFor(goodsItem)): String {
    val templateQuantity = goodsItem.defaultCartQuantity(stateValues.globalAppConfiguration)
    val unit = templateQuantity.immutableUnitName.extractLocalizedString(stateValues.appLanguage).orEmpty()
    val availableText = "${availableQuantity.quantityAmountText(templateQuantity.roundTotal)} $unit".trim()
    return "${localizedStringResource(1058, "Only this much is available")}: $availableText"
}

internal fun AppConfiguration.canAddSaleQuantityToCart(
    goodsItem: GoodsItemDataModel,
    currentCart: List<GoodsItemInCartDataModel>,
    quantityToAdd: QuantityDataModel?
): Boolean {
    val existingQuantity = currentCart.find { it.id == goodsItem.id }?.quantity?.total ?: 0.0
    val requestedTotal = existingQuantity + (quantityToAdd ?: goodsItem.defaultCartQuantity(stateValues.globalAppConfiguration)).total.coerceAtLeast(0.0)
    return requestedTotal <= availableSaleQuantityFor(goodsItem) + 0.000001
}

internal fun AppConfiguration.sameInventoryStoreGroupForUi(firstStoreId: String?, secondStoreId: String?): Boolean {
    val first = firstStoreId?.takeIf { it.isNotBlank() } ?: return false
    val second = secondStoreId?.takeIf { it.isNotBlank() } ?: return false
    if (first == second) return true

    fun rootId(storeId: String): String {
        val store = stateValues.stores.findStoreOrBranchForUi(storeId)
        return store?.parentStoreId?.takeIf { it.isNotBlank() } ?: store?.id ?: storeId
    }

    return rootId(first) == rootId(second)
}

internal fun AppConfiguration.itemHasSellableBatchInStoreForUi(goodsItemId: String, storeId: String?): Boolean {
    val cleanStoreId = storeId?.takeIf { it.isNotBlank() } ?: return false
    return stateValues.stockBatches.orEmpty().any { batch ->
        batch.goodsItemId == goodsItemId &&
                batchBelongsToInventoryStoreForUi(batch.storeId, cleanStoreId) &&
                batch.isActive &&
                batch.quantity.total > 0.0 &&
                batch.status != StockBatchStatusDataModel.Ordered &&
                batch.status != StockBatchStatusDataModel.Reserved &&
                batch.status != StockBatchStatusDataModel.Deleted &&
                batch.status != StockBatchStatusDataModel.WrittenOff &&
                batch.status != StockBatchStatusDataModel.SoldOut &&
                batch.status != StockBatchStatusDataModel.InTransit
    }
}

internal fun AppConfiguration.transactionStockCandidatesForUi(
    sortForDisplay: Boolean = true
): List<GoodsItemDataModel> {
    val activeStoreId = stateValues.activeStoreId
    val stock = stateValues.stock.orEmpty()
    if (activeStoreId.isNullOrBlank()) return stock

    val stores = stateValues.stores.orEmpty()
    val storeRootById = buildMap {
        stores.forEach { store ->
            put(store.id, store.rootStoreId())
            store.branches.forEach { branch -> put(branch.id, branch.rootStoreId()) }
        }
    }
    fun rootStoreIdFor(storeId: String?): String = storeId
        ?.takeIf { it.isNotBlank() }
        ?.let { storeRootById[it] ?: it }
        .orEmpty()

    val activeRootStoreId = rootStoreIdFor(activeStoreId)
    val sellableItemIds = stateValues.stockBatches.orEmpty()
        .asSequence()
        .filter { batch ->
            batch.isActive &&
                    batch.quantity.total > 0.0 &&
                    batch.status != StockBatchStatusDataModel.Ordered &&
                    batch.status != StockBatchStatusDataModel.Reserved &&
                    batch.status != StockBatchStatusDataModel.Deleted &&
                    batch.status != StockBatchStatusDataModel.WrittenOff &&
                    batch.status != StockBatchStatusDataModel.SoldOut &&
                    batch.status != StockBatchStatusDataModel.InTransit &&
                    rootStoreIdFor(batch.storeId) == activeRootStoreId
        }
        .map { it.goodsItemId }
        .filter { it.isNotBlank() }
        .toSet()

    val scopedStock = stock
        .filter { item -> rootStoreIdFor(item.storeId) == activeRootStoreId || item.id in sellableItemIds }

    if (!sortForDisplay) return scopedStock

    return scopedStock.sortedWith(
        compareBy<GoodsItemDataModel> { item ->
            when {
                item.storeId == activeStoreId -> 0
                rootStoreIdFor(item.storeId) == activeRootStoreId -> 1
                else -> 2
            }
        }
            .thenByDescending { item -> item.id in sellableItemIds }
            .thenBy { item -> item.name.extractLocalizedString(stateValues.appLanguage).orEmpty().lowercase() }
    )
}

internal fun AppConfiguration.postCartQuantityLimitNotification(goodsItem: GoodsItemDataModel) {
    postInAppNotification(
        message = "${goodsItem.name.visibleLocalizedString(stateValues.appLanguage, localizedStringResource(365, "Goods item"))} • ${cartQuantityLimitMessage(goodsItem)}",
        type = NotificationType.Negative,
        transient = true
    )
}

internal fun AppConfiguration.tryHandleTransactionBarcodeInput(
    rawInput: String,
    transactionTypeIndex: Int,
    clientId: Int,
    currentCart: List<GoodsItemInCartDataModel>
): Boolean {
    if (transactionTypeIndex == 1 && requestReturnReceiptScan(rawInput, clientId)) return true
    val receiptInput = rawInput.trim().removePrefix("]C0")
    if (transactionTypeIndex == 1 && receiptInput.startsWith("9910") && receiptInput.length <= 44 &&
        rawInput.none { it == '\n' || it == '\r' || it == '\t' }) return false
    val candidate = rawInput.transactionBarcodeCandidate() ?: return false

    if (candidate.length > 32) return false

    val compactInput = rawInput.compactTransactionBarcodeInput()
    val numericBarcodeTypedAlone = candidate.length >= 4 && candidate.all { it.isDigit() } && compactInput == candidate
    if (!rawInput.looksLikeCompleteRetailBarcodeInput() && !numericBarcodeTypedAlone) return false

    val embeddedWeightBarcodes = candidate.parseEmbeddedWeightBarcodeFormats()
    val stock = transactionStockCandidatesForUi(sortForDisplay = false)

    val weightedMatch = embeddedWeightBarcodes.firstNotNullOfOrNull { barcode ->
        stock.firstOrNull { item ->
            item.isWeightMeasurementUnit(stateValues.globalAppConfiguration) && item.matchesEmbeddedWeightBarcode(barcode)
        }?.let { item -> item to barcode }
    }

    val weightedGoodsItem = weightedMatch?.first
    val embeddedWeightBarcode = weightedMatch?.second ?: embeddedWeightBarcodes.firstOrNull()
    val exactGoodsItem = stock.firstOrNull { it.matchesScannedBarcode(candidate) }
    val goodsItem = weightedGoodsItem ?: exactGoodsItem

    if (goodsItem != null) {
        val quantityFromBarcode = weightQuantityFromBarcode(goodsItem, embeddedWeightBarcode)

        if (embeddedWeightBarcodes.isNotEmpty() && weightedGoodsItem == null && exactGoodsItem == null) {
            openQuickStockAddSheet(
                barcode = embeddedWeightBarcode?.rawBarcode ?: candidate,
                transactionTypeIndex = transactionTypeIndex,
                clientId = clientId
            )
            requestTransactionBarcodeFocus()
            return true
        }

        if (embeddedWeightBarcode != null && quantityFromBarcode == null && weightedGoodsItem != null) {
            postInAppNotification(
                "Barcode ${embeddedWeightBarcode.rawBarcode} matched ${goodsItem.name.visibleLocalizedString(stateValues.appLanguage, candidate)}, but this item is not configured as sold by weight.",
                NotificationType.Negative,
                transient = true
            )
            requestTransactionBarcodeFocus()
            return true
        }

        if (transactionTypeIndex == 0 && !hasSaleStock(goodsItem)) {
            postInAppNotification(
                "Barcode $candidate matched ${goodsItem.name.visibleLocalizedString(stateValues.appLanguage, candidate)}, but it is out of stock.",
                NotificationType.Negative,
                transient = true
            )
            requestTransactionBarcodeFocus()
            return true
        }

        if (transactionTypeIndex == 0 && !canAddSaleQuantityToCart(goodsItem, currentCart, quantityFromBarcode)) {
            postCartQuantityLimitNotification(goodsItem)
            requestTransactionBarcodeFocus()
            return true
        }

        addGoodsItemToTransactionCart(
            goodsItem = goodsItem,
            transactionTypeIndex = transactionTypeIndex,
            clientId = clientId,
            configuration = stateValues.globalAppConfiguration,
            currentCart = currentCart,
            quantityToAdd = quantityFromBarcode
        )

        val addedQuantityText = quantityFromBarcode?.quantityText(stateValues.appLanguage)
        postInAppNotification(
            buildString {
                append("Added ")
                append(goodsItem.name.visibleLocalizedString(stateValues.appLanguage, candidate))
                if (!addedQuantityText.isNullOrBlank()) {
                    append(" • ")
                    append(addedQuantityText)
                }
            },
            NotificationType.Positive,
            transient = true
        )
        requestTransactionBarcodeFocus()
        return true
    }

    if (rawInput.looksLikeCompleteRetailBarcodeInput()) {
        openQuickStockAddSheet(
            barcode = candidate,
            transactionTypeIndex = transactionTypeIndex,
            clientId = clientId
        )
        requestTransactionBarcodeFocus()
        return true
    }

    return false
}

@Composable
internal fun AppConfiguration.TransactionBarcodeHidInput(
    transactionTypeIndex: Int,
    clientId: Int,
    currentCart: List<GoodsItemInCartDataModel>,
    captureEnabled: Boolean
) {
    val focusRequester = remember { FocusRequester() }
    val softKeyboardController = LocalSoftwareKeyboardController.current
    val suppressSoftKeyboard = remember { getPlatformName().contains("android", ignoreCase = true) }
    val localScope = rememberCoroutineScope()
    val hideSoftKeyboardIfNeeded: () -> Unit = remember(softKeyboardController, suppressSoftKeyboard) {
        {
            if (suppressSoftKeyboard) {
                softKeyboardController?.hide()
                forceHidePlatformSoftKeyboard?.invoke()
            }
        }
    }
    val requestFocusWithoutSoftKeyboard: () -> Unit = remember(focusRequester, hideSoftKeyboardIfNeeded, suppressSoftKeyboard, localScope) {
        {
            focusRequester.requestFocus()
            hideSoftKeyboardIfNeeded()
            if (suppressSoftKeyboard) {
                localScope.launch {
                    delay(40)
                    hideSoftKeyboardIfNeeded()
                    delay(160)
                    hideSoftKeyboardIfNeeded()
                }
            }
        }
    }
    var buffer by rememberSaveable(transactionTypeIndex, clientId) { mutableStateOf("") }

    val latestCart by rememberUpdatedState(currentCart)
    val latestCaptureEnabled by rememberUpdatedState(captureEnabled)
    val barcodeHandler: (String) -> Boolean = remember(transactionTypeIndex, clientId) {
        { input ->
            val handled = latestCaptureEnabled && !transactionBarcodeModalOpen() && tryHandleTransactionBarcodeInput(
                rawInput = input,
                transactionTypeIndex = transactionTypeIndex,
                clientId = clientId,
                currentCart = latestCart
            )
            if (handled) transactionSearchCompletion.handled()
            handled
        }
    }
    DisposableEffect(barcodeHandler) {
        activeTransactionBarcodeHandler = barcodeHandler
        onDispose {
            if (activeTransactionBarcodeHandler === barcodeHandler) activeTransactionBarcodeHandler = null
        }
    }

    BasicTextField(
        value = buffer,
        enabled = captureEnabled,
        onValueChange = { raw ->
            val candidate = transactionHidBuffer(raw, if (transactionTypeIndex == 1) 64 else 32)

            // Keep a scanner terminator for completion detection; never store it in the buffer.
            buffer = if (raw.any { it == '\n' || it == '\r' || it == '\t' } && barcodeHandler(raw)) "" else candidate
        },
        modifier = Modifier
            .size(1.dp)
            .alpha(0f)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Tab)) {
                    val consumed = barcodeHandler("$buffer\n")
                    if (consumed) buffer = ""
                    consumed
                } else false
            }
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    hideSoftKeyboardIfNeeded()
                } else {
                    buffer = ""
                    requestTransactionBarcodeFocus()
                }
            },
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Ascii,
            showKeyboardOnFocus = !suppressSoftKeyboard
        )
    )

    LaunchedEffect(buffer, captureEnabled) {
        if (!captureEnabled || buffer.length < 4) return@LaunchedEffect
        val owner = captureReceiptActionOwner()
        val inventoryOwner = inventoryViewScopeKey()
        delay(160)
        if (owner.isCurrent() && inventoryOwner == inventoryViewScopeKey() && barcodeHandler(buffer + "\n")) buffer = ""
    }
    LaunchedEffect(captureEnabled) { if (!captureEnabled) buffer = "" }
    TransactionBarcodeFocusEffect(
        contextKey = "$transactionTypeIndex:$clientId:${stateValues.activeStoreId}",
        captureEnabled = captureEnabled,
        preferSearch = prefersVisibleTransactionSearch(stateValues.isNarrowScreen),
        requestHidFocus = requestFocusWithoutSoftKeyboard
    )
}

@Composable
internal fun AppConfiguration.rememberTransactionContext(): TransactionUiContext {
    val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
        is NavigationScreenModel.Transaction.MainSale -> 0
        is NavigationScreenModel.Transaction.MainReturn -> 1
        else -> 2
    }

    val clientId = when (transactionTypeIndex) {
        0 -> stateValues.navigationTransactionSaleClientId
        1 -> stateValues.navigationTransactionReturnClientId
        else -> stateValues.navigationTransactionSupplyClientId
    }

    val stateHost = when (transactionTypeIndex) {
        0 -> NavigationScreenModel.Transaction.MainSale
        1 -> NavigationScreenModel.Transaction.MainReturn
        else -> NavigationScreenModel.Transaction.MainSupply
    }

    return TransactionUiContext(
        transactionTypeIndex = transactionTypeIndex,
        clientId = clientId,
        stateHost = stateHost
    )
}


internal data class TransactionSelectionSmartSets(
    val popularIds: List<String> = emptyList(),
    val recentIds: List<String> = emptyList(),
    val restockIds: List<String> = emptyList(),
    val lowStockIds: Set<String> = emptySet(),
    val expiringIds: List<String> = emptyList(),
    val freshIds: List<String> = emptyList(),
    val freshItemCount: Int = 0,
    val slowMovingIds: List<String> = emptyList(),
    val inStockIds: Set<String> = emptySet(),
    val totalStockCount: Int = 0,
    val quickItemCount: Int = 0,
    val inStockItemCount: Int = 0
)

internal fun GoodsBatchDataModel.isSelectableActiveStockBatch(): Boolean {
    return isActive &&
            quantity.total > 0.0 &&
            status != StockBatchStatusDataModel.Ordered &&
            status != StockBatchStatusDataModel.Reserved &&
            status != StockBatchStatusDataModel.Deleted &&
            status != StockBatchStatusDataModel.WrittenOff &&
            status != StockBatchStatusDataModel.SoldOut &&
            status != StockBatchStatusDataModel.InTransit
}

internal const val AITA_ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
internal const val AITA_EXPIRING_REMINDER_WINDOW_MILLIS = 3L * AITA_ONE_DAY_MILLIS

internal fun stockDayStartMillis(millis: Long = getCurrentTimeMillis()): Long {
    val zone = TimeZone.currentSystemDefault()
    return Instant
        .fromEpochMilliseconds(millis)
        .toLocalDateTime(zone)
        .date
        .atStartOfDayIn(zone)
        .toEpochMilliseconds()
}

internal fun GoodsBatchDataModel.isExpiringWithinReminderWindow(nowMillis: Long = getCurrentTimeMillis()): Boolean {
    val expiration = expirationDateMillis ?: return false
    val todayStart = stockDayStartMillis(nowMillis)
    val reminderEndExclusive = todayStart + AITA_EXPIRING_REMINDER_WINDOW_MILLIS + AITA_ONE_DAY_MILLIS
    return isSelectableActiveStockBatch() && expiration >= todayStart && expiration < reminderEndExclusive
}

internal fun GoodsBatchDataModel.isFreshForTransactionSelection(nowMillis: Long = getCurrentTimeMillis()): Boolean {
    val expiration = expirationDateMillis
    return isSelectableActiveStockBatch() && (expiration == null || expiration >= stockDayStartMillis(nowMillis))
}

internal fun AppConfiguration.itemBatchesForActiveInventoryStore(
    item: GoodsItemDataModel,
    batches: List<GoodsBatchDataModel> = stateValues.stockBatches.orEmpty()
): List<GoodsBatchDataModel> {
    val activeStoreId = stateValues.activeStoreId
    return batches.filter { batch ->
        batch.goodsItemId == item.id &&
                batch.isActive &&
                (activeStoreId.isNullOrBlank() || batchBelongsToInventoryStoreForUi(batch.storeId, activeStoreId))
    }
}

internal fun AppConfiguration.itemIsFreshForTransactionSelection(
    item: GoodsItemDataModel,
    batches: List<GoodsBatchDataModel> = stateValues.stockBatches.orEmpty(),
    nowMillis: Long = getCurrentTimeMillis()
): Boolean {
    val itemBatches = itemBatchesForActiveInventoryStore(item, batches).filter { it.isSelectableActiveStockBatch() }
    return itemBatches.isEmpty() || itemBatches.any { it.isFreshForTransactionSelection(nowMillis) }
}

@Composable
internal fun AppConfiguration.expirationReminderTextForItem(
    item: GoodsItemDataModel,
    batches: List<GoodsBatchDataModel> = stateValues.stockBatches.orEmpty()
): String? {
    val now = getCurrentTimeMillis()
    val closestBatch = itemBatchesForActiveInventoryStore(item, batches)
        .filter { it.isExpiringWithinReminderWindow(now) }
        .minByOrNull { it.expirationDateMillis ?: Long.MAX_VALUE }
        ?: return null

    val dateText = closestBatch.expirationDateMillis?.toStockDateInputText().orEmpty()
    return listOf(
        localizedStringResource(1309, "Expires very soon"),
        dateText.takeIf { it.isNotBlank() }
    ).filterNotNull().joinToString(": ")
}

internal fun AppConfiguration.itemHasPresentStockBatchForTransactionSelection(item: GoodsItemDataModel): Boolean {
    val activeStoreId = stateValues.activeStoreId
    return stateValues.stockBatches.orEmpty().any { batch ->
        batch.goodsItemId == item.id &&
                batch.isSelectableActiveStockBatch() &&
                (activeStoreId.isNullOrBlank() || batchBelongsToInventoryStoreForUi(batch.storeId, activeStoreId))
    }
}

internal fun GoodsItemDataModel.transactionSelectionQuantity(activeBatches: List<GoodsBatchDataModel>): Double {
    return activeBatches
        .filter { it.goodsItemId == id }
        .sumOf { it.quantity.total }
}

internal fun GoodsItemInTransactionDataModel.transactionSelectionStockItemId(
    stockById: Map<String, GoodsItemDataModel>,
    stockByBarcode: Map<String, GoodsItemDataModel>
): String? {
    goodsItemId
        ?.takeIf { it.isNotBlank() && stockById.containsKey(it) }
        ?.let { return it }

    val normalizedBarcode = barcode.toStoredGoodsItemBarcode()
    return stockByBarcode[barcode]?.id
        ?: stockByBarcode[normalizedBarcode]?.id
}

internal fun GoodsItemDataModel.transactionSelectionMatchesIdSet(ids: Collection<String>): Boolean {
    if (id in ids) return true
    return allBarcodeValues().any { barcode -> barcode in ids || barcode.toStoredGoodsItemBarcode() in ids }
}

internal fun buildTransactionSelectionSmartSets(
    storeId: String?,
    transactionTypeIndex: Int,
    transactions: List<TransactionDataModel>,
    stock: List<GoodsItemDataModel>,
    batches: List<GoodsBatchDataModel>,
    language: String
): TransactionSelectionSmartSets {
    if (stock.isEmpty()) return TransactionSelectionSmartSets()

    // Caller has already applied the account/store hierarchy filter on the UI thread.
    val activeBatches = batches.filter { it.isSelectableActiveStockBatch() }
    val activeBatchesByItem = activeBatches.groupBy { it.goodsItemId }
    val quantityByItem = activeBatchesByItem.mapValues { (_, itemBatches) -> itemBatches.sumOf { it.quantity.total } }
    val inStockIds = quantityByItem
        .filterValues { quantity -> quantity > 0.0 }
        .keys
        .toSet()

    val stockById = lazy(LazyThreadSafetyMode.NONE) { stock.associateBy { it.id } }
    val stockByBarcode = lazy(LazyThreadSafetyMode.NONE) {
        stock
            .flatMap { item ->
                item.allBarcodeValues().flatMap { barcode ->
                    listOf(barcode, barcode.toStoredGoodsItemBarcode())
                }.filter { it.isNotBlank() }.map { it to item }
            }
            .associate { it }
    }

    val scopedTransactions = if (transactions.isEmpty()) {
        emptyList()
    } else {
        transactions
            .asSequence()
            .filter { transaction -> storeId.isNullOrBlank() || transaction.storeId == storeId }
            .take(600)
            .toList()
    }

    fun popularIds(source: List<TransactionDataModel>): List<String> {
        if (source.isEmpty()) return emptyList()
        val byId = stockById.value
        val byBarcode = stockByBarcode.value
        return source
            .asSequence()
            .flatMap { transaction ->
                transaction.goodsInTransaction
                    .asSequence()
                    .mapNotNull { it.transactionSelectionStockItemId(byId, byBarcode) }
                    .distinct()
                    .map { itemId -> itemId to transaction.id.ifBlank { "${transaction.timeMillis}:$itemId" } }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, transactionIds) -> transactionIds.distinct().size }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { byId[it.key]?.name?.extractLocalizedString(language).orEmpty().lowercase() })
            .map { it.key }
            .take(24)
    }

    fun recentIds(source: List<TransactionDataModel>): List<String> {
        if (source.isEmpty()) return emptyList()
        val byId = stockById.value
        val byBarcode = stockByBarcode.value
        return source
            .asSequence()
            .sortedByDescending { it.timeMillis }
            .take(180)
            .flatMap { it.goodsInTransaction.asSequence() }
            .mapNotNull { it.transactionSelectionStockItemId(byId, byBarcode) }
            .distinct()
            .take(24)
            .toList()
    }

    val saleTransactions = if (scopedTransactions.isEmpty()) emptyList() else scopedTransactions.filter { it.type == "purchase" }
    val transactionSpecificTransactions = if (scopedTransactions.isEmpty()) emptyList() else scopedTransactions.filter { it.type == transactionServerType(transactionTypeIndex) }
    val transactionSpecificRecentTransactions = when (transactionTypeIndex) {
        1 -> saleTransactions
        else -> transactionSpecificTransactions
    }

    val now = getCurrentTimeMillis()
    val todayStart = stockDayStartMillis(now)
    val expiringSoonCutoffExclusive = todayStart + 15L * AITA_ONE_DAY_MILLIS

    val freshIds = mutableListOf<String>()
    var freshItemCount = 0
    if (transactionTypeIndex != 1) {
        val seenFreshItemIds = mutableSetOf<String>()
        activeBatches.forEach { batch ->
            val itemId = batch.goodsItemId
            if (itemId.isNotBlank() && batch.isFreshForTransactionSelection(now) && seenFreshItemIds.add(itemId)) {
                freshItemCount += 1
                if (freshIds.size < 32) freshIds += itemId
            }
        }
    }

    val expiringIds = if (transactionTypeIndex == 0 || transactionTypeIndex == 2) {
        activeBatches
            .asSequence()
            .filter { batch -> batch.expirationDateMillis?.let { it >= todayStart && it < expiringSoonCutoffExclusive } == true }
            .sortedBy { it.expirationDateMillis ?: Long.MAX_VALUE }
            .map { it.goodsItemId }
            .distinct()
            .take(32)
            .toList()
    } else {
        emptyList()
    }

    val lowStockIds = if (transactionTypeIndex == 2) {
        stock
            .asSequence()
            .filter { item ->
                item.isActive && item.id.isNotBlank() &&
                        (quantityByItem[item.id] ?: 0.0) > 0.0 &&
                        (quantityByItem[item.id] ?: 0.0) <= 5.0
            }
            .map { it.id }
            .toSet()
    } else {
        emptySet()
    }

    val restockIds = if (transactionTypeIndex == 2) {
        val byId = stockById.value
        val outOfStockIds = stock
            .asSequence()
            .filter { item -> item.isActive && item.id.isNotBlank() && (quantityByItem[item.id] ?: 0.0) <= 0.0 }
            .map { it.id }
            .take(180)
            .toList()

        (outOfStockIds + lowStockIds)
            .distinct()
            .sortedWith(
                compareBy<String> { id -> if (id in outOfStockIds) 0 else 1 }
                    .thenBy { id -> byId[id]?.name?.extractLocalizedString(language).orEmpty().lowercase() }
            )
            .take(32)
    } else {
        emptyList()
    }

    val slowMovingIds = if ((transactionTypeIndex == 0 || transactionTypeIndex == 2) && saleTransactions.isNotEmpty()) {
        val recentlySoldIds = saleTransactions
            .asSequence()
            .sortedByDescending { it.timeMillis }
            .take(180)
            .flatMap { transaction -> transaction.goodsInTransaction.asSequence() }
            .mapNotNull { it.transactionSelectionStockItemId(stockById.value, stockByBarcode.value) }
            .toSet()

        inStockIds
            .asSequence()
            .filter { itemId -> itemId !in recentlySoldIds }
            .take(24)
            .toList()
    } else {
        emptyList()
    }

    return TransactionSelectionSmartSets(
        popularIds = popularIds(transactionSpecificTransactions),
        recentIds = recentIds(transactionSpecificRecentTransactions),
        restockIds = restockIds,
        lowStockIds = lowStockIds,
        expiringIds = expiringIds,
        freshIds = freshIds,
        freshItemCount = freshItemCount,
        slowMovingIds = slowMovingIds,
        inStockIds = inStockIds,
        totalStockCount = stock.size,
        quickItemCount = stock.count { it.isQuickItem },
        inStockItemCount = inStockIds.size
    )
}

@Composable
fun AppConfiguration.TransactionSelectionScreen(
    onBarcodeCaptureFocusRequested: (() -> Unit)? = null
) {
    val context = rememberTransactionContext()

    val canGoBack = when (context.transactionTypeIndex) {
        0 -> !Navigation.TransactionSale.isVeryFirstScreen(stateValues.isNarrowScreen, context.clientId)
        1 -> !Navigation.TransactionReturn.isVeryFirstScreen(stateValues.isNarrowScreen, context.clientId)
        else -> !Navigation.TransactionSupply.isVeryFirstScreen(stateValues.isNarrowScreen, context.clientId)
    }

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringSelect,
                trailingIcons = if (stateValues.isNarrowScreen) {
                    listOf(
                        Triple(stockAddIconPath(), stockAddIconFallback()) {
                            openQuickStockAddSheet(
                                transactionTypeIndex = context.transactionTypeIndex,
                                clientId = context.clientId
                            )
                        }
                    )
                } else emptyList(),
                onBack = if (canGoBack) {
                    {
                        coroutineScope.launch {
                            when (context.transactionTypeIndex) {
                                0 -> Navigation.TransactionSale.pop()
                                1 -> Navigation.TransactionReturn.pop()
                                else -> Navigation.TransactionSupply.pop()
                            }
                        }
                    }
                } else null
            )
        }
    ) {
        val goodsInCart by getCartState(
            context.transactionTypeIndex,
            context.clientId
        ).collectAsState()

        var transactionHistorySuggestionsReady by remember(context.transactionTypeIndex, context.clientId, stateValues.activeStoreId) {
            mutableStateOf(false)
        }

        LaunchedEffect(context.transactionTypeIndex, context.clientId, stateValues.activeStoreId) {
            transactionHistorySuggestionsReady = false
            val storeIdForHistory = stateValues.activeStoreId
            delay(700)
            storeIdForHistory?.let { getTransactions(it) }
            delay(220)
            transactionHistorySuggestionsReady = true
        }

        val transactionsPayload by transactionsState.payload.collectAsState()
        val transactionHistoryForSuggestions = if (transactionHistorySuggestionsReady) transactionsPayload.orEmpty() else emptyList()
        val transactionScopedStock = remember(
            stateValues.stock,
            stateValues.stockBatches,
            stateValues.stores,
            stateValues.activeStoreId
        ) {
            transactionStockCandidatesForUi(sortForDisplay = false)
        }

        val suggestionStoreId = stateValues.activeStoreId
        val suggestionLanguage = stateValues.appLanguage
        val suggestionBatches = remember(stateValues.stockBatches, stateValues.stores, suggestionStoreId) {
            stateValues.stockBatches.orEmpty().filter {
                batchBelongsToInventoryStoreForUi(it.storeId, suggestionStoreId)
            }
        }
        val smartSetsState = remember(stateValues.userAccount?.id, suggestionStoreId, context.transactionTypeIndex) {
            mutableStateOf<TransactionSelectionSmartSets?>(null)
        }
        LaunchedEffect(smartSetsState, transactionHistoryForSuggestions, transactionScopedStock, suggestionBatches, suggestionLanguage) {
            smartSetsState.value = withContext(Dispatchers.Default) {
                buildTransactionSelectionSmartSets(
                    storeId = suggestionStoreId,
                    transactionTypeIndex = context.transactionTypeIndex,
                    transactions = transactionHistoryForSuggestions,
                    stock = transactionScopedStock,
                    batches = suggestionBatches,
                    language = suggestionLanguage
                )
            }
        }
        val transactionSmartSets = smartSetsState.value ?: remember(transactionScopedStock) {
            TransactionSelectionSmartSets(
                totalStockCount = transactionScopedStock.size,
                quickItemCount = transactionScopedStock.count { it.isQuickItem }
            )
        }

        val transactionSelectionTabs = remember(
            context.transactionTypeIndex,
            transactionSmartSets,
            stateValues.appLanguage
        ) {
            buildList {
                add(TabContent("all", tabLabelWithCount(stateValues.stringAll, transactionSmartSets.totalStockCount)))
                add(TabContent("quick", tabLabelWithCount(stateValues.stringQuick, transactionSmartSets.quickItemCount)))
                add(TabContent("in_stock", tabLabelWithCount(localizedStringResource(1180, "In stock"), transactionSmartSets.inStockItemCount)))
                if (transactionSmartSets.freshIds.isNotEmpty()) {
                    add(TabContent("fresh", tabLabelWithCount(localizedStringResource(1310, "Fresh"), transactionSmartSets.freshItemCount)))
                }

                if (transactionSmartSets.popularIds.isNotEmpty()) {
                    add(TabContent("popular", tabLabelWithCount(localizedStringResource(713, "Most popular"), transactionSmartSets.popularIds.size)))
                }

                if (transactionSmartSets.recentIds.isNotEmpty()) {
                    add(
                        TabContent(
                            "recent",
                            tabLabelWithCount(
                                if (context.transactionTypeIndex == 1) localizedStringResource(721, "Recently sold")
                                else localizedStringResource(714, "Recently used"),
                                transactionSmartSets.recentIds.size
                            )
                        )
                    )
                }

                when (context.transactionTypeIndex) {
                    2 -> {
                        if (transactionSmartSets.restockIds.isNotEmpty()) {
                            add(TabContent("restock", tabLabelWithCount(localizedStringResource(715, "Restock"), transactionSmartSets.restockIds.size)))
                        }
                        if (transactionSmartSets.lowStockIds.isNotEmpty()) {
                            add(TabContent("low_stock", tabLabelWithCount(localizedStringResource(716, "Low stock"), transactionSmartSets.lowStockIds.size)))
                        }
                        if (transactionSmartSets.expiringIds.isNotEmpty()) {
                            add(TabContent("expiring", tabLabelWithCount(localizedStringResource(717, "Expiring"), transactionSmartSets.expiringIds.size)))
                        }
                        if (transactionSmartSets.slowMovingIds.isNotEmpty()) {
                            add(TabContent("slow", tabLabelWithCount(localizedStringResource(718, "Slow movers"), transactionSmartSets.slowMovingIds.size)))
                        }
                    }
                    0 -> {
                        if (transactionSmartSets.expiringIds.isNotEmpty()) {
                            add(TabContent("expiring", tabLabelWithCount(localizedStringResource(717, "Expiring"), transactionSmartSets.expiringIds.size)))
                        }
                        if (transactionSmartSets.slowMovingIds.isNotEmpty()) {
                            add(TabContent("slow", tabLabelWithCount(localizedStringResource(718, "Slow movers"), transactionSmartSets.slowMovingIds.size)))
                        }
                    }
                }
            }
        }

        val searchTextFieldContent = searchTextField(
            stateHost = context.stateHost,
            stateKey = NavigationScreenModel.KEY_STATE_SEARCH_QUERY,
            isFocusedInitial = false,
            autoFocus = false,
            updateIsFocusedAction = { focusState ->
                if (!focusState.isFocused) onBarcodeCaptureFocusRequested?.invoke()
            },
            forceRefocus = false,
            modifier = Modifier.padding(stateValues.marginTextField),
            barcodeCamScanner = true,
            captureTransactionBarcodeInput = true
        )

        TransactionSearchFocusTarget(
            requester = searchTextFieldContent.focusRequester,
            enabled = prefersVisibleTransactionSearch(stateValues.isNarrowScreen)
        )

        val scopeRowContent = tabRowWidget(
            modifier = Modifier.padding(horizontal = stateValues.marginTextField),
            tabs = transactionSelectionTabs,
            // A new/seeded catalogue often has no quick items; never open on an empty subset.
            selectedIndexInitial = "all",
            persistSelection = false
        )

        val addToCartAction: (GoodsItemDataModel) -> Unit = { goodsItem ->
            val activeStoreId = stateValues.activeStoreId
            if (!activeStoreId.isNullOrBlank() && !sameInventoryStoreGroupForUi(activeStoreId, goodsItem.storeId) && !itemHasSellableBatchInStoreForUi(goodsItem.id, activeStoreId)) {
                postInAppNotification(
                    message = localizedStringResource(1063, "This item belongs to another branch. Switch to that branch or move the batch here before selling it."),
                    type = NotificationType.Negative,
                    transient = true
                )
            } else if (context.transactionTypeIndex == 0 && !canAddSaleQuantityToCart(goodsItem, goodsInCart, null)) {
                postCartQuantityLimitNotification(goodsItem)
            } else {
                addGoodsItemToTransactionCart(
                    goodsItem = goodsItem,
                    transactionTypeIndex = context.transactionTypeIndex,
                    clientId = context.clientId,
                    configuration = stateValues.globalAppConfiguration,
                    currentCart = goodsInCart
                )
            }
            onBarcodeCaptureFocusRequested?.invoke()
        }

        val selectedTransactionFilterId = scopeRowContent.id
        val transactionSearchQuery = searchTextFieldContent.value.text.trim()
        val transactionSearchAcrossAllStock = transactionSearchQuery.isNotBlank()
        val selectedPreferredOrderIds = if (transactionSearchAcrossAllStock) {
            emptyList()
        } else when (selectedTransactionFilterId) {
            "popular" -> transactionSmartSets.popularIds
            "recent" -> transactionSmartSets.recentIds
            "restock" -> transactionSmartSets.restockIds
            "low_stock" -> transactionSmartSets.restockIds.filter { it in transactionSmartSets.lowStockIds }
            "expiring" -> transactionSmartSets.expiringIds
            "fresh" -> transactionSmartSets.freshIds
            "slow" -> transactionSmartSets.slowMovingIds
            else -> emptyList()
        }

        val transactionSelectionStockItems = remember(transactionScopedStock, selectedTransactionFilterId, transactionSmartSets, transactionSearchAcrossAllStock) {
            if (transactionSearchAcrossAllStock) {
                transactionScopedStock
            } else when (selectedTransactionFilterId) {
                "quick" -> transactionScopedStock.filter { item -> item.isQuickItem }
                "in_stock" -> transactionScopedStock.filter { item -> item.id in transactionSmartSets.inStockIds }
                "popular" -> transactionScopedStock.filter { item -> item.transactionSelectionMatchesIdSet(transactionSmartSets.popularIds) }
                "recent" -> transactionScopedStock.filter { item -> item.transactionSelectionMatchesIdSet(transactionSmartSets.recentIds) }
                "restock" -> transactionScopedStock.filter { item -> item.transactionSelectionMatchesIdSet(transactionSmartSets.restockIds) }
                "low_stock" -> transactionScopedStock.filter { item -> item.transactionSelectionMatchesIdSet(transactionSmartSets.lowStockIds) }
                "expiring" -> transactionScopedStock.filter { item -> item.transactionSelectionMatchesIdSet(transactionSmartSets.expiringIds) }
                "fresh" -> transactionScopedStock.filter { item -> item.transactionSelectionMatchesIdSet(transactionSmartSets.freshIds) }
                "slow" -> transactionScopedStock.filter { item -> item.transactionSelectionMatchesIdSet(transactionSmartSets.slowMovingIds) }
                else -> transactionScopedStock
            }
        }

        StockWarehouseScreenContent(
            modifier = Modifier.weight(1f),
            searchQuery = searchTextFieldContent.value.text,
            // Typed input shows candidates until tapped or submitted. Scanner completion
            // is owned by the HID/camera handler, never by a partial search projection.
            disableIfOutOfStock = context.transactionTypeIndex == 0,
            showStockType = false,
            showBatches = false,
            transactionTypeIndex = context.transactionTypeIndex,
            stockItemsOverride = transactionSelectionStockItems,
            preferredOrderIds = selectedPreferredOrderIds,
            scrollStateHost = context.stateHost,
            scrollStateKey = "transaction_selection_scroll_${context.transactionTypeIndex}_${context.clientId}_${selectedTransactionFilterId}" +
                if (transactionSearchAcrossAllStock) "_search" else "",
            onFilter = null,
            onClick = addToCartAction
        )
    }
}

//@Composable
//fun AppConfiguration.TransactionSelectionScreen() {
//  Column(
//    modifier = Modifier
//      .fillMaxSize()
//  ) {
//    val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
//      is NavigationScreenModel.Transaction.MainSale -> {
//        0
//      }
//
//      is NavigationScreenModel.Transaction.MainReturn -> {
//        1
//      }
//
//      else -> {
//        2
//      }
//    }
//
//    val clientId = when (stateValues.navigationScreensMain.last()) {
//      is NavigationScreenModel.Transaction.MainReturn -> {
//        stateValues.navigationTransactionReturnClientId
//      }
//
//      is NavigationScreenModel.Transaction.MainSupply -> {
//        stateValues.navigationTransactionSupplyClientId
//      }
//
//      else -> {
//        stateValues.navigationTransactionSaleClientId
//      }
//    }
//
//    ScreenAppBarWidget(
//      title = stateValues.stringSelect,
//      onBack = if (!Navigation.TransactionSale.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)) {
//        {
//          coroutineScope.launch {
//            Navigation.Menu.pop(stateValues.isNarrowScreen)
//          }
//        }
//      } else null
//    )
//
//    var searchTextFieldFocused by rememberSaveable {
//      mutableStateOf(true)
//    }
//
//    val searchTextFieldContent =
//      searchTextField(
//        stateHost = when (transactionTypeIndex) {
//          0 -> NavigationScreenModel.Transaction.MainSale
//          1 -> NavigationScreenModel.Transaction.MainReturn
//          else -> NavigationScreenModel.Transaction.MainSupply
//        },
//        stateKey = NavigationScreenModel.KEY_STATE_SEARCH_QUERY,
//        isFocusedInitial = searchTextFieldFocused,
//        forceRefocus = true,
//        modifier = Modifier
//          .padding(stateValues.marginTextField),
//        barcodeCamScanner = true
//      )
//
//    val scopeRowContent = tabRowWidget(
//      modifier = Modifier
//        .padding(horizontal = stateValues.marginTextField),
//      tabs = listOf(
//        TabContent("0", stateValues.stringAll),
//        TabContent("1", stateValues.stringQuick)
//      )
//    )
//
//    val addToCartAction: (GoodsItemDataModel) -> Unit = {
//      upsertCart(
//        id = it.id,
//        transactionTypeIndex,
//        clientId,
//        QuantityDataModel("", immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")), roundTotal = true) // TODO it.quantity.copy(total = it.quantity.total + it.quantity.pricedAmount)
//      )
//    }
//
//    when (scopeRowContent.id) {
//      "0" -> {
//        StockWarehouseScreenContent(
//          modifier = Modifier
//            .weight(1f),
//          searchTextFieldContent.value.text,
//          onExactSearchHit = addToCartAction,
//          disableIfOutOfStock = true,
//          onClick = addToCartAction,
//          showStockType = false
//        )
//      }
//
//      "1" -> {
//        StockWarehouseScreenContent(
//          modifier = Modifier
//            .weight(1f),
//          searchTextFieldContent.value.text,
//          onExactSearchHit = addToCartAction,
//          disableIfOutOfStock = true,
//          showStockType = false,
//          onFilter = {
//            it.isQuickItem
//          },
//          onClick = addToCartAction
//        )
//      }
//    }
//  }
//}


@Composable
internal fun AppConfiguration.TransactionPaneContent(
    model: NavigationScreenModel.Transaction,
    modifier: Modifier = Modifier,
    motionTarget: AitaSceneMotionTarget,
    onBarcodeCaptureFocusRequested: () -> Unit
) {
    Box(modifier = modifier.clipToBounds().aitaSceneMotion(motionTarget)) {
        when (model) {
            is NavigationScreenModel.Transaction.Cart -> TransactionCartScreen()
            is NavigationScreenModel.Transaction.Selection -> TransactionSelectionScreen(onBarcodeCaptureFocusRequested = onBarcodeCaptureFocusRequested)
            is NavigationScreenModel.Transaction.ReturnBatches -> ReturnBatchSelectionScreen()
            is NavigationScreenModel.Transaction.Payment -> TransactionPaymentScreen()
            is NavigationScreenModel.Transaction.ReceiptPreview -> TransactionReceiptPreviewScreen()
            else -> {}
        }
    }
}

internal suspend fun resetTransactionCartNavigationState(transactionTypeIndex: Int, clientId: Int) {
    Navigation.transactionWorkspace(transactionTypeIndex).resetCart(clientId)
}

@Composable
fun AppConfiguration.TransactionScreen() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (stateValues.activeStoreId == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MessageText(
                    text = stateValues.stringNoActiveStore,
                    textSize = stateValues.titleTextSize
                )

                actionButton(
                    autoLoading = false,
                    text = stateValues.stringSelectInMenu
                ) {
                    coroutineScope.launch {
                        Navigation.Menu.go(NavigationScreenModel.Menu.Stores)
                        Navigation.goMain(NavigationScreenModel.Menu.Main)
                    }
                }
            }
        } else {
            val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
                is NavigationScreenModel.Transaction.MainSale -> {
                    0
                }
                is NavigationScreenModel.Transaction.MainReturn -> {
                    1
                }
                else -> {
                    2
                }
            }

            val clientId = when(transactionTypeIndex) {
                0 -> {
                    stateValues.navigationTransactionSaleClientId
                }
                1 -> {
                    stateValues.navigationTransactionReturnClientId
                }
                else -> {
                    stateValues.navigationTransactionSupplyClientId
                }
            }

            val goodsInCart by getCartState(transactionTypeIndex, clientId).collectAsState()
            val cancelIconRes by stateValues.drawableResIconCancel.collectAsState()
            val cartBookState by DynamicCarts.state.collectAsState()
            val currentCartOwner = cartBookState.owner?.takeIf { cartBookState.ready && DynamicCarts.isCurrent(it) }
            var clearCartClientIdToConfirm by remember(currentCartOwner, transactionTypeIndex) { mutableStateOf<Int?>(null) }

            clearCartClientIdToConfirm?.let { targetClientId ->
                val targetCart by getCartState(transactionTypeIndex, targetClientId).collectAsState()
                val removing = targetClientId >= INITIAL_CART_SLOTS
                if (!cartBookState.book.contains(transactionTypeIndex, targetClientId) || (targetCart.isEmpty() && !removing)) {
                    LaunchedEffect(targetClientId) { clearCartClientIdToConfirm = null }
                } else {
                    ModalDialogWidget(
                        title = if (removing) checkoutText("remove_cart") else localizedStringResource(1176, "Clear cart?"),
                        subTitle = if (removing) checkoutText("remove_cart_details") else localizedStringResource(1177, "This will remove all items and reset this cart's payment and receipt navigation."),
                        negativeButtonText = stateValues.stringCancel,
                        positiveButtonText = if (removing) stateValues.stringDelete else stateValues.stringClear,
                        positiveAction = {
                            coroutineScope.launch {
                                val owner = currentCartOwner ?: return@launch
                                if (!DynamicCarts.isCurrent(owner)) return@launch
                                val cleared = try {
                                    if (removing) DynamicCarts.remove(transactionTypeIndex, targetClientId, owner)
                                    else DynamicCarts.delete(transactionTypeIndex, targetClientId, owner)
                                }
                                    catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                                    catch (_: Exception) {
                                        postInAppNotification(checkoutText("save_error"), NotificationType.Negative, transient = true)
                                        false
                                    }
                                if (!cleared || !DynamicCarts.isCurrent(owner)) return@launch
                                if (removing) Navigation.transactionWorkspace(transactionTypeIndex)
                                    .retainSlots(DynamicCarts.state.value.book.activeSlots(transactionTypeIndex))
                                else resetTransactionCartNavigationState(transactionTypeIndex, targetClientId)
                                latestTransactionReceiptSnapshotState.value
                                    ?.takeIf { snapshot ->
                                        snapshot.paymentDraft.transactionTypeIndex == transactionTypeIndex &&
                                                snapshot.paymentDraft.clientId == targetClientId
                                    }
                                    ?.let { latestTransactionReceiptSnapshotState.emit(null) }
                                clearCartClientIdToConfirm = null
                                postInAppNotification(
                                    if (removing) checkoutText("cart_removed") else localizedStringResource(1178, "Cart cleared"),
                                    NotificationType.Positive,
                                    transient = true
                                )
                            }
                        },
                        negativeAction = { clearCartClientIdToConfirm = null },
                        onDismiss = { clearCartClientIdToConfirm = null }
                    )
                }
            }

            val quickAddRequest by quickStockAddSheetRequestState.collectAsState()
            val scopedQuickAddRequest = quickAddRequest?.takeIf { request ->
                request.transactionTypeIndex == transactionTypeIndex && request.clientId == clientId
            }

            LaunchedEffect(transactionTypeIndex, clientId, quickAddRequest) {
                val request = quickAddRequest
                if (request != null && (request.transactionTypeIndex != transactionTypeIndex || request.clientId != clientId)) {
                    quickStockAddSheetRequestState.emit(null)
                }
            }

            scopedQuickAddRequest?.let { request ->
                QuickStockAddBottomSheet(
                    request = request,
                    onDismiss = { closeQuickStockAddSheet() }
                )
            }

            val supplySupplierIds by getTransactionSupplySupplierIdsState().collectAsState()
            val currentSupplySupplierId = supplySupplierIds[transactionSupplySupplierKey(transactionTypeIndex, clientId)]
            var supplierSheetOpen by remember(currentCartOwner, transactionTypeIndex, clientId) { mutableStateOf(false) }
            if (supplierSheetOpen && transactionTypeIndex == 2) {
                SupplierPickerBottomSheet(
                    title = localizedStringResource(640, "Select supplier for supply"),
                    selectedSupplierId = currentSupplySupplierId,
                    onDismiss = {
                        supplierSheetOpen = false
                    },
                    onSupplierSelected = selected@{ supplier ->
                        if (currentCartOwner == null || !DynamicCarts.isCurrent(currentCartOwner)) return@selected
                        setTransactionSupplySupplierId(transactionTypeIndex, clientId, supplier.id)
                        supplierSheetOpen = false
                        postInAppNotification(
                            "${localizedStringResource(641, "Supplier selected")}: ${supplier.visibleSupplierName(stateValues.appLanguage)}",
                            NotificationType.Positive,
                            transient = true
                        )
                    }
                )
            }

//      LaunchedEffect(goodsInCart) {
//        if (goodsInCart.isEmpty())
//          coroutineScope.launch {
//            when (transactionTypeIndex) {
//              0 -> Navigation.TransactionSale.clear()
//              1 -> Navigation.TransactionReturn.clear()
//              2 -> Navigation.TransactionSupply.clear()
//            }
//          }
//      }

            val latestReceiptSnapshot by latestTransactionReceiptSnapshotState.collectAsState()
            val cartPersistenceHydrated by cartPersistenceHydratedState.collectAsState()
            val cartNavigationReady by transactionNavigationRestoredState.collectAsState()

            LaunchedEffect(currentCartOwner, transactionTypeIndex, clientId, cartPersistenceHydrated, cartNavigationReady, goodsInCart, latestReceiptSnapshot) {
                if (!cartPersistenceHydrated || !cartNavigationReady || currentCartOwner == null || !DynamicCarts.isCurrent(currentCartOwner)) return@LaunchedEffect

                val currentScreens = Navigation
                    .getCurrentTransactionScreens(transactionTypeIndex, clientId, stateValues.isNarrowScreen)
                    .value

                val showingReceipt = currentScreens.lastOrNull() is NavigationScreenModel.Transaction.ReceiptPreview

                val receiptBelongsHere =
                    latestReceiptSnapshot?.paymentDraft?.transactionTypeIndex == transactionTypeIndex &&
                            latestReceiptSnapshot?.paymentDraft?.clientId == clientId

                // An empty cart must still be allowed to show the item picker on a phone.
                val showingPayment = currentScreens.lastOrNull() is NavigationScreenModel.Transaction.Payment
                if (goodsInCart.isEmpty() && (showingPayment || (showingReceipt && !receiptBelongsHere))) {
                    Navigation.transactionWorkspace(transactionTypeIndex).clearSlot(clientId, stateValues.isNarrowScreen)
                }
            }

            val navigationScreensLeft by Navigation.getCurrentTransactionScreens(transactionTypeIndex, clientId, true).collectAsState()
            val navigationScreensRight by Navigation.getCurrentTransactionScreens(transactionTypeIndex, clientId, false).collectAsState()

            TransactionCartTabsRow(
                type = transactionTypeIndex, selected = clientId,
                supplierId = currentSupplySupplierId, onSupplier = { supplierSheetOpen = true },
                onClearSupplier = {
                    if (currentCartOwner != null && DynamicCarts.isCurrent(currentCartOwner))
                        clearTransactionSupplySupplierId(transactionTypeIndex, clientId)
                },
                onClear = { clearCartClientIdToConfirm = it }
            )

            val leftTransactionPaneModel =
                navigationScreensLeft.lastOrNull() as? NavigationScreenModel.Transaction
                    ?: NavigationScreenModel.Transaction.Selection
            val rightTransactionPaneModel =
                navigationScreensRight.lastOrNull() as? NavigationScreenModel.Transaction
                    ?: NavigationScreenModel.Transaction.Cart

            // The selection pane can remain visible beside payment: do not scan into that cart.
            val visiblePaneModels = if (stateValues.isNarrowScreen) listOf(leftTransactionPaneModel)
                else listOf(leftTransactionPaneModel, rightTransactionPaneModel)
            TransactionBarcodeHidInput(
                transactionTypeIndex = transactionTypeIndex,
                clientId = clientId,
                currentCart = goodsInCart,
                captureEnabled = cartPersistenceHydrated && cartNavigationReady && visiblePaneModels.all {
                    it is NavigationScreenModel.Transaction.Selection || it is NavigationScreenModel.Transaction.Cart
                }
            )

            fun paneMotionTarget(stack: List<NavigationScreenModel>) = AitaSceneMotionTarget(
                family = "transaction:$transactionTypeIndex",
                selection = "$clientId",
                stack = stack.map { it.route },
                selectionIndex = clientId
            )

            if (stateValues.isNarrowScreen) {
                TransactionPaneContent(
                    modifier = Modifier.weight(1f),
                    model = leftTransactionPaneModel,
                    motionTarget = paneMotionTarget(navigationScreensLeft),
                    onBarcodeCaptureFocusRequested = { requestTransactionBarcodeFocus() }
                )
            } else {
                Row(
                    modifier = Modifier.weight(1f)
                ) {
                    TransactionPaneContent(
                        modifier = Modifier.weight(1f),
                        model = leftTransactionPaneModel,
                        motionTarget = paneMotionTarget(navigationScreensLeft),
                        onBarcodeCaptureFocusRequested = { requestTransactionBarcodeFocus() }
                    )

                    TransactionPaneContent(
                        modifier = Modifier.weight(1f),
                        model = rightTransactionPaneModel,
                        motionTarget = paneMotionTarget(navigationScreensRight),
                        onBarcodeCaptureFocusRequested = { requestTransactionBarcodeFocus() }
                    )
                }
            }
        }
    }
}


@Composable
internal fun ReceiptPreviewDivider() {
    Spacer(
        modifier = Modifier
            .height(1.dp)
            .fillMaxWidth()
            .background(Color.Black)
    )
}

internal fun AppConfiguration.receiptLabels(): ReceiptTextLabelsDataModel {
    return ReceiptTextLabelsDataModel(
        store = stateValues.stringStore,
        goodsReceiptTitle = stateValues.stringGoodsReceiptTitle,
        receipt = stateValues.stringReceiptNumber,
        transactionId = stateValues.stringTransactionId,
        draft = stateValues.stringDraft,
        date = stateValues.stringDate,
        cashier = stateValues.stringCashier,
        phone = stateValues.stringPhone,
        email = stateValues.stringEmail,
        barcode = stateValues.stringBarcode,
        noName = stateValues.stringNoName,
        noItems = stateValues.stringNoItems,
        total = stateValues.stringTotal,
        cash = stateValues.stringCash,
        cashless = stateValues.stringCashless,
        debt = stateValues.stringDebt,
        debtor = stateValues.stringDebtor,
        debtorPhone = stateValues.stringDebtorPhone,
        change = stateValues.stringChange,
        vat = stateValues.stringVat,
        vatNotSpecified = stateValues.stringVatNotSpecified,
        fiscalStatus = stateValues.stringFiscalStatus,
        nonFiscalSoftwareReceipt = stateValues.stringNonFiscalSoftwareReceipt,
        thankYou = stateValues.stringThankYou,
        saleReceiptTitle = stateValues.stringSaleReceiptTitle,
        returnReceiptTitle = stateValues.stringReturnReceiptTitle,
        supplyReceiptTitle = stateValues.stringSupplyReceiptTitle,
        returnReason = localizedStringResource(1308, "Return reason"),
        pdfExportNotConfigured = stateValues.stringReceiptActionFailed,
        pdfSharingNotConfigured = stateValues.stringReceiptActionFailed,
        printerNotConfigured = stateValues.stringReceiptActionFailed
    )
}

@Composable
internal fun AppConfiguration.ReceiptPreviewText(
    text: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    center: Boolean = false,
    large: Boolean = false,
    color: Color = Color.Black
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = color,
        fontSize = if (large) stateValues.accentTextSize else stateValues.textSize,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = if (center) TextAlign.Center else TextAlign.Start
    )
}

@Composable
internal fun AppConfiguration.ReceiptPreviewRow(
    title: String,
    value: String,
    bold: Boolean = false,
    large: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            color = Color.Black,
            fontSize = if (large) stateValues.accentTextSize else stateValues.textSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            color = Color.Black,
            fontSize = if (large) stateValues.accentTextSize else stateValues.textSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun AppConfiguration.ReceiptPreviewHeader(
    snapshot: TransactionReceiptSnapshotDataModel,
    labels: ReceiptTextLabelsDataModel
) {
    val store = snapshot.store
    val companyFormTitle = store
        ?.companyForms
        ?.firstOrNull()
        ?.name
        ?.visibleLocalizedString(stateValues.appLanguage, "")
        .orEmpty()
    val storeName = store?.name?.visibleLocalizedString(stateValues.appLanguage, labels.store) ?: labels.store

    ReceiptPreviewText(
        text = "$companyFormTitle $storeName".trim(),
        bold = true,
        center = true,
        large = true
    )

    store?.location?.name?.takeIf { it.isNotBlank() }?.let {
        Spacer(modifier = Modifier.height(3.dp))
        ReceiptPreviewText(text = it, center = true, bold = true, large = true)
    }

    store?.phoneNumbers?.asDisplayPhoneNumbers()?.takeIf { it.isNotEmpty() }?.let {
        Spacer(modifier = Modifier.height(3.dp))
        ReceiptPreviewText(text = "${labels.phone}: ${it.joinToString()}", center = true)
    }

    store?.emails?.takeIf { it.isNotEmpty() }?.let {
        Spacer(modifier = Modifier.height(3.dp))
        ReceiptPreviewText(text = "${labels.email}: ${it.joinToString()}", center = true)
    }

    Spacer(modifier = Modifier.height(8.dp))
    ReceiptPreviewDivider()
    Spacer(modifier = Modifier.height(8.dp))

    ReceiptPreviewText(
        text = labels.goodsReceiptTitle,
        bold = true,
        large = true,
        center = true
    )

    ReceiptPreviewText(
        text = snapshot.receiptTitle(labels),
        bold = true,
        center = true
    )

    Spacer(modifier = Modifier.height(8.dp))

    snapshot.transaction.serverReceiptIdOrNull()?.let { serverId ->
        ReceiptPreviewRow(labels.receipt, snapshot.receiptNumberText(labels), bold = true)
        ReceiptPreviewRow(labels.transactionId, serverId)
    }
    ReceiptPreviewRow(labels.date, receiptUiDateTime(snapshot.transaction.timeMillis))

    snapshot.cashierName.takeIf { it.isNotBlank() }?.let {
        ReceiptPreviewRow(labels.cashier, it)
    }
    snapshot.cashierPhoneNumber.asDisplayPhoneNumber().takeIf { it.isNotBlank() }?.let {
        ReceiptPreviewRow(labels.phone, it)
    }

    Spacer(modifier = Modifier.height(8.dp))
    ReceiptPreviewDivider()
    Spacer(modifier = Modifier.height(8.dp))
}

internal fun receiptUiDateTime(timeMillis: Long): String {
    return runCatching {
        val dt = Instant.fromEpochMilliseconds(timeMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        "${dt.dayOfMonth.toString().padStart(2, '0')}.${dt.monthNumber.toString().padStart(2, '0')}.${dt.year} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}:${dt.second.toString().padStart(2, '0')}"
    }.getOrElse { timeMillis.toString() }
}

@Composable
internal fun AppConfiguration.ReceiptPreviewLine(
    line: TransactionReceiptLineDataModel,
    labels: ReceiptTextLabelsDataModel
) {
    val name = line.name.visibleLocalizedString(stateValues.appLanguage, labels.noName)
    val quantityText = line.quantity.quantityText(stateValues.appLanguage)

    ReceiptPreviewText(
        text = "${line.index + 1}. $name",
        bold = true
    )

    line.barcode.takeIf { it.isNotBlank() }?.let {
        ReceiptPreviewText(
            text = "${labels.barcode}: $it",
            color = stateValues.TextColor
        )
    }

    if (line.saleMethodId == SALE_METHOD_WHOLESALE) {
        ReceiptPreviewText(
            text = line.saleMethodName.visibleLocalizedString(stateValues.appLanguage, localizedStringResource(245, "Wholesale")),
            color = stateValues.TextColor
        )
    }

    line.returnReason.takeIf { it.isNotBlank() }?.let { reason ->
        ReceiptPreviewText(
            text = "${labels.returnReason}: $reason",
            color = stateValues.TextColor
        )
    }

    ReceiptPreviewRow(
        title = "$quantityText x ${line.pricePerUnit.moneyText()} ${line.currencySymbol}".trim(),
        value = "${line.total.moneyText()} ${line.currencySymbol}",
        bold = true
    )

    Spacer(modifier = Modifier.height(7.dp))
}

@Composable
internal fun AppConfiguration.ReceiptPreviewTotals(
    snapshot: TransactionReceiptSnapshotDataModel,
    labels: ReceiptTextLabelsDataModel
) {
    val total = snapshot.totalAmount()
    val debt = snapshot.debtAmount()
    val change = snapshot.changeAmount()

    Spacer(modifier = Modifier.height(6.dp))
    ReceiptPreviewDivider()
    Spacer(modifier = Modifier.height(8.dp))

    ReceiptPreviewRow(
        title = labels.total.uppercase(),
        value = "${total.moneyText()} ${snapshot.currencySymbol}",
        bold = true,
        large = true
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (snapshot.paymentDraft.paidCash > 0.0) {
        ReceiptPreviewRow(
            title = labels.cash,
            value = "${snapshot.paymentDraft.paidCash.moneyText()} ${snapshot.currencySymbol}"
        )
    }

    if (snapshot.paymentDraft.paidCard > 0.0) {
        ReceiptPreviewRow(
            title = labels.cashless,
            value = "${snapshot.paymentDraft.paidCard.moneyText()} ${snapshot.currencySymbol}"
        )
    }

    if (debt > 0.0) {
        ReceiptPreviewRow(
            title = labels.debt,
            value = "${debt.moneyText()} ${snapshot.currencySymbol}"
        )

        snapshot.paymentDraft.debtor?.let { debtor ->
            ReceiptPreviewRow(labels.debtor, debtorDisplayName(debtor))
            ReceiptPreviewRow(localizedStringResource(309, "Type"), if (debtor.debtorType == "company") localizedStringResource(290, "Company") else localizedStringResource(289, "Individual"))
            debtor.idNumber.takeIf { it.isNotBlank() }?.let { ReceiptPreviewRow(localizedStringResource(293, "ID number"), it) }
            debtor.companyIdNumber.takeIf { it.isNotBlank() }?.let { ReceiptPreviewRow(localizedStringResource(292, "Company ID / BIN"), it) }
            debtor.phoneNumber.asDisplayPhoneNumber().takeIf { it.isNotBlank() }?.let {
                ReceiptPreviewRow(labels.debtorPhone, it)
            }
            debtor.debtDueAtMillis?.toStockDateInputText()?.let { ReceiptPreviewRow(localizedStringResource(366, "Debt due"), it) }
            debtor.interest?.takeIf { it.enabled && it.ratePercent > 0.0 }?.let {
                ReceiptPreviewRow(localizedStringResource(322, "Interest"), "${it.ratePercent}% / ${it.periodUnit}")
            }
            debtor.plannedPayments.takeIf { it.isNotEmpty() }?.let { plans ->
                plans.forEachIndexed { index, plan ->
                    ReceiptPreviewRow(
                        "${localizedStringResource(324, "Plan")} ${index + 1}",
                        "${moneyInputFromDouble(plan.amount)} ${debtor.currency} • ${plan.dueAtMillis.toStockDateInputText().ifBlank { localizedStringResource(318, "No date") }}"
                    )
                }
            }
        }
    }

    if (change > 0.0) {
        ReceiptPreviewRow(
            title = labels.change,
            value = "${change.moneyText()} ${snapshot.currencySymbol}"
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    ReceiptPreviewDivider()
    Spacer(modifier = Modifier.height(8.dp))

    ReceiptPreviewRow(labels.vat, labels.vatNotSpecified)
    ReceiptPreviewRow(labels.fiscalStatus, labels.nonFiscalSoftwareReceipt)

    Spacer(modifier = Modifier.height(10.dp))

    ReceiptPreviewText(
        text = labels.thankYou,
        center = true,
        bold = true
    )
    ReceiptTransactionBarcodeFooter(snapshot)
}

internal fun AppConfiguration.receiptActionNotification(
    result: ReceiptPlatformActionResult,
    positiveMessage: String,
    owner: ReceiptActionOwner = captureReceiptActionOwner()
) {
    if (!owner.isCurrent()) return
    val file = result.savedFile?.takeIf { result.success }
    val message = when {
        file != null -> "$positiveMessage\n${localizedStringResource(2601, "Folder")}: ${file.folder}\n${localizedStringResource(2602, "File")}: ${file.fileName}"
        result.success -> positiveMessage
        else -> result.message.ifBlank { stateValues.stringReceiptActionFailed }
    }
    val type = if (result.success) NotificationType.Positive else NotificationType.Negative
    val messageReference = if (file != null) {
        val resultReference = currentEventResourceCatalogue().referenceFor(positiveMessage)
            ?: legacyEventMessageReference(positiveMessage) ?: eventFact(positiveMessage)
        EventMessageReference("device.file.location",
            arguments = mapOf("folder" to file.folder, "file" to file.fileName),
            children = mapOf("result" to listOf(resultReference)))
    } else null
    // Native error strings can contain device paths too; keep the whole result local.
    coroutineScope.launch { postDeviceFileNotification(message, file, owner, type, messageReference) }
}

internal fun AppConfiguration.buildTransactionReceiptLines(
    cart: List<GoodsItemInCartDataModel>,
    stock: List<GoodsItemDataModel>,
    stockBatches: List<GoodsBatchDataModel>,
    transactionTypeIndex: Int,
    saleMethodIds: Map<String, String> = emptyMap(),
    returnReasons: Map<String, String> = emptyMap(),
    returnBatchSelections: Map<String, CartReturnBatchSelectionDataModel> = emptyMap(),
    clientId: Int = 0
): List<TransactionReceiptLineDataModel> {
    if (cart.isEmpty()) return emptyList()
    val stockById = stock.associateBy { it.id }
    val batchesByGoodsItemId = stockBatches
        .filter { it.isActive }
        .groupBy { it.goodsItemId }

    return cart.mapIndexedNotNull { index, cartItem ->
        val goodsItem = stockById[cartItem.id] ?: return@mapIndexedNotNull null
        val saleMethodId = saleMethodIds["${transactionTypeIndex}:$clientId:${goodsItem.id}"]
            ?: SALE_METHOD_RETAIL
        val itemBatches = batchesByGoodsItemId[goodsItem.id].orEmpty()
        val returnSelection = returnBatchSelections[cartReturnBatchSelectionKey(transactionTypeIndex, clientId, goodsItem.id)]
        val resolvedReturn = if (transactionTypeIndex == 1) {
            resolveReturnBatchSelection(goodsItem, cartItem, itemBatches, returnSelection)
        } else {
            null
        }
        val batch = resolvedReturn?.batch
            ?: itemBatches.sortedForShelf(goodsItem).firstOrNull { it.id == goodsItem.activeShelfBatchId }
            ?: itemBatches.sortedForShelf(goodsItem).firstOrNull()
        val price = resolvedReturn?.price ?: goodsItem.priceForTransaction(
            transactionTypeIndex = transactionTypeIndex,
            saleMethodId = saleMethodId,
            quantityTotal = cartItem.quantity.total,
            batch = batch
        ).withFallbackCurrency(defaultTransactionCurrencyCode())
        val currencySymbol = stateValues.globalAppConfiguration.countries
            .getCurrency(price.currency)
            ?.symbol
            ?: price.currency

        TransactionReceiptLineDataModel(
            index = index,
            goodsItemId = goodsItem.id,
            name = goodsItem.name,
            barcode = goodsItem.firstBarcode(),
            quantity = cartItem.quantity,
            pricePerUnit = price.price.toMoneyDouble(),
            currencyCode = price.currency,
            currencySymbol = currencySymbol,
            saleMethodId = saleMethodId,
            saleMethodName = saleMethodLocalizedName(saleMethodId),
            returnReason = if (transactionTypeIndex == 1) {
                returnReasons[cartReturnReasonKey(transactionTypeIndex, clientId, goodsItem.id)].orEmpty()
            } else {
                ""
            },
            stockBatchId = if (transactionTypeIndex == 1) resolvedReturn?.selection?.stockBatchId else null,
            originalTransactionId = returnSelection?.originalTransactionId,
            originalTransactionLineIndex = returnSelection?.originalTransactionLineIndex,
            originalClientOperationId = returnSelection?.originalClientOperationId,
            returnDestinationKind = returnSelection?.returnDestinationKind,
            sourceBatchAllocations = returnSelection?.sourceBatchAllocations.orEmpty(),
            shelfBatchIdAtSale = returnSelection?.shelfBatchIdAtSale
        )
    }
}

internal fun Double.moneyText(): String {
    val fixed = roundMoney()
    val whole = fixed.toLong()
    val cents = kotlin.math.round((fixed - whole) * 100).toInt()
    return "$whole.${cents.toString().padStart(2, '0')}"
}

internal fun Double.quantityAmountText(roundTotal: Boolean, maxFractionDigits: Int = 3): String {
    if (roundTotal)
        return toInt().toString()

    val safeFractionDigits = maxFractionDigits.coerceAtLeast(0)
    val multiplier = List(safeFractionDigits) { 10.0 }
        .fold(1.0) { acc, item -> acc * item }
    val scaled = kotlin.math.round(coerceAtLeast(0.0) * multiplier).toLong()
    val whole = (scaled / multiplier.toLong()).toString()

    if (safeFractionDigits <= 0)
        return whole

    val fraction = (scaled % multiplier.toLong()).toString().padStart(safeFractionDigits, '0')

    return "$whole.$fraction"
        .trimEnd('0')
        .trimEnd('.')
        .ifBlank { "0" }
}

internal fun QuantityDataModel.quantityText(language: String): String {
    val unit = immutableUnitName.extractLocalizedString(language).orEmpty()
    return "${total.quantityAmountText(roundTotal)} $unit".trim()
}

internal fun QuantityDataModel.isPieceQuantityUnit(): Boolean {
    if (id == "0") return true

    return immutableUnitName.any { localized ->
        val value = localized.value.trim().lowercase()
        value == "pc" ||
                value == "pcs" ||
                value == "pc." ||
                value == "pcs." ||
                value == "piece" ||
                value == "pieces" ||
                value == "шт" ||
                value == "шт." ||
                value == "штука" ||
                value == "штук" ||
                value == "дана"
    }
}

internal fun QuantityDataModel.allowsFractionalStockQuantityInput(): Boolean =
    !isPieceQuantityUnit() && (!roundTotal || isWeightQuantityUnit())

internal fun sanitizeStockQuantityInput(raw: String, allowFraction: Boolean): String {
    val normalized = raw.replace(',', '.')

    if (!allowFraction) {
        return normalized.substringBefore('.').filter { it.isDigit() }
    }

    var dotUsed = false
    return buildString {
        normalized.forEach { char ->
            when {
                char.isDigit() -> append(char)
                char == '.' && !dotUsed -> {
                    append(char)
                    dotUsed = true
                }
            }
        }
    }
}

internal fun String.isStockQuantityInputText(allowFraction: Boolean): Boolean {
    if (isEmpty()) return true
    return if (allowFraction) {
        isNumericalDoubleString()
    } else {
        isNumericalString()
    }
}

internal fun stockQuantityInputTextFromAmount(amount: Double, unit: QuantityDataModel): String {
    if (!unit.allowsFractionalStockQuantityInput()) {
        return amount.coerceAtLeast(0.0).toInt().toString()
    }

    val scaled = round(amount.coerceAtLeast(0.0) * 1000.0).toLong()
    val whole = scaled / 1000L
    val fraction = (scaled % 1000L)
        .toString()
        .padStart(3, '0')
        .trimEnd('0')
        .ifBlank { "0" }

    return "$whole.$fraction"
}

internal fun parseStockQuantityInputText(raw: String, unit: QuantityDataModel): Double? {
    val allowFraction = unit.allowsFractionalStockQuantityInput()
    val sanitized = sanitizeStockQuantityInput(raw, allowFraction)
    val parsed = sanitized.toDoubleOrNull() ?: return null
    return if (allowFraction) parsed else parsed.toInt().coerceAtLeast(0).toDouble()
}

internal fun QuantityDataModel.withStockQuantityInputTotalValue(total: Double): QuantityDataModel {
    return if (allowsFractionalStockQuantityInput()) {
        copy(
            total = round(total.coerceAtLeast(0.0) * 1000.0) / 1000.0,
            roundTotal = false
        )
    } else {
        copy(total = total.coerceAtLeast(0.0).toInt().toDouble())
    }
}

internal fun stockQuantityShortcutAmounts(unit: QuantityDataModel): List<Double> {
    return if (unit.allowsFractionalStockQuantityInput()) {
        listOf(5.0, 10.0, 25.0, 50.0, 100.0)
    } else {
        listOf(10.0, 30.0, 50.0, 100.0, 500.0, 1000.0)
    }
}

@Composable
internal fun AppConfiguration.StockQuantityQuickFillButtons(
    quantityUnit: QuantityDataModel,
    currentText: String,
    modifier: Modifier = Modifier,
    onAmountSelected: (String) -> Unit
) {
    val currentAmount = parseStockQuantityInputText(currentText, quantityUnit)
    val unitText = quantityUnit.immutableUnitName.extractLocalizedString(stateValues.appLanguage).orEmpty()
    val amounts = stockQuantityShortcutAmounts(quantityUnit)
        .filterNot { amount ->
            currentAmount != null && abs(currentAmount - amount) < 0.000001
        }

    if (amounts.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(amounts) { amount ->
            Box(
                modifier = Modifier
                    .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .border(
                        stateValues.unfocusedBorderWidth,
                        stateValues.AccentColor,
                        RoundedCornerShape(stateValues.cornerRadius)
                    )
                    .background(stateValues.BackgroundColor)
                    .aitaClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = stateValues.AccentColor)
                    ) {
                        onAmountSelected(stockQuantityInputTextFromAmount(amount, quantityUnit))
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = listOf(
                        stockQuantityInputTextFromAmount(amount, quantityUnit),
                        unitText
                    ).filter { it.isNotBlank() }.joinToString(" "),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = accentTextShadow(stateValues.AccentColor, stateValues.AccentColor)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


@Composable
fun AppConfiguration.TransactionReceiptPreviewScreen() {
    val context = rememberTransactionContext()
    val receiptScope = rememberCoroutineScope()
    var activeReceiptAction by remember(stateValues.userAccount?.id, context.transactionTypeIndex, context.clientId) {
        mutableStateOf<String?>(null)
    }
    val receiptLanguage = stateValues.appLanguage
    val draftCreatedAt = remember(stateValues.userAccount?.id, stateValues.activeStoreId, context.transactionTypeIndex, context.clientId) {
        getCurrentTimeMillis()
    }
    val darkActionIcons = isDarkAppTheme(stateValues.appThemeId)
    val completeReceiptIconPath = if (darkActionIcons) "svg/125_1.svg" else "svg/125_0.svg"
    val completeReceiptIconRes = if (darkActionIcons) Res.drawable._125_1 else Res.drawable._125_0

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringReceipt,
                iconPath = stateValues.drawablePathIconReceipt,
                onBack = {
                    coroutineScope.launch {
                        when (context.transactionTypeIndex) {
                            0 -> Navigation.TransactionSale.pop()
                            1 -> Navigation.TransactionReturn.pop()
                            else -> Navigation.TransactionSupply.pop()
                        }
                    }
                }
            )
        }
    ) {
        val goodsInCart by getCartState(
            context.transactionTypeIndex,
            context.clientId
        ).collectAsState()

        val latestSnapshot by latestTransactionReceiptSnapshotState.collectAsState()
        val saleMethodIds by cartSaleMethodIdsState.collectAsState()
        val cartReturnReasons by getCartReturnReasonsState().collectAsState()
        val returnBatchSelections by getCartReturnBatchSelectionsState().collectAsState()
        val paymentDrafts by getTransactionPaymentDraftsState().collectAsState()

        val paymentDraft = paymentDrafts[transactionSupplySupplierKey(context.transactionTypeIndex, context.clientId)]

        val liveLines = remember(goodsInCart, stateValues.stock, stateValues.stockBatches, context.transactionTypeIndex, context.clientId, saleMethodIds, cartReturnReasons, returnBatchSelections) {
            buildTransactionReceiptLines(
                cart = goodsInCart,
                stock = stateValues.stock.orEmpty(),
                stockBatches = stateValues.stockBatches.orEmpty(),
                transactionTypeIndex = context.transactionTypeIndex,
                saleMethodIds = saleMethodIds,
                returnReasons = cartReturnReasons,
                returnBatchSelections = returnBatchSelections,
                clientId = context.clientId
            )
        }

        val store = stateValues.stores.findStoreOrBranchForUi(stateValues.activeStoreId)

        val currencyCode = liveLines.firstOrNull()?.currencyCode
            ?: paymentDraft?.debtor?.currency
            ?: stateValues.globalAppConfiguration.countries.withTajikistanFallback()
                .find { it.locale.equals(stateValues.userAccount?.countryLocale, true) }
                ?.currencies
                ?.firstOrNull()
                ?.code
            ?: "KZT"

        val currencySymbol = liveLines.firstOrNull()?.currencySymbol
            ?: stateValues.globalAppConfiguration.countries.getCurrency(currencyCode)?.symbol
            ?: currencyCode

        val total = liveLines.sumOf { it.total }.roundMoney()
        val supplySupplierIds by getTransactionSupplySupplierIdsState().collectAsState()
        val currentSupplySupplierId = supplySupplierIds[transactionSupplySupplierKey(context.transactionTypeIndex, context.clientId)]

        val invalidWholesaleReceiptItems = remember(goodsInCart, stateValues.stock, saleMethodIds, context.transactionTypeIndex, context.clientId, receiptLanguage) {
            if (context.transactionTypeIndex != 0 || goodsInCart.isEmpty()) {
                emptyList()
            } else {
                val stockById = stateValues.stock.orEmpty().associateBy { it.id }
                goodsInCart.mapNotNull { cartItem ->
                    val selectedMethodId = saleMethodIds["${context.transactionTypeIndex}:${context.clientId}:${cartItem.id}"]
                    val goodsItem = stockById[cartItem.id]
                    if (selectedMethodId == SALE_METHOD_WHOLESALE && goodsItem != null && !goodsItem.isWholesaleEligible(cartItem.quantity.total)) {
                        goodsItem.name.extractLocalizedString(stateValues.appLanguage) ?: goodsItem.firstBarcode().ifBlank { cartItem.id }
                    } else {
                        null
                    }
                }
            }
        }

        val draft = paymentDraft ?: TransactionPaymentDraftDataModel(
            transactionTypeIndex = context.transactionTypeIndex,
            clientId = context.clientId,
            paymentModeId = "1",
            paidCash = 0.0,
            paidCard = total,
            cardPaymentOptionId = 0
        )

        val currentTransaction = TransactionDataModel(
            id = "",
            workshiftId = 0L,
            type = transactionServerType(context.transactionTypeIndex),
            storeId = stateValues.activeStoreId.orEmpty(),
            goodsInTransaction = liveLines.map {
                GoodsItemInTransactionDataModel(
                    barcode = it.barcode,
                    quantity = it.quantity.total,
                    pricePerUnit = it.pricePerUnit,
                    supplierId = null,
                    saleMethodId = it.saleMethodId,
                    supplierIdText = if (context.transactionTypeIndex == 2) currentSupplySupplierId else null,
                    name = it.name,
                    goodsItemId = it.goodsItemId,
                    quantityUnit = it.quantity,
                    currencyCode = it.currencyCode,
                    returnReason = if (context.transactionTypeIndex == 1) it.returnReason.trim() else "",
                    stockBatchId = if (context.transactionTypeIndex == 1) it.stockBatchId else null,
                    originalTransactionId = it.originalTransactionId,
                    originalTransactionLineIndex = it.originalTransactionLineIndex,
                    originalClientOperationId = it.originalClientOperationId,
                    returnDestinationKind = it.returnDestinationKind
                )
            },
            paidCash = draft.paidCash,
            paidCard = draft.paidCard,
            cardPaymentOptionId = draft.cardPaymentOptionId,
            debtor = draft.debtor,
            timeMillis = draftCreatedAt
        )

        val cashierName = "${stateValues.userAccount?.firstName.orEmpty()} ${stateValues.userAccount?.lastName.orEmpty()}".trim()

        val snapshotForScreen =
            latestSnapshot?.takeIf {
                it.transaction.type == transactionServerType(context.transactionTypeIndex) &&
                        it.paymentDraft.clientId == context.clientId &&
                        it.transaction.storeId == stateValues.activeStoreId
            } ?: TransactionReceiptSnapshotDataModel(
                transaction = currentTransaction,
                store = store,
                lines = liveLines,
                paymentDraft = draft,
                currencyCode = currencyCode,
                currencySymbol = currencySymbol,
                cashierName = cashierName,
                cashierPhoneNumber = stateValues.userAccount?.phoneNumber.orEmpty(),
                cashierEmail = stateValues.userAccount?.email.orEmpty()
            )

        val labels = receiptLabels()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
                .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(Color.White)
                .border(
                    stateValues.unfocusedBorderWidth,
                    stateValues.PlaceholderTextColor,
                    RoundedCornerShape(stateValues.cornerRadius)
                )
                .padding(stateValues.marginTextFieldGroup)
        ) {
            item {
                ReceiptPreviewHeader(snapshotForScreen, labels)
            }

            if (snapshotForScreen.lines.isEmpty()) {
                item {
                    ReceiptPreviewText(
                        text = labels.noItems,
                        center = true,
                        bold = true,
                        color = stateValues.TextColor
                    )
                }
            } else {
                items(snapshotForScreen.lines) { line ->
                    ReceiptPreviewLine(line, labels)
                }
            }

            item {
                ReceiptPreviewTotals(snapshotForScreen, labels)
                Spacer(modifier = Modifier.height(stateValues.screenHeight / 7))
            }
        }

        val alreadyCompleted = snapshotForScreen.transaction.id.isNotBlank()
        // No PDF layout/encoding during composition. Generate on demand on a CPU worker,
        // then reuse the same bytes for this immutable receipt and language.
        val pdfCache = remember(snapshotForScreen, receiptLanguage, labels) {
            mutableStateOf<ByteArray?>(null)
        }
        val fileName = remember(snapshotForScreen, labels) {
            snapshotForScreen.receiptPdfFileName(labels)
        }

        fun runReceiptAction(action: String, successMessage: String) {
            if (activeReceiptAction != null) return
            val actionOwner = captureReceiptActionOwner()
            activeReceiptAction = action // reserve synchronously, before launching
            receiptScope.launch {
                try {
                    if (action == "print" && preferHtmlDocumentPrinting) {
                        val html = snapshotForScreen.buildReceiptPdfDocument(receiptLanguage, labels).toPrintHtml(fileName)
                        if (!actionOwner.isCurrent()) return@launch
                        receiptActionNotification(printHtmlDocument(fileName, html), deviceWorkflowText("print_opened"), actionOwner)
                        return@launch
                    }
                    val pdf = if (action == "print" && receiptPrintUsesCurrentPage) byteArrayOf() else pdfCache.value ?: withContext(Dispatchers.Default) {
                        snapshotForScreen.buildReceiptPdfBytes(receiptLanguage, labels)
                    }.also { pdfCache.value = it }
                    if (!actionOwner.isCurrent()) return@launch
                    val result = when (action) {
                        "pdf" -> saveReceiptPdf(fileName, pdf, labels)
                        "share" -> shareReceiptPdf(fileName, pdf, whatsappOnly = false, labels = labels)
                        "whatsapp" -> shareReceiptPdf(fileName, pdf, whatsappOnly = true, labels = labels)
                        else -> {
                            val escPos = withContext(Dispatchers.Default) {
                                snapshotForScreen.buildReceiptEscPosBytes(receiptLanguage, labels)
                            }
                            if (!actionOwner.isCurrent()) return@launch
                            printReceipt(fileName, pdf, escPos, labels)
                        }
                    }
                    receiptActionNotification(result, successMessage, actionOwner)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (actionOwner.isCurrent()) postInAppNotification(stateValues.stringReceiptActionFailed, NotificationType.Negative)
                } finally {
                    activeReceiptAction = null
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (!alreadyCompleted) {
                if (invalidWholesaleReceiptItems.isNotEmpty()) {
                    MessageText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        text = "${localizedStringResource(251, "Not enough for wholesale")}: ${invalidWholesaleReceiptItems.joinToString(", ")}"
                    )
                }

                actionButton(
                    text = stateValues.stringComplete,
                    loading = stateValues.completeTransactionInProgress,
                    loadingText = localizedStringResource(224, "Completing transaction"),
                    enabled = (alreadyCompleted || context.transactionTypeIndex != 1 || returnDestinationsReady(context.clientId)) && snapshotForScreen.lines.isNotEmpty() && !stateValues.completeTransactionInProgress && stateValues.latestNotification == null && invalidWholesaleReceiptItems.isEmpty(),
                    iconPath = completeReceiptIconPath,
                    iconRes = completeReceiptIconRes,
                    iconTintColor = Color.White,
                    iconSizeOverride = 22.dp,
                    onClick = {
                        if (context.transactionTypeIndex == 1 && !returnDestinationsReady(context.clientId)) {
                            coroutineScope.launch { Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.ReturnBatches) }
                            return@actionButton
                        }
                        completeTransaction(
                            transaction = currentTransaction.copy(timeMillis = getCurrentTimeMillis()),
                            transactionTypeIndex = context.transactionTypeIndex,
                            clientId = context.clientId,
                            receiptSnapshot = snapshotForScreen
                        ) {
                            coroutineScope.launch {
                                when (context.transactionTypeIndex) {
                                    0 -> Navigation.TransactionSale.go(
                                        NavigationScreenModel.Transaction.ReceiptPreview,
                                        remove = true
                                    )

                                    1 -> Navigation.TransactionReturn.go(
                                        NavigationScreenModel.Transaction.ReceiptPreview,
                                        remove = true
                                    )

                                    else -> Navigation.TransactionSupply.go(
                                        NavigationScreenModel.Transaction.ReceiptPreview,
                                        remove = true
                                    )
                                }
                            }
                        }
                    }
                )
            } else {
                ReceiptActionToolbar(
                    activeAction = activeReceiptAction,
                    onAction = ::runReceiptAction,
                    onFinish = {
                        coroutineScope.launch {
                            latestTransactionReceiptSnapshotState.emit(null)
                            when (context.transactionTypeIndex) {
                                0 -> Navigation.TransactionSale.clear()
                                1 -> Navigation.TransactionReturn.clear()
                                else -> Navigation.TransactionSupply.clear()
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}


//@Composable
//fun AppConfiguration.TransactionReceiptPreviewScreen() {
//  Column(
//    modifier = Modifier
//      .fillMaxSize()
//  ) {
//    val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
//      is NavigationScreenModel.Transaction.MainSale -> {
//        0
//      }
//
//      is NavigationScreenModel.Transaction.MainReturn -> {
//        1
//      }
//
//      else -> {
//        2
//      }
//    }
//
//    val clientId = when (transactionTypeIndex) {
//      0 -> {
//        stateValues.navigationTransactionReturnClientId
//      }
//
//      1 -> {
//        stateValues.navigationTransactionSupplyClientId
//      }
//
//      else -> {
//        stateValues.navigationTransactionSaleClientId
//      }
//    }
//
//    ScreenAppBarWidget(
//      title = stateValues.stringReceipt,
//      iconPath = stateValues.drawablePathIconReceipt,
//      onBack = if (
//        when (transactionTypeIndex) {
//          0 -> !Navigation.TransactionSale.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
//          1 -> !Navigation.TransactionReturn.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
//          else -> !Navigation.TransactionSupply.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
//        }
//      ) {
//        {
//          coroutineScope.launch {
//            when (transactionTypeIndex) {
//              0 -> Navigation.TransactionSale.pop()
//              1 -> Navigation.TransactionReturn.pop()
//              2 -> Navigation.TransactionSupply.pop()
//            }
//          }
//        }
//      } else null
//    )
//
//    var goodsInCart by rememberSaveable {
//      mutableStateOf(emptyList<GoodsItemInCartDataModel>())
//    }
//
//    LaunchedEffect(Unit) {
//      observeCart(transactionTypeIndex, clientId)
//        .collect {
//          it?.let {
//            goodsInCart = it
//          }
//        }
//    }
//
//    LazyColumn(
//      modifier = Modifier
//        .padding(8.dp)
//        .background(Color.White)
//        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor)
//        .weight(1f)
//    ) {
//      var totalPrice = 0.0
//
//      stateValues
//        .stores
//        ?.find {
//          it.id == stateValues.activeStoreId
//        }?.run {
//          item {
//            val ownershipFormTitle = this@run.companyForms.first().name.extractLocalizedString(stateValues.appLanguage)
//
//            Text(
//              text = "$ownershipFormTitle ${name.extractLocalizedString(stateValues.appLanguage)}",
//              modifier = Modifier
//                .padding(stateValues.marginTextFieldGroup, stateValues.marginTextFieldGroup, stateValues.marginTextFieldGroup,)
//                .fillMaxWidth(),
//              textAlign = TextAlign.Center,
//              fontWeight = FontWeight.Bold,
//              fontSize = stateValues.accentTextSize
//            )
//
//            Text(
//              text = location.name,
//              modifier = Modifier
//                .fillMaxWidth()
//                .padding(start = stateValues.marginTextFieldGroup, top = 2.dp, end = stateValues.marginTextFieldGroup, 12.dp),
//              textAlign = TextAlign.Center,
//              fontSize = stateValues.textSize
//            )
//          }
//        }
//
//      goodsInCart.forEachIndexed { index, item ->
//        stateValues.stock?.find { it.id == item.id }?.run {
//          val price =
//            when (transactionTypeIndex) {
//              0 -> supplyPrices.first().price.toDouble()
//              1 -> returnPrices.first().price.toDouble()
//              else -> supplyPrices.first().price.toDouble()
//            }  // TODO
//
//          val currencySymbol = stateValues.globalAppConfiguration.countries.getCurrency(
//            when (transactionTypeIndex) {
//              0 -> supplyPrices.first().currency
//              1 -> returnPrices.first().currency
//              else -> supplyPrices.first().currency
//            }
//          )?.symbol
//
//          totalPrice += item.quantity.total * price
//
//          item {
//            Text(
//              text = "${index + 1} ${name.extractLocalizedString(stateValues.appLanguage) ?: "No name"}", // TODO
//              modifier = Modifier
//                .padding(horizontal = stateValues.marginTextFieldGroup),
//              fontWeight = FontWeight.Bold,
//              fontSize = stateValues.textSize
//            )
//
//            Row(
//              modifier = Modifier
//                .fillMaxWidth(),
//              horizontalArrangement = Arrangement.SpaceBetween
//            ) {
//              val suffix = item.quantity.immutableUnitName.extractLocalizedString(stateValues.appLanguage)
//
//              Text(
//                text = "${
//                  item.quantity.run { if (roundTotal) total.toInt() else total }
//                } $suffix x $price $currencySymbol",
//                modifier = Modifier
//                  .padding(horizontal = stateValues.marginTextFieldGroup),
//                fontSize = stateValues.textSize
//              )
//
//              Text(
//                text = "$price $currencySymbol",
//                modifier = Modifier
//                  .padding(horizontal = stateValues.marginTextFieldGroup),
//                fontSize = stateValues.accentTextSize
//              )
//            }
//          }
//        }
//      }
//    }
//
//    Column(
//      modifier = Modifier
//        .fillMaxWidth()
//        .padding(horizontal = 8.dp)
//    ) {
//      Spacer(modifier = Modifier.height(4.dp))
//
//      Row(
//        modifier = Modifier
//          .fillMaxWidth()
//      ) {
//        actionButton(
//          text = "",
//          enabledColor = stateValues.DisabledColor,
//          iconPath = stateValues.drawablePathIconAdd, // TODO
//          iconContentDescription = stateValues.stringAdd, // TODO
//          onClick = {
//
//          }
//        )
//      }
//
//      Spacer(modifier = Modifier.height(2.dp))
//
//      actionButton(
//        text = stateValues.stringComplete, // TODO
//        enabled = stateValues.latestNotification == null,
//        onClick = {
//
//        }
//      )
//
//      Spacer(modifier = Modifier.height(4.dp))
//    }
//  }
//}

@Composable
internal fun AppConfiguration.TransactionTotalCard(
    title: String,
    total: Double,
    currencySymbol: String,
    currencyCode: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(
                stateValues.unfocusedBorderWidth,
                stateValues.AccentColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextFieldGroup)
    ) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))
        }

        Text(
            text = "${total.moneyText()} $currencySymbol".trim(),
            color = stateValues.TextColor,
            fontSize = stateValues.titleTextSize,
            fontWeight = FontWeight.Bold
        )

        if (currencyCode.isNotBlank()) {
            Text(
                text = currencyCode,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize
            )
        }
    }
}

@Composable
internal fun AppConfiguration.TransactionPaymentInfoCard(
    title: String,
    subtitle: String,
    amount: Double,
    currencySymbol: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(
                stateValues.unfocusedBorderWidth,
                stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Text(
            text = title,
            color = stateValues.TextColor,
            fontWeight = FontWeight.Bold,
            fontSize = stateValues.accentTextSize
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subtitle,
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${amount.moneyText()} $currencySymbol".trim(),
            color = stateValues.TextColor,
            fontWeight = FontWeight.Bold,
            fontSize = stateValues.titleTextSize
        )
    }
}

@Composable
internal fun AppConfiguration.TransactionPaymentOptionButton(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = selected)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(
                stateValues.unfocusedBorderWidth,
                if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .background(if (selected) stateValues.AccentColor else stateValues.BackgroundColor)
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    color = if (selected) stateValues.AccentTextColor else stateValues.TextColor
                ),
                onClick = onClick
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun AppConfiguration.TransactionAmountField(
    title: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(
            text = title,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = stateValues.TextColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(stateValues.textFieldHeight)
                .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .border(
                    stateValues.unfocusedBorderWidth,
                    stateValues.PlaceholderTextColor,
                    RoundedCornerShape(stateValues.cornerRadius)
                )
                .background(stateValues.BackgroundColor)
                .padding(horizontal = stateValues.marginTextFieldGroup),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            color = stateValues.TextColor,
                            fontSize = stateValues.textSize
                        )
                    }

                    innerTextField()
                }
            }
        )
    }
}


internal fun decimalInputAppend(current: String, token: String, maxFractionDigits: Int): String {
    val clean = current.trim().replace(',', '.')

    if (token == "⌫")
        return clean.dropLast(1)

    if (token == ".") {
        return if (clean.contains('.')) clean else clean.ifBlank { "0" } + "."
    }

    if (!token.all { it.isDigit() })
        return clean

    val next = if (clean == "0") token else clean + token
    val decimals = next.substringAfter('.', "")

    return if (next.contains('.') && decimals.length > maxFractionDigits)
        clean
    else
        next
}

internal fun decimalInputNormalize(value: String, maxFractionDigits: Int): String {
    val clean = value.trim().replace(',', '.')

    if (clean.isBlank() || clean == ".")
        return ""

    val number = clean.toDoubleOrNull() ?: return clean.dropLast(1)
    val multiplier = List(maxFractionDigits.coerceAtLeast(0)) { 10.0 }
        .fold(1.0) { acc, item -> acc * item }
    val limited = kotlin.math.floor(number * multiplier) / multiplier

    return when {
        clean.endsWith(".") -> clean
        clean.contains('.') -> {
            val before = limited.toString().substringBefore('.')
            val after = clean.substringAfter('.', "").take(maxFractionDigits)
            "$before.$after"
        }
        else -> limited.toLong().toString()
    }
}

internal fun paymentInputAppend(current: String, token: String): String {
    return decimalInputAppend(current, token, maxFractionDigits = 2)
}

internal fun paymentInputNormalize(value: String): String {
    return decimalInputNormalize(value, maxFractionDigits = 2)
}

internal fun moneyInputFromDouble(value: Double): String {
    val rounded = kotlin.math.floor(value.coerceAtLeast(0.0) * 100.0) / 100.0
    val whole = rounded.toLong()
    val cents = kotlin.math.round((rounded - whole) * 100.0).toInt()
    return "$whole.${cents.toString().padStart(2, '0')}"
}

internal fun List<LocalizedStringDataModel>.visibleLocalizedString(
    language: String,
    fallback: String
): String {
    return extractLocalizedString(language)
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: firstOrNull { it.language.equals("main", ignoreCase = true) && it.value.trim().isNotBlank() && !it.value.trim().equals("null", ignoreCase = true) }
            ?.value
            ?.trim()
        ?: firstOrNull { it.value.trim().isNotBlank() && !it.value.trim().equals("null", ignoreCase = true) }
            ?.value
            ?.trim()
        ?: fallback
}

internal const val LOCALIZED_NOTE_STORAGE_PREFIX = "aita.localized.note.v1:"

internal fun List<LocalizedStringDataModel>.cleanLocalizedNoteValues(): List<LocalizedStringDataModel> =
    map { item ->
        LocalizedStringDataModel(
            language = item.language.ifBlank { "main" },
            value = item.value.trim()
        )
    }
        .filter { it.language.isNotBlank() && it.value.isNotBlank() }
        .distinctBy { it.language.lowercase() }

internal fun List<LocalizedStringDataModel>.toStoredLocalizedNoteOrNull(): String? {
    val cleaned = cleanLocalizedNoteValues()
    if (cleaned.isEmpty()) return null
    return LOCALIZED_NOTE_STORAGE_PREFIX + jsonBase.encodeToString(
        ListSerializer(LocalizedStringDataModel.serializer()),
        cleaned
    )
}

internal fun String?.decodeStoredLocalizedNoteOrNull(): List<LocalizedStringDataModel>? {
    val raw = this?.trim().orEmpty()
    if (!raw.startsWith(LOCALIZED_NOTE_STORAGE_PREFIX)) return null
    val payload = raw.removePrefix(LOCALIZED_NOTE_STORAGE_PREFIX)
    return runCatching {
        jsonBase.decodeFromString(
            ListSerializer(LocalizedStringDataModel.serializer()),
            payload
        )
    }.getOrNull()?.cleanLocalizedNoteValues()
}

internal fun String?.toLocalizedNoteEditorValues(): List<LocalizedStringDataModel> {
    val decoded = decodeStoredLocalizedNoteOrNull()
    if (!decoded.isNullOrEmpty()) return decoded
    val legacy = this?.trim().orEmpty()
    return if (legacy.isNotBlank()) listOf(LocalizedStringDataModel("main", legacy)) else listOf(LocalizedStringDataModel("main", ""))
}

internal fun String?.visibleStoredLocalizedNote(language: String): String? {
    val decoded = decodeStoredLocalizedNoteOrNull()
    val visible = decoded?.visibleLocalizedString(language, "")?.trim().orEmpty()
    if (visible.isNotBlank()) return visible
    return this?.trim()
        ?.takeIf { it.isNotBlank() && !it.startsWith(LOCALIZED_NOTE_STORAGE_PREFIX) }
}

internal fun String.withoutGoodsCategoryPrefix(): String {
    return trim()
        .replace(
            Regex(
                pattern = """^(Goods\s+(categor(?:y|ies)|section)|Product\s+category|Category|Категория\s+товаров|Раздел\s+товар(?:ов|а)?|Товарный\s+раздел|Категория|Тауар(?:лар)?\s+(санаты|бөлімі)|Товар(?:лар)?\s+(санаты|бөлімі)|Өнім(?:дер)?\s+(санаты|бөлімі)|Санат|Бөлім)\s*[:：\-—]?\s*""",
                option = RegexOption.IGNORE_CASE
            ),
            ""
        )
        .trim()
        .trimStart(':', '：', '-', '—')
        .trim()
}

internal fun List<LocalizedStringDataModel>.visibleGoodsCategoryName(
    language: String,
    fallback: String
): String = visibleLocalizedString(language, fallback).withoutGoodsCategoryPrefix()

internal fun AppConfiguration.goodsCategoryName(
    categoryId: String
): String? {
    return stateValues.goodsCategories
        .orEmpty()
        .find { it.id == categoryId }
        ?.name
        ?.visibleGoodsCategoryName(stateValues.appLanguage, categoryId)
}

internal fun cashTenderShortcutBase(currencyCode: String): List<Double> {
    return when (currencyCode.uppercase()) {
        "KZT" -> listOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 3000.0, 5000.0, 10000.0, 20000.0, 50000.0, 100000.0)
        "RUB" -> listOf(50.0, 100.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0)
        "USD" -> listOf(1.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
        "EUR" -> listOf(5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
        else -> listOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0)
    }
}

internal fun quickTenderAmounts(
    amount: Double,
    currencyCode: String,
    includeExactRemaining: Boolean
): List<Double> {
    val target = amount.roundMoney().coerceAtLeast(0.0)
    if (target <= 0.0)
        return emptyList()

    val result = mutableListOf<Double>()

    if (includeExactRemaining)
        result += target

    cashTenderShortcutBase(currencyCode)
        .filter { it >= target && it !in result }
        .forEach { result += it }

    if (result.size < 4) {
        var next = cashTenderShortcutBase(currencyCode).lastOrNull()?.takeIf { it > 0.0 } ?: 10000.0
        while (result.size < 4) {
            next *= 2.0
            if (next !in result)
                result += next
        }
    }

    return result.distinct().take(4)
}

@Composable
internal fun AppConfiguration.TransactionQuickAmountButtons(
    targetAmount: Double,
    currencyCode: String,
    currencySymbol: String,
    includeExactRemaining: Boolean,
    currentAmount: Double? = null,
    onAmountSelected: (Double) -> Unit
) {
    val target = targetAmount.roundMoney().coerceAtLeast(0.0)
    val current = currentAmount?.roundMoney()
    val exactAlreadyEntered = includeExactRemaining && current != null && kotlin.math.abs(current - target) < 0.01

    val amounts = quickTenderAmounts(
        amount = target,
        currencyCode = currencyCode,
        includeExactRemaining = includeExactRemaining && !exactAlreadyEntered
    ).filterNot { exactAlreadyEntered && kotlin.math.abs(it.roundMoney() - target) < 0.01 }

    if (amounts.isEmpty())
        return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(amounts) { amount ->
            Box(
                modifier = Modifier
                    .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .border(
                        stateValues.unfocusedBorderWidth,
                        stateValues.AccentColor,
                        RoundedCornerShape(stateValues.cornerRadius)
                    )
                    .background(stateValues.BackgroundColor)
                    .aitaClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = stateValues.AccentColor)
                    ) {
                        onAmountSelected(amount)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${moneyInputFromDouble(amount)} $currencySymbol",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = accentTextShadow(stateValues.AccentColor, stateValues.AccentColor)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


internal data class StoreContactQuickFillButton(
    val id: String,
    val text: String,
    val value: String
)

internal data class StorePhoneQuickFillOption(
    val id: String,
    val text: String,
    val selectedSecondaryId: String,
    val localNumber: String,
    val fullNumber: String
)

internal fun normalizePhoneDigits(value: String): String = value.filter { it.isDigit() }

internal fun List<CountryDataModel>.countryByPhoneSelection(selectedSecondaryId: String?): CountryDataModel? {
    val selectedCode = selectedSecondaryId
        ?.trim()
        ?.removePrefix("+")
        ?.filter { it.isDigit() }
        ?.takeIf { it.isNotBlank() }
        ?: return null

    return withTajikistanFallback()
        .sortedByDescending { it.phoneNumberCode.length }
        .firstOrNull { it.phoneNumberCode == selectedCode }
}

internal fun storePhoneQuickFillOption(
    id: String,
    label: String,
    rawPhoneNumber: String?,
    countries: List<CountryDataModel>
): StorePhoneQuickFillOption? {
    val digits = normalizePhoneDigits(rawPhoneNumber.orEmpty())
    if (digits.isBlank()) return null

    val country = countries
        .withTajikistanFallback()
        .sortedByDescending { it.phoneNumberCode.length }
        .firstOrNull { country ->
            digits.startsWith(country.phoneNumberCode) && digits.length > country.phoneNumberCode.length
        }
        ?: return null

    val localNumber = digits.removePrefix(country.phoneNumberCode).takeIf { it.isNotBlank() } ?: return null
    val fullNumber = country.phoneNumberCode + localNumber

    return StorePhoneQuickFillOption(
        id = id,
        text = "$label: ${fullNumber.asDisplayPhoneNumber()}",
        selectedSecondaryId = "+${country.phoneNumberCode}",
        localNumber = localNumber,
        fullNumber = fullNumber
    )
}

internal fun DomainSelectionTextFieldContent.fullPhoneDigitsForQuickFill(): String {
    return normalizePhoneDigits(selectedSecondaryId.orEmpty()) + normalizePhoneDigits(value.text)
}

@Composable
internal fun AppConfiguration.StoreContactQuickFillButtons(
    buttons: List<StoreContactQuickFillButton>,
    currentValue: String,
    normalize: (String) -> String,
    onSelected: (StoreContactQuickFillButton) -> Unit
) {
    val current = normalize(currentValue)
    val visibleButtons = buttons
        .filter { normalize(it.value).isNotBlank() }
        .distinctBy { normalize(it.value) }
        .filterNot { normalize(it.value) == current }

    if (visibleButtons.isEmpty()) return

    Spacer(modifier = Modifier.height(stateValues.marginTextField))

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(visibleButtons, key = { it.id }) { button ->
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .border(
                        stateValues.unfocusedBorderWidth,
                        stateValues.AccentColor,
                        RoundedCornerShape(stateValues.cornerRadius)
                    )
                    .background(stateValues.BackgroundColor)
                    .aitaClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = stateValues.AccentColor)
                    ) {
                        onSelected(button)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = button.text,
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = accentTextShadow(stateValues.AccentColor, stateValues.AccentColor)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.StorePhoneQuickFillButtons(
    phoneField: DomainSelectionTextFieldContent,
    options: List<StorePhoneQuickFillOption>
) {
    val optionById = options.associateBy { it.id }
    StoreContactQuickFillButtons(
        buttons = options.map { option ->
            StoreContactQuickFillButton(
                id = option.id,
                text = option.text,
                value = option.fullNumber
            )
        },
        currentValue = phoneField.fullPhoneDigitsForQuickFill(),
        normalize = ::normalizePhoneDigits,
        onSelected = { button ->
            val option = optionById[button.id] ?: return@StoreContactQuickFillButtons
            phoneField.replaceSelectedSecondaryId(option.selectedSecondaryId)
            phoneField.replaceText(option.localNumber)
        }
    )
}

@Composable
internal fun AppConfiguration.StoreEmailQuickFillButtons(
    emailField: GenericTextFieldContent,
    buttons: List<StoreContactQuickFillButton>
) {
    StoreContactQuickFillButtons(
        buttons = buttons,
        currentValue = emailField.value.text,
        normalize = { it.trim().lowercase() },
        onSelected = { button -> emailField.replaceText(button.value.trim().lowercase()) }
    )
}

@Composable
internal fun AppConfiguration.TransactionPaymentAmountField(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    identityKey: String,
    placeholder: String = "0.00",
    selected: Boolean,
    leadingIconPath: String,
    onSelected: () -> Unit,
    onValueChange: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val suppressSystemKeyboard = getPlatformName().contains("android", ignoreCase = true)

    genericTextField(
        modifier = modifier,
        titleText = title,
        valueInitial = value,
        identityKey = identityKey,
        parentOwnsValue = true,
        autoFocus = false,
        retainTextAcrossRecreation = false,
        persistTextDraft = false,
        enableVoiceInput = false,
        placeholderText = placeholder,
        leadingIconPath = leadingIconPath,
        keyboardType = KeyboardType.Decimal,
        imeWithAction = ImeWithAction(ImeAction.Done),
        readOnly = suppressSystemKeyboard,
        showClearButton = true,
        titleTextColor = if (selected) stateValues.AccentColor else stateValues.TextColor,
        focusedBorderColor = stateValues.AccentColor,
        unfocusedBorderColor = if (selected) stateValues.AccentColor else stateValues.TextColor,
        selectionBackgroundColor = stateValues.AccentColor,
        selectionFocusTextColor = stateValues.AccentTextColor,
        updateIsFocusedAction = { focusState ->
            if (focusState.isFocused) {
                onSelected()

                if (suppressSystemKeyboard)
                    keyboardController?.hide()
            }
        },
        onValueChange = { rawValue, applyChange ->
            val normalized = rawValue.trim().replace(',', '.')
            val isAcceptable = normalized.isEmpty() ||
                    normalized == "." ||
                    normalized.matches(Regex("^\\d*(\\.\\d{0,2})?$"))

            if (isAcceptable) {
                onValueChange(normalized)
                applyChange()
            }
        }
    )
}


@Composable
internal fun AppConfiguration.TransactionDecimalAmountField(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    placeholder: String,
    maxFractionDigits: Int,
    selected: Boolean,
    leadingIconPath: String? = null,
    onSelected: () -> Unit,
    onValueChange: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val suppressSystemKeyboard = getPlatformName().contains("android", ignoreCase = true)
    val decimalRegex = remember(maxFractionDigits) {
        Regex("^\\d*(\\.\\d{0,${maxFractionDigits.coerceAtLeast(0)}})?$")
    }

    genericTextField(
        modifier = modifier,
        titleText = title,
        valueInitial = value,
        placeholderText = placeholder,
        leadingIconPath = leadingIconPath,
        keyboardType = KeyboardType.Decimal,
        imeWithAction = ImeWithAction(ImeAction.Done),
        readOnly = suppressSystemKeyboard,
        showClearButton = true,
        titleTextColor = if (selected) stateValues.AccentColor else stateValues.TextColor,
        focusedBorderColor = stateValues.AccentColor,
        unfocusedBorderColor = if (selected) stateValues.AccentColor else stateValues.TextColor,
        selectionBackgroundColor = stateValues.AccentColor,
        selectionFocusTextColor = stateValues.AccentTextColor,
        updateIsFocusedAction = { focusState ->
            if (focusState.isFocused) {
                onSelected()

                if (suppressSystemKeyboard)
                    keyboardController?.hide()
            }
        },
        onValueChange = { rawValue, applyChange ->
            val normalized = rawValue.trim().replace(',', '.')
            val isAcceptable = normalized.isEmpty() ||
                    normalized == "." ||
                    normalized.matches(decimalRegex)

            if (isAcceptable) {
                onValueChange(normalized)
                applyChange()
            }
        }
    )
}

@Composable
internal fun AppConfiguration.CartQuantityBottomSheet(
    goodsItem: GoodsItemDataModel,
    quantity: QuantityDataModel,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val unitText = quantity.immutableUnitName.extractLocalizedString(stateValues.appLanguage).orEmpty()
    val allowFraction = quantity.allowsFractionalStockQuantityInput()
    val maxFractionDigits = if (allowFraction) 3 else 0
    val minimumAmount = quantity.pricedAmount
        .takeIf { it > 0.0 }
        ?: if (quantity.roundTotal) 1.0 else 0.001
    val autoFocusAmount = platformAllowsAutomaticTextFieldFocus()
    val suppressSystemKeyboard = getPlatformName().contains("android", ignoreCase = true)
    val keyboardController = LocalSoftwareKeyboardController.current

    fun normalizeQuantityText(raw: String): String {
        val sanitized = sanitizeStockQuantityInput(raw, allowFraction)
        return if (allowFraction) {
            if (sanitized == ".") "0." else decimalInputNormalize(sanitized, maxFractionDigits = maxFractionDigits)
        } else {
            sanitized
        }
    }

    fun appendQuantityToken(current: String, token: String): String {
        if (token == "." && !allowFraction) return current
        val appended = decimalInputAppend(current, token, maxFractionDigits = maxFractionDigits)
        return normalizeQuantityText(appended)
    }

    var amountText by rememberSaveable(goodsItem.id, quantity.total, quantity.roundTotal, quantity.id) {
        mutableStateOf(stockQuantityInputTextFromAmount(quantity.total, quantity))
    }

    val amount = parseStockQuantityInputText(amountText, quantity) ?: 0.0
    val amountValid = amount + 0.000001 >= minimumAmount
    val itemName = goodsItem.name.visibleLocalizedString(stateValues.appLanguage, localizedStringResource(365, "Goods item"))
    var amountFieldContent: GenericTextFieldContent? = null

    AitaBottomSheet(
        title = localizedStringResource(513, "Quantity"),
        iconPath = stateValues.drawablePathIconStock,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(stateValues.marginTextField),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = itemName,
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            amountFieldContent = genericTextField(
                modifier = Modifier.fillMaxWidth(),
                titleText = stateValues.stringEnterQuantity,
                valueInitial = amountText,
                stateHost = CartQuantityBottomSheetAutoFocusStateHost,
                stateKey = null,
                isFocusedInitial = autoFocusAmount,
                autoFocus = autoFocusAmount,
                forceRefocus = false,
                readOnly = suppressSystemKeyboard,
                keyboardType = if (allowFraction) KeyboardType.Decimal else KeyboardType.Number,
                imeWithAction = ImeWithAction(ImeAction.Done),
                leadingIconPath = stateValues.drawablePathIconStock,
                showClearButton = true,
                selectionBackgroundColor = stateValues.AccentColor,
                selectionFocusTextColor = stateValues.AccentTextColor,
                updateIsFocusedAction = { focusState ->
                    if (focusState.isFocused && suppressSystemKeyboard) {
                        keyboardController?.hide()
                    }
                },
                onTransformValue = { normalizeQuantityText(it) },
                onValueChange = { rawValue, applyChange ->
                    val normalized = normalizeQuantityText(rawValue)
                    val acceptable = normalized.isEmpty() || normalized.isStockQuantityInputText(allowFraction)
                    if (acceptable) {
                        amountText = normalized
                        applyChange()
                    }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${amountText.ifBlank { "0" }} $unitText".trim(),
                color = if (amountValid) stateValues.AccentColor else stateValues.ErrorColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = accentTextShadow(stateValues.AccentColor, stateValues.AccentColor)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!amountValid) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${localizedStringResource(1220, "Minimum quantity")} ${minimumAmount.quantityAmountText(quantity.roundTotal)} $unitText".trim(),
                    color = stateValues.ErrorColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            StockQuantityQuickFillButtons(
                quantityUnit = quantity,
                currentText = amountText,
                onAmountSelected = { selectedAmountText ->
                    amountText = selectedAmountText
                    amountFieldContent?.replaceText(selectedAmountText, applyTransform = false)
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            TransactionNumpad(
                modifier = Modifier.fillMaxWidth(),
                allowDecimal = allowFraction,
                onInput = { token ->
                    val nextText = appendQuantityToken(amountText, token)
                    amountText = nextText
                    amountFieldContent?.replaceText(nextText, applyTransform = false)
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stateValues.stringCancel,
                    enabledColor = stateValues.PlaceholderTextColor,
                    onClick = onDismiss
                )

                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stateValues.stringConfirm,
                    enabled = amountValid,
                    enabledColor = stateValues.AccentColor,
                    onClick = {
                        onConfirm(amount)
                    }
                )
            }
        }
    }
}


internal data class ResolvedReturnBatchSelectionUiModel(
    val batch: GoodsBatchDataModel?,
    val price: PriceDataModel,
    val selection: CartReturnBatchSelectionDataModel,
    val hasAnyBatch: Boolean
)

internal fun AppConfiguration.defaultTransactionCurrencyCode(): String =
    stateValues.globalAppConfiguration.countries.withTajikistanFallback()
        .find { it.locale.equals(stateValues.userAccount?.countryLocale, true) }
        ?.currencies
        ?.firstOrNull()
        ?.code
        ?: "KZT"

internal fun PriceDataModel.withFallbackCurrency(currencyCode: String): PriceDataModel =
    if (currency.isBlank()) copy(currency = currencyCode) else this

internal fun AppConfiguration.returnCandidateBatchesFor(
    goodsItem: GoodsItemDataModel,
    batches: List<GoodsBatchDataModel>
): List<GoodsBatchDataModel> {
    val activeStoreId = stateValues.activeStoreId
    return batches
        .asSequence()
        .filter { batch ->
            batch.goodsItemId == goodsItem.id &&
                    batch.isActive &&
                    when (batch.status) {
                        StockBatchStatusDataModel.Delivered,
                        StockBatchStatusDataModel.OnShelf,
                        StockBatchStatusDataModel.SoldOut -> true
                        else -> false
                    } &&
                    batchBelongsToInventoryStoreForUi(batch.storeId, activeStoreId)
        }
        .sortedWith(
            compareBy<GoodsBatchDataModel> { if (it.status == StockBatchStatusDataModel.OnShelf) 0 else 1 }
                .thenBy { if (it.quantity.total > 0.000001) 0 else 1 }
                .thenBy { if (it.id == goodsItem.activeShelfBatchId) 0 else 1 }
                .thenBy { it.shelfPriority }
                .thenBy { it.expirationDateMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.quantity.total }
                .thenByDescending { it.deliveredAtMillis ?: 0L }
        )
        .toList()
}

internal fun AppConfiguration.defaultReturnBatchFor(
    goodsItem: GoodsItemDataModel,
    candidateBatches: List<GoodsBatchDataModel>
): GoodsBatchDataModel? =
    candidateBatches.firstOrNull { it.status == StockBatchStatusDataModel.OnShelf && it.quantity.total > 0.000001 }
        ?: candidateBatches.firstOrNull { it.id == goodsItem.activeShelfBatchId }
        ?: candidateBatches.firstOrNull { it.quantity.total > 0.000001 }
        ?: candidateBatches.firstOrNull()

internal fun PriceDataModel.withReturnSelectionPrice(selection: CartReturnBatchSelectionDataModel?): PriceDataModel {
    val amount = selection?.pricePerUnit?.takeIf { it.isFinite() && it >= 0.0 }
    val priced = amount?.let { withMoneyAmount(it) } ?: this
    return if (!selection?.currencyCode.isNullOrBlank()) priced.copy(currency = selection!!.currencyCode) else priced
}

internal fun AppConfiguration.resolveReturnBatchSelection(
    goodsItem: GoodsItemDataModel,
    cartItem: GoodsItemInCartDataModel,
    allBatches: List<GoodsBatchDataModel>,
    selection: CartReturnBatchSelectionDataModel?
): ResolvedReturnBatchSelectionUiModel {
    val defaultCurrency = selection?.currencyCode?.takeIf { it.isNotBlank() } ?: defaultTransactionCurrencyCode()
    val candidates = returnCandidateBatchesFor(goodsItem, allBatches)
    val selectedBatch = selection?.stockBatchId?.let { selectedId -> candidates.firstOrNull { it.id == selectedId } }
    val batch = if (selection?.returnDestinationKind == StockBatchKindDataModel.RETURNED || selection?.stockBatchId != null)
        selectedBatch else defaultReturnBatchFor(goodsItem, candidates)
    val basePrice = goodsItem.priceForTransaction(
        transactionTypeIndex = 1,
        saleMethodId = SALE_METHOD_RETAIL,
        quantityTotal = cartItem.quantity.total,
        batch = batch
    ).withFallbackCurrency(defaultCurrency)
    val price = basePrice.withReturnSelectionPrice(selection).withFallbackCurrency(defaultCurrency)
    val normalized = (selection ?: CartReturnBatchSelectionDataModel(goodsItemId = goodsItem.id)).copy(
        stockBatchId = selection?.stockBatchId ?: batch?.id,
        pricePerUnit = price.price.toMoneyDouble().roundMoney(),
        currencyCode = price.currency.ifBlank { defaultCurrency },
        updatedAtMillis = selection?.updatedAtMillis ?: 0L
    )
    return ResolvedReturnBatchSelectionUiModel(
        batch = batch,
        price = price,
        selection = normalized,
        hasAnyBatch = candidates.isNotEmpty()
    )
}

internal fun AppConfiguration.returnBatchPriceForDisplay(
    goodsItem: GoodsItemDataModel,
    batch: GoodsBatchDataModel?,
    currencyCode: String
): PriceDataModel =
    goodsItem.priceForTransaction(
        transactionTypeIndex = 1,
        saleMethodId = SALE_METHOD_RETAIL,
        quantityTotal = 1.0,
        batch = batch
    ).withFallbackCurrency(currencyCode)

internal fun AppConfiguration.returnBatchSummaryText(
    goodsItem: GoodsItemDataModel,
    batch: GoodsBatchDataModel?,
    allBatches: List<GoodsBatchDataModel>,
    currencyCode: String
): String {
    if (batch == null) return returnFlowText("returned")
    val index = allBatches.indexOfFirst { it.id == batch.id }.takeIf { it >= 0 }?.plus(1)
    val price = returnBatchPriceForDisplay(goodsItem, batch, currencyCode)
    return listOfNotNull(
        index?.let { "#$it" },
        stockBatchStatusText(batch.status),
        batch.quantity.quantityText(stateValues.appLanguage),
        "${price.price.toMoneyDouble().moneyText()} ${price.currency}".trim(),
        batch.deliveredAtMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(342, "Delivered")} $it" },
        batch.expirationDateMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(234, "Expires")} $it" }
    ).joinToString(" • ")
}

@Composable
internal fun AppConfiguration.CartReturnPriceBatchBottomSheet(
    goodsItem: GoodsItemDataModel,
    cartItem: GoodsItemInCartDataModel,
    allBatches: List<GoodsBatchDataModel>,
    selection: CartReturnBatchSelectionDataModel?,
    onDismiss: () -> Unit,
    onConfirm: (CartReturnBatchSelectionDataModel) -> Unit
) {
    val receiptPriceLocked = selection?.originalTransactionId != null || selection?.originalClientOperationId != null
    val autoFocusAmount = !receiptPriceLocked && platformAllowsAutomaticTextFieldFocus()
    val suppressSystemKeyboard = getPlatformName().contains("android", ignoreCase = true)
    val keyboardController = LocalSoftwareKeyboardController.current
    val defaultResolved = remember(goodsItem.id, cartItem.quantity.total, allBatches, selection) {
        resolveReturnBatchSelection(goodsItem, cartItem, allBatches, selection)
    }
    val defaultCurrency = defaultResolved.price.currency.ifBlank { defaultTransactionCurrencyCode() }
    var amountText by rememberSaveable(goodsItem.id, selection?.stockBatchId, selection?.pricePerUnit, defaultResolved.price.price) {
        mutableStateOf(moneyInputFromDouble(defaultResolved.price.price.toMoneyDouble()))
    }
    var selectedBatchId by rememberSaveable(goodsItem.id, selection?.stockBatchId, defaultResolved.selection.stockBatchId) {
        mutableStateOf(defaultResolved.selection.stockBatchId.orEmpty())
    }
    val enteredAmount = amountText.toMoneyDouble().roundMoney()
    val candidateBatches = remember(goodsItem.id, allBatches, stateValues.activeStoreId) {
        returnCandidateBatchesFor(goodsItem, allBatches)
    }
    val filteredBatches = remember(candidateBatches, amountText, defaultCurrency) {
        val hasTypedPrice = amountText.isNotBlank()
        if (!hasTypedPrice || receiptPriceLocked) {
            candidateBatches
        } else {
            candidateBatches.filter { batch ->
                abs(returnBatchPriceForDisplay(goodsItem, batch, defaultCurrency).price.toMoneyDouble().roundMoney() - enteredAmount) <= 0.009
            }
        }
    }
    val selectedBatch = candidateBatches.firstOrNull { it.id == selectedBatchId }
    val visibleBatches = remember(filteredBatches, selectedBatch) {
        (listOfNotNull(selectedBatch) + filteredBatches).distinctBy { it.id }
    }
    val itemName = goodsItem.name.visibleLocalizedString(stateValues.appLanguage, localizedStringResource(365, "Goods item"))
    var amountFieldContent: GenericTextFieldContent? = null

    AitaBottomSheet(
        title = stateValues.stringReturnPrice,
        iconPath = stateValues.drawablePathIconTransactionReturn,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(stateValues.marginTextField),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = itemName,
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            amountFieldContent = genericTextField(
                modifier = Modifier.fillMaxWidth(),
                titleText = stateValues.stringReturnPrice,
                valueInitial = amountText,
                stateHost = CartReturnPriceBottomSheetAutoFocusStateHost,
                stateKey = goodsItem.id,
                isFocusedInitial = autoFocusAmount,
                autoFocus = autoFocusAmount,
                forceRefocus = false,
                readOnly = suppressSystemKeyboard || receiptPriceLocked,
                keyboardType = KeyboardType.Decimal,
                imeWithAction = ImeWithAction(ImeAction.Done),
                leadingIconPath = stateValues.drawablePathIconTransactionReturn,
                showClearButton = !receiptPriceLocked,
                selectionBackgroundColor = stateValues.AccentColor,
                selectionFocusTextColor = stateValues.AccentTextColor,
                updateIsFocusedAction = { focusState ->
                    if (focusState.isFocused && suppressSystemKeyboard) keyboardController?.hide()
                },
                onTransformValue = { paymentInputNormalize(it.trim().replace(',', '.')) },
                onValueChange = { rawValue, applyChange ->
                    val normalized = paymentInputNormalize(rawValue.trim().replace(',', '.'))
                    if (!receiptPriceLocked && (normalized.isEmpty() || normalized == "." || normalized.matches(Regex("^\\d*(\\.\\d{0,2})?$")))) {
                        amountText = normalized
                        applyChange()
                    }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${moneyInputFromDouble(enteredAmount)} $defaultCurrency".trim(),
                color = stateValues.AccentColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = accentTextShadow(stateValues.AccentColor, stateValues.AccentColor)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!receiptPriceLocked) TransactionNumpad(
                modifier = Modifier.fillMaxWidth(),
                allowDecimal = true,
                onInput = { token ->
                    val nextText = paymentInputAppend(amountText, token)
                    amountText = nextText
                    amountFieldContent?.replaceText(nextText, applyTransform = false)
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1316, "Return to stock batch"),
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            when {
                candidateBatches.isEmpty() -> {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .foregroundSubtleShadow(stateValues.cornerRadius)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                            .padding(10.dp),
                        text = localizedStringResource(1317, "No stock batches exist for this item yet. Return will create/use a special returned-items batch."),
                        color = stateValues.AccentColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                visibleBatches.isEmpty() -> {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .foregroundSubtleShadow(stateValues.cornerRadius)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                            .padding(10.dp),
                        text = localizedStringResource(1318, "No batch has this return price. Clear/change the price or select a different batch."),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(visibleBatches, key = { _, batch -> batch.id }) { _, batch ->
                            val selected = selectedBatchId == batch.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .foregroundSubtleShadow(stateValues.cornerRadius)
                                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                                    .background(if (selected) stateValues.AccentColor.copy(alpha = 0.10f) else stateValues.BackgroundColor)
                                    .border(
                                        stateValues.unfocusedBorderWidth,
                                        if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                                        RoundedCornerShape(stateValues.cornerRadius)
                                    )
                                    .aitaClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(color = stateValues.AccentColor)
                                    ) { selectedBatchId = batch.id }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AitaRoundCheckbox(
                                    checked = selected,
                                    onCheckedChange = null,
                                    borderColor = if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                                    containerSize = 28.dp,
                                    circleSize = 20.dp
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = returnBatchSummaryText(goodsItem, batch, candidateBatches, defaultCurrency),
                                        color = if (selected) stateValues.AccentColor else stateValues.TextColor,
                                        fontSize = stateValues.smallTextSize,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (selected) {
                                        Text(
                                            text = localizedStringResource(1396, "Selected"),
                                            color = stateValues.AccentColor,
                                            fontSize = stateValues.smallTextSize,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stateValues.stringCancel,
                    enabledColor = stateValues.PlaceholderTextColor,
                    onClick = onDismiss
                )

                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stateValues.stringConfirm,
                    enabled = amountText.isNotBlank() && enteredAmount >= 0.0,
                    enabledColor = stateValues.AccentColor,
                    onClick = {
                        onConfirm(
                            (selection ?: CartReturnBatchSelectionDataModel(goodsItemId = goodsItem.id)).copy(
                                stockBatchId = selectedBatchId.takeIf { selectedId -> candidateBatches.any { it.id == selectedId } },
                                returnDestinationKind = if (selectedBatchId.isNullOrBlank()) selection?.returnDestinationKind else null,
                                pricePerUnit = enteredAmount,
                                currencyCode = defaultCurrency,
                                updatedAtMillis = getCurrentTimeMillis()
                            )
                        )
                    }
                )
            }
        }
    }
}



@Composable
internal fun AppConfiguration.TransactionNumpad(
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = true,
    onInput: (String) -> Unit
) {
    val rows = if (allowDecimal) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "⌫")
        )
    } else {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("0", "⌫")
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { token ->
                    val tokenEnabled = allowDecimal || token != "."
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((stateValues.textFieldHeight.value * 0.9f).dp)
                            .foregroundSubtleShadow(stateValues.cornerRadius)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .border(
                                stateValues.unfocusedBorderWidth,
                                stateValues.PlaceholderTextColor,
                                RoundedCornerShape(stateValues.cornerRadius)
                            )
                            .background(if (token == "⌫" || !tokenEnabled) stateValues.DisabledColor else stateValues.BackgroundColor)
                            .aitaClickable(
                                enabled = tokenEnabled,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = stateValues.TextColor)
                            ) {
                                onInput(token)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = token,
                            color = stateValues.TextColor.copy(alpha = if (tokenEnabled) 1f else 0.42f),
                            fontSize = stateValues.accentTextSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


internal fun debtorDisplayName(debtor: DebtorDataModel): String {
    return if (debtor.debtorType == "company") {
        debtor.companyName.ifBlank { debtor.companyIdNumber.ifBlank { debtor.id } }
    } else {
        "${debtor.firstName} ${debtor.lastName}".trim().ifBlank { debtor.phoneNumber.asDisplayPhoneNumber().ifBlank { debtor.id } }
    }
}

internal fun AppConfiguration.debtDueStatusText(debtor: DebtorDataModel, now: Long = getCurrentTimeMillis()): String {
    val due = debtor.debtDueAtMillis ?: return localizedStringResource(1065, "No due date")
    val days = kotlin.math.floor((due - now).toDouble() / (24.0 * 60.0 * 60.0 * 1000.0)).toInt()
    return when {
        days > 1 -> "${localizedStringResource(1066, "Due in")} $days ${localizedStringResource(1067, "days")}"
        days == 1 -> localizedStringResource(1068, "Due tomorrow")
        days == 0 -> localizedStringResource(1069, "Due today")
        else -> "${localizedStringResource(1070, "Overdue by")} ${-days} ${localizedStringResource(1067, "days")}"
    }
}

internal fun estimatedDebtInterest(debtor: DebtorDataModel, atMillis: Long = getCurrentTimeMillis()): Double {
    val interest = debtor.interest?.takeIf { it.enabled && it.ratePercent > 0.0 } ?: return 0.0
    val start = interest.startsAtMillis ?: debtor.debtCreatedAtMillis.takeIf { it > 0L } ?: return 0.0
    val elapsedDays = ((atMillis - start).coerceAtLeast(0L)).toDouble() / (24.0 * 60.0 * 60.0 * 1000.0)
    val periods = when (interest.periodUnit) {
        "day" -> elapsedDays
        "week" -> elapsedDays / 7.0
        "year" -> elapsedDays / 365.0
        else -> elapsedDays / 30.0
    }.coerceAtLeast(0.0)
    return (debtor.debtAmount * (interest.ratePercent / 100.0) * periods).roundMoney()
}

internal fun debtWithInterest(debtor: DebtorDataModel): Double {
    return (debtor.debtAmount + estimatedDebtInterest(debtor)).roundMoney()
}

internal fun plannedRemainingBefore(debtor: DebtorDataModel, planId: String?): Double {
    var remaining = debtor.debtAmount.coerceAtLeast(0.0)
    for (plan in debtor.plannedPayments.filter { !it.completed }) {
        if (plan.id == planId) break
        remaining = (remaining - plan.amount).coerceAtLeast(0.0).roundMoney()
    }
    return remaining
}

@Composable
internal fun AppConfiguration.DebtorInfoLine(
    title: String,
    value: String,
    accent: Boolean = false
) {
    if (value.isBlank()) return

    Text(
        text = buildAnnotatedString {
            append("$title: ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(value)
            }
        },
        color = if (accent) stateValues.AccentColor else stateValues.TextColor,
        fontSize = stateValues.textSize,
        fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal,
        style = TextStyle(shadow = if (accent) accentTextShadow(stateValues.AccentColor, stateValues.AccentColor) else null),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun AppConfiguration.DebtPercentQuickButtons(
    baseAmount: Double,
    currencySymbol: String,
    currentAmount: Double,
    percents: List<Double> = listOf(10.0, 25.0, 50.0, 75.0, 100.0),
    onSelected: (Double) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(percents) { percent ->
            val amount = (baseAmount * percent / 100.0).roundMoney().coerceAtMost(baseAmount)
            if (amount > 0.0 && kotlin.math.abs(amount - currentAmount.roundMoney()) >= 0.01) {
                Box(
                    modifier = Modifier
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                        .aitaClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = stateValues.AccentColor)
                        ) { onSelected(amount) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${percent.toInt()}% • ${moneyInputFromDouble(amount)} $currencySymbol",
                        color = stateValues.AccentColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = accentTextShadow(stateValues.AccentColor, stateValues.AccentColor)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.DebtReceiptDialog(
    debtor: DebtorDataModel,
    paidAmount: Double,
    debtBefore: Double,
    debtAfter: Double,
    paymentKind: String,
    relatedTransactions: List<TransactionDataModel> = emptyList(),
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        TransactionBarcodeModalGuard()
        LazyColumn(
            modifier = Modifier
                .aitaDialogEntrance()
                .fillMaxWidth(if (stateValues.isNarrowScreen) 0.94f else 0.56f)
                .foregroundTactileShadow(stateValues.cornerRadius, elevated = true)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(Color.White)
                .border(stateValues.unfocusedBorderWidth, Color.Black, RoundedCornerShape(stateValues.cornerRadius))
                .padding(stateValues.marginTextFieldGroup)
        ) {
            item {
                ReceiptPreviewText(localizedStringResource(308, "Debt payment receipt"), bold = true, center = true, large = true)
                Spacer(modifier = Modifier.height(8.dp))
                ReceiptPreviewDivider()
                Spacer(modifier = Modifier.height(8.dp))
                ReceiptPreviewRow(stateValues.stringDebtor, debtorDisplayName(debtor), bold = true)
                ReceiptPreviewRow(localizedStringResource(309, "Type"), if (debtor.debtorType == "company") localizedStringResource(290, "Company") else localizedStringResource(289, "Individual"))
                debtor.idNumber.takeIf { it.isNotBlank() }?.let { ReceiptPreviewRow(localizedStringResource(293, "ID number"), it) }
                debtor.companyIdNumber.takeIf { it.isNotBlank() }?.let { ReceiptPreviewRow(localizedStringResource(292, "Company ID / BIN"), it) }
                debtor.phoneNumber.asDisplayPhoneNumber().takeIf { it.isNotBlank() }?.let { ReceiptPreviewRow(stateValues.stringPhone, it) }
                ReceiptPreviewRow(stateValues.stringPayment, if (paymentKind == "full") localizedStringResource(310, "Full") else localizedStringResource(311, "Partial"))
                ReceiptPreviewRow(stateValues.stringPaid, "${moneyInputFromDouble(paidAmount)} ${debtor.currency}", bold = true)
                ReceiptPreviewRow(localizedStringResource(312, "Debt before"), "${moneyInputFromDouble(debtBefore)} ${debtor.currency}")
                ReceiptPreviewRow(localizedStringResource(313, "Debt remaining"), "${moneyInputFromDouble(debtAfter)} ${debtor.currency}", bold = true)
                debtor.debtDueAtMillis?.toStockDateInputText()?.let { ReceiptPreviewRow(localizedStringResource(314, "Final due date"), it) }
                if (relatedTransactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ReceiptPreviewDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    ReceiptPreviewText(localizedStringResource(315, "Original cart content"), bold = true)
                    relatedTransactions.forEach { tx ->
                        ReceiptPreviewRow(localizedStringResource(316, "Transaction"), "${tx.id} • ${receiptUiDateTime(tx.timeMillis)}")
                        tx.goodsInTransaction.forEach { item ->
                            ReceiptPreviewRow(
                                item.barcode,
                                "${item.quantity} × ${moneyInputFromDouble(item.pricePerUnit)} = ${moneyInputFromDouble(item.quantity * item.pricePerUnit)}"
                            )
                        }
                    }
                }
                val openPlans = debtor.plannedPayments.filter { !it.completed }
                if (openPlans.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ReceiptPreviewDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    ReceiptPreviewText(localizedStringResource(317, "Planned next payments"), bold = true)
                    openPlans.forEachIndexed { index, plan ->
                        ReceiptPreviewRow(
                            "${index + 1}. ${plan.dueAtMillis.toStockDateInputText().ifBlank { localizedStringResource(318, "No date") }}",
                            "${moneyInputFromDouble(plan.amount)} ${debtor.currency}${plan.percent?.let { pct -> " • ${pct.toInt()}%" } ?: ""}"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ReceiptPreviewDivider()
                Spacer(modifier = Modifier.height(8.dp))
                ReceiptPreviewText(receiptUiDateTime(getCurrentTimeMillis()), center = true)
                Spacer(modifier = Modifier.height(12.dp))
                actionButton(text = localizedStringResource(319, "Close"), iconPath = stateValues.drawablePathIconCheck, onClick = onDismiss)
            }
        }
    }
}

@Composable
internal fun AppConfiguration.DebtorPaymentCard(
    debtor: DebtorDataModel,
    selected: Boolean = false,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val totalWithInterest = debtWithInterest(debtor)
    val overdue = debtor.debtDueAtMillis?.let { it < getCurrentTimeMillis() } == true
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = selected)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(if (selected) stateValues.AccentColor else stateValues.BackgroundColor)
            .border(
                if (selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (selected) stateValues.AccentColor else if (overdue) stateValues.ErrorColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = if (selected) stateValues.AccentTextColor else stateValues.TextColor),
                onClick = onClick
            )
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CpImage(
                modifier = Modifier.size(30.dp),
                url = if (debtor.debtorType == "company") stateValues.drawablePathIconStores else stateValues.drawablePathIconDebtors,
                fallbackRes = Res.drawable._24_0,
                contentDescription = debtorDisplayName(debtor),
                tintColor = if (selected) stateValues.AccentTextColor else stateValues.AccentColor
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = debtorDisplayName(debtor),
                    color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        if (debtor.debtorType == "company") localizedStringResource(290, "Company") else localizedStringResource(289, "Individual"),
                        debtor.phoneNumber.asDisplayPhoneNumber().takeIf { it.isNotBlank() },
                        debtor.idNumber.takeIf { it.isNotBlank() } ?: debtor.companyIdNumber.takeIf { it.isNotBlank() }
                    ).joinToString(" • "),
                    color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${moneyInputFromDouble(debtor.debtAmount)} ${debtor.currency}",
                color = if (selected) stateValues.AccentTextColor else if (overdue) stateValues.ErrorColor else stateValues.AccentColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = accentTextShadow(stateValues.AccentColor, stateValues.AccentColor))
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        DebtorInfoLine(localizedStringResource(320, "Due"), debtDueStatusText(debtor), accent = overdue)
        debtor.debtDueAtMillis?.toStockDateInputText()?.let { DebtorInfoLine(localizedStringResource(321, "Final date"), it) }
        debtor.interest?.takeIf { it.enabled && it.ratePercent > 0.0 }?.let {
            DebtorInfoLine(localizedStringResource(322, "Interest"), "${it.ratePercent}% ${localizedStringResource(327, "per")} ${it.periodUnit} • ${localizedStringResource(328, "est.")} ${moneyInputFromDouble(estimatedDebtInterest(debtor))} ${debtor.currency}")
            DebtorInfoLine(localizedStringResource(323, "Debt with interest"), "${moneyInputFromDouble(totalWithInterest)} ${debtor.currency}", accent = true)
        }
        if (debtor.plannedPayments.any { !it.completed }) {
            DebtorInfoLine(localizedStringResource(324, "Plan"), "${debtor.plannedPayments.count { !it.completed }} ${localizedStringResource(329, "open payments")}")
        }
        if (debtor.paymentHistory.isNotEmpty()) {
            DebtorInfoLine(localizedStringResource(325, "Paid records"), debtor.paymentHistory.size.toString())
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(326, "Edit / pay debt"),
                iconPath = stateValues.drawablePathIconEdit,
                onClick = onClick
            )
            onDelete?.let {
                actionButton(
                    autoLoading = false,
                    text = stateValues.stringDelete,
                    enabledColor = stateValues.ErrorColor,
                    iconPath = stateValues.drawablePathIconDelete,
                    onClick = it
                )
            }
        }
    }
}




@Composable
internal fun AppConfiguration.TransactionPlainTextField(
    title: String,
    value: String,
    placeholder: String,
    leadingIconPath: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    stateHost: StateHost? = null,
    stateKey: String? = null,
    onValueChange: (String) -> Unit
): GenericTextFieldContent {
    val textFieldContent = genericTextField(
        titleText = title,
        stateHost = stateHost,
        stateKey = stateKey,
        valueInitial = value,
        placeholderText = placeholder,
        leadingIconPath = leadingIconPath,
        keyboardType = keyboardType,
        imeWithAction = ImeWithAction(ImeAction.Next),
        showClearButton = true,
        onValueChange = { rawValue, applyChange ->
            onValueChange(rawValue)
            applyChange()
        }
    )

    LaunchedEffect(stateHost, stateKey, textFieldContent.value.text, value) {
        if (stateHost != null && !stateKey.isNullOrBlank() && textFieldContent.value.text != value) {
            onValueChange(textFieldContent.value.text)
        }
    }

    return textFieldContent
}



@Composable
internal fun AppConfiguration.TransactionPaymentHeadsUpCard(
    total: Double,
    paid: Double,
    debt: Double,
    remaining: Double,
    change: Double,
    currencySymbol: String,
    currencyCode: String,
    paymentValid: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = stateValues.marginTextField)
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                if (paymentValid) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Text(
            text = "${total.moneyText()} $currencySymbol".trim(),
            color = stateValues.TextColor,
            fontSize = stateValues.titleTextSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (currencyCode.isNotBlank()) {
            Text(
                text = currencyCode,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TransactionPaymentSummaryRows(
            paid = paid,
            debt = debt,
            remaining = remaining,
            change = change,
            currencySymbol = currencySymbol,
            large = true,
            paymentValid = paymentValid
        )
    }
}

@Composable
internal fun AppConfiguration.TransactionPaymentSummaryRows(
    paid: Double,
    debt: Double,
    remaining: Double,
    change: Double,
    currencySymbol: String,
    large: Boolean = false,
    paymentValid: Boolean = true
) {
    val textSize = if (large) stateValues.textSize else stateValues.smallTextSize
    val valueSize = if (large) stateValues.accentTextSize else stateValues.textSize

    @Composable
    fun SummaryRow(
        title: String,
        value: Double,
        valueColor: Color = stateValues.TextColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = textSize,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${moneyInputFromDouble(value)} $currencySymbol",
                color = valueColor,
                fontSize = valueSize,
                fontWeight = FontWeight.Bold
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SummaryRow(
            title = stateValues.stringPaid,
            value = paid,
            valueColor = stateValues.TextColor
        )

        if (debt > 0.0) {
            Spacer(modifier = Modifier.height(4.dp))
            SummaryRow(
                title = stateValues.stringDebt,
                value = debt,
                valueColor = stateValues.AccentColor
            )
        }

        if (remaining > 0.0) {
            Spacer(modifier = Modifier.height(4.dp))
            SummaryRow(
                title = localizedStringResource(330, "Remaining"),
                value = remaining,
                valueColor = stateValues.ErrorColor
            )
        }

        if (change > 0.0) {
            Spacer(modifier = Modifier.height(4.dp))
            SummaryRow(
                title = stateValues.stringChange,
                value = change,
                valueColor = stateValues.AccentColor
            )
        }
    }
}

@Composable
internal fun AppConfiguration.TransactionDebtText(
    debtAmount: Double,
    currencySymbol: String
) {
    Text(
        text = "${stateValues.stringDebt}: ${moneyInputFromDouble(debtAmount)} $currencySymbol",
        color = if (debtAmount > 0.0) stateValues.AccentColor else stateValues.PlaceholderTextColor,
        fontSize = stateValues.textSize,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun AppConfiguration.TransactionPaymentScreen() {
    val context = rememberTransactionContext()

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringPayment,
                onBack = {
                    coroutineScope.launch {
                        when (context.transactionTypeIndex) {
                            0 -> Navigation.TransactionSale.pop()
                            1 -> Navigation.TransactionReturn.pop()
                            else -> Navigation.TransactionSupply.pop()
                        }
                    }
                }
            )
        }
    ) {
        val goodsInCart by getCartState(
            context.transactionTypeIndex,
            context.clientId
        ).collectAsState()
        val saleMethodIds by cartSaleMethodIdsState.collectAsState()
        val returnBatchSelections by getCartReturnBatchSelectionsState().collectAsState()

        if (context.transactionTypeIndex == 1 && !returnDestinationsReady(context.clientId)) {
            Text(returnFlowText("choose"), color = stateValues.TextColor)
            actionButton(text = returnFlowText("batches"), autoLoading = false, confirmationRequired = false,
                onClick = { coroutineScope.launch { Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.ReturnBatches) } })
            return@AitaScreenColumn
        }

        val lines = remember(
            goodsInCart,
            stateValues.stock,
            stateValues.stockBatches,
            context.transactionTypeIndex,
            context.clientId,
            stateValues.appLanguage,
            saleMethodIds,
            returnBatchSelections
        ) {
            buildTransactionReceiptLines(
                cart = goodsInCart,
                stock = stateValues.stock.orEmpty(),
                stockBatches = stateValues.stockBatches.orEmpty(),
                transactionTypeIndex = context.transactionTypeIndex,
                saleMethodIds = saleMethodIds,
                returnBatchSelections = returnBatchSelections,
                clientId = context.clientId
            )
        }

        val total = remember(lines) {
            lines.sumOf { it.total }.roundMoney()
        }

        val paymentDrafts by getTransactionPaymentDraftsState().collectAsState()
        val persistedPaymentDraft = paymentDrafts[transactionSupplySupplierKey(context.transactionTypeIndex, context.clientId)]

        val country = stateValues.globalAppConfiguration.countries.find {
            it.locale.equals(stateValues.userAccount?.countryLocale, true)
        } ?: stateValues.globalAppConfiguration.countries.first()

        val currencyCode = lines.firstOrNull()?.currencyCode
            ?: country.currencies.firstOrNull()?.code
            ?: "KZT"

        val currencySymbol = lines.firstOrNull()?.currencySymbol
            ?: country.currencies.firstOrNull()?.symbol
            ?: currencyCode

        LaunchedEffect(stateValues.activeStoreId) {
            stateValues.activeStoreId?.let { getDebtors(it) }
        }

        val debtors by debtorsState.payload.collectAsState()

        var selectedCashlessPaymentMethodId by rememberSaveable(context.transactionTypeIndex, context.clientId) {
            mutableStateOf(
                persistedPaymentDraft?.cardPaymentOptionId
                    ?.takeIf { it > 0 }
                    ?.toString()
                    ?: country.preferredCashlessPaymentOptionId
            )
        }

        var cashText by rememberSaveable(context.transactionTypeIndex, context.clientId) {
            mutableStateOf(
                persistedPaymentDraft?.cashInputText?.takeIf { it.isNotBlank() }
                    ?: persistedPaymentDraft?.paidCash?.takeIf { it > 0.0 }?.let { moneyInputFromDouble(it) }
                    ?: ""
            )
        }

        var cardText by rememberSaveable(context.transactionTypeIndex, context.clientId) {
            mutableStateOf(
                persistedPaymentDraft?.cardInputText?.takeIf { it.isNotBlank() }
                    ?: persistedPaymentDraft?.paidCard?.takeIf { it > 0.0 }?.let { moneyInputFromDouble(it) }
                    ?: ""
            )
        }

        var debtText by rememberSaveable(context.transactionTypeIndex, context.clientId) {
            mutableStateOf(
                persistedPaymentDraft?.debtInputText?.takeIf { it.isNotBlank() }
                    ?: persistedPaymentDraft?.debtor?.debtAmount?.takeIf { it > 0.0 }?.let { moneyInputFromDouble(it) }
                    ?: ""
            )
        }

        var activeAmountField by rememberSaveable(context.transactionTypeIndex, context.clientId) {
            mutableStateOf(
                persistedPaymentDraft?.activeAmountField?.takeIf { it in listOf("cash", "card", "debt") }
                    ?: when {
                        persistedPaymentDraft?.paidCash?.let { it > 0.0 } == true -> "cash"
                        persistedPaymentDraft?.paidCard?.let { it > 0.0 } == true -> "card"
                        persistedPaymentDraft?.debtor?.debtAmount?.let { it > 0.0 } == true -> "debt"
                        else -> "card"
                    }
            )
        }

        var selectedDebtorId by rememberSaveable(context.transactionTypeIndex, context.clientId) {
            mutableStateOf(
                persistedPaymentDraft?.selectedDebtorId?.takeIf { it.isNotBlank() }
                    ?: persistedPaymentDraft?.debtor?.id?.takeIf { it.isNotBlank() }
            )
        }

        var newDebtorFirstName by rememberSaveable(context.transactionTypeIndex, context.clientId) { mutableStateOf(persistedPaymentDraft?.debtor?.firstName.orEmpty()) }
        var newDebtorLastName by rememberSaveable(context.transactionTypeIndex, context.clientId) { mutableStateOf(persistedPaymentDraft?.debtor?.lastName.orEmpty()) }
        var newDebtorPhone by rememberSaveable(context.transactionTypeIndex, context.clientId) { mutableStateOf(persistedPaymentDraft?.debtor?.phoneNumber.orEmpty()) }
        var newDebtorEmail by rememberSaveable(context.transactionTypeIndex, context.clientId) { mutableStateOf(persistedPaymentDraft?.debtor?.email.orEmpty()) }
        var newDebtorType by rememberSaveable(context.transactionTypeIndex, context.clientId) { mutableStateOf(persistedPaymentDraft?.debtor?.debtorType?.ifBlank { "individual" } ?: "individual") }
        var newDebtorIdNumber by rememberSaveable(context.transactionTypeIndex, context.clientId) { mutableStateOf(persistedPaymentDraft?.debtor?.idNumber.orEmpty()) }
        var newDebtorCompanyName by rememberSaveable(context.transactionTypeIndex, context.clientId) { mutableStateOf(persistedPaymentDraft?.debtor?.companyName.orEmpty()) }
        var newDebtorCompanyIdNumber by rememberSaveable(context.transactionTypeIndex, context.clientId) { mutableStateOf(persistedPaymentDraft?.debtor?.companyIdNumber.orEmpty()) }
        var newDebtDueDateText by rememberSaveable(context.transactionTypeIndex, context.clientId) {
            mutableStateOf(
                persistedPaymentDraft?.newDebtDueDateText?.takeIf { it.isNotBlank() }
                    ?: persistedPaymentDraft?.debtor?.debtDueAtMillis?.takeIf { it > 0L }?.toStockDateInputText()
                    ?: ""
            )
        }

        val paymentModeStateKey = "payment_mode_${context.transactionTypeIndex}_${context.clientId}"

        var persistedPaymentDraftApplied by rememberSaveable(context.transactionTypeIndex, context.clientId) { mutableStateOf(false) }
        var paymentDraftEdited by rememberSaveable(context.transactionTypeIndex, context.clientId) { mutableStateOf(false) }
        var selectedPaymentModeId by rememberSaveable(context.transactionTypeIndex, context.clientId) {
            mutableStateOf(
                context.stateHost.state.value[paymentModeStateKey]?.takeIf { it in listOf("0", "1", "2") }
                    ?: persistedPaymentDraft?.paymentModeId?.takeIf { it in listOf("0", "1", "2") }
                    ?: "1"
            )
        }
        var amountFieldScrollRequest by remember(context.transactionTypeIndex, context.clientId) {
            mutableStateOf<String?>(null)
        }

        fun selectAmountField(field: String) {
            paymentDraftEdited = true
            if (activeAmountField != field) {
                activeAmountField = field
                amountFieldScrollRequest = field
            }
        }

        fun selectPaymentMode(modeId: String) {
            paymentDraftEdited = true
            selectedPaymentModeId = modeId
            amountFieldScrollRequest = null
            coroutineScope.launch { context.stateHost.setState(paymentModeStateKey to modeId) }
        }
        var paymentModeDefaultsInitializedFor by rememberSaveable(context.transactionTypeIndex, context.clientId) { mutableStateOf<String?>(null) }

        LaunchedEffect(context.transactionTypeIndex, context.clientId, persistedPaymentDraft?.updatedAtMillis) {
            val draft = persistedPaymentDraft ?: return@LaunchedEffect
            if (persistedPaymentDraftApplied || paymentDraftEdited) return@LaunchedEffect

            selectedCashlessPaymentMethodId = draft.cardPaymentOptionId
                .takeIf { it > 0 }
                ?.toString()
                ?: country.preferredCashlessPaymentOptionId
            cashText = draft.cashInputText.takeIf { it.isNotBlank() }
                ?: draft.paidCash.takeIf { it > 0.0 }?.let { moneyInputFromDouble(it) }
                ?: ""
            cardText = draft.cardInputText.takeIf { it.isNotBlank() }
                ?: draft.paidCard.takeIf { it > 0.0 }?.let { moneyInputFromDouble(it) }
                ?: ""
            debtText = draft.debtInputText.takeIf { it.isNotBlank() }
                ?: draft.debtor?.debtAmount?.takeIf { it > 0.0 }?.let { moneyInputFromDouble(it) }
                ?: ""
            activeAmountField = draft.activeAmountField.takeIf { it in listOf("cash", "card", "debt") }
                ?: activeAmountField
            selectedDebtorId = draft.selectedDebtorId?.takeIf { it.isNotBlank() }
                ?: draft.debtor?.id?.takeIf { it.isNotBlank() }

            val debtor = draft.debtor
            newDebtorFirstName = debtor?.firstName.orEmpty()
            newDebtorLastName = debtor?.lastName.orEmpty()
            newDebtorPhone = debtor?.phoneNumber.orEmpty()
            newDebtorEmail = debtor?.email.orEmpty()
            newDebtorType = debtor?.debtorType?.ifBlank { "individual" } ?: "individual"
            newDebtorIdNumber = debtor?.idNumber.orEmpty()
            newDebtorCompanyName = debtor?.companyName.orEmpty()
            newDebtorCompanyIdNumber = debtor?.companyIdNumber.orEmpty()
            newDebtDueDateText = draft.newDebtDueDateText.takeIf { it.isNotBlank() }
                ?: debtor?.debtDueAtMillis?.takeIf { it > 0L }?.toStockDateInputText()
                ?: ""

            draft.paymentModeId.takeIf { it in listOf("0", "1", "2") }?.let { restoredModeId ->
                paymentModeDefaultsInitializedFor = restoredModeId
                selectedPaymentModeId = restoredModeId
                context.stateHost.setState(paymentModeStateKey to restoredModeId)
            }
            persistedPaymentDraftApplied = true
        }

        val paymentMode = tabRowWidget(
            modifier = Modifier.padding(
                start = stateValues.marginTextField,
                top = stateValues.marginTextField,
                end = stateValues.marginTextField
            ),
            selectedIndexInitial = selectedPaymentModeId,
            tabs = listOf(
                TabContent("0", stateValues.stringCash) {
                    selectPaymentMode(it)
                },
                TabContent("1", stateValues.stringCashless) {
                    selectPaymentMode(it)
                },
                TabContent("2", stateValues.stringMixed) {
                    selectPaymentMode(it)
                }
            )
        )

        LaunchedEffect(paymentMode.id, total) {
            // A disk hydration or click can supersede the mode this effect was composed for.
            if (paymentMode.id != selectedPaymentModeId) return@LaunchedEffect
            if (persistedPaymentDraft != null && paymentModeDefaultsInitializedFor == null && paymentMode.id == persistedPaymentDraft.paymentModeId) {
                paymentModeDefaultsInitializedFor = paymentMode.id
                return@LaunchedEffect
            }

            if (paymentModeDefaultsInitializedFor == paymentMode.id) return@LaunchedEffect
            paymentModeDefaultsInitializedFor = paymentMode.id

            when (paymentMode.id) {
                "0" -> {
                    cashText = moneyInputFromDouble(total)
                    cardText = ""
                    debtText = ""
                    activeAmountField = "cash"
                }

                "1" -> {
                    cashText = ""
                    cardText = moneyInputFromDouble(total)
                    debtText = ""
                    activeAmountField = "card"
                }

                else -> {
                    cashText = ""
                    cardText = ""
                    debtText = ""
                    activeAmountField = "cash"
                }
            }
        }

        fun rawPaymentValue(field: String): String {
            return when (field) {
                "card" -> cardText
                "debt" -> debtText
                else -> cashText
            }
        }

        fun fieldMax(field: String): Double {
            if (paymentMode.id != "2")
                return Double.POSITIVE_INFINITY

            val cash = if (field == "cash") 0.0 else cashText.toMoneyDouble()
            val card = if (field == "card") 0.0 else cardText.toMoneyDouble()
            val debt = if (field == "debt") 0.0 else debtText.toMoneyDouble()

            return (total - cash - card - debt).coerceAtLeast(0.0).roundMoney()
        }

        fun setPaymentField(field: String, rawValue: String) {
            paymentDraftEdited = true
            paymentModeDefaultsInitializedFor = paymentMode.id
            val normalized = paymentInputNormalize(rawValue)
            val numericValue = normalized.toMoneyDouble()
            val maxValue = fieldMax(field)

            val finalText = if (paymentMode.id == "2" && numericValue > maxValue) {
                moneyInputFromDouble(maxValue)
            } else {
                normalized
            }

            when (field) {
                "card" -> cardText = finalText
                "debt" -> debtText = finalText
                else -> cashText = finalText
            }
        }

        val paidCash = when (paymentMode.id) {
            "1" -> 0.0
            else -> cashText.toMoneyDouble()
        }.roundMoney()

        val paidCard = when (paymentMode.id) {
            "0" -> 0.0
            "1" -> total
            else -> cardText.toMoneyDouble()
        }.roundMoney()

        val debtAmount = when (paymentMode.id) {
            "2" -> debtText.toMoneyDouble()
            else -> 0.0
        }.roundMoney()

        val selectedDebtor = debtors.orEmpty().find { it.id == selectedDebtorId }

        val draftDebtor = if (debtAmount > 0.0) {
            selectedDebtor?.copy(
                debtAmount = debtAmount,
                originalDebtAmount = selectedDebtor.originalDebtAmount ?: selectedDebtor.debtAmount,
                currency = currencyCode
            ) ?: DebtorDataModel(
                debtorType = newDebtorType,
                firstName = if (newDebtorType == "individual") newDebtorFirstName.trim() else "",
                lastName = if (newDebtorType == "individual") newDebtorLastName.trim() else "",
                companyName = if (newDebtorType == "company") newDebtorCompanyName.trim() else "",
                idNumber = if (newDebtorType == "individual") newDebtorIdNumber.trim() else "",
                companyIdNumber = if (newDebtorType == "company") newDebtorCompanyIdNumber.trim() else "",
                phoneNumber = newDebtorPhone.trim(),
                email = newDebtorEmail.trim(),
                debtAmount = debtAmount,
                originalDebtAmount = debtAmount,
                debtCreatedAtMillis = getCurrentTimeMillis(),
                debtDueAtMillis = stockDateInputTextToMillis(newDebtDueDateText),
                currency = currencyCode
            )
        } else null

        LaunchedEffect(
            context.transactionTypeIndex,
            context.clientId,
            paymentMode.id,
            cashText,
            cardText,
            debtText,
            activeAmountField,
            paidCash,
            paidCard,
            selectedCashlessPaymentMethodId,
            debtAmount,
            selectedDebtorId,
            newDebtorType,
            newDebtorFirstName,
            newDebtorLastName,
            newDebtorPhone,
            newDebtorEmail,
            newDebtorIdNumber,
            newDebtorCompanyName,
            newDebtorCompanyIdNumber,
            newDebtDueDateText,
            currencyCode
        ) {
            delay(180L)
            setTransactionPaymentDraft(
                TransactionPaymentDraftDataModel(
                    transactionTypeIndex = context.transactionTypeIndex,
                    clientId = context.clientId,
                    paymentModeId = paymentMode.id,
                    paidCash = paidCash,
                    paidCard = paidCard,
                    cardPaymentOptionId = selectedCashlessPaymentMethodId.toIntOrNull() ?: 0,
                    debtor = draftDebtor,
                    cashInputText = cashText,
                    cardInputText = cardText,
                    debtInputText = debtText,
                    activeAmountField = activeAmountField,
                    selectedDebtorId = selectedDebtorId,
                    newDebtDueDateText = newDebtDueDateText,
                    updatedAtMillis = getCurrentTimeMillis()
                )
            )
        }

        val debtorValid = debtAmount <= 0.0 || (
                selectedDebtor != null ||
                        newDebtorFirstName.trim().isNotBlank() ||
                        newDebtorLastName.trim().isNotBlank() ||
                        newDebtorPhone.trim().isNotBlank() ||
                        newDebtorEmail.trim().isNotBlank() ||
                        newDebtorCompanyName.trim().isNotBlank() ||
                        newDebtorCompanyIdNumber.trim().isNotBlank() ||
                        newDebtorIdNumber.trim().isNotBlank()
                )

        val paymentSum = (paidCash + paidCard + debtAmount).roundMoney()
        val remainingAmount = (total - paymentSum).coerceAtLeast(0.0).roundMoney()
        val paidMoneyAmount = (paidCash + paidCard).roundMoney()
        val cashRequiredAmount = (total - paidCard - debtAmount).coerceAtLeast(0.0).roundMoney()
        val changeAmount = (paidCash - cashRequiredAmount).coerceAtLeast(0.0).roundMoney()

        val paymentValid = goodsInCart.isNotEmpty() &&
                total > 0.0 &&
                paidCash >= 0.0 &&
                paidCard >= 0.0 &&
                debtAmount >= 0.0 &&
                paymentSum >= total &&
                debtorValid

        fun targetForField(field: String): Double {
            return when (field) {
                "card" -> (total - paidCash - debtAmount).coerceAtLeast(0.0)
                "debt" -> (total - paidCash - paidCard).coerceAtLeast(0.0)
                else -> (total - paidCard - debtAmount).coerceAtLeast(0.0)
            }.roundMoney()
        }

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        TransactionPaymentHeadsUpCard(
            total = total,
            paid = paidMoneyAmount,
            debt = debtAmount,
            remaining = remainingAmount,
            change = changeAmount,
            currencySymbol = currencySymbol,
            currencyCode = currencyCode,
            paymentValid = paymentValid
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        val paymentListState = rememberPersistentLazyListState(
            stateHost = context.stateHost,
            stateKey = "transaction_payment_scroll_${context.transactionTypeIndex}_${context.clientId}"
        )

        val amountSlots = remember(paymentMode.id, activeAmountField) {
            paymentAmountSlots(paymentMode.id, activeAmountField)
        }
        LaunchedEffect(amountFieldScrollRequest, paymentMode.id, activeAmountField) {
            val field = amountFieldScrollRequest ?: return@LaunchedEffect
            val index = amountSlots.indexOfFirst { !it.keypad && it.field == field }
            if (index >= 0) paymentListState.animateScrollToItem(index)
            if (amountFieldScrollRequest == field) amountFieldScrollRequest = null
        }

        LazyColumn(
            state = paymentListState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = stateValues.marginTextField)
        ) {
            items(amountSlots, key = { it.key }, contentType = { if (it.keypad) "payment-keypad" else "payment-field" }) { slot ->
                val field = slot.field
                if (slot.keypad) {
                    TransactionNumpad(onInput = { token ->
                        setPaymentField(field, paymentInputAppend(rawPaymentValue(field), token))
                    })
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    TransactionQuickAmountButtons(
                        targetAmount = if (paymentMode.id == "0") total else targetForField(field),
                        currencyCode = currencyCode,
                        currencySymbol = currencySymbol,
                        includeExactRemaining = true,
                        currentAmount = rawPaymentValue(field).toMoneyDouble(),
                        onAmountSelected = { setPaymentField(field, moneyInputFromDouble(it)) }
                    )
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                } else {
                    TransactionPaymentAmountField(
                        title = when (field) {
                            "card" -> stateValues.stringCashless
                            "debt" -> stateValues.stringDebtors
                            else -> stateValues.stringCash
                        },
                        value = rawPaymentValue(field),
                        identityKey = "payment:${context.transactionTypeIndex}:${context.clientId}:$field",
                        selected = activeAmountField == field,
                        leadingIconPath = if (field == "debt") stateValues.drawablePathIconDebtors else stateValues.drawablePathIconFinances,
                        onSelected = { selectAmountField(field) },
                        onValueChange = { setPaymentField(field, it) }
                    )
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                }
            }
            when (paymentMode.id) {
                "1" -> {
                    item {
                        Text(
                            text = stateValues.stringCashless,
                            color = stateValues.TextColor,
                            fontSize = stateValues.accentTextSize,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    }

                    items(country.cashlessPaymentOptions.chunked(2)) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            row.forEach { option ->
                                TransactionPaymentOptionButton(
                                    modifier = Modifier.weight(1f),
                                    text = option.name.extractLocalizedString(stateValues.appLanguage) ?: option.id,
                                    selected = selectedCashlessPaymentMethodId == option.id
                                ) {
                                    paymentDraftEdited = true
                                    selectedCashlessPaymentMethodId = option.id
                                }
                            }

                            repeat(2 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }

                        Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    }
                }

                "2" -> {
                    item(key = "payment-debt-summary") {
                        TransactionDebtText(
                            debtAmount = debtAmount,
                            currencySymbol = currencySymbol
                        )

                        if (debtAmount > 0.0) {
                            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                            Text(
                                text = localizedStringResource(286, "Select debtor"),
                                color = stateValues.TextColor,
                                fontSize = stateValues.accentTextSize,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(stateValues.marginTextField))
                        }
                    }

                    if (debtAmount > 0.0) {
                        items(debtors.orEmpty().filter { it.debtAmount >= 0.0 }) { debtor ->
                            DebtorPaymentCard(
                                debtor = debtor,
                                selected = debtor.id == selectedDebtorId,
                                onClick = {
                                    paymentDraftEdited = true
                                    selectedDebtorId = if (selectedDebtorId == debtor.id) null else debtor.id
                                }
                            )

                            Spacer(modifier = Modifier.height(stateValues.marginTextField))
                        }

                        item {
                            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                            Text(
                                text = localizedStringResource(331, "Or add debtor for this transaction"),
                                color = stateValues.TextColor,
                                fontSize = stateValues.textSize,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(stateValues.marginTextField))

                            if (selectedDebtorId == null) {
                                SimpleDropdownField(
                                    title = localizedStringResource(288, "Debtor form"),
                                    selectedId = newDebtorType,
                                    options = listOf(DropdownOption("individual", localizedStringResource(289, "Individual")), DropdownOption("company", localizedStringResource(290, "Company"))),
                                    placeholder = localizedStringResource(288, "Debtor form"),
                                    onSelected = { paymentDraftEdited = true; newDebtorType = it }
                                )

                                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                if (newDebtorType == "company") {
                                    TransactionPlainTextField(
                                        title = localizedStringResource(291, "Company name"),
                                        value = newDebtorCompanyName,
                                        placeholder = localizedStringResource(291, "Company name"),
                                        leadingIconPath = stateValues.drawablePathIconStores,
                                        onValueChange = { paymentDraftEdited = true; newDebtorCompanyName = it }
                                    )

                                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                    TransactionPlainTextField(
                                        title = localizedStringResource(292, "Company ID / BIN"),
                                        value = newDebtorCompanyIdNumber,
                                        placeholder = localizedStringResource(292, "Company ID / BIN"),
                                        leadingIconPath = stateValues.drawablePathIconStores,
                                        keyboardType = KeyboardType.Number,
                                        onValueChange = { paymentDraftEdited = true; newDebtorCompanyIdNumber = it.filter { ch -> ch.isDigit() }.take(32) }
                                    )
                                } else {
                                    TransactionPlainTextField(
                                        title = stateValues.stringFirstName,
                                        value = newDebtorFirstName,
                                        placeholder = stateValues.stringFirstName,
                                        leadingIconPath = stateValues.drawablePathIconPerson,
                                        onValueChange = { paymentDraftEdited = true; newDebtorFirstName = it }
                                    )

                                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                    TransactionPlainTextField(
                                        title = stateValues.stringLastName,
                                        value = newDebtorLastName,
                                        placeholder = stateValues.stringLastName,
                                        leadingIconPath = stateValues.drawablePathIconPerson,
                                        onValueChange = { paymentDraftEdited = true; newDebtorLastName = it }
                                    )

                                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                    TransactionPlainTextField(
                                        title = localizedStringResource(293, "ID number"),
                                        value = newDebtorIdNumber,
                                        placeholder = localizedStringResource(294, "ID number optional"),
                                        leadingIconPath = stateValues.drawablePathIconPerson,
                                        keyboardType = KeyboardType.Number,
                                        onValueChange = { paymentDraftEdited = true; newDebtorIdNumber = it.filter { ch -> ch.isDigit() }.take(32) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                DebtorPhoneInput(
                                    value = newDebtorPhone,
                                    identityKey = "payment-debtor-phone:${stateValues.userAccount?.id}:${stateValues.activeStoreId}:${context.transactionTypeIndex}:${context.clientId}",
                                    onValueChange = { paymentDraftEdited = true; newDebtorPhone = it }
                                )

                                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                TransactionPlainTextField(
                                    title = stateValues.stringEmail,
                                    value = newDebtorEmail,
                                    placeholder = stateValues.stringEmail,
                                    leadingIconPath = stateValues.drawablePathIconEmail,
                                    keyboardType = KeyboardType.Email,
                                    onValueChange = { paymentDraftEdited = true; newDebtorEmail = it }
                                )

                                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                StockDatePartsEditor(localizedStringResource(314, "Final due date"), newDebtDueDateText) { paymentDraftEdited = true; newDebtDueDateText = it }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                TransactionPaymentSummaryRows(
                    paid = paidMoneyAmount,
                    debt = debtAmount,
                    remaining = remainingAmount,
                    change = changeAmount,
                    currencySymbol = currencySymbol,
                    large = false,
                    paymentValid = paymentValid
                )

                Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            actionButton(
                text = stateValues.stringReceipt,
                enabled = paymentValid && stateValues.latestNotification == null,
                onClick = {
                    val draft = TransactionPaymentDraftDataModel(
                        transactionTypeIndex = context.transactionTypeIndex,
                        clientId = context.clientId,
                        paymentModeId = paymentMode.id,
                        paidCash = paidCash,
                        paidCard = paidCard,
                        cardPaymentOptionId = selectedCashlessPaymentMethodId.toIntOrNull() ?: 0,
                        debtor = draftDebtor,
                        cashInputText = cashText,
                        cardInputText = cardText,
                        debtInputText = debtText,
                        activeAmountField = activeAmountField,
                        selectedDebtorId = selectedDebtorId,
                        newDebtDueDateText = newDebtDueDateText,
                        updatedAtMillis = getCurrentTimeMillis()
                    )

                    setTransactionPaymentDraft(draft)

                    coroutineScope.launch {
                        when (context.transactionTypeIndex) {
                            0 -> Navigation.TransactionSale.go(NavigationScreenModel.Transaction.ReceiptPreview)
                            1 -> Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.ReceiptPreview)
                            else -> Navigation.TransactionSupply.go(NavigationScreenModel.Transaction.ReceiptPreview)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}


//@Composable
//fun AppConfiguration.TransactionPaymentScreen() {
//  Column(
//    modifier = Modifier
//      .fillMaxSize()
//  ) {
//    val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
//      is NavigationScreenModel.Transaction.MainSale -> {
//        0
//      }
//
//      is NavigationScreenModel.Transaction.MainReturn -> {
//        1
//      }
//
//      else -> {
//        2
//      }
//    }
//
//    val clientId = when (stateValues.navigationScreensMain.last()) {
//      is NavigationScreenModel.Transaction.MainReturn -> {
//        stateValues.navigationTransactionReturnClientId
//      }
//
//      is NavigationScreenModel.Transaction.MainSupply -> {
//        stateValues.navigationTransactionSupplyClientId
//      }
//
//      else -> {
//        stateValues.navigationTransactionSaleClientId
//      }
//    }
//
//    ScreenAppBarWidget(
//      title = stateValues.stringPayment,
//      onBack = if (
//        when (transactionTypeIndex) {
//          0 -> !Navigation.TransactionSale.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
//          1 -> !Navigation.TransactionReturn.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
//          else -> !Navigation.TransactionSupply.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
//        }
//      ) {
//        {
//          coroutineScope.launch {
//            when (transactionTypeIndex) {
//              0 -> Navigation.TransactionSale.pop()
//              1 -> Navigation.TransactionReturn.pop()
//              2 -> Navigation.TransactionSupply.pop()
//            }
//          }
//        }
//      } else null
//    )
//
//    val goodsInCart by getCartState(transactionTypeIndex, clientId).collectAsState()
//
//    var selectedCashlessPaymentMethodId by rememberSaveable {
//      mutableStateOf(stateValues.globalAppConfiguration.countries.find {
//        it.locale.equals(stateValues.userAccount?.countryLocale, true)
//      }?.preferredCashlessPaymentOptionId ?: "0")
//    }
//
//    Column(
//      modifier = Modifier
//        .fillMaxWidth()
//        .weight(1f)
//    ) {
//      val scopeRowContent = tabRowWidget(
//        modifier = Modifier
//          .padding(stateValues.marginTextField),
//        selectedIndexInitial = "1",
//        tabs = listOf(
//          TabContent("0", stateValues.stringCash),
//          TabContent("1", stateValues.stringCashless),
//          TabContent("2", stateValues.stringMixed)
//        )
//      )
//
//      when (scopeRowContent.id) {
//        "0" -> {
//
//        }
//
//        "1" -> {
//          Column(
//            modifier = Modifier
//              .weight(1f)
//              .fillMaxSize()
//              .padding(stateValues.marginTextField),
//          ) {
//            LazyVerticalGrid(columns = GridCells.Fixed(2)) {
//              stateValues.globalAppConfiguration.countries.find {
//                it.locale.equals(stateValues.userAccount?.countryLocale, true)
//              }?.cashlessPaymentOptions?.forEach { item ->
//                item {
//                  Box(
//                    modifier = Modifier
//                      .weight(1f)
//                      .clip(RoundedCornerShape(stateValues.cornerRadius))
//                      .border(
//                        stateValues.unfocusedBorderWidth,
//                        stateValues.PlaceholderTextColor,
//                        RoundedCornerShape(
//                          stateValues.cornerRadius
//                        ),
//                      )
//                      .background(if (selectedCashlessPaymentMethodId == item.id) stateValues.AccentColor else Color.Transparent)
//                      .aitaClickable(
//                        interactionSource = remember {
//                          MutableInteractionSource()
//                        },
//                        indication = ripple(color = if (selectedCashlessPaymentMethodId == item.id) stateValues.AccentTextColor else stateValues.TextColor),
//                        onClick = {
//                          selectedCashlessPaymentMethodId = item.id
//                        }
//                      ),
//                    contentAlignment = Alignment.Center
//                  ) {
//                    item.name.extractLocalizedString(stateValues.appLanguage)?.let { text ->
//                      Text(
//                        text = text,
//                        modifier = Modifier
//                          .padding(24.dp),
//                        color = if (selectedCashlessPaymentMethodId == item.id) stateValues.AccentTextColor else stateValues.TextColor
//                      )
//                    }
//                  }
//                }
//              }
//            }
//          }
//        }
//
//        else -> {
//
//        }
//      }
//    }
//
//    if (goodsInCart.isNotEmpty())
//      Column(
//        modifier = Modifier
//          .fillMaxWidth()
//          .padding(horizontal = 8.dp)
//      ) {
//        Spacer(modifier = Modifier.height(4.dp))
//
//        actionButton(
//          text = stateValues.stringReceipt,
//          enabled = stateValues.latestNotification == null,
//          onClick = {
//            coroutineScope.launch {
//              when (transactionTypeIndex) {
//                0 -> {
//                  Navigation.TransactionSale.go(NavigationScreenModel.Transaction.ReceiptPreview)
//                }
//
//                1 -> {
//                  Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.ReceiptPreview)
//                }
//
//                else -> {
//                  Navigation.TransactionSupply.go(NavigationScreenModel.Transaction.ReceiptPreview)
//                }
//
//              }
//            }
//          }
//        )
//
//        Spacer(modifier = Modifier.height(4.dp))
//      }
//  }
//}

