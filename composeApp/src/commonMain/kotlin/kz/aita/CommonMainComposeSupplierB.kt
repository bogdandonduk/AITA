// THIS IS CommonMainCompose.kt split slice: SupplierB
@file:OptIn(ExperimentalTime::class, ExperimentalFoundationApi::class)
package kz.aita

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

@Composable
internal fun AppConfiguration.SupplierManufacturerBridgeCard(item: SupplierDashboardManufacturerBridgeDataModel) {
    val coroutineScope = rememberCoroutineScope()
    val title = supplierManufacturerBridgeTitle(item)
    val requestedQuantityText = supplierManufacturerBridgeQuantityText(item.requestedQuantityTotal, item.measurementUnitIdSnapshot)
    val acceptedQuantityText = supplierManufacturerBridgeQuantityText(item.acceptedQuantityTotal, item.measurementUnitIdSnapshot)
    val missingQuantityText = supplierManufacturerBridgeQuantityText(item.missingQuantityTotal, item.measurementUnitIdSnapshot)
    val actionTitle = supplierManufacturerBridgeActionTitle(item.suggestedAction)
    val amountText = item.estimatedAcceptedAmount.supplierDeskMoneyText()
    val storePreviewText = item.storePreview.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.storePreview.visibleLocalizedString("main", "") }
    val attentionText = item.attentionSummary.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.attentionSummary.visibleLocalizedString("main", "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (item.priorityScore >= 20 || item.missingQuantityTotal > 0.0) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (item.priorityScore >= 20 || item.missingQuantityTotal > 0.0) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(32.dp),
                    url = stateValues.drawablePathIconAppModeManufacturer,
                    fallbackRes = stateValues.drawableResIconAppModeManufacturer.value,
                    contentDescription = localizedStringResource(1710, "Manufacturer bridge"),
                    tintColor = null
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.barcodeSnapshots.take(3).joinToString(" • ").ifBlank { stateValues.stringNoName },
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = actionTitle,
                color = stateValues.AccentColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (stateValues.isNarrowScreen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SupplierCatalogChip(text = "${localizedStringResource(1424, "Total requested")}: $requestedQuantityText")
                SupplierCatalogChip(text = "${localizedStringResource(1713, "Accepted qty")}: $acceptedQuantityText")
                SupplierCatalogChip(text = "${localizedStringResource(1714, "Missing qty")}: $missingQuantityText")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1424, "Total requested")}: $requestedQuantityText") }
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1713, "Accepted qty")}: $acceptedQuantityText") }
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1714, "Missing qty")}: $missingQuantityText") }
            }
        }

        if (stateValues.isNarrowScreen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SupplierCatalogChip(text = "${localizedStringResource(1784, "Quote")}: ${item.quoteNeededOrderIds.size}")
                SupplierCatalogChip(text = "${localizedStringResource(1785, "Produce")}: ${item.productionOrderIds.size}")
                SupplierCatalogChip(text = "${localizedStringResource(1786, "Ship")}: ${item.shipmentOrderIds.size}")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1784, "Quote")}: ${item.quoteNeededOrderIds.size}") }
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1785, "Produce")}: ${item.productionOrderIds.size}") }
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1786, "Ship")}: ${item.shipmentOrderIds.size}") }
            }
        }

        storePreviewText.takeIf { it.isNotBlank() }?.let { preview ->
            StockCardInfoLine(localizedStringResource(1789, "Internal stores"), preview, stateValues.TextColor)
        }
        StockCardInfoLine(localizedStringResource(1422, "Stores asking"), item.storeCount.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1423, "Open requests"), item.openOrderCount.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1790, "Accepted orders"), item.confirmedOrderCount.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1715, "Response coverage"), "${item.responseCoveragePercent}%", stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1707, "Manual price book"), item.priceBookRowCount.toString(), stateValues.TextColor)
        amountText.takeIf { it.isNotBlank() }?.let { StockCardInfoLine(localizedStringResource(1727, "Factory value"), it, stateValues.TextColor) }
        item.earliestDueAtMillis?.takeIf { it > 0L }?.let { due ->
            StockCardInfoLine(localizedStringResource(1723, "Earliest due"), receiptUiDateTime(due), stateValues.TextColor)
        }
        StockCardInfoLine(localizedStringResource(1722, "Priority score"), item.priorityScore.toString(), stateValues.TextColor)

        if (attentionText.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.06f))
                    .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.28f), RoundedCornerShape(stateValues.cornerRadius))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = localizedStringResource(1756, "Attention notes"),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = attentionText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = localizedStringResource(1788, "Store names are omitted from this copied factory brief."),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )

        if (stateValues.isNarrowScreen) {
            Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1724, "Open factory-linked orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(searchQuery = item.goodsItemId)
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1725, "Copy factory brief"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    confirmationRequired = false,
                    onClick = { copyTextToClipboard(supplierManufacturerBridgeBrief(item)) }
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1724, "Open factory-linked orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(searchQuery = item.goodsItemId)
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1725, "Copy factory brief"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { copyTextToClipboard(supplierManufacturerBridgeBrief(item)) }
                )
            }
        }
    }
}


internal fun AppConfiguration.supplierBackorderTitle(item: SupplierDashboardBackorderDataModel): String =
    item.goodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.barcodeSnapshots.firstOrNull().orEmpty() }
        .ifBlank { stateValues.stringNoName }

internal fun AppConfiguration.supplierBackorderActionTitle(action: String): String = when (action) {
    "negotiate" -> localizedStringResource(1801, "Store negotiation")
    "source" -> localizedStringResource(1804, "Source more stock")
    else -> localizedStringResource(1720, "Watch backorder")
}

internal fun AppConfiguration.supplierBackorderRecoveryLaneTitle(lane: String): String = when (lane) {
    "split_delivery" -> localizedStringResource(1803, "Split delivery")
    "split_source" -> localizedStringResource(1804, "Source more stock")
    "source_or_cancel" -> localizedStringResource(1805, "Source or cancel")
    else -> localizedStringResource(1720, "Watch backorder")
}

internal fun AppConfiguration.supplierBackorderRecoveryUrgencyTitle(lane: String): String = when (lane) {
    "overdue" -> localizedStringResource(1810, "Overdue")
    "today" -> localizedStringResource(1811, "Due today")
    "soon" -> localizedStringResource(1812, "Due soon")
    else -> localizedStringResource(1813, "Flexible")
}

internal fun AppConfiguration.supplierBackorderRecoveryOwnerTitle(lane: String): String = when (lane) {
    "store_contact" -> localizedStringResource(1818, "Store contact")
    "upstream_sourcing" -> localizedStringResource(1819, "Upstream sourcing")
    "pack_lead" -> localizedStringResource(1820, "Pack lead")
    else -> localizedStringResource(1821, "Watch desk")
}

internal fun AppConfiguration.supplierBackorderRecoverySlaTitle(lane: String): String = when (lane) {
    "call_now" -> localizedStringResource(1824, "Call now")
    "commit_today" -> localizedStringResource(1825, "Commit today")
    "before_pack" -> localizedStringResource(1826, "Before packing")
    else -> localizedStringResource(1827, "Monitor")
}

internal fun AppConfiguration.supplierBackorderRecoveryEscalationTitle(lane: String): String = when (lane) {
    "store_escalation" -> localizedStringResource(1829, "Store escalation")
    "sourcing_escalation" -> localizedStringResource(1830, "Sourcing escalation")
    "pack_hold" -> localizedStringResource(1831, "Pack hold")
    else -> localizedStringResource(1832, "Watch only")
}

internal fun AppConfiguration.supplierBackorderRecoveryProofTitle(lane: String): String = when (lane) {
    "store_ack_required" -> localizedStringResource(1834, "Store ack needed")
    "sourcing_note_required" -> localizedStringResource(1835, "Sourcing note needed")
    "pack_guard_proof" -> localizedStringResource(1836, "Pack guard proof")
    else -> localizedStringResource(1837, "Watch note")
}

internal fun AppConfiguration.supplierBackorderRecoveryOutcomeTitle(lane: String): String = when (lane) {
    "second_drop" -> localizedStringResource(1840, "Second drop")
    "substitute_offer" -> localizedStringResource(1841, "Substitute offer")
    "cancel_review" -> localizedStringResource(1842, "Cancel review")
    "ship_now_guard" -> localizedStringResource(1843, "Ship-now guard")
    else -> localizedStringResource(1844, "Watch to close")
}

internal fun AppConfiguration.supplierBackorderRecoveryPackGuardTitle(lane: String): String = when (lane) {
    "block_pack" -> localizedStringResource(1846, "Block packing")
    "split_pack_only" -> localizedStringResource(1847, "Split pack only")
    "proof_before_pack" -> localizedStringResource(1848, "Proof before pack")
    else -> localizedStringResource(1849, "Safe to pack")
}

internal fun AppConfiguration.supplierBackorderRecoveryContactTitle(lane: String): String = when (lane) {
    "store_call" -> localizedStringResource(1852, "Store call")
    "upstream_request" -> localizedStringResource(1853, "Upstream request")
    "pack_lead_note" -> localizedStringResource(1854, "Pack note")
    "substitute_answer" -> localizedStringResource(1858, "Substitute answer")
    else -> localizedStringResource(1837, "Watch note")
}

