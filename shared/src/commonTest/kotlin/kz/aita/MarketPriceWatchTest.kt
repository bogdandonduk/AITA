package kz.aita

import kotlin.test.*
import kotlinx.serialization.encodeToString

class MarketPriceWatchTest {
    private val account = "11111111-1111-4111-8111-111111111111"
    private val store = "22222222-2222-4222-8222-222222222222"
    private val item = "33333333-3333-4333-8333-333333333333"
    private val code = "04006381333931"
    private val settings = MarketPriceWatchSettings(items = listOf(MarketPriceWatchItem(item, code)))
    private val basis = MarketShoppingBasis(code, "KZT", "0", 1.0)
    private fun page(median: Long = 10000, checked: Long = 1000) = MarketPriceInsightsPage(account, store, settings.reportRequest(),
        listOf(MarketPriceInsightRow(item, listOf(LocalizedStringDataModel("en", "Tea")), listOf(code), basis,
            ownPriceMinor = 12000, referenceKind = "shelf", compatibleGroups = 3,
            statistics = MarketPriceStatistics(3, median, median, median, median, median))), totalItems = 1, checkedAtMillis = checked)
    private fun baseline() = evaluateMarketPriceWatch(settings, emptyList(), page()).observations

    @Test fun firstObservationEstablishesBaselineWithoutAnAlert() {
        val value = evaluateMarketPriceWatch(settings, emptyList(), page())
        assertTrue(value.changedItemIds.isEmpty()); assertEquals(10000L, value.observations.single().baselineMinor)
    }
    @Test fun smallerMovesAccumulateAgainstLastNotifiedBaseline() {
        val small = evaluateMarketPriceWatch(settings, baseline(), page(10300))
        assertTrue(small.changedItemIds.isEmpty()); assertEquals(10000L, small.observations.single().baselineMinor)
        val changed = evaluateMarketPriceWatch(settings, small.observations, page(10500))
        assertEquals(listOf(item), changed.changedItemIds); assertEquals(10500L, changed.observations.single().baselineMinor)
        assertEquals(10000L, changed.changes.single().previousMinor)
        assertEquals(10500L, changed.changes.single().currentMinor)
        assertTrue(evaluateMarketPriceWatch(settings, changed.observations, page(10500)).changedItemIds.isEmpty())
    }
    @Test fun bothRisesAndFallsCanTriggerButNotRoundedSubThresholdChanges() {
        assertEquals(listOf(item), evaluateMarketPriceWatch(settings, baseline(), page(9500)).changedItemIds)
        assertTrue(evaluateMarketPriceWatch(settings, baseline(), page(9501)).changedItemIds.isEmpty())
        val largerBaseline = evaluateMarketPriceWatch(settings, emptyList(), page(100000)).observations
        assertTrue(evaluateMarketPriceWatch(settings, largerBaseline, page(104999)).changedItemIds.isEmpty())
    }
    @Test fun missingAndInsufficientDataNeverBecomeAZeroPriceAlert() {
        val missing = evaluateMarketPriceWatch(settings, baseline(), page().copy(rows = emptyList(), totalItems = 0))
        assertTrue(missing.changedItemIds.isEmpty()); assertEquals("missing", missing.observations.single().status)
        val insufficient = page().let { it.copy(rows = listOf(it.rows.single().copy(statistics = null, compatibleGroups = 2))) }
        val result = evaluateMarketPriceWatch(settings, baseline(), insufficient)
        assertTrue(result.changedItemIds.isEmpty()); assertNull(result.observations.single().medianMinor)
        assertEquals(10000L, result.observations.single().baselineMinor)
    }
    @Test fun cappedSamplesNeverSendPriceChangeAlerts() {
        val capped = page(20000).let { it.copy(rows = listOf(it.rows.single().copy(sampleLimited = true))) }
        val result = evaluateMarketPriceWatch(settings, baseline(), capped)
        assertTrue(result.changedItemIds.isEmpty()); assertEquals("limited", result.observations.single().status)
        assertEquals(10000L, result.observations.single().baselineMinor)
    }
    @Test fun currencyOrPackChangesStartANewBaselineInsteadOfComparingDifferentMoney() {
        val price = page(20000)
        for (nextBasis in listOf(basis.copy(currencyCode = "RUB"), basis.copy(pricedAmount = 2.0))) {
            val changed = price.copy(rows = listOf(price.rows.single().copy(basis = nextBasis, ownCurrencyCode = nextBasis.currencyCode)))
            val result = evaluateMarketPriceWatch(settings, baseline(), changed)
            assertTrue(result.changedItemIds.isEmpty()); assertEquals(20000L, result.observations.single().baselineMinor)
        }
    }
    @Test fun selectionIsBoundedDistinctAndCanonical() {
        assertEquals(settings, settings.copy(items = listOf(MarketPriceWatchItem(item, "4006381333931"))).normalized())
        assertNull(settings.copy(items = List(11) { MarketPriceWatchItem(item, code) }).normalized())
        assertNull(settings.copy(items = settings.items + settings.items).normalized())
        assertNull(settings.copy(thresholdBasisPoints = 0).normalized())
        assertNull(settings.copy(country = "us").normalized())
        assertNull(settings.copy(city = "Bad\ncity").normalized())
    }
    @Test fun changedCodeDoesNotSilentlyWatchAnUnrelatedProduct() {
        val invalid = page().let { it.copy(rows = listOf(it.rows.single().copy(basis = null, statistics = null, compatibleGroups = 0))) }
        val result = evaluateMarketPriceWatch(settings, baseline(), invalid)
        assertEquals("unpriced", result.observations.single().status); assertNull(result.observations.single().baselineMinor)
        assertTrue(result.changedItemIds.isEmpty())
    }
    @Test fun responseMustBelongToTheCurrentAccountAndHaveAnHonestSchedule() {
        val state = MarketPriceWatchState(account, store, 1, true, settings, baseline(), 1000, 1000 + MARKET_PRICE_WATCH_INTERVAL, 1000)
        assertTrue(state.isValidFor(account, store)); assertFalse(state.isValidFor(store, store))
        assertFalse(state.copy(nextCheckAtMillis = null).isValidFor(account, store))
        assertFalse(state.copy(observations = listOf(baseline().single().copy(medianMinor = 0))).isValidFor(account, store))
        assertFalse(state.copy(observations = listOf(baseline().single().copy(groups = 1))).isValidFor(account, store))
    }
    @Test fun notificationsAreTranslatedAndDoNotLeaveTemplateArgumentsUnresolved() {
        val reference = EventMessageReference("market.prices.changed.body", arguments = mapOf("store" to "Fixture", "count" to "2", "threshold" to "5", "items" to "Tea"))
        for (lang in listOf("en", "ru", "kk", "ky", "tg", "uz")) {
            val text = eventMessage(reference).extractLocalizedString(lang).orEmpty()
            assertTrue(text.contains("Fixture") && text.contains("Tea") && text.contains("5%"), lang)
            assertFalse(text.contains("{store}")); assertFalse(text.contains("{count}"))
        }
    }
    @Test fun notificationSnapshotsKeepHistoricalPricesAndRejectForeignOrMalformedDetails() {
        val changes = evaluateMarketPriceWatch(settings, baseline(), page(11000)).changes
        val snapshot = MarketPriceAlertDetails(account, store, "kz", "Astana", 1000, changes)
        fun notification(details: MarketPriceAlertDetails = snapshot) = NotificationDataModel("Alert", NotificationType.Neutral,
            userId = account, storeId = store, category = "market_prices", createdAtMillis = 1000,
            metadata = mapOf("priceChanges" to jsonBase.encodeToString(details)))
        assertEquals(snapshot, notification().marketPriceAlertDetails())
        for (bad in listOf(snapshot.copy(accountId = store), snapshot.copy(storeId = item), snapshot.copy(schema = 2),
            snapshot.copy(country = "us"), snapshot.copy(checkedAtMillis = 2000), snapshot.copy(changes = emptyList()),
            snapshot.copy(changes = changes + changes), snapshot.copy(changes = listOf(changes.single().copy(previousMinor = 0))),
            snapshot.copy(changes = listOf(changes.single().copy(currentMinor = 10000))))) {
            assertNull(notification(bad).marketPriceAlertDetails(), bad.toString())
        }
        assertNull(notification().copy(userId = store).marketPriceAlertDetails())
        assertNull(notification().copy(metadata = mapOf("priceChanges" to "{".repeat(MARKET_PRICE_ALERT_MAX_CHARS + 1))).marketPriceAlertDetails())
        // Subsequent observations never mutate a notification that was already produced.
        evaluateMarketPriceWatch(settings, baseline(), page(20000))
        assertEquals(11000L, notification().marketPriceAlertDetails()?.changes?.single()?.currentMinor)
    }
    @Test fun alertSnapshotsKeepOnlyBoundedDisplayNames() {
        val largeName = List(12) { LocalizedStringDataModel("lang$it", "Long title ".repeat(25)) }
        val changed = page(11000).let { it.copy(rows = listOf(it.rows.single().copy(name = largeName))) }
        val result = evaluateMarketPriceWatch(settings, baseline(), changed)
        assertEquals(6, result.changes.single().name.size)
        assertTrue(result.changes.single().name.all { it.value.length <= 120 })
    }

