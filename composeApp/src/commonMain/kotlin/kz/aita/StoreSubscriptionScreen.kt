package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun AppConfiguration.rememberStoreSubscriptionGate(storeId: String? = stateValues.activeStoreId): StoreSubscriptionGate {
    val revision = subscriptionAccessRevisionState.collectAsState()
    val account = stateValues.userAccount?.id
    val clock = remember(storeId, account) { mutableStateOf(getCurrentTimeMillis()) }
    LaunchedEffect(storeId, account) {
        while (isActive) {
            delay(1_000L)
            clock.value = getCurrentTimeMillis()
        }
    }
    // Re-evaluate the real registry; derivedStateOf prevents a full main-tree recompose on every tick.
    return remember(storeId, account) {
        derivedStateOf { revision.value; currentStoreSubscriptionGate(storeId, clock.value) }
    }.value
}

@Composable
internal fun AppConfiguration.rememberStoreSubscriptionAccess(storeId: String? = stateValues.activeStoreId): Boolean =
    rememberStoreSubscriptionGate(storeId) == StoreSubscriptionGate.Active

@Composable
internal fun AppConfiguration.rememberStoreWorkspaceGate(storeId: String? = stateValues.activeStoreId): StoreSubscriptionGate {
    val store = stateValues.stores.findStoreOrBranchForUi(storeId)
    if (store?.isManagementStore() == true) return if (currentStoreHasWorkspaceAccess(storeId))
        StoreSubscriptionGate.Active else StoreSubscriptionGate.Required
    return rememberStoreSubscriptionGate(storeId)
}

@Composable
internal fun AppConfiguration.rememberStoreWorkspaceAccess(storeId: String? = stateValues.activeStoreId): Boolean =
    rememberStoreWorkspaceGate(storeId) == StoreSubscriptionGate.Active

@Composable
internal fun AppConfiguration.SubscriptionRequiredPane(modifier: Modifier = Modifier) {
    val store = stateValues.activeStoreId
    val loading by subscriptionLoadingStoreIdState.collectAsState()
    val gate = rememberStoreSubscriptionGate(store)
    Column(modifier.fillMaxSize().padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (store != null && loading == store) LoadingSkeleton(layout = LoadingLayout.Subscription, modifier = Modifier.fillMaxWidth(), rows = 3)
        else MessageText(Modifier.fillMaxWidth(), if (store == null)
            authUiText("Choose a location", "Выберите торговую точку", "Сауда нүктесін таңдаңыз", "Жайды тандаңыз")
            else eventMessage(if (gate == StoreSubscriptionGate.Checking) "subscription.verify" else "subscription.required")
                .visibleLocalizedString(stateValues.appLanguage, ""))
        Spacer(Modifier.height(16.dp))
        actionButton(text = if (store == null) stateValues.stringStores else stateValues.stringSubscription,
            iconPath = if (store == null) stateValues.drawablePathIconStores else stateValues.drawablePathIconSubscription,
            autoLoading = false, confirmationRequired = false,
            onClick = { coroutineScope.launch {
                Navigation.goMain(NavigationScreenModel.Menu.Main)
                Navigation.Menu.go(if (store == null) NavigationScreenModel.Menu.Stores else NavigationScreenModel.Menu.StoreSubscriptionPlans,
                    stateValues.isNarrowScreen)
            } })
    }
}