internal fun AppConfiguration.supplierBackorderRecoveryRiskTitle(lane: String): String = when (lane) {
    "critical_recovery" -> localizedStringResource(1860, "Critical recovery")
    "decision_pressure" -> localizedStringResource(1861, "Decision pressure")
    "pack_sourcing_watch" -> localizedStringResource(1862, "Pack/sourcing risk")
    else -> localizedStringResource(1863, "Steady watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryConfidenceTitle(lane: String): String = when (lane) {
    "blocked_until_decision" -> localizedStringResource(1869, "Blocked until decision")
    "needs_confirmation" -> localizedStringResource(1870, "Needs confirmation")
    "ready_to_recover" -> localizedStringResource(1871, "Ready to recover")
    else -> localizedStringResource(1872, "Watch confidence")
}

internal fun AppConfiguration.supplierBackorderRecoveryFollowUpTitle(lane: String): String = when (lane) {
    "follow_up_now" -> localizedStringResource(1878, "Follow up now")
    "same_day_check" -> localizedStringResource(1879, "Same-day check")
    "before_pack_check" -> localizedStringResource(1880, "Before-pack check")
    else -> localizedStringResource(1881, "Watch later")
}

internal fun AppConfiguration.supplierBackorderRecoveryHandoffTitle(lane: String): String = when (lane) {
    "store_handoff" -> localizedStringResource(1887, "Store handoff")
    "sourcing_handoff" -> localizedStringResource(1888, "Sourcing handoff")
    "pack_handoff" -> localizedStringResource(1889, "Pack handoff")
    else -> localizedStringResource(1890, "Watch handoff")
}

internal fun AppConfiguration.supplierBackorderRecoveryClosureTitle(lane: String): String = when (lane) {
    "blocked_open" -> localizedStringResource(1897, "Blocked open")
    "needs_close_note" -> localizedStringResource(1898, "Needs close note")
    "ready_with_guard" -> localizedStringResource(1899, "Ready with guard")
    else -> localizedStringResource(1900, "Watch until clear")
}

internal fun AppConfiguration.supplierBackorderRecoveryLedgerTitle(lane: String): String = when (lane) {
    "audit_blocker" -> localizedStringResource(1908, "Audit blocker")
    "decision_record" -> localizedStringResource(1909, "Decision record")
    "pack_record" -> localizedStringResource(1910, "Pack record")
    "ledger_ready" -> localizedStringResource(1911, "Ledger ready")
    else -> localizedStringResource(1912, "Watch record")
}

internal fun AppConfiguration.supplierBackorderRecoveryTriageTitle(lane: String): String = when (lane) {
    "triage_now" -> localizedStringResource(1920, "Triage now")
    "decision_lane" -> localizedStringResource(1921, "Decision lane")
    "pack_split_lane" -> localizedStringResource(1922, "Pack split lane")
    "sourcing_lane" -> localizedStringResource(1923, "Sourcing lane")
    "ready_lane" -> localizedStringResource(1924, "Ready lane")
    else -> localizedStringResource(1925, "Watch lane")
}

internal fun AppConfiguration.supplierBackorderRecoveryCommandTitle(lane: String): String = when (lane) {
    "stop_pack" -> localizedStringResource(1933, "Stop pack")
    "call_store" -> localizedStringResource(1934, "Call store")
    "source_now" -> localizedStringResource(1935, "Source now")
    "split_and_ship" -> localizedStringResource(1936, "Split and ship")
    "ready_with_note" -> localizedStringResource(1937, "Ready with note")
    else -> localizedStringResource(1938, "Monitor promise")
}

internal fun AppConfiguration.supplierBackorderRecoveryPromiseShieldTitle(lane: String): String = when (lane) {
    "promise_at_risk" -> localizedStringResource(1946, "Promise at risk")
    "store_answer_needed" -> localizedStringResource(1947, "Store answer needed")
    "source_before_promise" -> localizedStringResource(1948, "Source before promise")
    "split_promise" -> localizedStringResource(1949, "Split promise")
    "promise_safe" -> localizedStringResource(1950, "Promise safe")
    else -> localizedStringResource(1951, "Promise watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryDeskTitle(lane: String): String = when (lane) {
    "desk_command" -> localizedStringResource(1959, "Command room")
    "desk_contact" -> localizedStringResource(1960, "Contact wave")
    "desk_source" -> localizedStringResource(1961, "Source wave")
    "desk_split" -> localizedStringResource(1962, "Split wave")
    "desk_ready" -> localizedStringResource(1963, "Ready wave")
    "desk_clear" -> localizedStringResource(1965, "Desk clear")
    else -> localizedStringResource(1964, "Watch wave")
}

internal fun AppConfiguration.supplierBackorderRecoveryWaveTitle(lane: String): String = when (lane) {
    "wave_command" -> localizedStringResource(1959, "Command room")
    "wave_contact" -> localizedStringResource(1960, "Contact wave")
    "wave_source" -> localizedStringResource(1961, "Source wave")
    "wave_split" -> localizedStringResource(1962, "Split wave")
    "wave_ready" -> localizedStringResource(1963, "Ready wave")
    else -> localizedStringResource(1964, "Watch wave")
}
internal fun AppConfiguration.supplierBackorderRecoveryAgingTitle(lane: String): String = when (lane) {
    "stale_blocker" -> localizedStringResource(1986, "Stale blocker")
    "touch_today" -> localizedStringResource(1987, "Touch today")
    "fresh_recovery" -> localizedStringResource(1988, "Fresh recovery")
    else -> localizedStringResource(1989, "Age watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryBottleneckTitle(lane: String): String = when (lane) {
    "decision_bottleneck" -> localizedStringResource(1999, "Decision bottleneck")
    "contact_bottleneck" -> localizedStringResource(2000, "Contact bottleneck")
    "sourcing_bottleneck" -> localizedStringResource(2001, "Sourcing bottleneck")
    "pack_bottleneck" -> localizedStringResource(2002, "Pack bottleneck")
    "proof_bottleneck" -> localizedStringResource(2003, "Proof bottleneck")
    "aging_bottleneck" -> localizedStringResource(2004, "Aging bottleneck")
    "ready_bottleneck" -> localizedStringResource(2005, "Ready bottleneck")
    else -> localizedStringResource(2006, "Watch bottleneck")
}

internal fun AppConfiguration.supplierBackorderRecoveryLoadTitle(lane: String): String = when (lane) {
    "heavy_load" -> localizedStringResource(2014, "Heavy load")
    "multi_store_load" -> localizedStringResource(2015, "Multi-store load")
    "pack_load" -> localizedStringResource(2016, "Pack load")
    "ready_load" -> localizedStringResource(2017, "Ready load")
    else -> localizedStringResource(2018, "Watch load")
}

internal fun AppConfiguration.supplierBackorderRecoveryImpactTitle(lane: String): String = when (lane) {
    "customer_promise_impact" -> localizedStringResource(2030, "Customer promise impact")
    "multi_store_impact" -> localizedStringResource(2031, "Multi-store impact")
    "store_replenishment_impact" -> localizedStringResource(2032, "Store replenishment impact")
    "controlled_impact" -> localizedStringResource(2033, "Controlled impact")
    else -> localizedStringResource(2034, "Impact watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryCommitTitle(lane: String): String = when (lane) {
    "commit_blocked" -> localizedStringResource(2045, "Commit blocked")
    "commit_store_today" -> localizedStringResource(2046, "Store commit due")
    "commit_source_eta" -> localizedStringResource(2047, "Source ETA commit")
    "commit_split_eta" -> localizedStringResource(2048, "Split ETA commit")
    "commit_ready" -> localizedStringResource(2049, "Commit ready")
    else -> localizedStringResource(2050, "Commit watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryAllocationTitle(lane: String): String = when (lane) {
    "fair_split_needed" -> localizedStringResource(2063, "Fair split needed")
    "priority_allocation" -> localizedStringResource(2064, "Priority allocation")
    "single_store_allocation" -> localizedStringResource(2065, "Single-store allocation")
    "allocation_ready" -> localizedStringResource(2066, "Allocation ready")
    else -> localizedStringResource(2067, "Allocation watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryExceptionTitle(lane: String): String = when (lane) {
    "exception_stop_pack" -> localizedStringResource(2080, "Stop-pack exception")
    "exception_cancel_review" -> localizedStringResource(2081, "Cancel review exception")
    "exception_substitute" -> localizedStringResource(2082, "Substitute exception")
    "exception_sourcing" -> localizedStringResource(2083, "Sourcing exception")
    "exception_allocation" -> localizedStringResource(2084, "Allocation exception")
    "exception_ready" -> localizedStringResource(2085, "Exception ready")
    else -> localizedStringResource(2086, "Exception watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryCauseTitle(lane: String): String = when (lane) {
    "zero_acceptance_cause" -> localizedStringResource(2102, "Zero accepted cause")
    "exception_cause" -> localizedStringResource(2103, "Exception cause")
    "promise_conflict_cause" -> localizedStringResource(2104, "Promise conflict cause")
    "allocation_cause" -> localizedStringResource(2105, "Allocation cause")
    "partial_capacity_cause" -> localizedStringResource(2106, "Partial capacity cause")
    "cause_ready" -> localizedStringResource(2107, "Cause ready")
    else -> localizedStringResource(2108, "Cause watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryVerificationTitle(lane: String): String = when (lane) {
    "verify_blocked" -> localizedStringResource(2121, "Verify blocked")
    "verify_store_answer" -> localizedStringResource(2122, "Store answer verify")
    "verify_source_proof" -> localizedStringResource(2123, "Source proof verify")
    "verify_pack_split" -> localizedStringResource(2124, "Pack split verify")
    "verify_cause_record" -> localizedStringResource(2125, "Cause record verify")
    "verify_ready" -> localizedStringResource(2126, "Verification ready")
    else -> localizedStringResource(2127, "Verify watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryApprovalTitle(lane: String): String = when (lane) {
    "approval_blocked" -> localizedStringResource(2142, "Approval blocked")
    "approval_manager_review" -> localizedStringResource(2143, "Manager review")
    "approval_store_ack" -> localizedStringResource(2144, "Store ack approval")
    "approval_source_ack" -> localizedStringResource(2145, "Source ack approval")
    "approval_pack_lead" -> localizedStringResource(2146, "Pack lead approval")
    "approval_ready" -> localizedStringResource(2147, "Approval ready")
    else -> localizedStringResource(2148, "Approval watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryExecutionTitle(lane: String): String = when (lane) {
    "execution_blocked" -> localizedStringResource(2163, "Execution blocked")
    "execute_store_call" -> localizedStringResource(2164, "Execute store call")
    "execute_source_eta" -> localizedStringResource(2165, "Execute source ETA")
    "execute_split_pack" -> localizedStringResource(2166, "Execute split pack")
    "execute_ship_ready" -> localizedStringResource(2167, "Execute ship-ready")
    else -> localizedStringResource(2168, "Execution watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryReleaseTitle(lane: String): String = when (lane) {
    "release_blocked" -> localizedStringResource(2182, "Release blocked")
    "release_store_update" -> localizedStringResource(2183, "Release store update")
    "release_source_eta" -> localizedStringResource(2184, "Release source ETA")
    "release_split_dispatch" -> localizedStringResource(2185, "Release split dispatch")
    "release_ready" -> localizedStringResource(2186, "Release ready")
    else -> localizedStringResource(2187, "Release watch")
}


internal fun AppConfiguration.supplierBackorderRecoverySealTitle(lane: String): String = when (lane) {
    "seal_blocked" -> localizedStringResource(2202, "Seal blocked")
    "seal_store_notice" -> localizedStringResource(2203, "Seal store notice")
    "seal_source_trace" -> localizedStringResource(2204, "Seal source trace")
    "seal_split_manifest" -> localizedStringResource(2205, "Seal split manifest")
    "seal_ready" -> localizedStringResource(2206, "Seal ready")
    else -> localizedStringResource(2207, "Seal watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryCloseoutTitle(lane: String): String = when (lane) {
    "closeout_blocked" -> localizedStringResource(2222, "Closeout blocked")
    "closeout_store_notice" -> localizedStringResource(2223, "Closeout store notice")
    "closeout_source_trace" -> localizedStringResource(2224, "Closeout source trace")
    "closeout_split_leftover" -> localizedStringResource(2225, "Closeout split leftover")
    "closeout_ready" -> localizedStringResource(2226, "Closeout ready")
    else -> localizedStringResource(2227, "Closeout watch")
}


internal fun AppConfiguration.supplierBackorderRecoveryReopenTitle(lane: String): String = when (lane) {
    "reopen_blocked" -> localizedStringResource(2242, "Reopen blocked")
    "reopen_after_answer" -> localizedStringResource(2243, "Reopen after answer")
    "reopen_if_promise_slips" -> localizedStringResource(2244, "Reopen if promise slips")
    "reopen_split_leftover" -> localizedStringResource(2245, "Reopen split leftover")
    "reopen_safe" -> localizedStringResource(2246, "Reopen safe")
    else -> localizedStringResource(2247, "Reopen watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryReconciliationTitle(lane: String): String = when (lane) {
    "reconcile_blocked" -> localizedStringResource(2263, "Reconcile blocked")
    "reconcile_store_delta" -> localizedStringResource(2264, "Reconcile store delta")
    "reconcile_source_delta" -> localizedStringResource(2265, "Reconcile source delta")
    "reconcile_split_delta" -> localizedStringResource(2266, "Reconcile split delta")
    "reconcile_ready" -> localizedStringResource(2267, "Reconcile ready")
    else -> localizedStringResource(2268, "Reconcile watch")
}

internal fun AppConfiguration.supplierBackorderRecoveryAuditTitle(lane: String): String = when (lane) {
    "audit_blocked" -> localizedStringResource(2282, "Audit blocked")
    "audit_quantity_gap" -> localizedStringResource(2283, "Audit quantity gap")
    "audit_evidence_gap" -> localizedStringResource(2284, "Audit evidence gap")
    "audit_store_note_gap" -> localizedStringResource(2285, "Audit store note gap")
    "audit_ready" -> localizedStringResource(2286, "Audit ready")
    else -> localizedStringResource(2287, "Audit watch")
}

internal fun AppConfiguration.supplierRecoveryWaveTopTitle(wave: SupplierDashboardRecoveryWaveDataModel): String =
    wave.topGoodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { wave.topGoodsItemNameSnapshot.visibleLocalizedString("main", "") }
        .ifBlank { stateValues.stringNoName }
        .ifBlank { localizedStringResource(1663, "No promised date") }

internal fun AppConfiguration.supplierRecoveryWaveNote(wave: SupplierDashboardRecoveryWaveDataModel): String = buildString {
    append(localizedStringResource(1973, "Recovery wave")).append('\n')
    append(supplierBackorderRecoveryWaveTitle(wave.recoveryWaveLane)).append('\n')
    append(localizedStringResource(1794, "Backorder watch")).append(": ").append(wave.shortageCount).append('\n')
    append(localizedStringResource(1796, "Short qty")).append(": ").append(wave.shortQuantityTotal.toStockMoneyText()).append('\n')
    append(localizedStringResource(1968, "Urgent desk")).append(": ").append(wave.urgentCount).append('\n')
    append(localizedStringResource(1969, "Stop-pack desk")).append(": ").append(wave.stopPackCount).append('\n')
    append(localizedStringResource(1955, "Promise risks")).append(": ").append(wave.promiseRiskCount).append('\n')
    append(localizedStringResource(1871, "Ready to recover")).append(": ").append(wave.readyCount).append('\n')
    append(localizedStringResource(1982, "Max risk")).append(": ").append(wave.maxRiskScore).append("/100").append('\n')
    append(localizedStringResource(1983, "Max priority")).append(": ").append(wave.maxPriorityScore).append('\n')
    wave.nextFollowUpAtMillis?.takeIf { it > 0L }?.let { followUp ->
        append(localizedStringResource(1882, "Next follow-up")).append(": ").append(receiptUiDateTime(followUp)).append('\n')
    }
    wave.topGoodsItemId.takeIf { it.isNotBlank() }?.let {
        append(localizedStringResource(1981, "Top wave item")).append(": ").append(supplierRecoveryWaveTopTitle(wave)).append('\n')
    }
    wave.recoveryWaveHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { wave.recoveryWaveHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1973, "Recovery wave")).append(": ").append(hint).append('\n') }
    wave.recoveryWaveChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { wave.recoveryWaveChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1976, "Wave checklist")).append(":\n").append(checklist).append('\n') }
    wave.recoveryWaveScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { wave.recoveryWaveScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1977, "Wave script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderWaveNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(1973, "Recovery wave")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(1973, "Recovery wave")).append(": ").append(supplierBackorderRecoveryWaveTitle(item.recoveryWaveLane)).append('\n')
    append(localizedStringResource(1975, "Wave score")).append(": ").append(item.recoveryWaveScore).append("/100").append('\n')
    append(localizedStringResource(1932, "Recovery command")).append(": ").append(supplierBackorderRecoveryCommandTitle(item.recoveryCommandLane)).append(" • ").append(localizedStringResource(1939, "Command score")).append(" ").append(item.recoveryCommandScore).append("/100").append('\n')
    append(localizedStringResource(1945, "Promise shield")).append(": ").append(supplierBackorderRecoveryPromiseShieldTitle(item.recoveryPromiseShieldLane)).append(" • ").append(localizedStringResource(1952, "Promise score")).append(" ").append(item.recoveryPromiseShieldScore).append("/100").append('\n')
    append(localizedStringResource(1859, "Recovery risk")).append(": ").append(supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)).append(" • ").append(localizedStringResource(1864, "Risk score")).append(" ").append(item.recoveryRiskScore).append("/100").append('\n')
    item.recoveryWaveHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryWaveHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1973, "Recovery wave")).append(": ").append(hint).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierRecoveryDeskTopTitle(desk: SupplierDashboardRecoveryDeskDataModel): String =
    desk.topGoodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { desk.topGoodsItemNameSnapshot.visibleLocalizedString("main", "") }
        .ifBlank { stateValues.stringNoName }
        .ifBlank { localizedStringResource(1663, "No promised date") }

internal fun AppConfiguration.supplierBackorderAgingNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(1985, "Recovery aging")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(1985, "Recovery aging")).append(": ").append(supplierBackorderRecoveryAgingTitle(item.recoveryAgingLane)).append('\n')
    append(localizedStringResource(1990, "Aging score")).append(": ").append(item.recoveryAgingScore).append("/100").append('\n')
    append(localizedStringResource(1996, "Oldest age")).append(": ").append(item.recoveryAgingHours).append("h").append('\n')
    item.recoveryAgingStartedAtMillis?.takeIf { it > 0L }?.let { startedAt ->
        append(localizedStringResource(1822, "Recovery checkpoint")).append(": ").append(receiptUiDateTime(startedAt)).append('\n')
    }
    append(localizedStringResource(1973, "Recovery wave")).append(": ").append(supplierBackorderRecoveryWaveTitle(item.recoveryWaveLane)).append(" • ").append(localizedStringResource(1975, "Wave score")).append(" ").append(item.recoveryWaveScore).append("/100").append('\n')
    append(localizedStringResource(1859, "Recovery risk")).append(": ").append(supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)).append(" • ").append(localizedStringResource(1864, "Risk score")).append(" ").append(item.recoveryRiskScore).append("/100").append('\n')
    append(localizedStringResource(1882, "Next follow-up")).append(": ").append(item.recoveryFollowUpAtMillis?.takeIf { it > 0L }?.let { receiptUiDateTime(it) } ?: localizedStringResource(1663, "No promised date")).append('\n')
    item.recoveryAgingHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAgingHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1985, "Recovery aging")).append(": ").append(hint).append('\n') }
    item.recoveryAgingChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAgingChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1991, "Aging checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryAgingScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAgingScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1995, "Aging script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}


internal fun AppConfiguration.supplierBackorderBottleneckNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(1998, "Recovery bottleneck")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(1998, "Recovery bottleneck")).append(": ").append(supplierBackorderRecoveryBottleneckTitle(item.recoveryBottleneckLane)).append('\n')
    append(localizedStringResource(2007, "Bottleneck score")).append(": ").append(item.recoveryBottleneckScore).append("/100").append('\n')
    append(localizedStringResource(1973, "Recovery wave")).append(": ").append(supplierBackorderRecoveryWaveTitle(item.recoveryWaveLane)).append(" • ").append(localizedStringResource(1975, "Wave score")).append(" ").append(item.recoveryWaveScore).append("/100").append('\n')
    append(localizedStringResource(1985, "Recovery aging")).append(": ").append(supplierBackorderRecoveryAgingTitle(item.recoveryAgingLane)).append(" • ").append(item.recoveryAgingHours).append("h").append('\n')
    append(localizedStringResource(1932, "Recovery command")).append(": ").append(supplierBackorderRecoveryCommandTitle(item.recoveryCommandLane)).append(" • ").append(localizedStringResource(1939, "Command score")).append(" ").append(item.recoveryCommandScore).append("/100").append('\n')
    append(localizedStringResource(1859, "Recovery risk")).append(": ").append(supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)).append(" • ").append(localizedStringResource(1864, "Risk score")).append(" ").append(item.recoveryRiskScore).append("/100").append('\n')
    item.recoveryBottleneckHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryBottleneckHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1998, "Recovery bottleneck")).append(": ").append(hint).append('\n') }
    item.recoveryBottleneckChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryBottleneckChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2008, "Bottleneck checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryBottleneckScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryBottleneckScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2011, "Bottleneck script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderLoadNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2013, "Recovery load")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2013, "Recovery load")).append(": ").append(supplierBackorderRecoveryLoadTitle(item.recoveryLoadLane)).append('\n')
    append(localizedStringResource(2019, "Load score")).append(": ").append(item.recoveryLoadScore).append("/100").append('\n')
    append(localizedStringResource(1798, "Affected orders")).append(": ").append(item.affectedOrderCount).append('\n')
    append(localizedStringResource(1799, "Affected stores")).append(": ").append(item.affectedStoreCount).append('\n')
    append(localizedStringResource(1998, "Recovery bottleneck")).append(": ").append(supplierBackorderRecoveryBottleneckTitle(item.recoveryBottleneckLane)).append(" • ").append(localizedStringResource(2007, "Bottleneck score")).append(" ").append(item.recoveryBottleneckScore).append("/100").append('\n')
    append(localizedStringResource(1973, "Recovery wave")).append(": ").append(supplierBackorderRecoveryWaveTitle(item.recoveryWaveLane)).append(" • ").append(localizedStringResource(1975, "Wave score")).append(" ").append(item.recoveryWaveScore).append("/100").append('\n')
    item.recoveryLoadHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLoadHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2013, "Recovery load")).append(": ").append(hint).append('\n') }
    item.recoveryLoadChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLoadChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2020, "Load checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryLoadScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLoadScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2023, "Load script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderImpactNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2029, "Recovery impact")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2029, "Recovery impact")).append(": ").append(supplierBackorderRecoveryImpactTitle(item.recoveryImpactLane)).append('\n')
    append(localizedStringResource(2035, "Impact score")).append(": ").append(item.recoveryImpactScore).append("/100").append('\n')
    append(localizedStringResource(1798, "Affected orders")).append(": ").append(item.affectedOrderCount).append('\n')
    append(localizedStringResource(1799, "Affected stores")).append(": ").append(item.affectedStoreCount).append('\n')
    append(localizedStringResource(1945, "Promise shield")).append(": ").append(supplierBackorderRecoveryPromiseShieldTitle(item.recoveryPromiseShieldLane)).append(" • ").append(localizedStringResource(1952, "Promise score")).append(" ").append(item.recoveryPromiseShieldScore).append("/100").append('\n')
    append(localizedStringResource(2013, "Recovery load")).append(": ").append(supplierBackorderRecoveryLoadTitle(item.recoveryLoadLane)).append(" • ").append(localizedStringResource(2019, "Load score")).append(" ").append(item.recoveryLoadScore).append("/100").append('\n')
    item.recoveryImpactHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryImpactHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2029, "Recovery impact")).append(": ").append(hint).append('\n') }
    item.recoveryImpactChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryImpactChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2036, "Impact checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryImpactScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryImpactScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2038, "Impact script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}


internal fun AppConfiguration.supplierBackorderCommitNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2044, "Recovery commit")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2044, "Recovery commit")).append(": ").append(supplierBackorderRecoveryCommitTitle(item.recoveryCommitLane)).append('\n')
    append(localizedStringResource(2051, "Commit score")).append(": ").append(item.recoveryCommitScore).append("/100").append('\n')
    item.recoveryCommitByMillis?.takeIf { it > 0L }?.let { commitAt ->
        append(localizedStringResource(2053, "Commit by")).append(": ").append(receiptUiDateTime(commitAt)).append('\n')
    }
    append(localizedStringResource(2029, "Recovery impact")).append(": ").append(supplierBackorderRecoveryImpactTitle(item.recoveryImpactLane)).append(" • ").append(localizedStringResource(2035, "Impact score")).append(" ").append(item.recoveryImpactScore).append("/100").append('\n')
    append(localizedStringResource(1945, "Promise shield")).append(": ").append(supplierBackorderRecoveryPromiseShieldTitle(item.recoveryPromiseShieldLane)).append(" • ").append(localizedStringResource(1952, "Promise score")).append(" ").append(item.recoveryPromiseShieldScore).append("/100").append('\n')
    item.recoveryCommitHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommitHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2044, "Recovery commit")).append(": ").append(hint).append('\n') }
    item.recoveryCommitChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommitChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2052, "Commit checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryCommitScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommitScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2055, "Commit script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderAllocationNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2062, "Recovery allocation")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2062, "Recovery allocation")).append(": ").append(supplierBackorderRecoveryAllocationTitle(item.recoveryAllocationLane)).append('\n')
    append(localizedStringResource(2068, "Allocation score")).append(": ").append(item.recoveryAllocationScore).append("/100").append('\n')
    append(localizedStringResource(1798, "Affected orders")).append(": ").append(item.affectedOrderCount).append('\n')
    append(localizedStringResource(1799, "Affected stores")).append(": ").append(item.affectedStoreCount).append('\n')
    append(localizedStringResource(1796, "Short qty")).append(": ").append(item.missingQuantityTotal.toStockMoneyText()).append('\n')
    append(localizedStringResource(2044, "Recovery commit")).append(": ").append(supplierBackorderRecoveryCommitTitle(item.recoveryCommitLane)).append(" • ").append(localizedStringResource(2051, "Commit score")).append(" ").append(item.recoveryCommitScore).append("/100").append('\n')
    append(localizedStringResource(2029, "Recovery impact")).append(": ").append(supplierBackorderRecoveryImpactTitle(item.recoveryImpactLane)).append(" • ").append(localizedStringResource(2035, "Impact score")).append(" ").append(item.recoveryImpactScore).append("/100").append('\n')
    item.recoveryAllocationHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAllocationHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2062, "Recovery allocation")).append(": ").append(hint).append('\n') }
    item.recoveryAllocationChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAllocationChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2069, "Allocation checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryAllocationScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAllocationScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2071, "Allocation script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}


internal fun AppConfiguration.supplierBackorderExceptionNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2079, "Recovery exception")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2079, "Recovery exception")).append(": ").append(supplierBackorderRecoveryExceptionTitle(item.recoveryExceptionLane)).append('\n')
    append(localizedStringResource(2087, "Exception score")).append(": ").append(item.recoveryExceptionScore).append("/100").append('\n')
    append(localizedStringResource(2062, "Recovery allocation")).append(": ").append(supplierBackorderRecoveryAllocationTitle(item.recoveryAllocationLane)).append(" • ").append(localizedStringResource(2068, "Allocation score")).append(" ").append(item.recoveryAllocationScore).append("/100").append('\n')
    append(localizedStringResource(2044, "Recovery commit")).append(": ").append(supplierBackorderRecoveryCommitTitle(item.recoveryCommitLane)).append(" • ").append(localizedStringResource(2051, "Commit score")).append(" ").append(item.recoveryCommitScore).append("/100").append('\n')
    append(localizedStringResource(1798, "Affected orders")).append(": ").append(item.affectedOrderCount).append('\n')
    append(localizedStringResource(1799, "Affected stores")).append(": ").append(item.affectedStoreCount).append('\n')
    append(localizedStringResource(1796, "Short qty")).append(": ").append(item.missingQuantityTotal.toStockMoneyText()).append('\n')
    item.recoveryExceptionHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExceptionHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2079, "Recovery exception")).append(": ").append(hint).append('\n') }
    item.recoveryExceptionChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExceptionChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2088, "Exception checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryExceptionScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExceptionScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2090, "Exception script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}


internal fun AppConfiguration.supplierBackorderCauseNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2101, "Recovery cause")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2101, "Recovery cause")).append(": ").append(supplierBackorderRecoveryCauseTitle(item.recoveryCauseLane)).append('\n')
    append(localizedStringResource(2109, "Cause score")).append(": ").append(item.recoveryCauseScore).append("/100").append('\n')
    append(localizedStringResource(2079, "Recovery exception")).append(": ").append(supplierBackorderRecoveryExceptionTitle(item.recoveryExceptionLane)).append(" • ").append(localizedStringResource(2087, "Exception score")).append(" ").append(item.recoveryExceptionScore).append("/100").append('\n')
    append(localizedStringResource(2062, "Recovery allocation")).append(": ").append(supplierBackorderRecoveryAllocationTitle(item.recoveryAllocationLane)).append(" • ").append(localizedStringResource(2068, "Allocation score")).append(" ").append(item.recoveryAllocationScore).append("/100").append('\n')
    append(localizedStringResource(2044, "Recovery commit")).append(": ").append(supplierBackorderRecoveryCommitTitle(item.recoveryCommitLane)).append(" • ").append(localizedStringResource(2051, "Commit score")).append(" ").append(item.recoveryCommitScore).append("/100").append('\n')
    append(localizedStringResource(1798, "Affected orders")).append(": ").append(item.affectedOrderCount).append('\n')
    append(localizedStringResource(1799, "Affected stores")).append(": ").append(item.affectedStoreCount).append('\n')
    append(localizedStringResource(1796, "Short qty")).append(": ").append(item.missingQuantityTotal.toStockMoneyText()).append('\n')
    item.recoveryCauseHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCauseHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2101, "Recovery cause")).append(": ").append(hint).append('\n') }
    item.recoveryCauseChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCauseChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2110, "Cause checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryCauseScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCauseScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2112, "Cause script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}