    @Test fun importedControlCharactersAndEmojiCannotBreakStoredAlertNames() {
        val title = "\u0001\u0085" + "x".repeat(119) + "\uD83D\uDED2"
        val changed = page(11000).let { it.copy(rows = listOf(it.rows.single().copy(
            name = listOf(LocalizedStringDataModel("en", title))))) }
        val result = evaluateMarketPriceWatch(settings, baseline(), changed)
        val expected = "x".repeat(119)
        assertEquals(expected, result.changes.single().name.single().value)
        assertEquals(expected, result.observations.single().name.single().value)
    }

    @Test fun tenMaximalImportedNamesFitTheHistoricalNotificationEnvelope() {
        val selected = settings.copy(items = (0 until 10).map {
            MarketPriceWatchItem("33333333-3333-4333-8333-${it.toString().padStart(12, '0')}", code)
        })
        // JSON-escaped names, a maximum-length legacy unit ID and ten simultaneous changes.
        // All inputs satisfy the report contract; notification history must still open.
        val names = List(12) { LocalizedStringDataModel("\\".repeat(12), "\u0001".repeat(180) + "\\".repeat(120)) }
        val longBasis = basis.copy(unitId = "\u0001".repeat(120))
        fun fullPage(price: Long) = page(price).copy(rows = selected.items.map { row ->
            page(price).rows.single().copy(goodsItemId = row.itemId, name = names, basis = longBasis)
        }, totalItems = 10)
        val first = evaluateMarketPriceWatch(selected, emptyList(), fullPage(10000))
        val changed = evaluateMarketPriceWatch(selected, first.observations, fullPage(11000))
        assertEquals(10, changed.changes.size)
        val details = MarketPriceAlertDetails(account, store, "kz", "", 1000, changed.changes)
        val encoded = jsonBase.encodeToString(details)
        assertTrue(encoded.length <= MARKET_PRICE_ALERT_MAX_CHARS, "Snapshot size: ${encoded.length}")
        val notification = NotificationDataModel("Alert", NotificationType.Neutral, id = "test-alert", userId = account,
            storeId = store, category = "market_prices", createdAtMillis = 1000, metadata = mapOf("priceChanges" to encoded))
        assertEquals(details, notification.marketPriceAlertDetails())
    }
}