@Composable
private fun AppConfiguration.SubscriptionSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
        .clip(RoundedCornerShape(stateValues.cornerRadius))
        .background(stateValues.BackgroundColor)
        .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.65f), RoundedCornerShape(stateValues.cornerRadius))
        .padding(stateValues.marginTextFieldGroup), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun AppConfiguration.MenuStoreSubscriptionPlansScreen() {
    val storeId = stateValues.activeStoreId
    val accountId = stateValues.userAccount?.id
    val dashboardState by currentSubscriptionDashboardState.value.collectAsState()
    val loadingStore by subscriptionLoadingStoreIdState.collectAsState()
    val loadFailure by subscriptionLoadFailureState.collectAsState()
    val pendingId by pendingSubscriptionCommandIdState.collectAsState()
    val hasAccess = rememberStoreSubscriptionAccess(storeId)
    val dashboard = (dashboardState as? DataState.Success<SubscriptionDashboardDataModel>)?.payload
        ?.takeIf { it.subscription.storeId == storeId }
    val subscription = dashboard?.subscription
    val lifetimeOnly = hasAccess && subscription?.accessKind == SUBSCRIPTION_ACCESS_LIFETIME
    val scope = rememberCoroutineScope()
    // Drafts/commands live ABOVE tab branches. Promo secrets are not saved in Android instance state.
    var promo by remember(accountId, storeId) { mutableStateOf("") }
    var quotedPromo by remember(accountId, storeId) { mutableStateOf("") }
    var quote by remember(accountId, storeId) { mutableStateOf<StoreSubscriptionQuoteDataModel?>(null) }
    var pendingRequest by remember(accountId, storeId) { mutableStateOf<StoreSubscriptionUpdateRequestDataModel?>(null) }
    var autoRenew by remember(accountId, storeId) { mutableStateOf(false) }
    var busy by remember(accountId, storeId) { mutableStateOf(false) }
    var feedback by remember(accountId, storeId) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var feedbackNegative by remember(accountId, storeId) { mutableStateOf(false) }
    fun launchAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { feedback = eventMessage("subscription.result_unknown"); feedbackNegative = true }
            finally { busy = false }
        }
    }
    suspend fun submit(request: StoreSubscriptionUpdateRequestDataModel) {
        pendingRequest = request
        val response = submitStoreSubscriptionNow(request)
        feedback = response.message
        feedbackNegative = response.negative
        if (!response.negative) { quote = null; pendingRequest = null; promo = ""; quotedPromo = "" }
        else if (pendingSubscriptionCommandIdState.value == null) { pendingRequest = null; quote = null }
    }
    LaunchedEffect(accountId, storeId) {
        if (storeId != null) {
            refreshStoreSubscriptionNow(storeId)
            val recovered = recoverPendingSubscriptionCommand(storeId)
            if (recovered != null && recovered.negative) { feedback = recovered.message; feedbackNegative = true }
        }
    }
    AitaScreenColumn(
        Modifier.fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(title = stateValues.stringSubscription, iconPath = stateValues.drawablePathIconSubscription,
                onBack = if (Navigation.Menu.isVeryFirstScreen(stateValues.isNarrowScreen)) null
                    else { { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } } })
        }
    ) {
        if (storeId == null) {
            SubscriptionRequiredPane(Modifier.weight(1f))
            return@AitaScreenColumn
        }
        val tabs = listOf(TabContent("current", localizedStringResource(587, "Current subscription"), icon = AitaTabIcon.Security),
            TabContent("plans", authUiText("Plans", "Тарифы", "Тарифтер", "Тарифтер"))) +
            if (dashboard?.canManage == true) listOf(TabContent("charges", localizedStringResource(589, "Subscription charges"))) else emptyList()
        val section = if (lifetimeOnly) "current" else sectionTabsWidget(stateKey = "location-subscription:$accountId:$storeId", tabs = tabs,
            modifier = Modifier.fillMaxWidth().padding(horizontal = stateValues.marginTextField))
        LazyColumn(state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.StoreSubscriptionPlans, "$storeId:$section"),
            modifier = Modifier.weight(1f).fillMaxWidth().aitaWidthCap(760.dp)
                .align(Alignment.CenterHorizontally).aitaPaneEntrance(section).padding(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
            if (feedback != null && (!lifetimeOnly || feedbackNegative)) item("feedback") {
                Text(feedback.orEmpty().visibleLocalizedString(stateValues.appLanguage, ""),
                    color = if (feedbackNegative) stateValues.ErrorColor else stateValues.OkayColor, fontSize = stateValues.smallTextSize)
            }
            if (dashboard == null) {
                item("loading") {
                    if (loadingStore == storeId) LoadingSkeleton(layout = LoadingLayout.Subscription, modifier = Modifier.fillMaxWidth(), rows = 4)
                    else {
                        MessageText(text = loadFailure.orEmpty().visibleLocalizedString(stateValues.appLanguage,
                            eventMessage("subscription.verify").visibleLocalizedString(stateValues.appLanguage, "")))
                        actionButton(text = localizedStringResource(237, "Refresh"), loading = busy, autoLoading = false,
                            confirmationRequired = false, onClick = { launchAction { refreshStoreSubscriptionNow(storeId) } })
                    }
                }
            } else {
                if (pendingId != null && !lifetimeOnly) item("pending-command") {
                    SubscriptionSurface {
                        Text(eventMessage("subscription.result_unknown").visibleLocalizedString(stateValues.appLanguage, ""),
                            color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                        actionButton(text = authUiText("Check request status", "Проверить результат запроса", "Сұрау нәтижесін тексеру", "Суроо-талаптын абалын текшерүү"),
                            loading = busy, autoLoading = false, confirmationRequired = false, onClick = { launchAction {
                                val response = recoverPendingSubscriptionCommand(storeId)
                                feedback = response?.message; feedbackNegative = response?.negative == true
                            } })
                        pendingRequest?.let { request ->
                            actionButton(text = authUiText("Retry the same request", "Повторить тот же запрос", "Сол сұрауды қайталау", "Ошол эле суроо-талапты кайталоо"),
                                loading = busy, autoLoading = false, confirmationRequired = false,
                                onClick = { launchAction { submit(request) } })
                        }
                        actionButton(text = authUiText("Refresh and review again", "Обновить и проверить заново", "Жаңартып, қайта тексеру", "Жаңыртып, кайра карап чыгуу"),
                            loading = busy, autoLoading = false, confirmationRequired = true, onClick = { launchAction {
                                if (reviewSubscriptionAfterUnknownResult(storeId)) { pendingRequest = null; quote = null; feedback = null }
                            } })
                    }
                }
                if (section == "current") item("current") {
                    val current = dashboard.subscription
                    val lifetime = hasAccess && current.accessKind == SUBSCRIPTION_ACCESS_LIFETIME
                    val plan = dashboard.plans.firstOrNull { it.id == current.planId }
                    if (lifetime) LifetimeSubscriptionCard()
                    else SubscriptionSurface {
                        Text(plan?.name?.visibleLocalizedString(stateValues.appLanguage, "Basic") ?: "Basic",
                            color = stateValues.TextColor, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
                        Text(if (hasAccess) localizedStringResource(812, "active") else
                            authUiText("Not active for this location", "Для этой точки не активна", "Бұл нүктеде белсенді емес", "Бул жай үчүн активдүү эмес"),
                            color = if (hasAccess) stateValues.AccentColor else stateValues.PlaceholderTextColor, fontSize = stateValues.accentTextSize)
                        Text(authUiText("Access belongs to this location only. Branches subscribe separately.",
                            "Доступ действует только для этой точки. У каждого филиала своя подписка.",
                            "Қолжетімділік тек осы нүктеге арналған. Әр филиалға бөлек жазылым қажет.", "Мүмкүнчүлүк ушул жайга гана таандык. Филиалдар өзүнчө жазылат."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        current.currentPeriodEndMillis?.let { end ->
                            Text(authUiText("Access until", "Доступ до", "Қолжетімділік мерзімі", "Мүмкүнчүлүк аяктайт") + ": " + securitySessionDateTimeText(end),
                                color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                        }
                        if (dashboard.canManage) {
                            val wallet = dashboard.billingWallet
                            Text(authUiText("Billing owner's balance", "Баланс владельца для оплаты", "Төлем жасайтын иесінің балансы", "Төлөөчү ээсинин балансы") + ": " +
                                (wallet?.let { it.available.aitaMoney(it.currencyCode) } ?: "—"),
                                color = changedValueColor(wallet?.balanceMinor, "$accountId:$storeId:billing-wallet"), fontSize = stateValues.smallTextSize)
                            if ((hasAccess || current.autoRenew) && current.accessKind == SUBSCRIPTION_ACCESS_PAID) {
                                Text(authUiText("Regular renewal", "Обычное продление", "Әдеттегі ұзарту", "Кезектеги узартуу") + ": " + current.renewalPriceMinor.fromMinorCurrencyUnits().aitaMoney(current.currencyCode),
                                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                actionButton(text = if (current.autoRenew) authUiText("Turn off renewal", "Отключить продление", "Ұзартуды өшіру", "Узартууну өчүрүү")
                                    else authUiText("Enable paid renewal", "Включить платное продление", "Ақылы ұзартуды қосу", "Акы төлөнүүчү узартууну күйгүзүү"),
                                    enabled = !busy && pendingId == null, loading = busy, autoLoading = false, confirmationRequired = true,
                                    onClick = { launchAction { submit(StoreSubscriptionUpdateRequestDataModel(storeId, current.planId,
                                        autoRenew = !current.autoRenew, activateNow = false, commandId = newClientSideUuidString(), expectedRevision = current.revision)) } })
                            }
                        } else Text(authUiText("Only the owner or subscription manager can activate or change billing.",
                            "Подключить подписку и изменить оплату может владелец или управляющий подпиской.",
                            "Жазылымды иесі немесе жазылым басқарушысы ғана қосып, төлемді өзгерте алады.", "Төлөмдөрдү ээси же жазылууну башкаруучу гана иштетип же өзгөртө алат."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        actionButton(text = localizedStringResource(237, "Refresh"), autoLoading = false, loading = busy, confirmationRequired = false,
                            onClick = { launchAction { refreshStoreSubscriptionNow(storeId) } })
                    }
                }
                if (!lifetimeOnly && section == "plans") {
                    val plan = dashboard.plans.firstOrNull { it.id == SUBSCRIPTION_BASIC_PLAN && !it.hidden }
                    if (plan == null) item("no-regional-price") { MessageText(text = eventMessage("subscription.price_unavailable").visibleLocalizedString(stateValues.appLanguage, "")) }
                    else item("basic-offer") {
                        SubscriptionSurface {
                            Text(plan.name.visibleLocalizedString(stateValues.appLanguage, "Basic"), color = stateValues.TextColor, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
                            Text(plan.price.aitaMoney(plan.currencyCode) + " / " + subscriptionPeriodText(plan.periodUnit, plan.periodCount),
                                color = stateValues.AccentColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                            Text(plan.description.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            if (dashboard.canManage && !(hasAccess && subscription?.accessKind == SUBSCRIPTION_ACCESS_LIFETIME)) {
                                genericTextField(valueInitial = promo, identityKey = "subscription-promo:$accountId:$storeId", parentOwnsValue = true,
                                    enabled = !busy && pendingId == null, autoFocus = false, enableVoiceInput = false,
                                    retainTextAcrossRecreation = false, persistTextDraft = false,
                                    placeholderText = authUiText("Promo code (optional)", "Промокод (необязательно)", "Промокод (міндетті емес)", "Промокод (милдеттүү эмес)"),
                                    leadingIconPath = stateValues.drawablePathIconSubscription,
                                    onValueChange = { value, apply -> promo = value.take(96); quote = null; pendingRequest = null; apply() })
                                val reviewed = quote
                                if (reviewed == null) actionButton(text = authUiText("Review offer", "Проверить предложение", "Ұсынысты тексеру", "Сунушту кароо"),
                                    enabled = !busy && pendingId == null, loading = busy, autoLoading = false, confirmationRequired = false,
                                    onClick = { launchAction {
                                        val response = quoteStoreSubscriptionNow(StoreSubscriptionQuoteRequestDataModel(storeId, plan.id, promo))
                                        quote = response.payload.takeIf { !response.negative }
                                        quotedPromo = promo
                                        autoRenew = false
                                        feedback = response.message; feedbackNegative = response.negative
                                    } })
                                else {
                                    Text(when (reviewed.accessKind) {
                                        SUBSCRIPTION_ACCESS_LIFETIME -> authUiText("Lifetime access · no expiry", "Бессрочный доступ · без срока окончания", "Мерзімсіз қолжетімділік", "Мөөнөтсүз мүмкүнчүлүк · бүтүү күнү жок")
                                        SUBSCRIPTION_ACCESS_TIMED -> authUiText("Access period", "Период доступа", "Қолжетімділік мерзімі", "Мүмкүнчүлүк мөөнөтү") + ": " +
                                            ((reviewed.durationMillis ?: 0L) / 86_400_000.0).toStockMoneyText() + " " + authUiText("days", "дней", "күн", "күн")
                                        else -> plan.name.visibleLocalizedString(stateValues.appLanguage, "Basic")
                                    }, color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                                    Text(authUiText("Charged now", "Списание сейчас", "Қазір алынатын төлем", "Азыр алынган төлөм") + ": " + reviewed.chargeMinor.fromMinorCurrencyUnits().aitaMoney(reviewed.currencyCode),
                                        color = stateValues.AccentColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                                    if (reviewed.canAutoRenew) Row(verticalAlignment = Alignment.CenterVertically) {
                                        AitaRoundCheckbox(autoRenew, { autoRenew = it }, enabled = !busy && pendingId == null)
                                        Text(authUiText("Automatically renew at", "Автопродление за", "Автоматты ұзарту бағасы", "Автоматтык узартуу убактысы") + " " +
                                            reviewed.regularPriceMinor.fromMinorCurrencyUnits().aitaMoney(reviewed.currencyCode) + " / " + subscriptionPeriodText(plan.periodUnit, plan.periodCount),
                                            color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                                    }
                                    else Text(authUiText("No automatic paid renewal", "Без автоматического платного продления", "Автоматты ақылы ұзарту жоқ", "Автоматтык акы төлөнүүчү узартуу жок"),
                                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                    if (hasAccess && reviewed.accessKind == SUBSCRIPTION_ACCESS_LIFETIME) Text(
                                        authUiText("Lifetime access replaces the remaining paid period. No automatic refund is issued.",
                                            "Бессрочный доступ заменит оставшийся оплаченный период. Автоматического возврата нет.",
                                            "Мерзімсіз қолжетімділік қалған ақылы кезеңді ауыстырады. Автоматты қайтарым жасалмайды.", "Мөөнөтсүз мүмкүнчүлүк акы төлөнгөн калган мезгилдин ордуна келет. Автоматтык түрдө акча кайтарылбайт."),
                                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                    actionButton(text = if (reviewed.chargeMinor == 0L) authUiText("Activate access", "Активировать доступ", "Қолжетімділікті қосу", "Мүмкүнчүлүктү иштетүү")
                                        else authUiText("Pay and activate", "Оплатить и подключить", "Төлеп, қосу", "Төлөө жана иштетүү"),
                                        enabled = !busy && pendingId == null, loading = busy, autoLoading = false, confirmationRequired = true,
                                        onClick = { launchAction { submit(StoreSubscriptionUpdateRequestDataModel(storeId, reviewed.planId,
                                            autoRenew = autoRenew && reviewed.canAutoRenew, activateNow = true, promoCode = quotedPromo,
                                            commandId = newClientSideUuidString(), expectedRevision = reviewed.expectedRevision,
                                            expectedChargeMinor = reviewed.chargeMinor, expectedCurrencyCode = reviewed.currencyCode,
                                            expectedPriceVersion = reviewed.priceVersion, quoteValidUntilMillis = reviewed.validUntilMillis,
                                            expectedRegularPriceMinor = reviewed.regularPriceMinor, expectedAccessKind = reviewed.accessKind,
                                            expectedDurationMillis = reviewed.durationMillis)) } })
                                }
                            }
                        }
                    }
                }
                if (!lifetimeOnly && section == "charges" && dashboard.canManage) {
                    if (dashboard.charges.isEmpty()) item("no-charges") { MessageText(Modifier.fillParentMaxSize(), text = stateValues.stringListEmpty) }
                    items(dashboard.charges, key = { it.id }) { charge ->
                        FinanceLedgerCard(title = if (charge.planId == SUBSCRIPTION_LIFETIME_PLAN) authUiText("Lifetime access", "Бессрочный доступ", "Мерзімсіз қолжетімділік", "Мөөнөтсүз мүмкүнчүлүк") else "Basic",
                            subtitle = if (charge.status == "promo_grant") authUiText("Promo code activated", "Активировано промокодом", "Промокодпен қосылған", "Промокод иштетилди") else when (charge.status) {
                                "paid" -> authUiText("Paid", "Оплачено", "Төленді", "Төлөндү")
                                "failed" -> authUiText("Payment failed", "Оплата не прошла", "Төлем орындалмады", "Төлөм өтпөй калды")
                                else -> charge.status
                            },
                            amountText = (if (charge.amountMinor == 0L) "" else "−") + charge.amount.aitaMoney(charge.currencyCode), timeMillis = charge.createdAtMillis)
                    }
                }
            }
        }
    }
}