internal fun AppConfiguration.supplierBackorderVerificationNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2120, "Recovery verification")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2120, "Recovery verification")).append(": ").append(supplierBackorderRecoveryVerificationTitle(item.recoveryVerificationLane)).append('\n')
    append(localizedStringResource(2128, "Verification score")).append(": ").append(item.recoveryVerificationScore).append("/100").append('\n')
    append(localizedStringResource(2101, "Recovery cause")).append(": ").append(supplierBackorderRecoveryCauseTitle(item.recoveryCauseLane)).append(" • ").append(localizedStringResource(2109, "Cause score")).append(" ").append(item.recoveryCauseScore).append("/100").append('\n')
    append(localizedStringResource(2079, "Recovery exception")).append(": ").append(supplierBackorderRecoveryExceptionTitle(item.recoveryExceptionLane)).append(" • ").append(localizedStringResource(2087, "Exception score")).append(" ").append(item.recoveryExceptionScore).append("/100").append('\n')
    append(localizedStringResource(1907, "Recovery ledger")).append(": ").append(supplierBackorderRecoveryLedgerTitle(item.recoveryLedgerLane)).append(" • ").append(localizedStringResource(1913, "Ledger score")).append(" ").append(item.recoveryLedgerScore).append("/100").append('\n')
    append(localizedStringResource(1868, "Recovery confidence")).append(": ").append(supplierBackorderRecoveryConfidenceTitle(item.recoveryConfidenceLane)).append(" • ").append(localizedStringResource(1873, "Confidence score")).append(" ").append(item.recoveryConfidenceScore).append("/100").append('\n')
    append(localizedStringResource(1798, "Affected orders")).append(": ").append(item.affectedOrderCount).append('\n')
    append(localizedStringResource(1799, "Affected stores")).append(": ").append(item.affectedStoreCount).append('\n')
    append(localizedStringResource(1796, "Short qty")).append(": ").append(item.missingQuantityTotal.toStockMoneyText()).append('\n')
    item.recoveryVerificationHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryVerificationHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2120, "Recovery verification")).append(": ").append(hint).append('\n') }
    item.recoveryVerificationChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryVerificationChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2129, "Verification checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryVerificationScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryVerificationScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2131, "Verification script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderApprovalNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2141, "Recovery approval")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2141, "Recovery approval")).append(": ").append(supplierBackorderRecoveryApprovalTitle(item.recoveryApprovalLane)).append('\n')
    append(localizedStringResource(2149, "Approval score")).append(": ").append(item.recoveryApprovalScore).append("/100").append('\n')
    append(localizedStringResource(2120, "Recovery verification")).append(": ").append(supplierBackorderRecoveryVerificationTitle(item.recoveryVerificationLane)).append(" • ").append(localizedStringResource(2128, "Verification score")).append(" ").append(item.recoveryVerificationScore).append("/100").append('\n')
    append(localizedStringResource(2079, "Recovery exception")).append(": ").append(supplierBackorderRecoveryExceptionTitle(item.recoveryExceptionLane)).append(" • ").append(localizedStringResource(2087, "Exception score")).append(" ").append(item.recoveryExceptionScore).append("/100").append('\n')
    append(localizedStringResource(2044, "Recovery commit")).append(": ").append(supplierBackorderRecoveryCommitTitle(item.recoveryCommitLane)).append(" • ").append(localizedStringResource(2051, "Commit score")).append(" ").append(item.recoveryCommitScore).append("/100").append('\n')
    append(localizedStringResource(1798, "Affected orders")).append(": ").append(item.affectedOrderCount).append('\n')
    append(localizedStringResource(1799, "Affected stores")).append(": ").append(item.affectedStoreCount).append('\n')
    append(localizedStringResource(1796, "Short qty")).append(": ").append(item.missingQuantityTotal.toStockMoneyText()).append('\n')
    item.recoveryApprovalHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryApprovalHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2141, "Recovery approval")).append(": ").append(hint).append('\n') }
    item.recoveryApprovalChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryApprovalChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2150, "Approval checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryApprovalScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryApprovalScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2152, "Approval script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderExecutionNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2162, "Recovery execution")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2162, "Recovery execution")).append(": ").append(supplierBackorderRecoveryExecutionTitle(item.recoveryExecutionLane)).append('\n')
    append(localizedStringResource(2169, "Execution score")).append(": ").append(item.recoveryExecutionScore).append("/100").append('\n')
    append(localizedStringResource(2141, "Recovery approval")).append(": ").append(supplierBackorderRecoveryApprovalTitle(item.recoveryApprovalLane)).append(" • ").append(localizedStringResource(2149, "Approval score")).append(" ").append(item.recoveryApprovalScore).append("/100").append('\n')
    append(localizedStringResource(2120, "Recovery verification")).append(": ").append(supplierBackorderRecoveryVerificationTitle(item.recoveryVerificationLane)).append(" • ").append(localizedStringResource(2128, "Verification score")).append(" ").append(item.recoveryVerificationScore).append("/100").append('\n')
    append(localizedStringResource(1932, "Recovery command")).append(": ").append(supplierBackorderRecoveryCommandTitle(item.recoveryCommandLane)).append(" • ").append(localizedStringResource(1939, "Command score")).append(" ").append(item.recoveryCommandScore).append("/100").append('\n')
    append(localizedStringResource(1798, "Affected orders")).append(": ").append(item.affectedOrderCount).append('\n')
    append(localizedStringResource(1799, "Affected stores")).append(": ").append(item.affectedStoreCount).append('\n')
    append(localizedStringResource(1796, "Short qty")).append(": ").append(item.missingQuantityTotal.toStockMoneyText()).append('\n')
    item.recoveryExecutionHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExecutionHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2162, "Recovery execution")).append(": ").append(hint).append('\n') }
    item.recoveryExecutionChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExecutionChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2170, "Execution checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryExecutionScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExecutionScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2172, "Execution script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}


internal fun AppConfiguration.supplierBackorderReleaseNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2181, "Recovery release")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2181, "Recovery release")).append(": ").append(supplierBackorderRecoveryReleaseTitle(item.recoveryReleaseLane)).append('\n')
    append(localizedStringResource(2188, "Release score")).append(": ").append(item.recoveryReleaseScore).append("/100").append('\n')
    append(localizedStringResource(2162, "Recovery execution")).append(": ").append(supplierBackorderRecoveryExecutionTitle(item.recoveryExecutionLane)).append(" • ").append(localizedStringResource(2169, "Execution score")).append(" ").append(item.recoveryExecutionScore).append("/100").append('\n')
    append(localizedStringResource(2141, "Recovery approval")).append(": ").append(supplierBackorderRecoveryApprovalTitle(item.recoveryApprovalLane)).append(" • ").append(localizedStringResource(2149, "Approval score")).append(" ").append(item.recoveryApprovalScore).append("/100").append('\n')
    append(localizedStringResource(1945, "Promise shield")).append(": ").append(supplierBackorderRecoveryPromiseShieldTitle(item.recoveryPromiseShieldLane)).append(" • ").append(localizedStringResource(1952, "Promise score")).append(" ").append(item.recoveryPromiseShieldScore).append("/100").append('\n')
    append(localizedStringResource(1845, "Pack guard")).append(": ").append(supplierBackorderRecoveryPackGuardTitle(item.recoveryPackGuardLane)).append('\n')
    append(localizedStringResource(1798, "Affected orders")).append(": ").append(item.affectedOrderCount).append('\n')
    append(localizedStringResource(1799, "Affected stores")).append(": ").append(item.affectedStoreCount).append('\n')
    append(localizedStringResource(1796, "Short qty")).append(": ").append(item.missingQuantityTotal.toStockMoneyText()).append('\n')
    item.recoveryReleaseHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReleaseHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2181, "Recovery release")).append(": ").append(hint).append('\n') }
    item.recoveryReleaseChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReleaseChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2189, "Release checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryReleaseScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReleaseScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2191, "Release script")).append(":\n").append(script).append('\n') }
    item.recoverySealHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySealHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2201, "Recovery seal")).append(": ").append(hint).append('\n') }
    item.recoverySealChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySealChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2209, "Seal checklist")).append(":\n").append(checklist).append('\n') }
    item.recoverySealScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySealScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2212, "Seal script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}


internal fun AppConfiguration.supplierBackorderSealNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2201, "Recovery seal")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2201, "Recovery seal")).append(": ").append(supplierBackorderRecoverySealTitle(item.recoverySealLane)).append('\n')
    append(localizedStringResource(2208, "Seal score")).append(": ").append(item.recoverySealScore).append("/100").append('\n')
    append(localizedStringResource(2181, "Recovery release")).append(": ").append(supplierBackorderRecoveryReleaseTitle(item.recoveryReleaseLane)).append(" • ").append(localizedStringResource(2188, "Release score")).append(" ").append(item.recoveryReleaseScore).append("/100").append('\n')
    append(localizedStringResource(2162, "Recovery execution")).append(": ").append(supplierBackorderRecoveryExecutionTitle(item.recoveryExecutionLane)).append(" • ").append(localizedStringResource(2169, "Execution score")).append(" ").append(item.recoveryExecutionScore).append("/100").append('\n')
    append(localizedStringResource(2120, "Recovery verification")).append(": ").append(supplierBackorderRecoveryVerificationTitle(item.recoveryVerificationLane)).append(" • ").append(localizedStringResource(2128, "Verification score")).append(" ").append(item.recoveryVerificationScore).append("/100").append('\n')
    append(localizedStringResource(1798, "Affected orders")).append(": ").append(item.affectedOrderCount).append('\n')
    append(localizedStringResource(1799, "Affected stores")).append(": ").append(item.affectedStoreCount).append('\n')
    append(localizedStringResource(1796, "Short qty")).append(": ").append(item.missingQuantityTotal.toStockMoneyText()).append('\n')
    item.recoverySealHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySealHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2201, "Recovery seal")).append(": ").append(hint).append('\n') }
    item.recoverySealChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySealChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2209, "Seal checklist")).append(":\n").append(checklist).append('\n') }
    item.recoverySealScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySealScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2212, "Seal script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderCloseoutNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2221, "Recovery closeout")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2221, "Recovery closeout")).append(": ").append(supplierBackorderRecoveryCloseoutTitle(item.recoveryCloseoutLane)).append('\n')
    append(localizedStringResource(2228, "Closeout score")).append(": ").append(item.recoveryCloseoutScore).append("/100").append('\n')
    append(localizedStringResource(2201, "Recovery seal")).append(": ").append(supplierBackorderRecoverySealTitle(item.recoverySealLane)).append(" • ").append(localizedStringResource(2208, "Seal score")).append(" ").append(item.recoverySealScore).append("/100").append('\n')
    append(localizedStringResource(2181, "Recovery release")).append(": ").append(supplierBackorderRecoveryReleaseTitle(item.recoveryReleaseLane)).append(" • ").append(localizedStringResource(2188, "Release score")).append(" ").append(item.recoveryReleaseScore).append("/100").append('\n')
    append(localizedStringResource(1907, "Recovery ledger")).append(": ").append(supplierBackorderRecoveryLedgerTitle(item.recoveryLedgerLane)).append(" • ").append(localizedStringResource(1913, "Ledger score")).append(" ").append(item.recoveryLedgerScore).append("/100").append('\n')
    append(localizedStringResource(1798, "Affected orders")).append(": ").append(item.affectedOrderCount).append('\n')
    append(localizedStringResource(1799, "Affected stores")).append(": ").append(item.affectedStoreCount).append('\n')
    append(localizedStringResource(1796, "Short qty")).append(": ").append(item.missingQuantityTotal.toStockMoneyText()).append('\n')
    item.recoveryCloseoutHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCloseoutHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2221, "Recovery closeout")).append(": ").append(hint).append('\n') }
    item.recoveryCloseoutChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCloseoutChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2229, "Closeout checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryCloseoutScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCloseoutScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2232, "Closeout script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}


