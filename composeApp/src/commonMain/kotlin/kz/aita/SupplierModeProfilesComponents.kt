package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun AppConfiguration.SupplierProfileWorkspaceCard(
    item: SupplierProfileWorkspaceItemUiModel,
    showFocusAction: Boolean,
    isDeleting: Boolean,
    onFocus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (item.selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (item.selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(26.dp),
                    url = stateValues.drawablePathIconAppModeSupplier,
                    fallbackRes = stateValues.drawableResIconAppModeSupplier.value,
                    contentDescription = item.title,
                    tintColor = stateValues.AccentColor
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Text(
                    text = item.title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                androidx.compose.material3.Text(
                    text = item.contactText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SupplierCatalogChip(
                text = if (item.ready) {
                    localizedStringResource(2493, "Profile ready")
                } else {
                    localizedStringResource(2494, "Setup check")
                }
            )
        }

        if (item.readinessIssues.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item.readinessIssues.sortedBy { it.ordinal }.take(3).forEach { issue ->
                    SupplierCatalogChip(text = issue.profileReadinessLabel(this@SupplierProfileWorkspaceCard))
                }
            }
        }

        if (stateValues.isNarrowScreen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SupplierCatalogChip(text = "${localizedStringResource(1377, "Open")}: ${item.openOrderCount}")
                SupplierCatalogChip(text = "${localizedStringResource(1453, "Partner stores")}: ${item.partnerCount}")
                SupplierCatalogChip(text = "${localizedStringResource(1479, "Supplier contracts")}: ${item.liveAgreementCount}")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SupplierCatalogChip(text = "${localizedStringResource(1377, "Open")}: ${item.openOrderCount}")
                }
                Box(modifier = Modifier.weight(1f)) {
                    SupplierCatalogChip(text = "${localizedStringResource(1408, "Catalog SKUs")}: ${item.catalogSkuCount}")
                }
                Box(modifier = Modifier.weight(1f)) {
                    SupplierCatalogChip(text = "${localizedStringResource(1453, "Partner stores")}: ${item.partnerCount}")
                }
                Box(modifier = Modifier.weight(1f)) {
                    SupplierCatalogChip(text = "${localizedStringResource(1479, "Supplier contracts")}: ${item.liveAgreementCount}")
                }
            }
        }

        if (!item.usageLoaded || !item.canDeleteFromLoadedState) {
            androidx.compose.material3.Text(
                text = if (!item.usageLoaded) {
                    if (item.usageCheckFailed) {
                        localizedStringResource(2517, "Commercial history couldn’t be verified. Refresh before deleting.")
                    } else {
                        localizedStringResource(2516, "Checking commercial history before delete…")
                    }
                } else {
                    localizedStringResource(2504, "Only unused profiles can be deleted. Profiles with order, agreement, offer, or stock history are kept for audit.")
                },
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }

        if (stateValues.isNarrowScreen) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                if (showFocusAction) {
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = if (item.selected) localizedStringResource(2501, "Working profile") else localizedStringResource(2500, "Work as this profile"),
                        iconPath = stateValues.drawablePathIconAppModeSupplier,
                        iconRes = stateValues.drawableResIconAppModeSupplier.value,
                        enabled = !item.selected && !isDeleting,
                        confirmationRequired = false,
                        onClick = onFocus
                    )
                }
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(2502, "Edit profile"),
                    iconPath = stateValues.drawablePathIconEdit,
                    iconRes = stateValues.drawableResIconEdit.value,
                    enabled = !isDeleting,
                    confirmationRequired = false,
                    onClick = onEdit
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (isDeleting) localizedStringResource(2518, "Deleting profile…") else localizedStringResource(2503, "Delete profile"),
                    iconPath = stateValues.drawablePathIconDelete,
                    iconRes = stateValues.drawableResIconDelete.value,
                    enabledColor = stateValues.ErrorColor,
                    enabled = item.canDeleteFromLoadedState && !isDeleting,
                    confirmationRequired = true,
                    onDisabledClick = {
                        postInAppNotification(
                            if (isDeleting) {
                                localizedStringResource(2518, "Deleting profile…")
                            } else if (!item.usageLoaded) {
                                if (item.usageCheckFailed) {
                                    localizedStringResource(2517, "Commercial history couldn’t be verified. Refresh before deleting.")
                                } else {
                                    localizedStringResource(2516, "Checking commercial history before delete…")
                                }
                            } else {
                                localizedStringResource(2504, "Only unused profiles can be deleted. Profiles with order, agreement, offer, or stock history are kept for audit.")
                            },
                            NotificationType.Neutral,
                            transient = true
                        )
                    },
                    onClick = onDelete
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                if (showFocusAction) {
                    actionButton(
                        modifier = Modifier.weight(1f),
                        text = if (item.selected) localizedStringResource(2501, "Working profile") else localizedStringResource(2500, "Work as this profile"),
                        iconPath = stateValues.drawablePathIconAppModeSupplier,
                        iconRes = stateValues.drawableResIconAppModeSupplier.value,
                        enabled = !item.selected && !isDeleting,
                        confirmationRequired = false,
                        onClick = onFocus
                    )
                }
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(2502, "Edit profile"),
                    iconPath = stateValues.drawablePathIconEdit,
                    iconRes = stateValues.drawableResIconEdit.value,
                    enabled = !isDeleting,
                    confirmationRequired = false,
                    onClick = onEdit
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = if (isDeleting) localizedStringResource(2518, "Deleting profile…") else localizedStringResource(2503, "Delete profile"),
                    iconPath = stateValues.drawablePathIconDelete,
                    iconRes = stateValues.drawableResIconDelete.value,
                    enabledColor = stateValues.ErrorColor,
                    enabled = item.canDeleteFromLoadedState && !isDeleting,
                    confirmationRequired = true,
                    onDisabledClick = {
                        postInAppNotification(
                            if (isDeleting) {
                                localizedStringResource(2518, "Deleting profile…")
                            } else if (!item.usageLoaded) {
                                if (item.usageCheckFailed) {
                                    localizedStringResource(2517, "Commercial history couldn’t be verified. Refresh before deleting.")
                                } else {
                                    localizedStringResource(2516, "Checking commercial history before delete…")
                                }
                            } else {
                                localizedStringResource(2504, "Only unused profiles can be deleted. Profiles with order, agreement, offer, or stock history are kept for audit.")
                            },
                            NotificationType.Neutral,
                            transient = true
                        )
                    },
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierProfileEditorContent(
    editedSupplier: SupplierDataModel?,
    stateHost: StateHost,
    editorSessionKey: String,
    onCancel: () -> Unit,
    onSaved: (SupplierDataModel) -> Unit
) {
    val editorKey = supplierProfileEditorStableKey(editedSupplier?.id, editorSessionKey)
    val coroutineScope = rememberCoroutineScope()
    val operationGate = remember(editorKey) { SupplierWorkspaceOperationGate() }
    DisposableEffect(operationGate) {
        onDispose { operationGate.invalidate() }
    }
    var supplierName by rememberSaveable(editorKey) {
        mutableStateOf(
            editedSupplier?.name?.extractLocalizedString("main")
                ?: editedSupplier?.visibleSupplierName(stateValues.appLanguage)
                ?: ""
        )
    }
    var supplierEmail by rememberSaveable(editorKey) {
        mutableStateOf(editedSupplier?.emails.orEmpty().firstOrNull().orEmpty())
    }
    var isSaving by remember(editorKey) { mutableStateOf(false) }

    val phoneStateKey = supplierProfileEditorPhoneStateKey(editedSupplier?.id, editorSessionKey)
    val phoneContent = countrySelectionPhoneNumberTextField(
        titleText = localizedStringResource(620, "Supplier phone number"),
        placeholderText = stateValues.stringEnterPhoneNumber,
        valueInitial = editedSupplier?.phoneNumbers.orEmpty().firstOrNull().orEmpty(),
        stateHost = stateHost,
        stateKey = phoneStateKey
    )

    val supplierEmailConfirmation = rememberContactEmailConfirmation(
        kz.aita.auth.AitaContactPurpose.SUPPLIER_CONTACT, editedSupplier?.id.orEmpty(),
        mergeSupplierProfilePrimaryEmail(editedSupplier?.emails, normalizeSupplierProfileEmail(supplierEmail)).orEmpty(),
        editedSupplier?.emails.orEmpty())

    val cancelEditor = {
        if (!isSaving) {
            operationGate.invalidate()
            coroutineScope.launch {
                stateHost.removeState(phoneStateKey)
                onCancel()
            }
        }
    }

    val saveProfile = saveProfile@{
        if (isSaving || !supplierEmailConfirmation.ready) return@saveProfile
        val cleanName = supplierName.trim()
        if (cleanName.isBlank()) {
            postInAppNotification(
                localizedStringResource(2509, "Supplier name is required"),
                NotificationType.Negative,
                transient = true
            )
            return@saveProfile
        }

        val cleanEmail = normalizeSupplierProfileEmail(supplierEmail)
        if (cleanEmail != null && !supplierProfileEmailLooksValid(cleanEmail)) {
            postInAppNotification(
                localizedStringResource(2510, "Supplier email is invalid"),
                NotificationType.Negative,
                transient = true
            )
            return@saveProfile
        }

        val phoneLocal = phoneContent.value.text.trim()
        val phoneNumber = if (phoneLocal.isBlank()) {
            null
        } else {
            phoneContent.checkContentValidity()
            if (!phoneContent.isContentValid) {
                postInAppNotification(
                    stateValues.stringPhoneNumberMustBe,
                    NotificationType.Negative,
                    transient = true
                )
                return@saveProfile
            }
            phoneContent.selectedSecondaryId.orEmpty().removePrefix("+") + phoneLocal
        }

        val supplier = SupplierDataModel(
            id = editedSupplier?.id.orEmpty(),
            userIds = editedSupplier?.userIds.orEmpty(),
            typeIds = editedSupplier?.typeIds,
            categoryIds = editedSupplier?.categoryIds.orEmpty(),
            name = mergeSupplierProfilePrimaryName(editedSupplier?.name.orEmpty(), cleanName),
            phoneNumbers = mergeSupplierProfilePrimaryPhone(editedSupplier?.phoneNumbers, phoneNumber),
            emails = mergeSupplierProfilePrimaryEmail(editedSupplier?.emails, cleanEmail),
            contactVerificationId = supplierEmailConfirmation.draftId,
            contactEmailProofs = supplierEmailConfirmation.proofs,
            addedAt = editedSupplier?.addedAt ?: getCurrentTimeMillis(),
            isActive = true
        )
        val issues = supplier.supplierProfileValidationIssues()
        if (issues.isNotEmpty()) {
            postInAppNotification(
                if (SupplierProfileValidationIssue.InvalidEmail in issues) {
                    localizedStringResource(2510, "Supplier email is invalid")
                } else {
                    localizedStringResource(2511, "Check the supplier profile fields")
                },
                NotificationType.Negative,
                transient = true
            )
            return@saveProfile
        }

        isSaving = true
        val operationTicket = operationGate.begin(editedSupplier?.id, "profile.save")
        val completed: (DataState<SupplierDataModel>) -> Unit = { result ->
            coroutineScope.launch {
                if (!operationGate.isCurrent(operationTicket)) return@launch
                isSaving = false
                if (result is DataState.Success) {
                    stateHost.removeState(phoneStateKey)
                    postInAppNotification(
                        localizedStringResource(2512, "Supplier profile saved"),
                        NotificationType.Positive,
                        transient = true
                    )
                    onSaved(result.payload)
                }
            }
        }
        if (editedSupplier == null) addSupplier(supplier, completed) else updateSupplier(supplier, completed)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        androidx.compose.material3.Text(
            text = localizedStringResource(2505, "Store-facing supplier identity"),
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold
        )
        androidx.compose.material3.Text(
            text = localizedStringResource(2506, "Stores use this name and contact information when ordering, confirming terms, and arranging delivery."),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )

        genericTextField(
            titleText = stateValues.stringName,
            placeholderText = localizedStringResource(622, "Enter supplier name"),
            valueInitial = supplierName,
            onValueChange = { value, apply ->
                supplierName = value
                apply()
            }
        )

        genericTextField(
            titleText = localizedStringResource(621, "Supplier email"),
            placeholderText = stateValues.stringEnterEmailAddress,
            valueInitial = supplierEmail,
            keyboardType = KeyboardType.Email,
            onValueChange = { value, apply ->
                supplierEmail = value
                apply()
            }
        )

        ContactEmailConfirmationContent(supplierEmailConfirmation, enabled = !isSaving)

        if (editedSupplier != null && (editedSupplier.phoneNumbers.orEmpty().size > 1 || editedSupplier.emails.orEmpty().size > 1)) {
            androidx.compose.material3.Text(
                text = localizedStringResource(2507, "Additional existing contacts are preserved when the primary contact changes."),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }

        if (stateValues.isNarrowScreen) {
            Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stateValues.stringCancel,
                    iconPath = stateValues.drawablePathIconCancel,
                    iconRes = stateValues.drawableResIconCancel.value,
                    enabled = !isSaving,
                    confirmationRequired = false,
                    onClick = cancelEditor
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (isSaving) localizedStringResource(2508, "Saving profile…") else localizedStringResource(631, "Save supplier"),
                    iconPath = stateValues.drawablePathIconCheck,
                    iconRes = stateValues.drawableResIconCheck.value,
                    enabled = !isSaving && supplierName.isNotBlank() && supplierEmailConfirmation.ready,
                    confirmationRequired = false,
                    onClick = saveProfile
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = stateValues.stringCancel,
                    iconPath = stateValues.drawablePathIconCancel,
                    iconRes = stateValues.drawableResIconCancel.value,
                    enabled = !isSaving,
                    confirmationRequired = false,
                    onClick = cancelEditor
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = if (isSaving) localizedStringResource(2508, "Saving profile…") else localizedStringResource(631, "Save supplier"),
                    iconPath = stateValues.drawablePathIconCheck,
                    iconRes = stateValues.drawableResIconCheck.value,
                    enabled = !isSaving && supplierName.isNotBlank() && supplierEmailConfirmation.ready,
                    confirmationRequired = false,
                    onClick = saveProfile
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierWorkspaceReadinessCard(
    summary: SupplierWorkspaceReadinessUiModel,
    onNextStep: () -> Unit
) {
    val actionText = when (summary.nextStep) {
        SupplierWorkspaceReadinessNextStep.CreateProfile -> localizedStringResource(2492, "Add profile")
        SupplierWorkspaceReadinessNextStep.CompleteProfile -> localizedStringResource(2521, "Complete profile")
        SupplierWorkspaceReadinessNextStep.ReviewOrders -> localizedStringResource(2522, "Review open orders")
        SupplierWorkspaceReadinessNextStep.BuildCatalog -> localizedStringResource(2523, "Build supplier catalog")
        SupplierWorkspaceReadinessNextStep.ConnectStores -> localizedStringResource(2524, "Connect partner stores")
        SupplierWorkspaceReadinessNextStep.ReviewAgreements -> localizedStringResource(2525, "Review agreements")
        SupplierWorkspaceReadinessNextStep.OpenInsights -> localizedStringResource(2526, "Open supplier insights")
    }
    val helperText = when (summary.nextStep) {
        SupplierWorkspaceReadinessNextStep.CreateProfile ->
            localizedStringResource(2527, "Create the business identity stores will order from.")
        SupplierWorkspaceReadinessNextStep.CompleteProfile ->
            localizedStringResource(2528, "Finish the selected profile’s contacts and setup checks.")
        SupplierWorkspaceReadinessNextStep.ReviewOrders ->
            localizedStringResource(2529, "Open Store orders are waiting for a clear Supplier response.")
        SupplierWorkspaceReadinessNextStep.BuildCatalog ->
            localizedStringResource(2530, "Add reusable offers so Stores can order with less manual work.")
        SupplierWorkspaceReadinessNextStep.ConnectStores ->
            localizedStringResource(2531, "Build the partner Store workspace around this Supplier identity.")
        SupplierWorkspaceReadinessNextStep.ReviewAgreements ->
            localizedStringResource(2532, "Record delivery, payment, and product terms with partner Stores.")
        SupplierWorkspaceReadinessNextStep.OpenInsights ->
            localizedStringResource(2533, "The core Supplier workspace is ready. Review performance and next actions.")
    }
    val actionIconPath = when (summary.nextStep) {
        SupplierWorkspaceReadinessNextStep.CreateProfile -> stateValues.drawablePathIconAdd
        SupplierWorkspaceReadinessNextStep.CompleteProfile -> stateValues.drawablePathIconEdit
        SupplierWorkspaceReadinessNextStep.ReviewOrders -> stateValues.drawablePathIconClipboard
        SupplierWorkspaceReadinessNextStep.BuildCatalog -> stateValues.drawablePathIconSupplierCatalog
        SupplierWorkspaceReadinessNextStep.ConnectStores -> stateValues.drawablePathIconSupplierPartners
        SupplierWorkspaceReadinessNextStep.ReviewAgreements -> stateValues.drawablePathIconSupplierContracts
        SupplierWorkspaceReadinessNextStep.OpenInsights -> stateValues.drawablePathIconSupplierDemandRadar
    }
    val actionIconRes = when (summary.nextStep) {
        SupplierWorkspaceReadinessNextStep.CreateProfile -> stateValues.drawableResIconAdd.value
        SupplierWorkspaceReadinessNextStep.CompleteProfile -> stateValues.drawableResIconEdit.value
        SupplierWorkspaceReadinessNextStep.ReviewOrders -> stateValues.drawableResIconClipboard.value
        SupplierWorkspaceReadinessNextStep.BuildCatalog -> stateValues.drawableResIconSupplierCatalog.value
        SupplierWorkspaceReadinessNextStep.ConnectStores -> stateValues.drawableResIconSupplierPartners.value
        SupplierWorkspaceReadinessNextStep.ReviewAgreements -> stateValues.drawableResIconSupplierContracts.value
        SupplierWorkspaceReadinessNextStep.OpenInsights -> stateValues.drawableResIconSupplierDemandRadar.value
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.AccentColor.copy(alpha = 0.07f))
            .border(
                stateValues.unfocusedBorderWidth,
                stateValues.AccentColor.copy(alpha = 0.55f),
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(26.dp),
                    url = stateValues.drawablePathIconSupplierDemandRadar,
                    fallbackRes = stateValues.drawableResIconSupplierDemandRadar.value,
                    contentDescription = localizedStringResource(2519, "Supplier workspace readiness"),
                    tintColor = stateValues.AccentColor
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Text(
                    text = localizedStringResource(2519, "Supplier workspace readiness"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                androidx.compose.material3.Text(
                    text = localizedStringResource(2520, "A calm checklist for getting this Supplier desk ready."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SupplierCatalogChip(text = "${summary.readinessPercent}%")
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(stateValues.PlaceholderTextColor.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((summary.readinessPercent / 100f).coerceIn(0f, 1f))
                    .height(7.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(stateValues.AccentColor)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(2493, "Profile ready")}: ${summary.readyProfileCount}/${summary.profileCount}"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(1337, "Orders")}: ${summary.openOrderCount}"
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(1338, "Catalog")}: ${summary.catalogSkuCount}"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(1453, "Partner stores")}: ${summary.partnerCount}"
                    )
                }
            }
        }

        androidx.compose.material3.Text(
            text = helperText,
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )

        actionButton(
            modifier = Modifier.fillMaxWidth(),
            text = actionText,
            iconPath = actionIconPath,
            iconRes = actionIconRes,
            confirmationRequired = false,
            onClick = onNextStep
        )
    }
}
