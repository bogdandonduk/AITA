package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
        if (isSaving) return@saveProfile
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
                    enabled = !isSaving && supplierName.isNotBlank(),
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
                    enabled = !isSaving && supplierName.isNotBlank(),
                    confirmationRequired = false,
                    onClick = saveProfile
                )
            }
        }
    }
}