internal fun AppConfiguration.supplierBackorderReopenNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2241, "Recovery reopen")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2241, "Recovery reopen")).append(": ").append(supplierBackorderRecoveryReopenTitle(item.recoveryReopenLane)).append('\n')
    append(localizedStringResource(2248, "Reopen score")).append(": ").append(item.recoveryReopenScore).append("/100").append('\n')
    item.recoveryReopenAtMillis?.takeIf { it > 0L }?.let { reopenAt ->
        append(localizedStringResource(2249, "Reopen checkpoint")).append(": ").append(receiptUiDateTime(reopenAt)).append('\n')
    }
    append(localizedStringResource(2221, "Recovery closeout")).append(": ").append(supplierBackorderRecoveryCloseoutTitle(item.recoveryCloseoutLane)).append(" • ").append(localizedStringResource(2228, "Closeout score")).append(" ").append(item.recoveryCloseoutScore).append("/100").append('\n')
    append(localizedStringResource(2201, "Recovery seal")).append(": ").append(supplierBackorderRecoverySealTitle(item.recoverySealLane)).append(" • ").append(localizedStringResource(2208, "Seal score")).append(" ").append(item.recoverySealScore).append("/100").append('\n')
    append(localizedStringResource(2181, "Recovery release")).append(": ").append(supplierBackorderRecoveryReleaseTitle(item.recoveryReleaseLane)).append(" • ").append(localizedStringResource(2188, "Release score")).append(" ").append(item.recoveryReleaseScore).append("/100").append('\n')
    item.recoveryReopenHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReopenHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2241, "Recovery reopen")).append(": ").append(hint).append('\n') }
    item.recoveryReopenChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReopenChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2250, "Reopen checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryReopenScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReopenScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2253, "Reopen script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderReconciliationNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2262, "Recovery reconciliation")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2262, "Recovery reconciliation")).append(": ").append(supplierBackorderRecoveryReconciliationTitle(item.recoveryReconciliationLane)).append('\n')
    append(localizedStringResource(2269, "Reconcile score")).append(": ").append(item.recoveryReconciliationScore).append("/100").append('\n')
    append(localizedStringResource(2241, "Recovery reopen")).append(": ").append(supplierBackorderRecoveryReopenTitle(item.recoveryReopenLane)).append(" • ").append(localizedStringResource(2248, "Reopen score")).append(" ").append(item.recoveryReopenScore).append("/100").append('\n')
    append(localizedStringResource(2221, "Recovery closeout")).append(": ").append(supplierBackorderRecoveryCloseoutTitle(item.recoveryCloseoutLane)).append(" • ").append(localizedStringResource(2228, "Closeout score")).append(" ").append(item.recoveryCloseoutScore).append("/100").append('\n')
    item.recoveryReconciliationHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReconciliationHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2262, "Recovery reconciliation")).append(": ").append(hint).append('\n') }
    item.recoveryReconciliationChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReconciliationChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2270, "Reconcile checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryReconciliationScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReconciliationScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2273, "Reconcile script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderAuditNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(2281, "Recovery audit")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(2281, "Recovery audit")).append(": ").append(supplierBackorderRecoveryAuditTitle(item.recoveryAuditLane)).append('\n')
    append(localizedStringResource(2288, "Audit score")).append(": ").append(item.recoveryAuditScore).append("/100").append('\n')
    append(localizedStringResource(2262, "Recovery reconciliation")).append(": ").append(supplierBackorderRecoveryReconciliationTitle(item.recoveryReconciliationLane)).append(" • ").append(localizedStringResource(2269, "Reconcile score")).append(" ").append(item.recoveryReconciliationScore).append("/100").append('\n')
    append(localizedStringResource(2221, "Recovery closeout")).append(": ").append(supplierBackorderRecoveryCloseoutTitle(item.recoveryCloseoutLane)).append(" • ").append(localizedStringResource(2228, "Closeout score")).append(" ").append(item.recoveryCloseoutScore).append("/100").append('\n')
    append(localizedStringResource(2201, "Recovery seal")).append(": ").append(supplierBackorderRecoverySealTitle(item.recoverySealLane)).append(" • ").append(localizedStringResource(2208, "Seal score")).append(" ").append(item.recoverySealScore).append("/100").append('\n')
    append(localizedStringResource(1798, "Affected orders")).append(": ").append(item.affectedOrderCount).append('\n')
    append(localizedStringResource(1799, "Affected stores")).append(": ").append(item.affectedStoreCount).append('\n')
    append(localizedStringResource(1796, "Short qty")).append(": ").append(supplierManufacturerBridgeQuantityText(item.missingQuantityTotal, item.measurementUnitIdSnapshot)).append('\n')
    item.recoveryAuditHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAuditHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2281, "Recovery audit")).append(": ").append(hint).append('\n') }
    item.recoveryAuditChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAuditChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2289, "Audit checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryAuditScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAuditScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2290, "Audit script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierRecoveryDeskNote(desk: SupplierDashboardRecoveryDeskDataModel): String = buildString {
    append(localizedStringResource(1958, "Recovery desk")).append('\n')
    append(supplierBackorderRecoveryDeskTitle(desk.recoveryDeskLane)).append('\n')
    append(localizedStringResource(1794, "Backorder watch")).append(": ").append(desk.shortageCount).append('\n')
    append(localizedStringResource(1968, "Urgent desk")).append(": ").append(desk.urgentCount).append('\n')
    append(localizedStringResource(1969, "Stop-pack desk")).append(": ").append(desk.stopPackCount).append('\n')
    append(localizedStringResource(1955, "Promise risks")).append(": ").append(desk.promiseRiskCount).append('\n')
    append(localizedStringResource(1871, "Ready to recover")).append(": ").append(desk.readyCount).append('\n')
    append(localizedStringResource(1993, "Stale age")).append(": ").append(desk.staleRecoveryCount).append('\n')
    append(localizedStringResource(1987, "Touch today")).append(": ").append(desk.touchTodayRecoveryCount).append('\n')
    append(localizedStringResource(1994, "Fresh touches")).append(": ").append(desk.freshRecoveryCount).append('\n')
    append(localizedStringResource(1996, "Oldest age")).append(": ").append(desk.oldestRecoveryAgeHours).append("h").append('\n')
    append(localizedStringResource(1997, "Average age")).append(": ").append(desk.averageRecoveryAgeHours).append("h").append('\n')
    append(localizedStringResource(2010, "Top bottleneck")).append(": ").append(supplierBackorderRecoveryBottleneckTitle(desk.topBottleneckLane)).append('\n')
    append(localizedStringResource(1999, "Decision bottleneck")).append(": ").append(desk.decisionBottleneckCount).append('\n')
    append(localizedStringResource(2000, "Contact bottleneck")).append(": ").append(desk.contactBottleneckCount).append('\n')
    append(localizedStringResource(2001, "Sourcing bottleneck")).append(": ").append(desk.sourcingBottleneckCount).append('\n')
    append(localizedStringResource(2002, "Pack bottleneck")).append(": ").append(desk.packBottleneckCount).append('\n')
    append(localizedStringResource(2003, "Proof bottleneck")).append(": ").append(desk.proofBottleneckCount).append('\n')
    append(localizedStringResource(2004, "Aging bottleneck")).append(": ").append(desk.agingBottleneckCount).append('\n')
    append(localizedStringResource(2005, "Ready bottleneck")).append(": ").append(desk.readyBottleneckCount).append('\n')
    append(localizedStringResource(2022, "Top load")).append(": ").append(supplierBackorderRecoveryLoadTitle(desk.topLoadLane)).append('\n')
    append(localizedStringResource(2024, "Heavy loads")).append(": ").append(desk.heavyLoadCount).append('\n')
    append(localizedStringResource(2025, "Multi-store loads")).append(": ").append(desk.multiStoreLoadCount).append('\n')
    append(localizedStringResource(2026, "Pack loads")).append(": ").append(desk.packLoadCount).append('\n')
    append(localizedStringResource(2027, "Ready loads")).append(": ").append(desk.readyLoadCount).append('\n')
    append(localizedStringResource(2028, "Average load")).append(": ").append(desk.averageLoadScore).append("/100").append('\n')
    append(localizedStringResource(2041, "Top impact")).append(": ").append(supplierBackorderRecoveryImpactTitle(desk.topImpactLane)).append('\n')
    append(localizedStringResource(2039, "High impact")).append(": ").append(desk.highImpactCount).append('\n')
    append(localizedStringResource(2040, "Promise impact")).append(": ").append(desk.promiseImpactCount).append('\n')
    append(localizedStringResource(2031, "Multi-store impact")).append(": ").append(desk.multiStoreImpactCount).append('\n')
    append(localizedStringResource(2032, "Store replenishment impact")).append(": ").append(desk.replenishmentImpactCount).append('\n')
    append(localizedStringResource(2033, "Controlled impact")).append(": ").append(desk.controlledImpactCount).append('\n')
    append(localizedStringResource(2042, "Impact average")).append(": ").append(desk.averageImpactScore).append("/100").append('\n')
    append(localizedStringResource(2043, "Impact max")).append(": ").append(desk.maxImpactScore).append("/100").append('\n')
    append(localizedStringResource(2056, "Top commit")).append(": ").append(supplierBackorderRecoveryCommitTitle(desk.topCommitLane)).append('\n')
    append(localizedStringResource(2057, "Blocked commits")).append(": ").append(desk.blockedCommitCount).append('\n')
    append(localizedStringResource(2058, "Due commits")).append(": ").append(desk.dueCommitCount).append('\n')
    append(localizedStringResource(2047, "Source ETA commit")).append(": ").append(desk.sourceCommitCount).append('\n')
    append(localizedStringResource(2048, "Split ETA commit")).append(": ").append(desk.splitCommitCount).append('\n')
    append(localizedStringResource(2059, "Ready commits")).append(": ").append(desk.readyCommitCount).append('\n')
    append(localizedStringResource(2060, "Average commit")).append(": ").append(desk.averageCommitScore).append("/100").append('\n')
    desk.nextCommitAtMillis?.takeIf { it > 0L }?.let { commitAt ->
        append(localizedStringResource(2061, "Next commit")).append(": ").append(receiptUiDateTime(commitAt)).append('\n')
    }
    append(localizedStringResource(2072, "Top allocation")).append(": ").append(supplierBackorderRecoveryAllocationTitle(desk.topAllocationLane)).append('\n')
    append(localizedStringResource(2073, "Allocation pressure")).append(": ").append(desk.allocationPressureCount).append('\n')
    append(localizedStringResource(2074, "Fair splits")).append(": ").append(desk.fairSplitAllocationCount).append('\n')
    append(localizedStringResource(2075, "Priority allocations")).append(": ").append(desk.priorityAllocationCount).append('\n')
    append(localizedStringResource(2076, "Ready allocations")).append(": ").append(desk.allocationReadyCount).append('\n')
    append(localizedStringResource(2077, "Allocation average")).append(": ").append(desk.averageAllocationScore).append("/100").append('\n')
    append(localizedStringResource(2078, "Allocation max")).append(": ").append(desk.maxAllocationScore).append("/100").append('\n')
    append(localizedStringResource(2091, "Top exception")).append(": ").append(supplierBackorderRecoveryExceptionTitle(desk.topExceptionLane)).append('\n')
    append(localizedStringResource(2092, "Exception pressure")).append(": ").append(desk.exceptionPressureCount).append('\n')
    append(localizedStringResource(2093, "Stop-pack exceptions")).append(": ").append(desk.stopPackExceptionCount).append('\n')
    append(localizedStringResource(2094, "Cancel exceptions")).append(": ").append(desk.cancelReviewExceptionCount).append('\n')
    append(localizedStringResource(2095, "Substitute exceptions")).append(": ").append(desk.substituteExceptionCount).append('\n')
    append(localizedStringResource(2096, "Sourcing exceptions")).append(": ").append(desk.sourcingExceptionCount).append('\n')
    append(localizedStringResource(2097, "Allocation exceptions")).append(": ").append(desk.allocationExceptionCount).append('\n')
    append(localizedStringResource(2098, "Ready exceptions")).append(": ").append(desk.exceptionReadyCount).append('\n')
    append(localizedStringResource(2099, "Exception average")).append(": ").append(desk.averageExceptionScore).append("/100").append('\n')
    append(localizedStringResource(2100, "Exception max")).append(": ").append(desk.maxExceptionScore).append("/100").append('\n')
    append(localizedStringResource(2113, "Top cause")).append(": ").append(supplierBackorderRecoveryCauseTitle(desk.topCauseLane)).append('\n')
    append(localizedStringResource(2114, "Cause pressure")).append(": ").append(desk.causePressureCount).append('\n')
    append(localizedStringResource(2115, "Zero causes")).append(": ").append(desk.zeroAcceptanceCauseCount).append('\n')
    append(localizedStringResource(2116, "Capacity causes")).append(": ").append(desk.partialCapacityCauseCount).append('\n')
    append(localizedStringResource(2117, "Promise causes")).append(": ").append(desk.promiseConflictCauseCount).append('\n')
    append(localizedStringResource(2073, "Allocation pressure")).append(": ").append(desk.allocationCauseCount).append('\n')
    append(localizedStringResource(2092, "Exception pressure")).append(": ").append(desk.exceptionCauseCount).append('\n')
    append(localizedStringResource(2118, "Ready causes")).append(": ").append(desk.causeReadyCount).append('\n')
    append(localizedStringResource(2119, "Cause max")).append(": ").append(desk.maxCauseScore).append("/100").append('\n')
    append(localizedStringResource(2132, "Top verification")).append(": ").append(supplierBackorderRecoveryVerificationTitle(desk.topVerificationLane)).append('\n')
    append(localizedStringResource(2133, "Verification blockers")).append(": ").append(desk.verificationBlockerCount).append('\n')
    append(localizedStringResource(2137, "Cause verifications")).append(": ").append(desk.causeVerificationCount).append('\n')
    append(localizedStringResource(2134, "Store verifications")).append(": ").append(desk.storeVerificationCount).append('\n')
    append(localizedStringResource(2135, "Source verifications")).append(": ").append(desk.sourceVerificationCount).append('\n')
    append(localizedStringResource(2136, "Pack verifications")).append(": ").append(desk.packVerificationCount).append('\n')
    append(localizedStringResource(2138, "Ready verifications")).append(": ").append(desk.verificationReadyCount).append('\n')
    append(localizedStringResource(2140, "Verification average")).append(": ").append(desk.averageVerificationScore).append("/100").append('\n')
    append(localizedStringResource(2139, "Verification max")).append(": ").append(desk.maxVerificationScore).append("/100").append('\n')
    append(localizedStringResource(2153, "Top approval")).append(": ").append(supplierBackorderRecoveryApprovalTitle(desk.topApprovalLane)).append('\n')
    append(localizedStringResource(2154, "Approval blockers")).append(": ").append(desk.approvalBlockerCount).append('\n')
    append(localizedStringResource(2155, "Manager approvals")).append(": ").append(desk.managerApprovalCount).append('\n')
    append(localizedStringResource(2156, "Store approvals")).append(": ").append(desk.storeApprovalCount).append('\n')
    append(localizedStringResource(2157, "Source approvals")).append(": ").append(desk.sourceApprovalCount).append('\n')
    append(localizedStringResource(2158, "Pack approvals")).append(": ").append(desk.packApprovalCount).append('\n')
    append(localizedStringResource(2159, "Ready approvals")).append(": ").append(desk.approvalReadyCount).append('\n')
    append(localizedStringResource(2161, "Approval average")).append(": ").append(desk.averageApprovalScore).append("/100").append('\n')
    append(localizedStringResource(2160, "Approval max")).append(": ").append(desk.maxApprovalScore).append("/100").append('\n')
    append(localizedStringResource(2173, "Top execution")).append(": ").append(supplierBackorderRecoveryExecutionTitle(desk.topExecutionLane)).append('\n')
    append(localizedStringResource(2174, "Execution blockers")).append(": ").append(desk.executionBlockerCount).append('\n')
    append(localizedStringResource(2175, "Store executions")).append(": ").append(desk.storeExecutionCount).append('\n')
    append(localizedStringResource(2176, "Source executions")).append(": ").append(desk.sourceExecutionCount).append('\n')
    append(localizedStringResource(2177, "Split executions")).append(": ").append(desk.splitExecutionCount).append('\n')
    append(localizedStringResource(2178, "Ready executions")).append(": ").append(desk.readyExecutionCount).append('\n')
    append(localizedStringResource(2180, "Execution average")).append(": ").append(desk.averageExecutionScore).append("/100").append('\n')
    append(localizedStringResource(2179, "Execution max")).append(": ").append(desk.maxExecutionScore).append("/100").append('\n')
    append(localizedStringResource(2193, "Top release")).append(": ").append(supplierBackorderRecoveryReleaseTitle(desk.topReleaseLane)).append('\n')
    append(localizedStringResource(2194, "Release blockers")).append(": ").append(desk.releaseBlockerCount).append('\n')
    append(localizedStringResource(2196, "Store releases")).append(": ").append(desk.storeReleaseCount).append('\n')
    append(localizedStringResource(2197, "Source releases")).append(": ").append(desk.sourceReleaseCount).append('\n')
    append(localizedStringResource(2198, "Split releases")).append(": ").append(desk.splitReleaseCount).append('\n')
    append(localizedStringResource(2195, "Ready releases")).append(": ").append(desk.readyReleaseCount).append('\n')
    append(localizedStringResource(2199, "Release average")).append(": ").append(desk.averageReleaseScore).append("/100").append('\n')
    append(localizedStringResource(2200, "Release max")).append(": ").append(desk.maxReleaseScore).append("/100").append('\n')
    append(localizedStringResource(2213, "Top seal")).append(": ").append(supplierBackorderRecoverySealTitle(desk.topSealLane)).append('\n')
    append(localizedStringResource(2214, "Seal blockers")).append(": ").append(desk.sealBlockerCount).append('\n')
    append(localizedStringResource(2216, "Store seals")).append(": ").append(desk.storeSealCount).append('\n')
    append(localizedStringResource(2217, "Source seals")).append(": ").append(desk.sourceSealCount).append('\n')
    append(localizedStringResource(2218, "Split seals")).append(": ").append(desk.splitSealCount).append('\n')
    append(localizedStringResource(2215, "Ready seals")).append(": ").append(desk.readySealCount).append('\n')
    append(localizedStringResource(2219, "Seal average")).append(": ").append(desk.averageSealScore).append("/100").append('\n')
    append(localizedStringResource(2220, "Seal max")).append(": ").append(desk.maxSealScore).append("/100").append('\n')
    append(localizedStringResource(2233, "Top closeout")).append(": ").append(supplierBackorderRecoveryCloseoutTitle(desk.topCloseoutLane)).append('\n')
    append(localizedStringResource(2234, "Closeout blockers")).append(": ").append(desk.closeoutBlockerCount).append('\n')
    append(localizedStringResource(2236, "Store closeouts")).append(": ").append(desk.storeCloseoutCount).append('\n')
    append(localizedStringResource(2237, "Source closeouts")).append(": ").append(desk.sourceCloseoutCount).append('\n')
    append(localizedStringResource(2238, "Split closeouts")).append(": ").append(desk.splitCloseoutCount).append('\n')
    append(localizedStringResource(2235, "Ready closeouts")).append(": ").append(desk.readyCloseoutCount).append('\n')
    append(localizedStringResource(2239, "Closeout average")).append(": ").append(desk.averageCloseoutScore).append("/100").append('\n')
    append(localizedStringResource(2240, "Closeout max")).append(": ").append(desk.maxCloseoutScore).append("/100").append('\n')
    append(localizedStringResource(2254, "Top reopen")).append(": ").append(supplierBackorderRecoveryReopenTitle(desk.topReopenLane)).append('\n')
    append(localizedStringResource(2255, "Reopen blockers")).append(": ").append(desk.reopenBlockerCount).append('\n')
    append(localizedStringResource(2256, "Answer reopens")).append(": ").append(desk.reopenAnswerCount).append('\n')
    append(localizedStringResource(2257, "Promise reopens")).append(": ").append(desk.reopenPromiseCount).append('\n')
    append(localizedStringResource(2258, "Split reopens")).append(": ").append(desk.reopenSplitCount).append('\n')
    append(localizedStringResource(2259, "Ready reopens")).append(": ").append(desk.reopenReadyCount).append('\n')
    append(localizedStringResource(2260, "Reopen average")).append(": ").append(desk.averageReopenScore).append("/100").append('\n')
    append(localizedStringResource(2261, "Reopen max")).append(": ").append(desk.maxReopenScore).append("/100").append('\n')
    desk.nextReopenAtMillis?.takeIf { it > 0L }?.let { reopenAt ->
        append(localizedStringResource(2249, "Reopen checkpoint")).append(": ").append(receiptUiDateTime(reopenAt)).append('\n')
    }
    append(localizedStringResource(2274, "Top reconcile")).append(": ").append(supplierBackorderRecoveryReconciliationTitle(desk.topReconciliationLane)).append('\n')
    append(localizedStringResource(2275, "Reconcile blockers")).append(": ").append(desk.reconciliationBlockerCount).append('\n')
    append(localizedStringResource(2276, "Store reconciles")).append(": ").append(desk.reconciliationStoreCount).append('\n')
    append(localizedStringResource(2277, "Source reconciles")).append(": ").append(desk.reconciliationSourceCount).append('\n')
    append(localizedStringResource(2278, "Split reconciles")).append(": ").append(desk.reconciliationSplitCount).append('\n')
    append(localizedStringResource(2279, "Ready reconciles")).append(": ").append(desk.reconciliationReadyCount).append('\n')
    append(localizedStringResource(2280, "Reconcile max")).append(": ").append(desk.maxReconciliationScore).append("/100").append('\n')
    append(localizedStringResource(1864, "Risk score")).append(": ").append(desk.averageRiskScore).append("/100").append('\n')
    append(localizedStringResource(1722, "Priority score")).append(": ").append(desk.maxPriorityScore).append('\n')
    desk.nextFollowUpAtMillis?.takeIf { it > 0L }?.let { followUp ->
        append(localizedStringResource(1972, "Next desk follow-up")).append(": ").append(receiptUiDateTime(followUp)).append('\n')
    }
    desk.topGoodsItemId.takeIf { it.isNotBlank() }?.let {
        append(localizedStringResource(1970, "Top recovery")).append(": ").append(supplierRecoveryDeskTopTitle(desk)).append('\n')
    }
    desk.recoveryWaves.takeIf { it.isNotEmpty() }?.let { waves ->
        append(localizedStringResource(1980, "Active waves")).append(": ")
            .append(waves.joinToString(" • ") { wave -> "${supplierBackorderRecoveryWaveTitle(wave.recoveryWaveLane)} ${wave.shortageCount}" })
            .append('\n')
    }
    desk.recoveryDeskHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { desk.recoveryDeskHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1958, "Recovery desk")).append(": ").append(hint).append('\n') }
    desk.recoveryDeskChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { desk.recoveryDeskChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1966, "Desk checklist")).append(":\n").append(checklist).append('\n') }
    desk.recoveryDeskScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { desk.recoveryDeskScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1971, "Desk script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderContactScript(item: SupplierDashboardBackorderDataModel): String =
    item.recoveryContactScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryContactScript.visibleLocalizedString("main", "") }
        .ifBlank { supplierBackorderBrief(item) }

internal fun AppConfiguration.supplierBackorderRiskNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(1859, "Recovery risk")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(1864, "Risk score")).append(": ").append(item.recoveryRiskScore).append('\n')
    append(localizedStringResource(1859, "Recovery risk")).append(": ").append(supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)).append('\n')
    append(localizedStringResource(1796, "Short qty")).append(": ").append(supplierManufacturerBridgeQuantityText(item.missingQuantityTotal, item.measurementUnitIdSnapshot)).append('\n')
    item.recoveryRiskHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryRiskHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1859, "Recovery risk")).append(": ").append(hint).append('\n') }
    item.recoveryRiskReasons.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryRiskReasons.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { reasons -> append(localizedStringResource(1865, "Risk reasons")).append(": ").append(reasons).append('\n') }
    append(localizedStringResource(1851, "Contact lane")).append(": ").append(supplierBackorderRecoveryContactTitle(item.recoveryContactLane)).append('\n')
    append(localizedStringResource(1845, "Pack guard")).append(": ").append(supplierBackorderRecoveryPackGuardTitle(item.recoveryPackGuardLane)).append('\n')
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderConfidenceNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(1868, "Recovery confidence")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(1873, "Confidence score")).append(": ").append(item.recoveryConfidenceScore).append('\n')
    append(localizedStringResource(1868, "Recovery confidence")).append(": ").append(supplierBackorderRecoveryConfidenceTitle(item.recoveryConfidenceLane)).append('\n')
    append(localizedStringResource(1859, "Recovery risk")).append(": ").append(supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)).append(" • ").append(localizedStringResource(1864, "Risk score")).append(" ").append(item.recoveryRiskScore).append('\n')
    append(localizedStringResource(1833, "Recovery proof")).append(": ").append(supplierBackorderRecoveryProofTitle(item.recoveryProofLane)).append('\n')
    item.recoveryConfidenceHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryConfidenceHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1868, "Recovery confidence")).append(": ").append(hint).append('\n') }
    item.recoveryConfidenceChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryConfidenceChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1874, "Confidence checklist")).append(":\n").append(checklist).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderFollowUpNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(1877, "Follow-up cadence")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(1877, "Follow-up cadence")).append(": ").append(supplierBackorderRecoveryFollowUpTitle(item.recoveryFollowUpLane)).append('\n')
    item.recoveryFollowUpAtMillis?.takeIf { it > 0L }?.let { followUpAt ->
        append(localizedStringResource(1882, "Next follow-up")).append(": ").append(receiptUiDateTime(followUpAt)).append('\n')
    }
    append(localizedStringResource(1859, "Recovery risk")).append(": ").append(supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)).append(" • ").append(localizedStringResource(1864, "Risk score")).append(" ").append(item.recoveryRiskScore).append('\n')
    append(localizedStringResource(1868, "Recovery confidence")).append(": ").append(supplierBackorderRecoveryConfidenceTitle(item.recoveryConfidenceLane)).append(" • ").append(localizedStringResource(1873, "Confidence score")).append(" ").append(item.recoveryConfidenceScore).append('\n')
    item.recoveryFollowUpHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryFollowUpHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1877, "Follow-up cadence")).append(": ").append(hint).append('\n') }
    item.recoveryFollowUpScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryFollowUpScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1884, "Follow-up script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}
internal fun AppConfiguration.supplierBackorderHandoffNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(1886, "Recovery handoff")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(1886, "Recovery handoff")).append(": ").append(supplierBackorderRecoveryHandoffTitle(item.recoveryHandoffLane)).append('\n')
    append(localizedStringResource(1877, "Follow-up cadence")).append(": ").append(supplierBackorderRecoveryFollowUpTitle(item.recoveryFollowUpLane)).append('\n')
    append(localizedStringResource(1817, "Recovery owner")).append(": ").append(supplierBackorderRecoveryOwnerTitle(item.recoveryOwnerLane)).append('\n')
    append(localizedStringResource(1845, "Pack guard")).append(": ").append(supplierBackorderRecoveryPackGuardTitle(item.recoveryPackGuardLane)).append('\n')
    item.recoveryHandoffHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryHandoffHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1886, "Recovery handoff")).append(": ").append(hint).append('\n') }
    item.recoveryHandoffChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryHandoffChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1891, "Handoff checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryHandoffScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryHandoffScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1895, "Handoff script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderClosureNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(1896, "Close gate")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(1896, "Close gate")).append(": ").append(supplierBackorderRecoveryClosureTitle(item.recoveryClosureLane)).append('\n')
    append(localizedStringResource(1901, "Closure score")).append(": ").append(item.recoveryClosureScore).append("/100").append('\n')
    append(localizedStringResource(1886, "Recovery handoff")).append(": ").append(supplierBackorderRecoveryHandoffTitle(item.recoveryHandoffLane)).append('\n')
    append(localizedStringResource(1868, "Recovery confidence")).append(": ").append(supplierBackorderRecoveryConfidenceTitle(item.recoveryConfidenceLane)).append(" • ").append(localizedStringResource(1873, "Confidence score")).append(" ").append(item.recoveryConfidenceScore).append('\n')
    append(localizedStringResource(1859, "Recovery risk")).append(": ").append(supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)).append(" • ").append(localizedStringResource(1864, "Risk score")).append(" ").append(item.recoveryRiskScore).append('\n')
    item.recoveryClosureHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryClosureHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1896, "Close gate")).append(": ").append(hint).append('\n') }
    item.recoveryClosureChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryClosureChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1902, "Closure checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryClosureScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryClosureScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1906, "Close-gate script")).append(":\n").append(script).append('\n') }
    item.recoveryLedgerHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLedgerHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1907, "Recovery ledger")).append(": ").append(hint).append('\n') }
    item.recoveryLedgerChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLedgerChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1914, "Ledger checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryLedgerScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLedgerScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1918, "Ledger script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderLedgerNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(1907, "Recovery ledger")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(1907, "Recovery ledger")).append(": ").append(supplierBackorderRecoveryLedgerTitle(item.recoveryLedgerLane)).append('\n')
    append(localizedStringResource(1913, "Ledger score")).append(": ").append(item.recoveryLedgerScore).append("/100").append('\n')
    append(localizedStringResource(1896, "Close gate")).append(": ").append(supplierBackorderRecoveryClosureTitle(item.recoveryClosureLane)).append(" • ").append(localizedStringResource(1901, "Closure score")).append(" ").append(item.recoveryClosureScore).append("/100").append('\n')
    append(localizedStringResource(1868, "Recovery confidence")).append(": ").append(supplierBackorderRecoveryConfidenceTitle(item.recoveryConfidenceLane)).append(" • ").append(localizedStringResource(1873, "Confidence score")).append(" ").append(item.recoveryConfidenceScore).append('\n')
    append(localizedStringResource(1859, "Recovery risk")).append(": ").append(supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)).append(" • ").append(localizedStringResource(1864, "Risk score")).append(" ").append(item.recoveryRiskScore).append('\n')
    item.recoveryLedgerHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLedgerHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1907, "Recovery ledger")).append(": ").append(hint).append('\n') }
    item.recoveryLedgerChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLedgerChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1914, "Ledger checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryLedgerScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLedgerScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1918, "Ledger script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderTriageNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(1919, "Triage desk")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(1919, "Triage desk")).append(": ").append(supplierBackorderRecoveryTriageTitle(item.recoveryTriageLane)).append('\n')
    append(localizedStringResource(1926, "Triage score")).append(": ").append(item.recoveryTriageScore).append("/100").append('\n')
    append(localizedStringResource(1859, "Recovery risk")).append(": ").append(supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)).append(" • ").append(localizedStringResource(1864, "Risk score")).append(" ").append(item.recoveryRiskScore).append("/100").append('\n')
    append(localizedStringResource(1868, "Recovery confidence")).append(": ").append(supplierBackorderRecoveryConfidenceTitle(item.recoveryConfidenceLane)).append(" • ").append(localizedStringResource(1873, "Confidence score")).append(" ").append(item.recoveryConfidenceScore).append("/100").append('\n')
    append(localizedStringResource(1907, "Recovery ledger")).append(": ").append(supplierBackorderRecoveryLedgerTitle(item.recoveryLedgerLane)).append(" • ").append(localizedStringResource(1913, "Ledger score")).append(" ").append(item.recoveryLedgerScore).append("/100").append('\n')
    item.recoveryTriageHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryTriageHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1919, "Triage desk")).append(": ").append(hint).append('\n') }
    item.recoveryTriageChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryTriageChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1927, "Triage checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryTriageScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryTriageScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1931, "Triage script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderCommandNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(1932, "Recovery command")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(1932, "Recovery command")).append(": ").append(supplierBackorderRecoveryCommandTitle(item.recoveryCommandLane)).append('\n')
    append(localizedStringResource(1939, "Command score")).append(": ").append(item.recoveryCommandScore).append("/100").append('\n')
    append(localizedStringResource(1919, "Triage desk")).append(": ").append(supplierBackorderRecoveryTriageTitle(item.recoveryTriageLane)).append(" • ").append(localizedStringResource(1926, "Triage score")).append(" ").append(item.recoveryTriageScore).append("/100").append('\n')
    append(localizedStringResource(1859, "Recovery risk")).append(": ").append(supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)).append(" • ").append(localizedStringResource(1864, "Risk score")).append(" ").append(item.recoveryRiskScore).append("/100").append('\n')
    item.recoveryCommandHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommandHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1932, "Recovery command")).append(": ").append(hint).append('\n') }
    item.recoveryCommandChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommandChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1940, "Command checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryCommandScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommandScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1944, "Command script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun SupplierDashboardBackorderDataModel.supplierBackorderDueFilterSeed(): String = when (recoveryUrgencyLane) {
    "overdue" -> "overdue"
    "today" -> "today"
    "soon" -> "soon"
    else -> "all"
}

internal fun AppConfiguration.supplierBackorderSearchKey(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(item.backorderId).append(' ')
    append(item.goodsItemId).append(' ')
    append(item.affectedOrderIds.joinToString(" ")).append(' ')
    append(supplierBackorderTitle(item)).append(' ')
    append(item.barcodeSnapshots.joinToString(" ")).append(' ')
    append(item.storePreview.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.storePreview.visibleLocalizedString("main", "")).append(' ')
    append(item.attentionSummary.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.attentionSummary.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryUrgencyHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryUrgencyHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryOwnerHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryOwnerHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoverySlaHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoverySlaHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryEscalationHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryEscalationHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryProofHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryProofHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryOutcomeHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryOutcomeHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryPackGuardHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryPackGuardHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryContactHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryContactHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryContactScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryContactScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryRiskHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryRiskHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryRiskReasons.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryRiskReasons.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryConfidenceHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryConfidenceHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryConfidenceChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryConfidenceChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryFollowUpHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryFollowUpHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryFollowUpScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryFollowUpScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryHandoffHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryHandoffHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryHandoffChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryHandoffChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryHandoffScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryHandoffScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryClosureHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryClosureHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryClosureChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryClosureChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryClosureScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryClosureScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryLedgerHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryLedgerHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryLedgerChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryLedgerChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryLedgerScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryLedgerScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryTriageHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryTriageHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryTriageChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryTriageChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryTriageScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryTriageScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryCommandHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryCommandHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryCommandChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryCommandChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryCommandScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryCommandScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryPromiseShieldHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryPromiseShieldHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryPromiseShieldChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryPromiseShieldChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryPromiseShieldScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryPromiseShieldScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryWaveHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryWaveHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryAgingHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryAgingHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryAgingChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryAgingChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryAgingScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryAgingScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryBottleneckHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryBottleneckHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryBottleneckChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryBottleneckChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryBottleneckScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryBottleneckScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryLoadHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryLoadHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryLoadChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryLoadChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryLoadScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryLoadScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryImpactHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryImpactHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryImpactChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryImpactChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryImpactScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryImpactScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryCommitHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryCommitHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryCommitChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryCommitChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryCommitScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryCommitScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryAllocationHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryAllocationHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryAllocationChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryAllocationChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryAllocationScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryAllocationScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryExceptionHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryExceptionHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryExceptionChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryExceptionChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryExceptionScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryExceptionScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryCauseHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryCauseHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryCauseChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryCauseChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryCauseScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryCauseScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryVerificationHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryVerificationHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryVerificationChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryVerificationChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryVerificationScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryVerificationScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryApprovalHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryApprovalHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryApprovalChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryApprovalChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryApprovalScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryApprovalScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryExecutionHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryExecutionHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryExecutionChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryExecutionChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryExecutionScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryExecutionScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryReleaseHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryReleaseHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryReleaseChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryReleaseChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryReleaseScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryReleaseScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoverySealHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoverySealHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoverySealChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoverySealChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoverySealScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoverySealScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryCloseoutHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryCloseoutHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryCloseoutChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryCloseoutChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryCloseoutScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryCloseoutScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryReopenHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryReopenHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryReopenChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryReopenChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryReopenScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryReopenScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryReconciliationHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryReconciliationHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryReconciliationChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryReconciliationChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryReconciliationScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryReconciliationScript.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryAuditHint.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryAuditHint.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryAuditChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryAuditChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryAuditScript.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryAuditScript.visibleLocalizedString("main", "")).append(' ')
    append(item.nextRecoveryStep.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.nextRecoveryStep.visibleLocalizedString("main", "")).append(' ')
    append(item.recoveryChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.recoveryChecklist.visibleLocalizedString("main", "")).append(' ')
    append(item.suggestedAction).append(' ')
    append(item.recoveryLane).append(' ')
    append(item.recoveryUrgencyLane).append(' ')
    append(item.recoveryOwnerLane).append(' ')
    append(item.recoverySlaLane).append(' ')
    append(item.recoveryEscalationLane).append(' ')
    append(item.recoveryProofLane).append(' ')
    append(item.recoveryOutcomeLane).append(' ')
    append(item.recoveryPackGuardLane).append(' ')
    append(item.recoveryContactLane).append(' ')
    append(item.recoveryRiskLane).append(' ')
    append(item.recoveryConfidenceLane).append(' ')
    append(item.recoveryFollowUpLane).append(' ')
    append(item.recoveryHandoffLane).append(' ')
    append(item.recoveryClosureLane).append(' ')
    append(item.recoveryLedgerLane).append(' ')
    append(item.recoveryTriageLane).append(' ')
    append(item.recoveryCommandLane).append(' ')
    append(item.recoveryPromiseShieldLane).append(' ')
    append(item.recoveryWaveLane).append(' ')
    append(item.recoveryAgingLane).append(' ')
    append(item.recoveryBottleneckLane).append(' ')
    append(item.recoveryLoadLane).append(' ')
    append(item.recoveryImpactLane).append(' ')
    append(item.recoveryCommitLane).append(' ')
    append(item.recoveryAllocationLane).append(' ')
    append(item.recoveryExceptionLane).append(' ')
    append(item.recoveryCauseLane).append(' ')
    append(item.recoveryVerificationLane).append(' ')
    append(item.recoveryApprovalLane).append(' ')
    append(item.recoveryExecutionLane).append(' ')
    append(item.recoveryReleaseLane).append(' ')
    append(item.recoverySealLane).append(' ')
    append(item.recoveryCloseoutLane).append(' ')
    append(item.recoveryReopenLane).append(' ')
    append(item.recoveryReconciliationLane).append(' ')
    append(item.recoveryAuditLane).append(' ')
    append(item.recoveryRiskScore).append(' ')
    append(item.recoveryConfidenceScore).append(' ')
    append(item.recoveryClosureScore).append(' ')
    append(item.recoveryLedgerScore).append(' ')
    append(item.recoveryTriageScore).append(' ')
    append(item.recoveryCommandScore).append(' ')
    append(item.recoveryPromiseShieldScore).append(' ')
    append(item.recoveryWaveScore).append(' ')
    append(item.recoveryAgingScore).append(' ')
    append(item.recoveryBottleneckScore).append(' ')
    append(item.recoveryLoadScore).append(' ')
    append(item.recoveryImpactScore).append(' ')
    append(item.recoveryCommitScore).append(' ')
    append(item.recoveryAllocationScore).append(' ')
    append(item.recoveryExceptionScore).append(' ')
    append(item.recoveryCauseScore).append(' ')
    append(item.recoveryVerificationScore).append(' ')
    append(item.recoveryApprovalScore).append(' ')
    append(item.recoveryExecutionScore).append(' ')
    append(item.recoveryReleaseScore).append(' ')
    append(item.recoverySealScore).append(' ')
    append(item.recoveryCloseoutScore).append(' ')
    append(item.recoveryReopenScore).append(' ')
    append(item.recoveryReconciliationScore).append(' ')
    append(item.recoveryAuditScore).append(' ')
    append(item.recoveryAgingHours).append(' ')
    append(item.recoveryAgingStartedAtMillis ?: 0L).append(' ')
    append(item.recoveryCheckpointAtMillis ?: 0L).append(' ')
    append(item.recoveryFollowUpAtMillis ?: 0L).append(' ')
    append(item.recoveryCommitByMillis ?: 0L).append(' ')
    append(item.recoveryReopenAtMillis ?: 0L).append(' ')
    append(supplierBackorderActionTitle(item.suggestedAction)).append(' ')
    append(supplierBackorderRecoveryLaneTitle(item.recoveryLane)).append(' ')
    append(supplierBackorderRecoveryUrgencyTitle(item.recoveryUrgencyLane)).append(' ')
    append(supplierBackorderRecoveryOwnerTitle(item.recoveryOwnerLane)).append(' ')
    append(supplierBackorderRecoverySlaTitle(item.recoverySlaLane)).append(' ')
    append(supplierBackorderRecoveryEscalationTitle(item.recoveryEscalationLane)).append(' ')
    append(supplierBackorderRecoveryProofTitle(item.recoveryProofLane)).append(' ')
    append(supplierBackorderRecoveryOutcomeTitle(item.recoveryOutcomeLane)).append(' ')
    append(supplierBackorderRecoveryPackGuardTitle(item.recoveryPackGuardLane)).append(' ')
    append(supplierBackorderRecoveryContactTitle(item.recoveryContactLane)).append(' ')
    append(supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)).append(' ')
    append(supplierBackorderRecoveryConfidenceTitle(item.recoveryConfidenceLane)).append(' ')
    append(supplierBackorderRecoveryFollowUpTitle(item.recoveryFollowUpLane)).append(' ')
    append(supplierBackorderRecoveryHandoffTitle(item.recoveryHandoffLane)).append(' ')
    append(supplierBackorderRecoveryClosureTitle(item.recoveryClosureLane)).append(' ')
    append(supplierBackorderRecoveryLedgerTitle(item.recoveryLedgerLane)).append(' ')
    append(supplierBackorderRecoveryTriageTitle(item.recoveryTriageLane)).append(' ')
    append(supplierBackorderRecoveryCommandTitle(item.recoveryCommandLane)).append(' ')
    append(supplierBackorderRecoveryPromiseShieldTitle(item.recoveryPromiseShieldLane)).append(' ')
    append(supplierBackorderRecoveryWaveTitle(item.recoveryWaveLane)).append(' ')
    append(supplierBackorderRecoveryAgingTitle(item.recoveryAgingLane)).append(' ')
    append(supplierBackorderRecoveryBottleneckTitle(item.recoveryBottleneckLane)).append(' ')
    append(supplierBackorderRecoveryLoadTitle(item.recoveryLoadLane)).append(' ')
    append(supplierBackorderRecoveryImpactTitle(item.recoveryImpactLane)).append(' ')
    append(supplierBackorderRecoveryCommitTitle(item.recoveryCommitLane)).append(' ')
    append(supplierBackorderRecoveryAllocationTitle(item.recoveryAllocationLane)).append(' ')
    append(supplierBackorderRecoveryExceptionTitle(item.recoveryExceptionLane)).append(' ')
    append(supplierBackorderRecoveryCauseTitle(item.recoveryCauseLane)).append(' ')
    append(supplierBackorderRecoveryVerificationTitle(item.recoveryVerificationLane)).append(' ')
    append(supplierBackorderRecoveryApprovalTitle(item.recoveryApprovalLane)).append(' ')
    append(supplierBackorderRecoveryExecutionTitle(item.recoveryExecutionLane)).append(' ')
    append(supplierBackorderRecoveryReleaseTitle(item.recoveryReleaseLane)).append(' ')
    append(supplierBackorderRecoverySealTitle(item.recoverySealLane)).append(' ')
    append(supplierBackorderRecoveryCloseoutTitle(item.recoveryCloseoutLane)).append(' ')
    append(supplierBackorderRecoveryReopenTitle(item.recoveryReopenLane)).append(' ')
    append(supplierBackorderRecoveryReconciliationTitle(item.recoveryReconciliationLane)).append(' ')
    append(supplierBackorderRecoveryAuditTitle(item.recoveryAuditLane)).append(' ')
    append(item.requestedQuantityTotal).append(' ')
    append(item.acceptedQuantityTotal).append(' ')
    append(item.missingQuantityTotal)
}.lowercase()

internal fun AppConfiguration.supplierBackorderPromiseShieldNote(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(1945, "Promise shield")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    append(localizedStringResource(1945, "Promise shield")).append(": ").append(supplierBackorderRecoveryPromiseShieldTitle(item.recoveryPromiseShieldLane)).append('\n')
    append(localizedStringResource(1952, "Promise score")).append(": ").append(item.recoveryPromiseShieldScore).append("/100").append('\n')
    append(localizedStringResource(1932, "Recovery command")).append(": ").append(supplierBackorderRecoveryCommandTitle(item.recoveryCommandLane)).append(" • ").append(localizedStringResource(1939, "Command score")).append(" ").append(item.recoveryCommandScore).append("/100").append('\n')
    append(localizedStringResource(1859, "Recovery risk")).append(": ").append(supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)).append(" • ").append(localizedStringResource(1864, "Risk score")).append(" ").append(item.recoveryRiskScore).append("/100").append('\n')
    append(localizedStringResource(1868, "Recovery confidence")).append(": ").append(supplierBackorderRecoveryConfidenceTitle(item.recoveryConfidenceLane)).append(" • ").append(localizedStringResource(1873, "Confidence score")).append(" ").append(item.recoveryConfidenceScore).append("/100").append('\n')
    item.recoveryPromiseShieldHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryPromiseShieldHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1945, "Promise shield")).append(": ").append(hint).append('\n') }
    item.recoveryPromiseShieldChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryPromiseShieldChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1953, "Promise checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryPromiseShieldScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryPromiseShieldScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1957, "Promise script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief."))
}

internal fun AppConfiguration.supplierBackorderBrief(item: SupplierDashboardBackorderDataModel): String = buildString {
    append(localizedStringResource(1794, "Backorder watch")).append('\n')
    append(supplierBackorderTitle(item)).append('\n')
    if (item.barcodeSnapshots.isNotEmpty()) append(localizedStringResource(69, "Barcode")).append(": ").append(item.barcodeSnapshots.joinToString(", ")).append('\n')
    append(localizedStringResource(1424, "Total requested")).append(": ").append(supplierManufacturerBridgeQuantityText(item.requestedQuantityTotal, item.measurementUnitIdSnapshot)).append('\n')
    append(localizedStringResource(1713, "Accepted qty")).append(": ").append(supplierManufacturerBridgeQuantityText(item.acceptedQuantityTotal, item.measurementUnitIdSnapshot)).append('\n')
    append(localizedStringResource(1796, "Short qty")).append(": ").append(supplierManufacturerBridgeQuantityText(item.missingQuantityTotal, item.measurementUnitIdSnapshot)).append('\n')
    append(localizedStringResource(1797, "Affected orders")).append(": ").append(item.affectedOrderCount).append('\n')
    append(localizedStringResource(1422, "Stores asking")).append(": ").append(item.affectedStoreCount).append('\n')
    append(localizedStringResource(1773, "Declined lines")).append(": ").append(item.declinedLineCount).append('\n')
    append(localizedStringResource(1806, "Partial lines")).append(": ").append(item.partialLineCount).append('\n')
    append(localizedStringResource(1807, "Fully short lines")).append(": ").append(item.fullyShortLineCount).append('\n')
    append(localizedStringResource(1802, "Recovery plan")).append(": ").append(supplierBackorderRecoveryLaneTitle(item.recoveryLane)).append('\n')
    append(localizedStringResource(1809, "Recovery urgency")).append(": ").append(supplierBackorderRecoveryUrgencyTitle(item.recoveryUrgencyLane)).append('\n')
    append(localizedStringResource(1817, "Recovery owner")).append(": ").append(supplierBackorderRecoveryOwnerTitle(item.recoveryOwnerLane)).append('\n')
    append(localizedStringResource(1823, "Promise clock")).append(": ").append(supplierBackorderRecoverySlaTitle(item.recoverySlaLane)).append('\n')
    item.recoveryCheckpointAtMillis?.takeIf { it > 0L }?.let { checkpoint ->
        append(localizedStringResource(1822, "Recovery checkpoint")).append(": ").append(receiptUiDateTime(checkpoint)).append('\n')
    }
    append(localizedStringResource(1828, "Escalation lane")).append(": ").append(supplierBackorderRecoveryEscalationTitle(item.recoveryEscalationLane)).append('\n')
    append(localizedStringResource(1833, "Recovery proof")).append(": ").append(supplierBackorderRecoveryProofTitle(item.recoveryProofLane)).append('\n')
    append(localizedStringResource(1839, "Resolution path")).append(": ").append(supplierBackorderRecoveryOutcomeTitle(item.recoveryOutcomeLane)).append('\n')
    append(localizedStringResource(1845, "Pack guard")).append(": ").append(supplierBackorderRecoveryPackGuardTitle(item.recoveryPackGuardLane)).append('\n')
    append(localizedStringResource(1851, "Contact lane")).append(": ").append(supplierBackorderRecoveryContactTitle(item.recoveryContactLane)).append('\n')
    append(localizedStringResource(1859, "Recovery risk")).append(": ").append(supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)).append(" • ").append(localizedStringResource(1864, "Risk score")).append(" ").append(item.recoveryRiskScore).append('\n')
    append(localizedStringResource(1868, "Recovery confidence")).append(": ").append(supplierBackorderRecoveryConfidenceTitle(item.recoveryConfidenceLane)).append(" • ").append(localizedStringResource(1873, "Confidence score")).append(" ").append(item.recoveryConfidenceScore).append('\n')
    append(localizedStringResource(1877, "Follow-up cadence")).append(": ").append(supplierBackorderRecoveryFollowUpTitle(item.recoveryFollowUpLane)).append('\n')
    item.recoveryFollowUpAtMillis?.takeIf { it > 0L }?.let { followUpAt ->
        append(localizedStringResource(1882, "Next follow-up")).append(": ").append(receiptUiDateTime(followUpAt)).append('\n')
    }
    append(localizedStringResource(1886, "Recovery handoff")).append(": ").append(supplierBackorderRecoveryHandoffTitle(item.recoveryHandoffLane)).append('\n')
    append(localizedStringResource(1896, "Close gate")).append(": ").append(supplierBackorderRecoveryClosureTitle(item.recoveryClosureLane)).append(" • ").append(localizedStringResource(1901, "Closure score")).append(" ").append(item.recoveryClosureScore).append("/100").append('\n')
    append(localizedStringResource(1907, "Recovery ledger")).append(": ").append(supplierBackorderRecoveryLedgerTitle(item.recoveryLedgerLane)).append(" • ").append(localizedStringResource(1913, "Ledger score")).append(" ").append(item.recoveryLedgerScore).append("/100").append('\n')
    append(localizedStringResource(1919, "Triage desk")).append(": ").append(supplierBackorderRecoveryTriageTitle(item.recoveryTriageLane)).append(" • ").append(localizedStringResource(1926, "Triage score")).append(" ").append(item.recoveryTriageScore).append("/100").append('\n')
    append(localizedStringResource(1932, "Recovery command")).append(": ").append(supplierBackorderRecoveryCommandTitle(item.recoveryCommandLane)).append(" • ").append(localizedStringResource(1939, "Command score")).append(" ").append(item.recoveryCommandScore).append("/100").append('\n')
    append(localizedStringResource(1945, "Promise shield")).append(": ").append(supplierBackorderRecoveryPromiseShieldTitle(item.recoveryPromiseShieldLane)).append(" • ").append(localizedStringResource(1952, "Promise score")).append(" ").append(item.recoveryPromiseShieldScore).append("/100").append('\n')
    append(localizedStringResource(1973, "Recovery wave")).append(": ").append(supplierBackorderRecoveryWaveTitle(item.recoveryWaveLane)).append(" • ").append(localizedStringResource(1975, "Wave score")).append(" ").append(item.recoveryWaveScore).append("/100").append('\n')
    append(localizedStringResource(1985, "Recovery aging")).append(": ").append(supplierBackorderRecoveryAgingTitle(item.recoveryAgingLane)).append(" • ").append(localizedStringResource(1990, "Aging score")).append(" ").append(item.recoveryAgingScore).append("/100 • ").append(item.recoveryAgingHours).append("h").append('\n')
    append(localizedStringResource(1998, "Recovery bottleneck")).append(": ").append(supplierBackorderRecoveryBottleneckTitle(item.recoveryBottleneckLane)).append(" • ").append(localizedStringResource(2007, "Bottleneck score")).append(" ").append(item.recoveryBottleneckScore).append("/100").append('\n')
    append(localizedStringResource(2013, "Recovery load")).append(": ").append(supplierBackorderRecoveryLoadTitle(item.recoveryLoadLane)).append(" • ").append(localizedStringResource(2019, "Load score")).append(" ").append(item.recoveryLoadScore).append("/100").append('\n')
    append(localizedStringResource(2029, "Recovery impact")).append(": ").append(supplierBackorderRecoveryImpactTitle(item.recoveryImpactLane)).append(" • ").append(localizedStringResource(2035, "Impact score")).append(" ").append(item.recoveryImpactScore).append("/100").append('\n')
    append(localizedStringResource(2044, "Recovery commit")).append(": ").append(supplierBackorderRecoveryCommitTitle(item.recoveryCommitLane)).append(" • ").append(localizedStringResource(2051, "Commit score")).append(" ").append(item.recoveryCommitScore).append("/100").append('\n')
    append(localizedStringResource(2062, "Recovery allocation")).append(": ").append(supplierBackorderRecoveryAllocationTitle(item.recoveryAllocationLane)).append(" • ").append(localizedStringResource(2068, "Allocation score")).append(" ").append(item.recoveryAllocationScore).append("/100").append('\n')
    append(localizedStringResource(2079, "Recovery exception")).append(": ").append(supplierBackorderRecoveryExceptionTitle(item.recoveryExceptionLane)).append(" • ").append(localizedStringResource(2087, "Exception score")).append(" ").append(item.recoveryExceptionScore).append("/100").append('\n')
    append(localizedStringResource(2101, "Root cause")).append(": ").append(supplierBackorderRecoveryCauseTitle(item.recoveryCauseLane)).append(" • ").append(localizedStringResource(2109, "Cause score")).append(" ").append(item.recoveryCauseScore).append("/100").append('\n')
    append(localizedStringResource(2120, "Recovery verification")).append(": ").append(supplierBackorderRecoveryVerificationTitle(item.recoveryVerificationLane)).append(" • ").append(localizedStringResource(2128, "Verification score")).append(" ").append(item.recoveryVerificationScore).append("/100").append('\n')
    append(localizedStringResource(2141, "Recovery approval")).append(": ").append(supplierBackorderRecoveryApprovalTitle(item.recoveryApprovalLane)).append(" • ").append(localizedStringResource(2149, "Approval score")).append(" ").append(item.recoveryApprovalScore).append("/100").append('\n')
    append(localizedStringResource(2162, "Recovery execution")).append(": ").append(supplierBackorderRecoveryExecutionTitle(item.recoveryExecutionLane)).append(" • ").append(localizedStringResource(2169, "Execution score")).append(" ").append(item.recoveryExecutionScore).append("/100").append('\n')
    append(localizedStringResource(2181, "Recovery release")).append(": ").append(supplierBackorderRecoveryReleaseTitle(item.recoveryReleaseLane)).append(" • ").append(localizedStringResource(2188, "Release score")).append(" ").append(item.recoveryReleaseScore).append("/100").append('\n')
    append(localizedStringResource(2201, "Recovery seal")).append(": ").append(supplierBackorderRecoverySealTitle(item.recoverySealLane)).append(" • ").append(localizedStringResource(2208, "Seal score")).append(" ").append(item.recoverySealScore).append("/100").append('\n')
    append(localizedStringResource(2221, "Recovery closeout")).append(": ").append(supplierBackorderRecoveryCloseoutTitle(item.recoveryCloseoutLane)).append(" • ").append(localizedStringResource(2228, "Closeout score")).append(" ").append(item.recoveryCloseoutScore).append("/100").append('\n')
    append(localizedStringResource(2241, "Recovery reopen")).append(": ").append(supplierBackorderRecoveryReopenTitle(item.recoveryReopenLane)).append(" • ").append(localizedStringResource(2248, "Reopen score")).append(" ").append(item.recoveryReopenScore).append("/100").append('\n')
    item.recoveryReopenAtMillis?.takeIf { it > 0L }?.let { reopenAt ->
        append(localizedStringResource(2249, "Reopen checkpoint")).append(": ").append(receiptUiDateTime(reopenAt)).append('\n')
    }
    append(localizedStringResource(2262, "Recovery reconciliation")).append(": ").append(supplierBackorderRecoveryReconciliationTitle(item.recoveryReconciliationLane)).append(" • ").append(localizedStringResource(2269, "Reconcile score")).append(" ").append(item.recoveryReconciliationScore).append("/100").append('\n')
    append(localizedStringResource(2281, "Recovery audit")).append(": ").append(supplierBackorderRecoveryAuditTitle(item.recoveryAuditLane)).append(" • ").append(localizedStringResource(2288, "Audit score")).append(" ").append(item.recoveryAuditScore).append("/100").append('\n')
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1808, "Store names are omitted from this copied shortage brief.")).append('\n')
    item.recoveryHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1802, "Recovery plan")).append(": ").append(hint).append('\n') }
    item.recoveryUrgencyHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryUrgencyHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1809, "Recovery urgency")).append(": ").append(hint).append('\n') }
    item.recoveryOwnerHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryOwnerHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1817, "Recovery owner")).append(": ").append(hint).append('\n') }
    item.recoverySlaHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySlaHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1823, "Promise clock")).append(": ").append(hint).append('\n') }
    item.recoveryEscalationHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryEscalationHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1828, "Escalation lane")).append(": ").append(hint).append('\n') }
    item.recoveryProofHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryProofHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1833, "Recovery proof")).append(": ").append(hint).append('\n') }
    item.recoveryOutcomeHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryOutcomeHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1839, "Resolution path")).append(": ").append(hint).append('\n') }
    item.recoveryPackGuardHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryPackGuardHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1845, "Pack guard")).append(": ").append(hint).append('\n') }
    item.recoveryContactHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryContactHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1851, "Contact lane")).append(": ").append(hint).append('\n') }
    item.recoveryContactScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryContactScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1857, "Contact script")).append(":\n").append(script).append('\n') }
    item.recoveryRiskHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryRiskHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1859, "Recovery risk")).append(": ").append(hint).append('\n') }
    item.recoveryRiskReasons.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryRiskReasons.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { reasons -> append(localizedStringResource(1865, "Risk reasons")).append(": ").append(reasons).append('\n') }
    item.recoveryConfidenceHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryConfidenceHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1868, "Recovery confidence")).append(": ").append(hint).append('\n') }
    item.recoveryConfidenceChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryConfidenceChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1874, "Confidence checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryFollowUpHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryFollowUpHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1877, "Follow-up cadence")).append(": ").append(hint).append('\n') }
    item.recoveryFollowUpScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryFollowUpScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1884, "Follow-up script")).append(":\n").append(script).append('\n') }
    item.recoveryHandoffHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryHandoffHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1886, "Recovery handoff")).append(": ").append(hint).append('\n') }
    item.recoveryHandoffChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryHandoffChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1891, "Handoff checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryHandoffScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryHandoffScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1895, "Handoff script")).append(":\n").append(script).append('\n') }
    item.recoveryClosureHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryClosureHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1896, "Close gate")).append(": ").append(hint).append('\n') }
    item.recoveryClosureChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryClosureChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1902, "Closure checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryClosureScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryClosureScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1906, "Close-gate script")).append(":\n").append(script).append('\n') }
    item.recoveryLedgerHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLedgerHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1907, "Recovery ledger")).append(": ").append(hint).append('\n') }
    item.recoveryLedgerChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLedgerChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1914, "Ledger checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryLedgerScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLedgerScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1918, "Ledger script")).append(":\n").append(script).append('\n') }
    item.recoveryTriageHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryTriageHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1919, "Triage desk")).append(": ").append(hint).append('\n') }
    item.recoveryTriageChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryTriageChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1927, "Triage checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryTriageScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryTriageScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1931, "Triage script")).append(":\n").append(script).append('\n') }
    item.recoveryCommandHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommandHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1932, "Recovery command")).append(": ").append(hint).append('\n') }
    item.recoveryCommandChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommandChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1940, "Command checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryCommandScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommandScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1944, "Command script")).append(":\n").append(script).append('\n') }
    item.recoveryPromiseShieldHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryPromiseShieldHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1945, "Promise shield")).append(": ").append(hint).append('\n') }
    item.recoveryPromiseShieldChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryPromiseShieldChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1953, "Promise checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryPromiseShieldScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryPromiseShieldScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(1957, "Promise script")).append(":\n").append(script).append('\n') }
    item.recoveryWaveHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryWaveHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1973, "Recovery wave")).append(": ").append(hint).append('\n') }
    item.recoveryBottleneckHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryBottleneckHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(1998, "Recovery bottleneck")).append(": ").append(hint).append('\n') }
    item.recoveryBottleneckChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryBottleneckChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2008, "Bottleneck checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryBottleneckScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryBottleneckScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2011, "Bottleneck script")).append(":\n").append(script).append('\n') }
    item.recoveryLoadHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLoadHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2013, "Recovery load")).append(": ").append(hint).append('\n') }
    item.recoveryLoadChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLoadChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2020, "Load checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryLoadScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLoadScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2023, "Load script")).append(":\n").append(script).append('\n') }
    item.recoveryImpactHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryImpactHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2029, "Recovery impact")).append(": ").append(hint).append('\n') }
    item.recoveryImpactChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryImpactChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2036, "Impact checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryImpactScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryImpactScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2038, "Impact script")).append(":\n").append(script).append('\n') }
    append(localizedStringResource(2044, "Recovery commit")).append(": ").append(supplierBackorderRecoveryCommitTitle(item.recoveryCommitLane)).append(" • ").append(localizedStringResource(2051, "Commit score")).append(" ").append(item.recoveryCommitScore).append("/100").append('\n')
    append(localizedStringResource(2062, "Recovery allocation")).append(": ").append(supplierBackorderRecoveryAllocationTitle(item.recoveryAllocationLane)).append(" • ").append(localizedStringResource(2068, "Allocation score")).append(" ").append(item.recoveryAllocationScore).append("/100").append('\n')
    append(localizedStringResource(2079, "Recovery exception")).append(": ").append(supplierBackorderRecoveryExceptionTitle(item.recoveryExceptionLane)).append(" • ").append(localizedStringResource(2087, "Exception score")).append(" ").append(item.recoveryExceptionScore).append("/100").append('\n')
    append(localizedStringResource(2101, "Recovery cause")).append(": ").append(supplierBackorderRecoveryCauseTitle(item.recoveryCauseLane)).append(" • ").append(localizedStringResource(2109, "Cause score")).append(" ").append(item.recoveryCauseScore).append("/100").append('\n')
    append(localizedStringResource(2120, "Recovery verification")).append(": ").append(supplierBackorderRecoveryVerificationTitle(item.recoveryVerificationLane)).append(" • ").append(localizedStringResource(2128, "Verification score")).append(" ").append(item.recoveryVerificationScore).append("/100").append('\n')
    append(localizedStringResource(2141, "Recovery approval")).append(": ").append(supplierBackorderRecoveryApprovalTitle(item.recoveryApprovalLane)).append(" • ").append(localizedStringResource(2149, "Approval score")).append(" ").append(item.recoveryApprovalScore).append("/100").append('\n')
    append(localizedStringResource(2162, "Recovery execution")).append(": ").append(supplierBackorderRecoveryExecutionTitle(item.recoveryExecutionLane)).append(" • ").append(localizedStringResource(2169, "Execution score")).append(" ").append(item.recoveryExecutionScore).append("/100").append('\n')
    append(localizedStringResource(2181, "Recovery release")).append(": ").append(supplierBackorderRecoveryReleaseTitle(item.recoveryReleaseLane)).append(" • ").append(localizedStringResource(2188, "Release score")).append(" ").append(item.recoveryReleaseScore).append("/100").append('\n')
    item.recoveryCommitByMillis?.takeIf { it > 0L }?.let { commitAt ->
        append(localizedStringResource(2053, "Commit by")).append(": ").append(receiptUiDateTime(commitAt)).append('\n')
    }
    item.recoveryCommitHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommitHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2044, "Recovery commit")).append(": ").append(hint).append('\n') }
    item.recoveryCommitChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommitChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2052, "Commit checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryCommitScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommitScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2055, "Commit script")).append(":\n").append(script).append('\n') }
    item.recoveryCauseHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCauseHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2101, "Recovery cause")).append(": ").append(hint).append('\n') }
    item.recoveryCauseChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCauseChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2110, "Cause checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryCauseScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCauseScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2112, "Cause script")).append(":\n").append(script).append('\n') }
    item.recoveryVerificationHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryVerificationHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2120, "Recovery verification")).append(": ").append(hint).append('\n') }
    item.recoveryVerificationChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryVerificationChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2129, "Verification checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryVerificationScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryVerificationScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2131, "Verification script")).append(":\n").append(script).append('\n') }
    item.recoveryApprovalHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryApprovalHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2141, "Recovery approval")).append(": ").append(hint).append('\n') }
    item.recoveryApprovalChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryApprovalChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2150, "Approval checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryApprovalScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryApprovalScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2152, "Approval script")).append(":\n").append(script).append('\n') }
    item.recoveryExecutionHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExecutionHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2162, "Recovery execution")).append(": ").append(hint).append('\n') }
    item.recoveryExecutionChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExecutionChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2170, "Execution checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryExecutionScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExecutionScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2172, "Execution script")).append(":\n").append(script).append('\n') }
    item.recoveryReleaseHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReleaseHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2181, "Recovery release")).append(": ").append(hint).append('\n') }
    item.recoveryReleaseChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReleaseChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2189, "Release checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryReleaseScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReleaseScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2191, "Release script")).append(":\n").append(script).append('\n') }
    item.recoverySealHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySealHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2201, "Recovery seal")).append(": ").append(hint).append('\n') }
    item.recoverySealChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySealChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2209, "Seal checklist")).append(":\n").append(checklist).append('\n') }
    item.recoverySealScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySealScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2212, "Seal script")).append(":\n").append(script).append('\n') }
    item.recoveryCloseoutHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCloseoutHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2221, "Recovery closeout")).append(": ").append(hint).append('\n') }
    item.recoveryCloseoutChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCloseoutChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2229, "Closeout checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryCloseoutScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCloseoutScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2232, "Closeout script")).append(":\n").append(script).append('\n') }
    item.recoveryReopenHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReopenHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2241, "Recovery reopen")).append(": ").append(hint).append('\n') }
    item.recoveryReopenChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReopenChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2250, "Reopen checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryReopenScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReopenScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2253, "Reopen script")).append(":\n").append(script).append('\n') }
    item.recoveryReconciliationHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReconciliationHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2262, "Recovery reconciliation")).append(": ").append(hint).append('\n') }
    item.recoveryReconciliationChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReconciliationChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2270, "Reconcile checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryReconciliationScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReconciliationScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2273, "Reconcile script")).append(":\n").append(script).append('\n') }
    item.recoveryAuditHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAuditHint.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { hint -> append(localizedStringResource(2281, "Recovery audit")).append(": ").append(hint).append('\n') }
    item.recoveryAuditChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAuditChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(2289, "Audit checklist")).append(":\n").append(checklist).append('\n') }
    item.recoveryAuditScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAuditScript.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { script -> append(localizedStringResource(2290, "Audit script")).append(":\n").append(script).append('\n') }
    item.nextRecoveryStep.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.nextRecoveryStep.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { step -> append(localizedStringResource(1814, "Next recovery step")).append(": ").append(step).append('\n') }
    item.recoveryChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryChecklist.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { checklist -> append(localizedStringResource(1815, "Recovery checklist")).append(":\n").append(checklist).append('\n') }
    item.attentionSummary.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.attentionSummary.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { attention -> append(localizedStringResource(1756, "Attention notes")).append(": ").append(attention).append('\n') }
    item.earliestDueAtMillis?.takeIf { it > 0L }?.let { due ->
        append(localizedStringResource(1723, "Earliest due")).append(": ").append(receiptUiDateTime(due)).append('\n')
    }
    append(localizedStringResource(1722, "Priority score")).append(": ").append(item.priorityScore).append('\n')
    append(localizedStringResource(1650, "Actions")).append(": ").append(supplierBackorderActionTitle(item.suggestedAction))
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCard(item: SupplierDashboardBackorderDataModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.ErrorColor.copy(alpha = 0.65f), RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        SupplierBackorderWatchCardIntroContent(
            item = item
        )
        val section = sectionTabsWidget(
            stateKey = "supplier-backorder:${item.backorderId.ifBlank { item.goodsItemId }}",
            tabs = listOf(
                TabContent("overview", authUiText("Overview", "Обзор", "Шолу", "Жалпы көрүнүш")),
                TabContent("owner", authUiText("Owner & timing", "Ответственный и сроки", "Жауапты және мерзімдер", "Жооптуу жана мөөнөттөр")),
                TabContent("outcome", authUiText("Outcome & risk", "Результат и риск", "Нәтиже және тәуекел", "Натыйжа жана тобокелдик")),
                TabContent("followup", authUiText("Follow-up", "Сопровождение", "Бақылау", "Кийинки аракеттер")),
                TabContent("closure", authUiText("Closure & ledger", "Закрытие и учёт", "Жабу және есеп", "Жабуу жана эсеп журналы")),
                TabContent("promise", authUiText("Commands & promises", "Действия и обещания", "Әрекеттер мен уәделер", "Буйруктар жана убадалар")),
                TabContent("wave", authUiText("Waves & load", "Волны и нагрузка", "Толқындар және жүктеме", "Толкундар жана жүктөм")),
                TabContent("allocation", authUiText("Impact & allocation", "Влияние и распределение", "Әсер және бөлу", "Таасир жана бөлүштүрүү")),
                TabContent("exception", authUiText("Exceptions & causes", "Исключения и причины", "Ерекше жағдайлар және себептер", "Өзгөчө учурлар жана себептер")),
                TabContent("verification", authUiText("Verification & release", "Проверка и выпуск", "Тексеру және шығару", "Текшерүү жана бошотуу")),
                TabContent("plan", authUiText("Plan & attention", "План и внимание", "Жоспар және назар", "План жана көңүл буруу")),
                TabContent("closeout", authUiText("Seal & closeout", "Фиксация и завершение", "Бекіту және аяқтау", "Бекитүү жана жыйынтыктоо")),
                TabContent("audit", authUiText("Reopen & audit", "Повторное открытие и аудит", "Қайта ашу және аудит", "Кайра ачуу жана текшерүү")),
                TabContent("actions", authUiText("Actions", "Действия", "Әрекеттер", "Аракеттер"))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(vertical = stateValues.marginTextField / 2),
        )

        if (section == "overview") {
            SupplierBackorderWatchCardMetricLines(
                item = item
            )
        }
        if (section == "owner") {
            SupplierBackorderWatchCardGuideOwnerClockProofContent(
                item = item
            )
        }
        if (section == "outcome") {
            SupplierBackorderWatchCardGuideOutcomeRiskConfidenceContent(
                item = item
            )
        }
        if (section == "followup") {
            SupplierBackorderWatchCardGuideFollowupHandoffContent(
                item = item
            )
        }
        if (section == "closure") {
            SupplierBackorderWatchCardGuideClosureLedgerTriageContent(
                item = item
            )
        }
        if (section == "promise") {
            SupplierBackorderWatchCardGuideCommandPromiseContent(
                item = item
            )
        }
        if (section == "wave") {
            SupplierBackorderWatchCardGuideWaveLoadContent(
                item = item
            )
        }
        if (section == "allocation") {
            SupplierBackorderWatchCardGuideImpactAllocationContent(
                item = item
            )
        }
        if (section == "exception") {
            SupplierBackorderWatchCardGuideExceptionCauseContent(
                item = item
            )
        }
        if (section == "verification") {
            SupplierBackorderWatchCardGuideVerifyReleaseContent(
                item = item
            )
        }
        if (section == "plan") {
            SupplierBackorderWatchCardGuidePlanAttentionContent(
                item = item
            )
        }
        if (section == "closeout") {
            SupplierBackorderWatchCardGuideSealCloseoutContent(
                item = item
            )
        }
        if (section == "audit") {
            SupplierBackorderWatchCardGuideReopenAuditContent(
                item = item
            )
        }
        if (section == "actions") {
            SupplierBackorderWatchCardActions(
                item = item
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardIntroContent(
    item: SupplierDashboardBackorderDataModel
) {
    val title = supplierBackorderTitle(item)
    val actionTitle = supplierBackorderActionTitle(item.suggestedAction)
    val requestedQuantityText = supplierManufacturerBridgeQuantityText(item.requestedQuantityTotal, item.measurementUnitIdSnapshot)
    val acceptedQuantityText = supplierManufacturerBridgeQuantityText(item.acceptedQuantityTotal, item.measurementUnitIdSnapshot)
    val missingQuantityText = supplierManufacturerBridgeQuantityText(item.missingQuantityTotal, item.measurementUnitIdSnapshot)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.ErrorColor.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            CpImage(
                modifier = Modifier.size(32.dp),
                url = stateValues.drawablePathIconSupplierBackorderRecovery,
                fallbackRes = stateValues.drawableResIconSupplierBackorderRecovery.value,
                contentDescription = localizedStringResource(1816, "Backorder recovery"),
                tintColor = stateValues.ErrorColor
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.barcodeSnapshots.take(3).joinToString(" • ").ifBlank { stateValues.stringNoName },
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = actionTitle,
            color = stateValues.ErrorColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (stateValues.isNarrowScreen) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SupplierCatalogChip(text = "${localizedStringResource(1424, "Total requested")}: $requestedQuantityText")
            SupplierCatalogChip(text = "${localizedStringResource(1713, "Accepted qty")}: $acceptedQuantityText")
            SupplierCatalogChip(text = "${localizedStringResource(1796, "Short qty")}: $missingQuantityText")
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1424, "Total requested")}: $requestedQuantityText") }
            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1713, "Accepted qty")}: $acceptedQuantityText") }
            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1796, "Short qty")}: $missingQuantityText") }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardMetricLines(
    item: SupplierDashboardBackorderDataModel
) {
    val recoveryCommitByText = item.recoveryCommitByMillis
        ?.takeIf { it > 0L }
        ?.let { receiptUiDateTime(it) }
        .orEmpty()
    val recoveryReopenAtText = item.recoveryReopenAtMillis
        ?.takeIf { it > 0L }
        ?.let { receiptUiDateTime(it) }
        .orEmpty()
    val recoveryFollowUpAtText = item.recoveryFollowUpAtMillis
        ?.takeIf { it > 0L }
        ?.let { receiptUiDateTime(it) }
        .orEmpty()
    val recoveryCheckpointText = item.recoveryCheckpointAtMillis?.takeIf { it > 0L }?.let { checkpoint -> receiptUiDateTime(checkpoint) }.orEmpty()

    StockCardInfoLine(localizedStringResource(1797, "Affected orders"), item.affectedOrderCount.toString(), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1422, "Stores asking"), item.affectedStoreCount.toString(), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1773, "Declined lines"), item.declinedLineCount.toString(), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1806, "Partial lines"), item.partialLineCount.toString(), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1807, "Fully short lines"), item.fullyShortLineCount.toString(), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1802, "Recovery plan"), supplierBackorderRecoveryLaneTitle(item.recoveryLane), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1809, "Recovery urgency"), supplierBackorderRecoveryUrgencyTitle(item.recoveryUrgencyLane), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1817, "Recovery owner"), supplierBackorderRecoveryOwnerTitle(item.recoveryOwnerLane), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1823, "Promise clock"), supplierBackorderRecoverySlaTitle(item.recoverySlaLane), stateValues.TextColor)
    if (recoveryCheckpointText.isNotBlank()) {
        StockCardInfoLine(localizedStringResource(1822, "Recovery checkpoint"), recoveryCheckpointText, stateValues.TextColor)
    }
    StockCardInfoLine(localizedStringResource(1828, "Escalation lane"), supplierBackorderRecoveryEscalationTitle(item.recoveryEscalationLane), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1833, "Recovery proof"), supplierBackorderRecoveryProofTitle(item.recoveryProofLane), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1839, "Resolution path"), supplierBackorderRecoveryOutcomeTitle(item.recoveryOutcomeLane), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1845, "Pack guard"), supplierBackorderRecoveryPackGuardTitle(item.recoveryPackGuardLane), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1851, "Contact lane"), supplierBackorderRecoveryContactTitle(item.recoveryContactLane), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1859, "Recovery risk"), "${supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)} • ${localizedStringResource(1864, "Risk score")} ${item.recoveryRiskScore}", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1868, "Recovery confidence"), "${supplierBackorderRecoveryConfidenceTitle(item.recoveryConfidenceLane)} • ${localizedStringResource(1873, "Confidence score")} ${item.recoveryConfidenceScore}", stateValues.TextColor)
    StockCardInfoLine(
        localizedStringResource(1877, "Follow-up cadence"),
        listOfNotNull(
            supplierBackorderRecoveryFollowUpTitle(item.recoveryFollowUpLane),
            recoveryFollowUpAtText.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(1882, "Next follow-up")}: $it" }
        ).joinToString(" • "),
        stateValues.TextColor
    )
    StockCardInfoLine(localizedStringResource(1886, "Recovery handoff"), supplierBackorderRecoveryHandoffTitle(item.recoveryHandoffLane), stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1896, "Close gate"), "${supplierBackorderRecoveryClosureTitle(item.recoveryClosureLane)} • ${localizedStringResource(1901, "Closure score")} ${item.recoveryClosureScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1907, "Recovery ledger"), "${supplierBackorderRecoveryLedgerTitle(item.recoveryLedgerLane)} • ${localizedStringResource(1913, "Ledger score")} ${item.recoveryLedgerScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1919, "Triage desk"), "${supplierBackorderRecoveryTriageTitle(item.recoveryTriageLane)} • ${localizedStringResource(1926, "Triage score")} ${item.recoveryTriageScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1932, "Recovery command"), "${supplierBackorderRecoveryCommandTitle(item.recoveryCommandLane)} • ${localizedStringResource(1939, "Command score")} ${item.recoveryCommandScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1945, "Promise shield"), "${supplierBackorderRecoveryPromiseShieldTitle(item.recoveryPromiseShieldLane)} • ${localizedStringResource(1952, "Promise score")} ${item.recoveryPromiseShieldScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1973, "Recovery wave"), "${supplierBackorderRecoveryWaveTitle(item.recoveryWaveLane)} • ${localizedStringResource(1975, "Wave score")} ${item.recoveryWaveScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1985, "Recovery aging"), "${supplierBackorderRecoveryAgingTitle(item.recoveryAgingLane)} • ${localizedStringResource(1990, "Aging score")} ${item.recoveryAgingScore}/100 • ${item.recoveryAgingHours}h", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(1998, "Recovery bottleneck"), "${supplierBackorderRecoveryBottleneckTitle(item.recoveryBottleneckLane)} • ${localizedStringResource(2007, "Bottleneck score")} ${item.recoveryBottleneckScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(2013, "Recovery load"), "${supplierBackorderRecoveryLoadTitle(item.recoveryLoadLane)} • ${localizedStringResource(2019, "Load score")} ${item.recoveryLoadScore}/100 • ${localizedStringResource(1798, "Affected orders")} ${item.affectedOrderCount}", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(2029, "Recovery impact"), "${supplierBackorderRecoveryImpactTitle(item.recoveryImpactLane)} • ${localizedStringResource(2035, "Impact score")} ${item.recoveryImpactScore}/100", stateValues.TextColor)
    StockCardInfoLine(
        localizedStringResource(2044, "Recovery commit"),
        listOfNotNull(
            "${supplierBackorderRecoveryCommitTitle(item.recoveryCommitLane)} • ${localizedStringResource(2051, "Commit score")} ${item.recoveryCommitScore}/100",
            recoveryCommitByText.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(2053, "Commit by")}: $it" }
        ).joinToString(" • "),
        stateValues.TextColor
    )
    StockCardInfoLine(localizedStringResource(2062, "Recovery allocation"), "${supplierBackorderRecoveryAllocationTitle(item.recoveryAllocationLane)} • ${localizedStringResource(2068, "Allocation score")} ${item.recoveryAllocationScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(2079, "Recovery exception"), "${supplierBackorderRecoveryExceptionTitle(item.recoveryExceptionLane)} • ${localizedStringResource(2087, "Exception score")} ${item.recoveryExceptionScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(2101, "Recovery cause"), "${supplierBackorderRecoveryCauseTitle(item.recoveryCauseLane)} • ${localizedStringResource(2109, "Cause score")} ${item.recoveryCauseScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(2120, "Recovery verification"), "${supplierBackorderRecoveryVerificationTitle(item.recoveryVerificationLane)} • ${localizedStringResource(2128, "Verification score")} ${item.recoveryVerificationScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(2141, "Recovery approval"), "${supplierBackorderRecoveryApprovalTitle(item.recoveryApprovalLane)} • ${localizedStringResource(2149, "Approval score")} ${item.recoveryApprovalScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(2162, "Recovery execution"), "${supplierBackorderRecoveryExecutionTitle(item.recoveryExecutionLane)} • ${localizedStringResource(2169, "Execution score")} ${item.recoveryExecutionScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(2181, "Recovery release"), "${supplierBackorderRecoveryReleaseTitle(item.recoveryReleaseLane)} • ${localizedStringResource(2188, "Release score")} ${item.recoveryReleaseScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(2201, "Recovery seal"), "${supplierBackorderRecoverySealTitle(item.recoverySealLane)} • ${localizedStringResource(2208, "Seal score")} ${item.recoverySealScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(2221, "Recovery closeout"), "${supplierBackorderRecoveryCloseoutTitle(item.recoveryCloseoutLane)} • ${localizedStringResource(2228, "Closeout score")} ${item.recoveryCloseoutScore}/100", stateValues.TextColor)
    StockCardInfoLine(
        localizedStringResource(2241, "Recovery reopen"),
        buildString {
            append(supplierBackorderRecoveryReopenTitle(item.recoveryReopenLane))
            append(" • ").append(localizedStringResource(2248, "Reopen score")).append(' ').append(item.recoveryReopenScore).append("/100")
            if (recoveryReopenAtText.isNotBlank()) append(" • ").append(localizedStringResource(2249, "Reopen checkpoint")).append(' ').append(recoveryReopenAtText)
        },
        stateValues.TextColor
    )
    StockCardInfoLine(localizedStringResource(2262, "Recovery reconciliation"), "${supplierBackorderRecoveryReconciliationTitle(item.recoveryReconciliationLane)} • ${localizedStringResource(2269, "Reconcile score")} ${item.recoveryReconciliationScore}/100", stateValues.TextColor)
    StockCardInfoLine(localizedStringResource(2281, "Recovery audit"), "${supplierBackorderRecoveryAuditTitle(item.recoveryAuditLane)} • ${localizedStringResource(2288, "Audit score")} ${item.recoveryAuditScore}/100", stateValues.TextColor)
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardGuideOwnerClockProofContent(
    item: SupplierDashboardBackorderDataModel
) {
    val recoveryOwnerHintText = item.recoveryOwnerHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryOwnerHint.visibleLocalizedString("main", "") }
    val recoverySlaHintText = item.recoverySlaHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySlaHint.visibleLocalizedString("main", "") }
    val recoveryEscalationHintText = item.recoveryEscalationHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryEscalationHint.visibleLocalizedString("main", "") }
    val recoveryProofHintText = item.recoveryProofHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryProofHint.visibleLocalizedString("main", "") }

    if (recoveryOwnerHintText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.07f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.24f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryOwner,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryOwner.value,
                    contentDescription = localizedStringResource(1817, "Recovery owner"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = supplierBackorderRecoveryOwnerTitle(item.recoveryOwnerLane),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = recoveryOwnerHintText,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    if (recoverySlaHintText.isNotBlank() || recoveryEscalationHintText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.08f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.30f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryClock,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryClock.value,
                    contentDescription = localizedStringResource(1823, "Promise clock"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = localizedStringResource(1823, "Promise clock"),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoverySlaHintText.isNotBlank()) {
                Text(
                    text = recoverySlaHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryEscalationHintText.isNotBlank()) {
                Text(
                    text = recoveryEscalationHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (recoveryProofHintText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.07f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.26f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryProof,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryProof.value,
                    contentDescription = localizedStringResource(1833, "Recovery proof"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = supplierBackorderRecoveryProofTitle(item.recoveryProofLane),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = recoveryProofHintText,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardGuideOutcomeRiskConfidenceContent(
    item: SupplierDashboardBackorderDataModel
) {
    val recoveryOutcomeHintText = item.recoveryOutcomeHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryOutcomeHint.visibleLocalizedString("main", "") }
    val recoveryPackGuardHintText = item.recoveryPackGuardHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryPackGuardHint.visibleLocalizedString("main", "") }
    val recoveryContactHintText = item.recoveryContactHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryContactHint.visibleLocalizedString("main", "") }
    val recoveryContactScriptText = item.recoveryContactScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryContactScript.visibleLocalizedString("main", "") }
    val recoveryRiskHintText = item.recoveryRiskHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryRiskHint.visibleLocalizedString("main", "") }
    val recoveryRiskReasonsText = item.recoveryRiskReasons.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryRiskReasons.visibleLocalizedString("main", "") }
    val recoveryConfidenceHintText = item.recoveryConfidenceHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryConfidenceHint.visibleLocalizedString("main", "") }
    val recoveryConfidenceChecklistText = item.recoveryConfidenceChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryConfidenceChecklist.visibleLocalizedString("main", "") }

    if (recoveryOutcomeHintText.isNotBlank() || recoveryPackGuardHintText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.07f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.28f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryResolution,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryResolution.value,
                    contentDescription = localizedStringResource(1839, "Resolution path"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = supplierBackorderRecoveryOutcomeTitle(item.recoveryOutcomeLane),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryOutcomeHintText.isNotBlank()) {
                Text(
                    text = recoveryOutcomeHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryPackGuardHintText.isNotBlank()) {
                Text(
                    text = recoveryPackGuardHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (recoveryContactHintText.isNotBlank() || recoveryContactScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.075f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.28f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryContact,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryContact.value,
                    contentDescription = localizedStringResource(1851, "Contact lane"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = supplierBackorderRecoveryContactTitle(item.recoveryContactLane),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryContactHintText.isNotBlank()) {
                Text(
                    text = recoveryContactHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryContactScriptText.isNotBlank()) {
                Text(
                    text = recoveryContactScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (recoveryRiskHintText.isNotBlank() || recoveryRiskReasonsText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.ErrorColor.copy(alpha = 0.055f))
                .border(stateValues.unfocusedBorderWidth, stateValues.ErrorColor.copy(alpha = 0.24f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryRisk,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryRisk.value,
                    contentDescription = localizedStringResource(1859, "Recovery risk"),
                    tintColor = stateValues.ErrorColor
                )
                Text(
                    text = "${supplierBackorderRecoveryRiskTitle(item.recoveryRiskLane)} • ${localizedStringResource(1864, "Risk score")} ${item.recoveryRiskScore}",
                    color = stateValues.ErrorColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryRiskHintText.isNotBlank()) {
                Text(
                    text = recoveryRiskHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryRiskReasonsText.isNotBlank()) {
                Text(
                    text = recoveryRiskReasonsText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (recoveryConfidenceHintText.isNotBlank() || recoveryConfidenceChecklistText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.07f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.26f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryConfidence,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryConfidence.value,
                    contentDescription = localizedStringResource(1868, "Recovery confidence"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryConfidenceTitle(item.recoveryConfidenceLane)} • ${localizedStringResource(1873, "Confidence score")} ${item.recoveryConfidenceScore}",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryConfidenceHintText.isNotBlank()) {
                Text(
                    text = recoveryConfidenceHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryConfidenceChecklistText.isNotBlank()) {
                Text(
                    text = recoveryConfidenceChecklistText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardGuideFollowupHandoffContent(
    item: SupplierDashboardBackorderDataModel
) {
    val recoveryFollowUpHintText = item.recoveryFollowUpHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryFollowUpHint.visibleLocalizedString("main", "") }
    val recoveryFollowUpScriptText = item.recoveryFollowUpScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryFollowUpScript.visibleLocalizedString("main", "") }
    val recoveryHandoffHintText = item.recoveryHandoffHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryHandoffHint.visibleLocalizedString("main", "") }
    val recoveryHandoffChecklistText = item.recoveryHandoffChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryHandoffChecklist.visibleLocalizedString("main", "") }
    val recoveryHandoffScriptText = item.recoveryHandoffScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryHandoffScript.visibleLocalizedString("main", "") }
    val recoveryFollowUpAtText = item.recoveryFollowUpAtMillis
        ?.takeIf { it > 0L }
        ?.let { receiptUiDateTime(it) }
        .orEmpty()

    if (recoveryFollowUpHintText.isNotBlank() || recoveryFollowUpScriptText.isNotBlank() || recoveryFollowUpAtText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.PlaceholderTextColor.copy(alpha = 0.055f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.22f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryFollowUp,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryFollowUp.value,
                    contentDescription = localizedStringResource(1877, "Follow-up cadence"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = listOfNotNull(
                        supplierBackorderRecoveryFollowUpTitle(item.recoveryFollowUpLane),
                        recoveryFollowUpAtText.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(1882, "Next follow-up")}: $it" }
                    ).joinToString(" • "),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryFollowUpHintText.isNotBlank()) {
                Text(
                    text = recoveryFollowUpHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryFollowUpScriptText.isNotBlank()) {
                Text(
                    text = recoveryFollowUpScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (recoveryHandoffHintText.isNotBlank() || recoveryHandoffChecklistText.isNotBlank() || recoveryHandoffScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.065f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.24f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryHandoff,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryHandoff.value,
                    contentDescription = localizedStringResource(1886, "Recovery handoff"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = supplierBackorderRecoveryHandoffTitle(item.recoveryHandoffLane),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryHandoffHintText.isNotBlank()) {
                Text(
                    text = recoveryHandoffHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryHandoffChecklistText.isNotBlank()) {
                Text(
                    text = recoveryHandoffChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryHandoffScriptText.isNotBlank()) {
                Text(
                    text = recoveryHandoffScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardGuideClosureLedgerTriageContent(
    item: SupplierDashboardBackorderDataModel
) {
    val recoveryClosureHintText = item.recoveryClosureHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryClosureHint.visibleLocalizedString("main", "") }
    val recoveryClosureChecklistText = item.recoveryClosureChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryClosureChecklist.visibleLocalizedString("main", "") }
    val recoveryClosureScriptText = item.recoveryClosureScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryClosureScript.visibleLocalizedString("main", "") }
    val recoveryLedgerHintText = item.recoveryLedgerHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLedgerHint.visibleLocalizedString("main", "") }
    val recoveryLedgerChecklistText = item.recoveryLedgerChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLedgerChecklist.visibleLocalizedString("main", "") }
    val recoveryLedgerScriptText = item.recoveryLedgerScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLedgerScript.visibleLocalizedString("main", "") }
    val recoveryTriageHintText = item.recoveryTriageHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryTriageHint.visibleLocalizedString("main", "") }
    val recoveryTriageChecklistText = item.recoveryTriageChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryTriageChecklist.visibleLocalizedString("main", "") }
    val recoveryTriageScriptText = item.recoveryTriageScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryTriageScript.visibleLocalizedString("main", "") }

    if (recoveryClosureHintText.isNotBlank() || recoveryClosureChecklistText.isNotBlank() || recoveryClosureScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.075f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.26f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryClosure,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryClosure.value,
                    contentDescription = localizedStringResource(1896, "Close gate"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryClosureTitle(item.recoveryClosureLane)} • ${localizedStringResource(1901, "Closure score")} ${item.recoveryClosureScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryClosureHintText.isNotBlank()) {
                Text(
                    text = recoveryClosureHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryClosureChecklistText.isNotBlank()) {
                Text(
                    text = recoveryClosureChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryClosureScriptText.isNotBlank()) {
                Text(
                    text = recoveryClosureScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (recoveryLedgerHintText.isNotBlank() || recoveryLedgerChecklistText.isNotBlank() || recoveryLedgerScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.07f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.25f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryLedger,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryLedger.value,
                    contentDescription = localizedStringResource(1907, "Recovery ledger"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryLedgerTitle(item.recoveryLedgerLane)} • ${localizedStringResource(1913, "Ledger score")} ${item.recoveryLedgerScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryLedgerHintText.isNotBlank()) {
                Text(
                    text = recoveryLedgerHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryLedgerChecklistText.isNotBlank()) {
                Text(
                    text = recoveryLedgerChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryLedgerScriptText.isNotBlank()) {
                Text(
                    text = recoveryLedgerScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (recoveryTriageHintText.isNotBlank() || recoveryTriageChecklistText.isNotBlank() || recoveryTriageScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.085f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.30f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryTriage,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryTriage.value,
                    contentDescription = localizedStringResource(1919, "Triage desk"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryTriageTitle(item.recoveryTriageLane)} • ${localizedStringResource(1926, "Triage score")} ${item.recoveryTriageScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryTriageHintText.isNotBlank()) {
                Text(
                    text = recoveryTriageHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryTriageChecklistText.isNotBlank()) {
                Text(
                    text = recoveryTriageChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryTriageScriptText.isNotBlank()) {
                Text(
                    text = recoveryTriageScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardGuideCommandPromiseContent(
    item: SupplierDashboardBackorderDataModel
) {
    val recoveryCommandHintText = item.recoveryCommandHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommandHint.visibleLocalizedString("main", "") }
    val recoveryCommandChecklistText = item.recoveryCommandChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommandChecklist.visibleLocalizedString("main", "") }
    val recoveryCommandScriptText = item.recoveryCommandScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommandScript.visibleLocalizedString("main", "") }
    val recoveryPromiseShieldHintText = item.recoveryPromiseShieldHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryPromiseShieldHint.visibleLocalizedString("main", "") }
    val recoveryPromiseShieldChecklistText = item.recoveryPromiseShieldChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryPromiseShieldChecklist.visibleLocalizedString("main", "") }
    val recoveryPromiseShieldScriptText = item.recoveryPromiseShieldScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryPromiseShieldScript.visibleLocalizedString("main", "") }

    if (recoveryCommandHintText.isNotBlank() || recoveryCommandChecklistText.isNotBlank() || recoveryCommandScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.095f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.34f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryCommand,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryCommand.value,
                    contentDescription = localizedStringResource(1932, "Recovery command"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryCommandTitle(item.recoveryCommandLane)} • ${localizedStringResource(1939, "Command score")} ${item.recoveryCommandScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryCommandHintText.isNotBlank()) {
                Text(
                    text = recoveryCommandHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryCommandChecklistText.isNotBlank()) {
                Text(
                    text = recoveryCommandChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryCommandScriptText.isNotBlank()) {
                Text(
                    text = recoveryCommandScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }


    if (recoveryPromiseShieldHintText.isNotBlank() || recoveryPromiseShieldChecklistText.isNotBlank() || recoveryPromiseShieldScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.10f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.36f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryPromiseShield,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryPromiseShield.value,
                    contentDescription = localizedStringResource(1945, "Promise shield"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryPromiseShieldTitle(item.recoveryPromiseShieldLane)} • ${localizedStringResource(1952, "Promise score")} ${item.recoveryPromiseShieldScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryPromiseShieldHintText.isNotBlank()) {
                Text(
                    text = recoveryPromiseShieldHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryPromiseShieldChecklistText.isNotBlank()) {
                Text(
                    text = recoveryPromiseShieldChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryPromiseShieldScriptText.isNotBlank()) {
                Text(
                    text = recoveryPromiseShieldScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardGuideWaveLoadContent(
    item: SupplierDashboardBackorderDataModel
) {
    val recoveryWaveHintText = item.recoveryWaveHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryWaveHint.visibleLocalizedString("main", "") }
    val recoveryAgingHintText = item.recoveryAgingHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAgingHint.visibleLocalizedString("main", "") }
    val recoveryAgingChecklistText = item.recoveryAgingChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAgingChecklist.visibleLocalizedString("main", "") }
    val recoveryAgingScriptText = item.recoveryAgingScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAgingScript.visibleLocalizedString("main", "") }
    val recoveryBottleneckHintText = item.recoveryBottleneckHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryBottleneckHint.visibleLocalizedString("main", "") }
    val recoveryBottleneckChecklistText = item.recoveryBottleneckChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryBottleneckChecklist.visibleLocalizedString("main", "") }
    val recoveryBottleneckScriptText = item.recoveryBottleneckScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryBottleneckScript.visibleLocalizedString("main", "") }
    val recoveryLoadHintText = item.recoveryLoadHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLoadHint.visibleLocalizedString("main", "") }
    val recoveryLoadChecklistText = item.recoveryLoadChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLoadChecklist.visibleLocalizedString("main", "") }
    val recoveryLoadScriptText = item.recoveryLoadScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryLoadScript.visibleLocalizedString("main", "") }

    if (recoveryWaveHintText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.105f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.38f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryWave,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryWave.value,
                    contentDescription = localizedStringResource(1973, "Recovery wave"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryWaveTitle(item.recoveryWaveLane)} • ${localizedStringResource(1975, "Wave score")} ${item.recoveryWaveScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = recoveryWaveHintText,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (recoveryAgingHintText.isNotBlank() || recoveryAgingChecklistText.isNotBlank() || recoveryAgingScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.06f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.24f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryAging,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryAging.value,
                    contentDescription = localizedStringResource(1985, "Recovery aging"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryAgingTitle(item.recoveryAgingLane)} • ${localizedStringResource(1990, "Aging score")} ${item.recoveryAgingScore}/100 • ${item.recoveryAgingHours}h",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryAgingHintText.isNotBlank()) {
                Text(
                    text = recoveryAgingHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryAgingChecklistText.isNotBlank()) {
                Text(
                    text = recoveryAgingChecklistText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryAgingScriptText.isNotBlank()) {
                Text(
                    text = recoveryAgingScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (recoveryBottleneckHintText.isNotBlank() || recoveryBottleneckChecklistText.isNotBlank() || recoveryBottleneckScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.11f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.38f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryBottleneck,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryBottleneck.value,
                    contentDescription = localizedStringResource(2012, "Bottleneck map"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryBottleneckTitle(item.recoveryBottleneckLane)} • ${localizedStringResource(2007, "Bottleneck score")} ${item.recoveryBottleneckScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryBottleneckHintText.isNotBlank()) {
                Text(
                    text = recoveryBottleneckHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryBottleneckChecklistText.isNotBlank()) {
                Text(
                    text = recoveryBottleneckChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryBottleneckScriptText.isNotBlank()) {
                Text(
                    text = recoveryBottleneckScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (recoveryLoadHintText.isNotBlank() || recoveryLoadChecklistText.isNotBlank() || recoveryLoadScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.10f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.34f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryLoad,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryLoad.value,
                    contentDescription = localizedStringResource(2013, "Recovery load"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryLoadTitle(item.recoveryLoadLane)} • ${localizedStringResource(2019, "Load score")} ${item.recoveryLoadScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryLoadHintText.isNotBlank()) {
                Text(
                    text = recoveryLoadHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryLoadChecklistText.isNotBlank()) {
                Text(
                    text = recoveryLoadChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryLoadScriptText.isNotBlank()) {
                Text(
                    text = recoveryLoadScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardGuideImpactAllocationContent(
    item: SupplierDashboardBackorderDataModel
) {
    val recoveryImpactHintText = item.recoveryImpactHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryImpactHint.visibleLocalizedString("main", "") }
    val recoveryImpactChecklistText = item.recoveryImpactChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryImpactChecklist.visibleLocalizedString("main", "") }
    val recoveryImpactScriptText = item.recoveryImpactScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryImpactScript.visibleLocalizedString("main", "") }
    val recoveryCommitHintText = item.recoveryCommitHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommitHint.visibleLocalizedString("main", "") }
    val recoveryCommitChecklistText = item.recoveryCommitChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommitChecklist.visibleLocalizedString("main", "") }
    val recoveryCommitScriptText = item.recoveryCommitScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCommitScript.visibleLocalizedString("main", "") }
    val recoveryCommitByText = item.recoveryCommitByMillis
        ?.takeIf { it > 0L }
        ?.let { receiptUiDateTime(it) }
        .orEmpty()
    val recoveryAllocationHintText = item.recoveryAllocationHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAllocationHint.visibleLocalizedString("main", "") }
    val recoveryAllocationChecklistText = item.recoveryAllocationChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAllocationChecklist.visibleLocalizedString("main", "") }
    val recoveryAllocationScriptText = item.recoveryAllocationScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAllocationScript.visibleLocalizedString("main", "") }

    if (recoveryImpactHintText.isNotBlank() || recoveryImpactChecklistText.isNotBlank() || recoveryImpactScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.09f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.34f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryImpact,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryImpact.value,
                    contentDescription = localizedStringResource(2029, "Recovery impact"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryImpactTitle(item.recoveryImpactLane)} • ${localizedStringResource(2035, "Impact score")} ${item.recoveryImpactScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryImpactHintText.isNotBlank()) {
                Text(
                    text = recoveryImpactHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryImpactChecklistText.isNotBlank()) {
                Text(
                    text = recoveryImpactChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryImpactScriptText.isNotBlank()) {
                Text(
                    text = recoveryImpactScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (recoveryCommitHintText.isNotBlank() || recoveryCommitChecklistText.isNotBlank() || recoveryCommitScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.10f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.36f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryCommit,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryCommit.value,
                    contentDescription = localizedStringResource(2044, "Recovery commit"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = listOfNotNull(
                        "${supplierBackorderRecoveryCommitTitle(item.recoveryCommitLane)} • ${localizedStringResource(2051, "Commit score")} ${item.recoveryCommitScore}/100",
                        recoveryCommitByText.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(2053, "Commit by")}: $it" }
                    ).joinToString(" • "),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryCommitHintText.isNotBlank()) {
                Text(
                    text = recoveryCommitHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryCommitChecklistText.isNotBlank()) {
                Text(
                    text = recoveryCommitChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryCommitScriptText.isNotBlank()) {
                Text(
                    text = recoveryCommitScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (recoveryAllocationHintText.isNotBlank() || recoveryAllocationChecklistText.isNotBlank() || recoveryAllocationScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.10f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.36f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryAllocation,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryAllocation.value,
                    contentDescription = localizedStringResource(2062, "Recovery allocation"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryAllocationTitle(item.recoveryAllocationLane)} • ${localizedStringResource(2068, "Allocation score")} ${item.recoveryAllocationScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryAllocationHintText.isNotBlank()) {
                Text(
                    text = recoveryAllocationHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryAllocationChecklistText.isNotBlank()) {
                Text(
                    text = recoveryAllocationChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryAllocationScriptText.isNotBlank()) {
                Text(
                    text = recoveryAllocationScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardGuideExceptionCauseContent(
    item: SupplierDashboardBackorderDataModel
) {
    val recoveryExceptionHintText = item.recoveryExceptionHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExceptionHint.visibleLocalizedString("main", "") }
    val recoveryExceptionChecklistText = item.recoveryExceptionChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExceptionChecklist.visibleLocalizedString("main", "") }
    val recoveryExceptionScriptText = item.recoveryExceptionScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExceptionScript.visibleLocalizedString("main", "") }
    val recoveryCauseHintText = item.recoveryCauseHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCauseHint.visibleLocalizedString("main", "") }
    val recoveryCauseChecklistText = item.recoveryCauseChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCauseChecklist.visibleLocalizedString("main", "") }
    val recoveryCauseScriptText = item.recoveryCauseScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCauseScript.visibleLocalizedString("main", "") }

    if (recoveryExceptionHintText.isNotBlank() || recoveryExceptionChecklistText.isNotBlank() || recoveryExceptionScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.11f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.38f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryException,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryException.value,
                    contentDescription = localizedStringResource(2079, "Recovery exception"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryExceptionTitle(item.recoveryExceptionLane)} • ${localizedStringResource(2087, "Exception score")} ${item.recoveryExceptionScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryExceptionHintText.isNotBlank()) {
                Text(
                    text = recoveryExceptionHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryExceptionChecklistText.isNotBlank()) {
                Text(
                    text = recoveryExceptionChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryExceptionScriptText.isNotBlank()) {
                Text(
                    text = recoveryExceptionScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }


    if (recoveryCauseHintText.isNotBlank() || recoveryCauseChecklistText.isNotBlank() || recoveryCauseScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.080f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.30f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryCause,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryCause.value,
                    contentDescription = localizedStringResource(2101, "Recovery cause"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryCauseTitle(item.recoveryCauseLane)} • ${localizedStringResource(2109, "Cause score")} ${item.recoveryCauseScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryCauseHintText.isNotBlank()) {
                Text(
                    text = recoveryCauseHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryCauseChecklistText.isNotBlank()) {
                Text(
                    text = recoveryCauseChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryCauseScriptText.isNotBlank()) {
                Text(
                    text = recoveryCauseScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

