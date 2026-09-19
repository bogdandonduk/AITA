// THIS IS CommonMainCompose.kt split slice: MenuA
@file:OptIn(ExperimentalTime::class, ExperimentalFoundationApi::class)
package kz.aita

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.time.ExperimentalTime

@Composable
fun AppConfiguration.ModalDialogWidget(
    title: String,
    subTitle: String? = null,
    negativeButtonText: String = stateValues.stringCancel,
    positiveButtonText: String = stateValues.stringConfirm,
    backgroundColor: Color = stateValues.BackgroundColor,
    cornerRadius: Dp = stateValues.cornerRadius,
    titleTextSize: TextUnit = stateValues.accentTextSize,
    subTitleTextSize: TextUnit = stateValues.textSize,
    titleTextColor: Color = stateValues.TextColor,
    subTitleTextColor: Color = titleTextColor,
    onDismiss: () -> Unit,
    negativeAction: () -> Unit,
    positiveAction: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
    ) {
        TransactionBarcodeModalGuard()
        Column(
            modifier = Modifier
                .aitaDialogEntrance()
                .widthIn(min = 300.dp, max = 460.dp)
                .foregroundTactileShadow(cornerRadius = cornerRadius, elevated = true)
                .clip(RoundedCornerShape(cornerRadius))
                .background(backgroundColor)
                .border(
                    stateValues.unfocusedBorderWidth,
                    stateValues.PlaceholderTextColor,
                    RoundedCornerShape(cornerRadius)
                )
                .padding(
                    start = stateValues.marginTextFieldGroup,
                    top = stateValues.marginTextFieldGroup,
                    end = stateValues.marginTextFieldGroup,
                    bottom = stateValues.marginTextFieldGroup
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                fontSize = titleTextSize,
                color = titleTextColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            subTitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = it,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    fontSize = subTitleTextSize,
                    color = subTitleTextColor,
                    textAlign = TextAlign.Center,
                    lineHeight = subTitleTextSize * 1.25f,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabledColor = stateValues.DisabledColor,
                    text = negativeButtonText,
                    confirmationRequired = false,
                    onClick = negativeAction
                )

                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = positiveButtonText,
                    confirmationRequired = false,
                    onClick = positiveAction
                )
            }
        }
    }
}

@Composable
fun AppConfiguration.MessageText(
    modifier: Modifier = Modifier,
    text: String,
    subText: String? = null,
    textColor: Color = stateValues.TextColor,
    textSize: TextUnit = stateValues.accentTextSize,
    subTextColor: Color = textColor,
    subTextSize: TextUnit = stateValues.textSize,
    loadingLayout: LoadingLayout? = null
) {
    val cleanSubText = subText?.takeIf { it.isNotBlank() }
    if (text == localizedStringResource(1141, "Please wait…") && cleanSubText == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            if (loadingLayout != null) LoadingSkeleton(Modifier.fillMaxWidth().padding(16.dp), layout = loadingLayout)
            else Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AitaBusyIndicator(Modifier.size(24.dp), stateValues.AccentColor)
                Text(text, color = textColor, fontSize = textSize)
            }
        }
        return
    }

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = if (cleanSubText == null) 56.dp else 88.dp)
            .padding(horizontal = 16.dp, vertical = if (cleanSubText == null) 8.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = textColor,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        cleanSubText?.run {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = this,
                modifier = Modifier.fillMaxWidth(),
                color = subTextColor,
                fontSize = subTextSize,
                textAlign = TextAlign.Center
            )
        }
    }
}

internal fun AppConfiguration.workerRoleLabel(
    roleId: String,
    templates: List<StoreWorkerRoleTemplateDataModel> = storeWorkerRoleTemplatesState.payloadValue.orEmpty()
): String {
    templates.firstOrNull { it.id == roleId }?.let { template ->
        return template.name.extractLocalizedString(stateValues.appLanguage).orEmpty().ifBlank { template.displayName }
    }
    return when (roleId) {
        WORKER_ROLE_ADMIN -> localizedStringResource(448, "Admin")
        WORKER_ROLE_OWNER -> localizedStringResource(449, "Owner")
        else -> localizedStringResource(450, "Standard")
    }
}

internal fun AppConfiguration.workerPermissionCategory(permissionId: String): String {
    return when (permissionId) {
        STORE_PERMISSION_SALE_TRANSACTION,
        STORE_PERMISSION_RETURN_TRANSACTION,
        STORE_PERMISSION_SUPPLY_TRANSACTION,
        STORE_PERMISSION_TRANSACTION_HISTORY_VIEW,
        STORE_PERMISSION_CASH_REGISTER_VIEW,
        STORE_PERMISSION_CASH_REGISTER_EXTRACT -> localizedStringResource(1249, "Checkout and cash")

        STORE_PERMISSION_STOCK_READ,
        STORE_PERMISSION_STOCK_ITEM_CREATE,
        STORE_PERMISSION_STOCK_ITEM_EDIT,
        STORE_PERMISSION_STOCK_ITEM_DELETE,
        STORE_PERMISSION_STOCK_BATCH_CREATE,
        STORE_PERMISSION_STOCK_BATCH_EDIT,
        STORE_PERMISSION_STOCK_BATCH_DELETE,
        STORE_PERMISSION_STOCK_BATCH_MOVE,
        STORE_PERMISSION_STOCK_BATCH_TRANSFER_DECIDE,
        STORE_PERMISSION_STOCK_BATCH_SET_ACTIVE_SHELF,
        STORE_PERMISSION_STOCK_PROMOTIONS_MANAGE,
        STORE_PERMISSION_STOCK_HISTORY_VIEW -> localizedStringResource(1250, "Stock")

        STORE_PERMISSION_SUPPLIERS_VIEW,
        STORE_PERMISSION_SUPPLIERS_MANAGE,
        STORE_PERMISSION_SUPPLIER_PRICES_MANAGE,
        STORE_PERMISSION_SUPPLIER_ORDERS_VIEW,
        STORE_PERMISSION_SUPPLIER_ORDERS_MANAGE,
        STORE_PERMISSION_SUPPLIER_ORDERS_RECEIVE -> localizedStringResource(1251, "Suppliers")

        STORE_PERMISSION_DEBTORS_VIEW,
        STORE_PERMISSION_DEBTORS_MANAGE,
        STORE_PERMISSION_DEBTOR_PAYMENTS_MANAGE -> localizedStringResource(1252, "Debtors")

        STORE_PERMISSION_WORKERS_VIEW,
        STORE_PERMISSION_WORKERS_INVITE,
        STORE_PERMISSION_WORKERS_DECIDE_REQUESTS,
        STORE_PERMISSION_WORKERS_EDIT_PERMISSIONS,
        STORE_PERMISSION_WORKERS_REMOVE,
        STORE_PERMISSION_WORKER_ROLE_TEMPLATES_MANAGE -> localizedStringResource(1253, "Workers")

        else -> localizedStringResource(1254, "Administration")
    }
}

internal fun AppConfiguration.workerPermissionLabel(permissionId: String): String {
    return when (permissionId) {
        STORE_PERMISSION_SALE_TRANSACTION -> localizedStringResource(451, "Complete sales")
        STORE_PERMISSION_RETURN_TRANSACTION -> localizedStringResource(452, "Complete returns")
        STORE_PERMISSION_SUPPLY_TRANSACTION -> localizedStringResource(453, "Complete supply transactions")
        STORE_PERMISSION_TRANSACTION_HISTORY_VIEW -> localizedStringResource(456, "View transaction history")
        STORE_PERMISSION_CASH_REGISTER_VIEW -> localizedStringResource(458, "View cash register")
        STORE_PERMISSION_CASH_REGISTER_EXTRACT -> localizedStringResource(459, "Extract cash")
        STORE_PERMISSION_STOCK_READ -> localizedStringResource(454, "View stock")
        STORE_PERMISSION_STOCK_HISTORY_VIEW -> localizedStringResource(1332, "View stock history")
        STORE_PERMISSION_STOCK_ITEM_CREATE -> localizedStringResource(1255, "Create goods")
        STORE_PERMISSION_STOCK_ITEM_EDIT -> localizedStringResource(1256, "Edit goods")
        STORE_PERMISSION_STOCK_ITEM_DELETE -> localizedStringResource(1257, "Delete goods")
        STORE_PERMISSION_STOCK_BATCH_CREATE -> localizedStringResource(1258, "Create stock batches")
        STORE_PERMISSION_STOCK_BATCH_EDIT -> localizedStringResource(1259, "Edit stock batches")
        STORE_PERMISSION_STOCK_BATCH_DELETE -> localizedStringResource(1260, "Delete stock batches")
        STORE_PERMISSION_STOCK_BATCH_MOVE -> localizedStringResource(1261, "Move stock between branches")
        STORE_PERMISSION_STOCK_BATCH_TRANSFER_DECIDE -> localizedStringResource(1262, "Accept or decline stock moves")
        STORE_PERMISSION_STOCK_BATCH_SET_ACTIVE_SHELF -> localizedStringResource(1263, "Set active shelf batch")
        STORE_PERMISSION_STOCK_PROMOTIONS_MANAGE -> localizedStringResource(1264, "Manage stock promotions")
        STORE_PERMISSION_SUPPLIERS_VIEW -> localizedStringResource(1265, "View suppliers")
        STORE_PERMISSION_SUPPLIERS_MANAGE -> localizedStringResource(1266, "Create and edit suppliers")
        STORE_PERMISSION_SUPPLIER_PRICES_MANAGE -> localizedStringResource(1267, "Manage supplier prices")
        STORE_PERMISSION_SUPPLIER_ORDERS_VIEW -> localizedStringResource(1268, "View supplier orders")
        STORE_PERMISSION_SUPPLIER_ORDERS_MANAGE -> localizedStringResource(1269, "Create and edit supplier orders")
        STORE_PERMISSION_SUPPLIER_ORDERS_RECEIVE -> localizedStringResource(1270, "Receive supplier orders")
        STORE_PERMISSION_DEBTORS_VIEW -> localizedStringResource(1271, "View debtors")
        STORE_PERMISSION_DEBTORS_MANAGE -> localizedStringResource(1272, "Create and edit debtors")
        STORE_PERMISSION_DEBTOR_PAYMENTS_MANAGE -> localizedStringResource(1273, "Record debtor payments")
        STORE_PERMISSION_ANALYTICS_VIEW -> localizedStringResource(457, "View analytics")
        STORE_PERMISSION_LOGS_VIEW -> localizedStringResource(1075, "View operation logs")
        STORE_PERMISSION_WORKERS_VIEW -> localizedStringResource(460, "View workers")
        STORE_PERMISSION_WORKERS_INVITE -> localizedStringResource(1274, "Invite workers")
        STORE_PERMISSION_WORKERS_DECIDE_REQUESTS -> localizedStringResource(1275, "Accept employment requests")
        STORE_PERMISSION_WORKERS_EDIT_PERMISSIONS -> localizedStringResource(1276, "Edit worker permissions")
        STORE_PERMISSION_WORKERS_REMOVE -> localizedStringResource(1277, "Request worker removal")
        STORE_PERMISSION_WORKER_ROLE_TEMPLATES_MANAGE -> localizedStringResource(1278, "Manage role templates")
        STORE_PERMISSION_STORE_MANAGE -> localizedStringResource(462, "Manage store settings")
        STORE_PERMISSION_BRANCHES_MANAGE -> localizedStringResource(1279, "Manage branches")
        STORE_PERMISSION_SUBSCRIPTION_MANAGE -> localizedStringResource(1280, "Manage subscription")
        STORE_PERMISSION_STOCK_WRITE -> localizedStringResource(455, "Edit stock")
        STORE_PERMISSION_WORKERS_MANAGE -> localizedStringResource(461, "Manage workers")
        else -> permissionId
    }
}

internal fun AppConfiguration.workerRoleOptions(templates: List<StoreWorkerRoleTemplateDataModel>): List<DropdownOption> {
    return listOf(
        DropdownOption(WORKER_ROLE_STANDARD, workerRoleLabel(WORKER_ROLE_STANDARD), localizedStringResource(463, "Cashier/basic worker")),
        DropdownOption(WORKER_ROLE_ADMIN, workerRoleLabel(WORKER_ROLE_ADMIN), localizedStringResource(464, "Can manage most store operations"))
    ) + templates.filter { it.isActive }.map { template ->
        DropdownOption(
            template.id,
            workerRoleLabel(template.id, templates),
            template.description.extractLocalizedString(stateValues.appLanguage).orEmpty().ifBlank { localizedStringResource(1281, "Custom permission template") }
        )
    }
}

internal fun AppConfiguration.defaultAssignablePermissionsForWorkerRole(
    roleId: String,
    assignablePermissions: Set<String>,
    templates: List<StoreWorkerRoleTemplateDataModel> = storeWorkerRoleTemplatesState.payloadValue.orEmpty()
): List<String> {
    val templatePermissions = templates.firstOrNull { it.id == roleId }?.permissions
    val defaultPermissions = (templatePermissions ?: defaultStorePermissionsForRole(roleId)).filter { it in assignablePermissions }
    return defaultPermissions.ifEmpty {
        if (roleId == WORKER_ROLE_ADMIN) ALL_STORE_PERMISSION_IDS.filter { it in assignablePermissions } else emptyList()
    }
}

internal fun permissionsFromSerialized(value: String): List<String> {
    return value
        .split("|")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

internal fun normalizeWorkerSalaryInput(value: String): String {
    val raw = value.trim().replace(',', '.')
    val firstDot = raw.indexOf('.')
    val compact = raw.filterIndexed { index, char ->
        char.isDigit() || (char == '.' && index == firstDot)
    }.take(14)
    val parts = compact.split('.', limit = 2)
    val whole = parts.getOrNull(0).orEmpty().trimStart('0').ifBlank { if (compact.startsWith('.')) "0" else "" }.take(9)
    val fractional = parts.getOrNull(1)?.take(2).orEmpty()
    return when {
        compact.isBlank() -> ""
        compact.endsWith('.') && fractional.isBlank() -> "$whole."
        fractional.isNotBlank() -> "$whole.$fractional"
        else -> whole
    }
}

internal fun workerSalaryLine(salary: String, currencyCode: String): String {
    val clean = salary.trim()
    return if (clean.isBlank()) "" else "$clean ${currencyCode.trim().uppercase().ifBlank { "KZT" }}"
}

internal fun AppConfiguration.workerJobTitleText(jobTitle: String, jobTitleLocalized: List<LocalizedStringDataModel>): String =
    jobTitleLocalized.extractLocalizedString(stateValues.appLanguage).orEmpty().ifBlank { jobTitle.trim() }

@Composable
internal fun AppConfiguration.WorkerOfferDetailLine(
    title: String,
    value: String,
    accent: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            modifier = Modifier.weight(0.42f),
            text = title,
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
        )
        Text(
            modifier = Modifier.weight(0.58f),
            text = value.ifBlank { "—" },
            color = if (accent) stateValues.AccentColor else stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AppConfiguration.WorkerOfferDetails(
    roleId: String,
    permissions: List<String>,
    jobTitle: String,
    jobTitleLocalized: List<LocalizedStringDataModel> = emptyList(),
    salary: String,
    salaryCurrencyCode: String = "KZT",
    showPermissionPreview: Boolean = true,
    offerNote: String? = null
) {
    val jobTitleText = workerJobTitleText(jobTitle, jobTitleLocalized).ifBlank { localizedStringResource(1438, "No job title specified") }
    val salaryText = workerSalaryLine(salary, salaryCurrencyCode).ifBlank { localizedStringResource(1437, "No salary specified") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.AccentColor.copy(alpha = 0.08f))
            .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.34f), RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextField),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = localizedStringResource(1436, "Offer details"),
            color = stateValues.AccentColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
        )
        WorkerOfferDetailLine(localizedStringResource(1432, "Job title"), jobTitleText, accent = jobTitle.isNotBlank())
        WorkerOfferDetailLine(localizedStringResource(466, "Role"), workerRoleLabel(roleId.ifBlank { WORKER_ROLE_STANDARD }), accent = true)
        WorkerOfferDetailLine(localizedStringResource(1433, "Salary"), salaryText, accent = salary.isNotBlank())
        offerNote?.trim()?.takeIf { it.isNotBlank() }?.let { note ->
            WorkerOfferDetailLine(localizedStringResource(1449, "Offer note"), note, accent = true)
        }
        if (showPermissionPreview) {
            WorkerOfferDetailLine(
                localizedStringResource(1439, "Permissions included"),
                permissions.take(4).joinToString(" • ") { workerPermissionLabel(it) } + if (permissions.size > 4) " +${permissions.size - 4}" else ""
            )
        }
    }
}


internal fun AppConfiguration.workerRequestDirectionLabel(request: StoreWorkerRequestDataModel): String {
    return when (request.direction) {
        WORKER_REQUEST_DIRECTION_STORE_TO_USER -> localizedStringResource(1103, "From store to worker")
        WORKER_REQUEST_DIRECTION_STORE_REMOVAL_TO_USER -> localizedStringResource(1221, "Removal request from store")
        else -> localizedStringResource(1102, "From worker to store")
    }
}

internal fun AppConfiguration.workerRequestStoreTitle(request: StoreWorkerRequestDataModel): String {
    return request.storeName.extractLocalizedString(stateValues.appLanguage).orEmpty()
        .ifBlank { request.storePublicId }
        .ifBlank { request.storeId }
}

internal fun AppConfiguration.workerRequestPersonLine(request: StoreWorkerRequestDataModel): String {
    return listOf(
        request.requesterPublicId,
        request.phoneNumber.asDisplayPhoneNumber(),
        request.email
    ).filter { it.isNotBlank() }.joinToString(" • ")
}

internal fun AppConfiguration.workerResponseStatusColor(status: String): Color {
    return when (status) {
        WORKER_REQUEST_STATUS_ACCEPTED -> stateValues.OkayColor
        WORKER_REQUEST_STATUS_DECLINED -> stateValues.ErrorColor
        else -> stateValues.AccentColor
    }
}

@Composable
internal fun AppConfiguration.WorkerDecisionNoteDialog(
    title: String,
    subtitle: String,
    positiveButtonText: String,
    positiveColor: Color = stateValues.AccentColor,
    positiveIconPath: String = stateValues.drawablePathIconCheck,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var note by rememberSaveable(title, subtitle) { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        TransactionBarcodeModalGuard()
        Column(
            modifier = Modifier
                .aitaDialogEntrance()
                .fillMaxWidth(if (stateValues.isNarrowScreen) 0.92f else 0.52f)
                .widthIn(max = 520.dp)
                .foregroundTactileShadow(stateValues.cornerRadius, elevated = true)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.BackgroundColor)
                .border(stateValues.focusedBorderWidth, positiveColor, RoundedCornerShape(stateValues.cornerRadius))
                .padding(stateValues.marginTextFieldGroup),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CpImage(
                modifier = Modifier.size(44.dp),
                url = stateValues.drawablePathIconResponse,
                fallbackRes = stateValues.drawableResIconResponse.value,
                contentDescription = title,
                tintColor = positiveColor
            )

            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = subtitle,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                textAlign = TextAlign.Center
            )

            SimpleTextInput(
                modifier = Modifier.fillMaxWidth(),
                value = note,
                placeholder = localizedStringResource(1098, "Add an optional note for the other party"),
                singleLine = false,
                leadingIconPath = stateValues.drawablePathIconResponse,
                onValueChange = { note = it.take(240) }
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stateValues.stringCancel,
                    iconPath = stateValues.drawablePathIconCancel,
                    enabledColor = stateValues.DisabledColor,
                    confirmationRequired = false,
                    onClick = onDismiss
                )

                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = positiveButtonText,
                    iconPath = positiveIconPath,
                    enabledColor = positiveColor,
                    confirmationRequired = false,
                    onClick = { onConfirm(note.trim().takeIf { it.isNotBlank() }) }
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.WorkerResponseInfoRow(
    label: String,
    value: String,
    accent: Boolean = false
) {
    if (value.isBlank()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            modifier = Modifier.weight(0.42f),
            text = label,
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            modifier = Modifier.weight(0.58f),
            text = value,
            color = if (accent) stateValues.AccentColor else stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
internal fun AppConfiguration.WorkerResponseCard(
    request: StoreWorkerRequestDataModel,
    storePerspective: Boolean
) {
    val statusColor = workerResponseStatusColor(request.status)
    val storeTitle = workerRequestStoreTitle(request)
    val workerLine = workerRequestPersonLine(request)
    val title = if (storePerspective) request.displayName.ifBlank { request.requesterPublicId } else storeTitle
    val secondaryLine = if (storePerspective) {
        listOf(storeTitle, workerLine).filter { it.isNotBlank() }.joinToString(" • ")
    } else {
        workerLine.ifBlank { request.storePublicId }
    }
    val directionHint = when (request.direction) {
        WORKER_REQUEST_DIRECTION_STORE_TO_USER -> localizedStringResource(1114, "Store invited this worker")
        WORKER_REQUEST_DIRECTION_STORE_REMOVAL_TO_USER -> localizedStringResource(1237, "Store asked this worker to confirm removal")
        else -> localizedStringResource(1113, "You requested work in this store")
    }
    val permissionLine = request.permissions.joinToString(" • ") { workerPermissionLabel(it) }
    val noteText = request.responseNoteVisible(stateValues.appLanguage).orEmpty().trim()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(stateValues.unfocusedBorderWidth, statusColor, RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            CpImage(
                modifier = Modifier.size(34.dp),
                url = stateValues.drawablePathIconResponse,
                fallbackRes = stateValues.drawableResIconResponse.value,
                contentDescription = localizedStringResource(1093, "Employment responses"),
                tintColor = statusColor
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (secondaryLine.isNotBlank()) {
                    Text(
                        text = secondaryLine,
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = workerRequestStatusLabel(request),
                color = statusColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }

        Text(
            text = directionHint,
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        WorkerResponseInfoRow(localizedStringResource(1116, "Request direction"), workerRequestDirectionLabel(request))
        WorkerResponseInfoRow(localizedStringResource(1432, "Job title"), workerJobTitleText(request.jobTitle, request.jobTitleLocalized))
        WorkerResponseInfoRow(localizedStringResource(466, "Role"), workerRoleLabel(request.roleId.ifBlank { WORKER_ROLE_STANDARD }))
        WorkerResponseInfoRow(localizedStringResource(1433, "Salary"), workerSalaryLine(request.salary, request.salaryCurrencyCode))
        request.offerNoteVisible(stateValues.appLanguage).orEmpty().trim().takeIf { it.isNotBlank() }?.let { offerNote ->
            WorkerResponseInfoRow(localizedStringResource(1449, "Offer note"), offerNote)
        }
        WorkerResponseInfoRow(localizedStringResource(467, "Allowed actions"), permissionLine)
        WorkerResponseInfoRow(localizedStringResource(1096, "Request time"), receiptUiDateTime(request.requestedAtMillis))
        WorkerResponseInfoRow(localizedStringResource(1095, "Response time"), request.decidedAtMillis?.let { receiptUiDateTime(it) }.orEmpty(), accent = true)
        WorkerResponseInfoRow(localizedStringResource(1101, "Responded by"), request.decidedByUserId.orEmpty())

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = localizedStringResource(1097, "Response note"),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = noteText.ifBlank { localizedStringResource(1106, "No note from responding party") },
            color = if (noteText.isBlank()) stateValues.PlaceholderTextColor else stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AppConfiguration.WorkerPermissionEditor(
    permissions: List<String>,
    availablePermissions: Set<String> = ALL_STORE_PERMISSION_IDS.toSet(),
    onChanged: (List<String>) -> Unit
) {
    val visiblePermissionIds = ALL_STORE_PERMISSION_IDS.filter { it in availablePermissions }
    val selectedPermissionSet = permissions.toSet()
    val groups = visiblePermissionIds.groupBy { workerPermissionCategory(it) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        groups.forEach { (category, permissionIds) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.35f), RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.BackgroundColor)
                    .padding(stateValues.marginTextField),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = category,
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )

                permissionIds.chunked(if (stateValues.isNarrowScreen) 1 else 2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { permissionId ->
                            val checked = permissionId in selectedPermissionSet
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                                    .aitaClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(color = stateValues.AccentColor),
                                        onClick = {
                                            onChanged(
                                                normalizeStorePermissionIds(if (checked) permissions - permissionId else permissions + permissionId)
                                            )
                                        }
                                    )
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AitaRoundCheckbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        onChanged(normalizeStorePermissionIds(if (isChecked) permissions + permissionId else permissions - permissionId))
                                    }
                                )

                                Text(
                                    text = workerPermissionLabel(permissionId),
                                    color = stateValues.TextColor,
                                    fontSize = stateValues.smallTextSize,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        repeat((if (stateValues.isNarrowScreen) 1 else 2) - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.ResetPermissionsText(
    roleId: String,
    assignablePermissions: Set<String>,
    templates: List<StoreWorkerRoleTemplateDataModel>,
    onReset: (List<String>) -> Unit
) {
    Text(
        text = localizedStringResource(1282, "Reset checkboxes to this role default"),
        color = stateValues.AccentColor,
        fontSize = stateValues.smallTextSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.AccentColor),
                onClick = { onReset(defaultAssignablePermissionsForWorkerRole(roleId, assignablePermissions, templates)) }
            )
            .padding(vertical = 4.dp),
        textAlign = TextAlign.End
    )
}

@Composable
internal fun AppConfiguration.WorkerRoleTemplateManager(
    storeId: String,
    templates: List<StoreWorkerRoleTemplateDataModel>,
    assignablePermissions: Set<String>
) {
    var editingTemplateId by rememberSaveable(storeId) { mutableStateOf("") }
    var nameText by rememberSaveable(storeId) { mutableStateOf("") }
    var descriptionText by rememberSaveable(storeId) { mutableStateOf("") }
    var permissionsText by rememberSaveable(storeId, assignablePermissions.sorted().joinToString("|")) {
        mutableStateOf(defaultAssignablePermissionsForWorkerRole(WORKER_ROLE_STANDARD, assignablePermissions, templates).joinToString("|"))
    }
    val permissions = permissionsFromSerialized(permissionsText)
        .filter { it in assignablePermissions }
        .ifEmpty { defaultAssignablePermissionsForWorkerRole(WORKER_ROLE_STANDARD, assignablePermissions, templates) }

    fun startEdit(template: StoreWorkerRoleTemplateDataModel) {
        editingTemplateId = template.id
        nameText = template.name.extractLocalizedString(stateValues.appLanguage).orEmpty().ifBlank { template.displayName }
        descriptionText = template.description.extractLocalizedString(stateValues.appLanguage).orEmpty()
        permissionsText = template.permissions.filter { it in assignablePermissions }.joinToString("|")
    }

    fun resetEditor() {
        editingTemplateId = ""
        nameText = ""
        descriptionText = ""
        permissionsText = defaultAssignablePermissionsForWorkerRole(WORKER_ROLE_STANDARD, assignablePermissions, templates).joinToString("|")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.55f), RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            CpImage(
                modifier = Modifier.size(32.dp),
                url = stateValues.drawablePathIconWorkerRoleTemplates,
                fallbackRes = stateValues.drawableResIconWorkerRoleTemplates.value,
                contentDescription = localizedStringResource(1336, "Worker role templates"),
                tintColor = stateValues.AccentColor,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedStringResource(1336, "Worker role templates"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localizedStringResource(1405, "Save reusable permission sets for cashier, stockkeeper, branch lead, or any cute custom role."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
        }

        if (templates.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                templates.forEach { template ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(stateValues.BackgroundColor)
                            .padding(stateValues.marginTextField),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = workerRoleLabel(template.id, templates),
                                color = stateValues.TextColor,
                                fontSize = stateValues.textSize,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = localizedStringResource(1285, "{count} permissions").replace("{count}", template.permissions.size.toString()),
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.smallTextSize
                            )
                        }
                        actionButton(
                            text = localizedStringResource(1086, "Edit"),
                            iconPath = stateValues.drawablePathIconEdit,
                            confirmationRequired = false,
                            onClick = { startEdit(template) }
                        )
                        actionButton(
                            text = localizedStringResource(1277, "Delete"),
                            enabledColor = stateValues.ErrorColor,
                            iconPath = stateValues.drawablePathIconDelete,
                            confirmationRequired = true,
                            onClick = { deleteStoreWorkerRoleTemplate(storeId, template.id) }
                        )
                    }
                }
            }
        }

        Text(
            text = if (editingTemplateId.isBlank()) localizedStringResource(1286, "Create role template") else localizedStringResource(1287, "Edit role template"),
            color = stateValues.AccentColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
        )

        SimpleTextInput(
            modifier = Modifier.fillMaxWidth(),
            value = nameText,
            placeholder = localizedStringResource(1288, "Role name, for example Stockkeeper"),
            leadingIconPath = stateValues.drawablePathIconWorkerRoleTemplates,
            stateHost = NavigationScreenModel.Menu.Workers,
            stateKey = "worker_role_template_name_$storeId",
            onValueChange = { nameText = it.take(64) }
        )

        SimpleTextInput(
            modifier = Modifier.fillMaxWidth(),
            value = descriptionText,
            placeholder = localizedStringResource(1289, "Optional short description"),
            leadingIconPath = stateValues.drawablePathIconResponse,
            singleLine = false,
            stateHost = NavigationScreenModel.Menu.Workers,
            stateKey = "worker_role_template_description_$storeId",
            onValueChange = { descriptionText = it.take(180) }
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = workerRoleLabel(WORKER_ROLE_STANDARD),
                iconPath = stateValues.drawablePathIconPerson,
                confirmationRequired = false,
                onClick = { permissionsText = defaultAssignablePermissionsForWorkerRole(WORKER_ROLE_STANDARD, assignablePermissions, templates).joinToString("|") }
            )
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = workerRoleLabel(WORKER_ROLE_ADMIN),
                iconPath = stateValues.drawablePathIconSecurity,
                confirmationRequired = false,
                onClick = { permissionsText = defaultAssignablePermissionsForWorkerRole(WORKER_ROLE_ADMIN, assignablePermissions, templates).joinToString("|") }
            )
        }

        WorkerPermissionEditor(
            permissions = permissions,
            availablePermissions = assignablePermissions,
            onChanged = { permissionsText = it.distinct().joinToString("|") }
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = stateValues.stringCancel,
                iconPath = stateValues.drawablePathIconCancel,
                enabledColor = stateValues.DisabledColor,
                confirmationRequired = false,
                onClick = { resetEditor() }
            )
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(471, "Save"),
                enabled = nameText.isNotBlank() && permissions.isNotEmpty(),
                iconPath = stateValues.drawablePathIconCheck,
                confirmationRequired = false,
                onClick = {
                    upsertStoreWorkerRoleTemplate(
                        storeId = storeId,
                        templateId = editingTemplateId,
                        name = listOf(
                            LocalizedStringDataModel("main", nameText.trim()),
                            LocalizedStringDataModel(stateValues.appLanguage.ifBlank { DEFAULT_APP_LANGUAGE }, nameText.trim())
                        ).distinctBy { it.language },
                        description = descriptionText.trim().takeIf { it.isNotBlank() }?.let { listOf(LocalizedStringDataModel("main", it)) }.orEmpty(),
                        permissions = permissions
                    ) { result ->
                        if (result is DataState.Success) resetEditor()
                    }
                }
            )
        }
    }
}


@Composable
internal fun AppConfiguration.WorkerRequestCard(
    storeId: String,
    request: StoreWorkerRequestDataModel
) {
    val requestStoreId = request.storeId.ifBlank { storeId }
    val assignablePermissions = currentUserAssignableStorePermissions(requestStoreId)
    val roleTemplates = storeWorkerRoleTemplatesState.payloadValue.orEmpty()
    fun defaultAssignablePermissionsForRole(selectedRoleId: String): List<String> =
        defaultAssignablePermissionsForWorkerRole(selectedRoleId, assignablePermissions, roleTemplates)

    var roleId by rememberSaveable(request.id) { mutableStateOf(request.roleId.ifBlank { WORKER_ROLE_STANDARD }) }
    var jobTitle by rememberSaveable(request.id) { mutableStateOf(workerJobTitleText(request.jobTitle, request.jobTitleLocalized)) }
    var salary by rememberSaveable(request.id) { mutableStateOf(request.salary) }
    val salaryCurrencyCode = request.salaryCurrencyCode.ifBlank { "KZT" }
    var permissionsText by rememberSaveable(request.id, assignablePermissions.sorted().joinToString("|")) {
        mutableStateOf(
            request.permissions
                .ifEmpty { defaultAssignablePermissionsForRole(roleId) }
                .filter { it in assignablePermissions }
                .joinToString("|")
        )
    }
    val permissions = permissionsFromSerialized(permissionsText)
        .filter { it in assignablePermissions }
        .ifEmpty { defaultAssignablePermissionsForRole(roleId) }
    val roleOptions = workerRoleOptions(roleTemplates)
    var decisionDialog by rememberSaveable(request.id) { mutableStateOf<String?>(null) }

    decisionDialog?.let { action ->
        val accepting = action == "accept"
        WorkerDecisionNoteDialog(
            title = if (accepting) localizedStringResource(1434, "Send job offer?") else localizedStringResource(1110, "Decline employment request?"),
            subtitle = if (accepting) localizedStringResource(1435, "The worker will review this job offer and accept or decline it.") else listOf(request.displayName, workerRequestDirectionLabel(request)).filter { it.isNotBlank() }.joinToString(" • "),
            positiveButtonText = if (accepting) localizedStringResource(1434, "Send job offer") else localizedStringResource(469, "Decline"),
            positiveColor = if (accepting) stateValues.AccentColor else stateValues.ErrorColor,
            positiveIconPath = if (accepting) stateValues.drawablePathIconCheck else stateValues.drawablePathIconCancel,
            onDismiss = { decisionDialog = null },
            onConfirm = { responseNote ->
                decisionDialog = null
                if (accepting) {
                    acceptStoreEmploymentRequest(
                        storeId = requestStoreId,
                        requestId = request.id,
                        roleId = roleId,
                        permissions = permissions,
                        jobTitle = jobTitle,
                        salary = salary,
                        salaryCurrencyCode = salaryCurrencyCode,
                        note = responseNote
                    )
                } else {
                    declineStoreEmploymentRequest(
                        storeId = requestStoreId,
                        requestId = request.id,
                        note = responseNote
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Text(
            text = request.displayName,
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = listOf(request.requesterPublicId, request.phoneNumber.asDisplayPhoneNumber(), request.email).filter { it.isNotBlank() }.joinToString(" • "),
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize
        )

        Text(
            text = "${localizedStringResource(465, "Requested")}: ${receiptUiDateTime(request.requestedAtMillis)}",
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        SimpleTextInput(
            modifier = Modifier.fillMaxWidth(),
            value = jobTitle,
            placeholder = localizedStringResource(1432, "Job title"),
            leadingIconPath = stateValues.drawablePathIconPerson,
            stateHost = NavigationScreenModel.Menu.Workers,
            stateKey = "menu_workers_offer_job_title_${request.id}",
            onValueChange = { jobTitle = it.take(120) }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        SimpleTextInput(
            modifier = Modifier.fillMaxWidth(),
            value = salary,
            placeholder = localizedStringResource(1433, "Salary"),
            keyboardType = KeyboardType.Decimal,
            leadingIconPath = stateValues.drawablePathIconFinances,
            stateHost = NavigationScreenModel.Menu.Workers,
            stateKey = "menu_workers_offer_salary_${request.id}",
            onTransformValue = ::normalizeWorkerSalaryInput,
            onValueChange = { salary = normalizeWorkerSalaryInput(it) }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        WorkerOfferDetails(
            roleId = roleId,
            permissions = permissions,
            jobTitle = jobTitle,
            salary = salary,
            salaryCurrencyCode = salaryCurrencyCode,
            showPermissionPreview = false
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        SimpleDropdownField(
            modifier = Modifier.fillMaxWidth(),
            title = localizedStringResource(466, "Role"),
            selectedId = roleId,
            options = roleOptions,
            placeholder = workerRoleLabel(WORKER_ROLE_STANDARD),
            onSelected = { selectedRole ->
                roleId = selectedRole
                permissionsText = defaultAssignablePermissionsForRole(selectedRole).joinToString("|")
            }
        )

        ResetPermissionsText(
            roleId = roleId,
            assignablePermissions = assignablePermissions,
            templates = roleTemplates,
            onReset = { permissionsText = it.joinToString("|") }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        Text(
            text = localizedStringResource(467, "Allowed actions"),
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        WorkerPermissionEditor(
            permissions = permissions,
            availablePermissions = assignablePermissions,
            onChanged = { permissionsText = it.distinct().joinToString("|") }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        Text(
            text = localizedStringResource(1435, "The worker will review this job offer and accept or decline it."),
            color = stateValues.AccentColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = localizedStringResource(1440, "Structured employment offer"),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1434, "Send job offer"),
                iconPath = stateValues.drawablePathIconCheck,
                confirmationRequired = false,
                onClick = { decisionDialog = "accept" }
            )

            actionButton(
                modifier = Modifier.fillMaxWidth(),
                enabledColor = stateValues.ErrorColor,
                text = localizedStringResource(469, "Decline"),
                iconPath = stateValues.drawablePathIconCancel,
                confirmationRequired = false,
                onClick = { decisionDialog = "decline" }
            )
        }
    }
}

@Composable
internal fun AppConfiguration.WorkerRemovalRequestCard(
    request: StoreWorkerRequestDataModel
) {
    var decisionDialog by rememberSaveable(request.id) { mutableStateOf<String?>(null) }
    val storeTitle = workerRequestStoreTitle(request)
    val subtitle = listOf(storeTitle, workerRequestDirectionLabel(request)).filter { it.isNotBlank() }.joinToString(" • ")

    decisionDialog?.let { action ->
        val confirming = action == "confirm"
        WorkerDecisionNoteDialog(
            title = if (confirming) localizedStringResource(1226, "Confirm removal request?") else localizedStringResource(1227, "Keep your access?"),
            subtitle = subtitle,
            positiveButtonText = if (confirming) localizedStringResource(1228, "Confirm removal") else localizedStringResource(1229, "Keep my access"),
            positiveColor = if (confirming) stateValues.ErrorColor else stateValues.AccentColor,
            positiveIconPath = if (confirming) stateValues.drawablePathIconCancel else stateValues.drawablePathIconCheck,
            onDismiss = { decisionDialog = null },
            onConfirm = { responseNote ->
                decisionDialog = null
                if (confirming) {
                    acceptMyStoreWorkerRemovalRequest(request.id, note = responseNote)
                } else {
                    declineMyStoreWorkerRemovalRequest(request.id, note = responseNote)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(stateValues.unfocusedBorderWidth, stateValues.ErrorColor, RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CpImage(
                modifier = Modifier.size(34.dp),
                url = stateValues.drawablePathIconWorkers,
                fallbackRes = stateValues.drawableResIconWorkers.value,
                contentDescription = localizedStringResource(1221, "Removal request from store"),
                tintColor = stateValues.ErrorColor
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = storeTitle,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = receiptUiDateTime(request.requestedAtMillis),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
        }

        Text(
            text = localizedStringResource(1230, "This store asked to end your worker access. Confirm only if you agree."),
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize
        )

        request.requestNoteVisible(stateValues.appLanguage)?.let { noteText ->
            Text(
                text = noteText,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1228, "Confirm removal"),
                enabledColor = stateValues.ErrorColor,
                iconPath = stateValues.drawablePathIconCancel,
                confirmationRequired = false,
                onClick = { decisionDialog = "confirm" }
            )
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1229, "Keep my access"),
                iconPath = stateValues.drawablePathIconCheck,
                confirmationRequired = false,
                onClick = { decisionDialog = "deny" }
            )
        }
    }
}

@Composable
internal fun AppConfiguration.WorkerMembershipCard(
    worker: StoreWorkerDataModel,
    editable: Boolean,
    storeId: String? = null,
    showSelfPasswordEditor: Boolean = false,
    pendingRemovalRequest: StoreWorkerRequestDataModel? = null,
    canRemove: Boolean = false
) {
    val workerStoreId = storeId ?: worker.storeId
    val assignablePermissions = currentUserAssignableStorePermissions(workerStoreId)
    val roleTemplates = storeWorkerRoleTemplatesState.payloadValue.orEmpty()
    fun defaultAssignablePermissionsForRole(selectedRoleId: String): List<String> =
        defaultAssignablePermissionsForWorkerRole(selectedRoleId, assignablePermissions, roleTemplates)

    var roleId by rememberSaveable(worker.id) { mutableStateOf(worker.roleId.ifBlank { WORKER_ROLE_STANDARD }) }
    var jobTitle by rememberSaveable(worker.id) { mutableStateOf(workerJobTitleText(worker.jobTitle, worker.jobTitleLocalized)) }
    var salary by rememberSaveable(worker.id) { mutableStateOf(worker.salary) }
    val salaryCurrencyCode = worker.salaryCurrencyCode.ifBlank { "KZT" }
    var permissionsText by rememberSaveable(worker.id) { mutableStateOf(normalizeStorePermissionIds(worker.permissions).joinToString("|")) }
    val permissions = permissionsFromSerialized(permissionsText).ifEmpty {
        if (editable) defaultAssignablePermissionsForRole(roleId) else normalizeStorePermissionIds(worker.permissions.ifEmpty { defaultStorePermissionsForRole(roleId) })
    }
    var selfWorkshiftPasswordSaving by remember(worker.id) { mutableStateOf(false) }
    val roleOptions = workerRoleOptions(roleTemplates)
    val storeName = worker.storeName.extractLocalizedString(stateValues.appLanguage).orEmpty()
    val contactLine = listOf(worker.userPublicId, worker.phoneNumber.asDisplayPhoneNumber(), worker.email)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
    val permissionLine = permissions.joinToString(" • ") { workerPermissionLabel(it) }
    val currentUserId = stateValues.userAccount?.id.orEmpty()
    val removalControlAllowed = canRemove && storeId != null && worker.userId.isNotBlank() && worker.userId != currentUserId

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextFieldGroup)
    ) {
        StorePersonLink(workerStoreId,worker.userId,worker.displayName)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (showSelfPasswordEditor) storeName.ifBlank { worker.storePublicId.ifBlank { worker.displayName } } else worker.displayName,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold
                )

                val secondaryLine = if (showSelfPasswordEditor) {
                    worker.displayName.takeIf { it.isNotBlank() }
                } else {
                    storeName.takeIf { it.isNotBlank() }
                }
                if (!secondaryLine.isNullOrBlank()) {
                    Text(
                        text = secondaryLine,
                        color = stateValues.TextColor,
                        fontSize = stateValues.smallTextSize
                    )
                }
            }

            Text(
                text = workerRoleLabel(worker.roleId, roleTemplates),
                color = stateValues.AccentColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (contactLine.isNotBlank()) {
            Text(
                text = contactLine,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize
            )
        }

        if (worker.storePublicId.isNotBlank()) {
            Text(
                text = "${localizedStringResource(1083, "Store public ID")}: ${worker.storePublicId}",
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize
            )
        }

        Text(
            text = "${localizedStringResource(470, "Accepted")}: ${receiptUiDateTime(worker.acceptedAtMillis)}",
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )

        Spacer(modifier = Modifier.height(6.dp))

        WorkerOfferDetails(
            roleId = worker.roleId,
            permissions = normalizeStorePermissionIds(worker.permissions),
            jobTitle = worker.jobTitle,
            jobTitleLocalized = worker.jobTitleLocalized,
            salary = worker.salary,
            salaryCurrencyCode = salaryCurrencyCode
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${localizedStringResource(1085, "Shift password status")}: ${if (worker.hasWorkshiftPassword) localizedStringResource(1080, "Password is set") else localizedStringResource(1081, "Password is not set yet")}",
            color = if (worker.hasWorkshiftPassword) stateValues.OkayColor else stateValues.ErrorColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        if (editable && storeId != null) {
            SimpleTextInput(
                modifier = Modifier.fillMaxWidth(),
                value = jobTitle,
                placeholder = localizedStringResource(1432, "Job title"),
                leadingIconPath = stateValues.drawablePathIconPerson,
                stateHost = NavigationScreenModel.Menu.Workers,
                stateKey = "menu_workers_membership_job_title_${worker.id}",
                onValueChange = { jobTitle = it.take(120) }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            SimpleTextInput(
                modifier = Modifier.fillMaxWidth(),
                value = salary,
                placeholder = localizedStringResource(1433, "Salary"),
                keyboardType = KeyboardType.Decimal,
                leadingIconPath = stateValues.drawablePathIconFinances,
                stateHost = NavigationScreenModel.Menu.Workers,
                stateKey = "menu_workers_membership_salary_${worker.id}",
                onTransformValue = ::normalizeWorkerSalaryInput,
                onValueChange = { salary = normalizeWorkerSalaryInput(it) }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            SimpleDropdownField(
                modifier = Modifier.fillMaxWidth(),
                title = localizedStringResource(466, "Role"),
                selectedId = roleId,
                options = roleOptions,
                placeholder = workerRoleLabel(WORKER_ROLE_STANDARD),
                onSelected = { selectedRole ->
                    roleId = selectedRole
                    permissionsText = defaultAssignablePermissionsForRole(selectedRole).joinToString("|")
                }
            )

            ResetPermissionsText(
                roleId = roleId,
                assignablePermissions = assignablePermissions,
                templates = roleTemplates,
                onReset = { permissionsText = it.joinToString("|") }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            WorkerPermissionEditor(
                permissions = permissions,
                availablePermissions = assignablePermissions,
                onChanged = { permissionsText = it.distinct().joinToString("|") }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            Text(
                text = localizedStringResource(1088, "You can edit role and permissions here. The worker manages their own shift password from My work."),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            actionButton(
                text = localizedStringResource(471, "Save permissions"),
                iconPath = stateValues.drawablePathIconCheck,
                confirmationRequired = false,
                onClick = {
                    updateStoreWorkerPermissions(
                        storeId = storeId,
                        workerId = worker.id,
                        roleId = roleId,
                        permissions = permissions,
                        jobTitle = jobTitle,
                        salary = salary,
                        salaryCurrencyCode = salaryCurrencyCode
                    )
                }
            )

            if (removalControlAllowed) {
                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                if (pendingRemovalRequest != null) {
                    Text(
                        text = localizedStringResource(1231, "Removal request is waiting for worker confirmation"),
                        color = stateValues.AccentColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = localizedStringResource(1235, "The worker stays active until they confirm this request."),
                        color = stateValues.TextColor,
                        fontSize = stateValues.smallTextSize,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    actionButton(
                        text = localizedStringResource(1232, "Request removal"),
                        enabledColor = stateValues.ErrorColor,
                        iconPath = stateValues.drawablePathIconDelete,
                        iconContentDescription = localizedStringResource(1232, "Request removal"),
                        confirmationRequired = true,
                        onClick = {
                            removeStoreWorker(
                                storeId = storeId,
                                workerId = worker.id
                            )
                        }
                    )
                }
            }
        } else {
            Text(
                text = localizedStringResource(1084, "Employment permissions"),
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = permissionLine,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize
            )

            if (removalControlAllowed) {
                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                if (pendingRemovalRequest != null) {
                    Text(
                        text = localizedStringResource(1231, "Removal request is waiting for worker confirmation"),
                        color = stateValues.AccentColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = localizedStringResource(1235, "The worker stays active until they confirm this request."),
                        color = stateValues.TextColor,
                        fontSize = stateValues.smallTextSize,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    actionButton(
                        text = localizedStringResource(1232, "Request removal"),
                        enabledColor = stateValues.ErrorColor,
                        iconPath = stateValues.drawablePathIconDelete,
                        iconContentDescription = localizedStringResource(1232, "Request removal"),
                        confirmationRequired = true,
                        onClick = {
                            removeStoreWorker(
                                storeId = storeId,
                                workerId = worker.id
                            )
                        }
                    )
                }
            }

            if (showSelfPasswordEditor) {
                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                val passwordStateKey = "keyState_workshiftPassword_${worker.id}"
                val repeatedPasswordStateKey = "keyState_workshiftRepeatedPassword_${worker.id}"
                val (workshiftPasswordField, repeatedWorkshiftPasswordField) = repeatedPasswordTextFieldGroup(
                    modifier = Modifier.fillMaxWidth(),
                    stateHost = NavigationScreenModel.Menu.Workers,
                    stateKey = passwordStateKey,
                    repeatedStateKey = repeatedPasswordStateKey,
                    passwordTitleText = localizedStringResource(1078, "Workshift password"),
                    passwordPlaceholderText = if (worker.hasWorkshiftPassword) localizedStringResource(655, "Set or replace your password for starting workshifts") else localizedStringResource(1078, "Workshift password"),
                    repeatPasswordTitleText = stateValues.stringRepeatPassword,
                    repeatPasswordPlaceholderText = stateValues.stringRepeatPassword
                )

                Text(
                    text = stateValues.stringPasswordMustBe,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )

                Text(
                    text = localizedStringResource(1082, "You use this password to start workshifts in this store. Only you set it; store managers can only edit your role and permissions."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                val accountPasswordStateKey = "keyState_workshiftAccountPassword_${worker.id}"
                val accountPasswordField = passwordTextField(
                    modifier = Modifier.fillMaxWidth(),
                    stateHost = NavigationScreenModel.Menu.Workers,
                    stateKey = accountPasswordStateKey,
                    titleText = localizedStringResource(1143, "Account password"),
                    placeholderText = localizedStringResource(1144, "Enter your account password"),
                    contentInvalidText = localizedStringResource(1145, "Account password is required to change the workshift password"),
                    onContentValidityCheck = { it.isNotBlank() }
                )

                Text(
                    text = localizedStringResource(1146, "Confirm with your account password so nobody can change the shift password on an unlocked device."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                actionButton(
                    text = localizedStringResource(1079, "Set my workshift password"),
                    enabled = !selfWorkshiftPasswordSaving && workshiftPasswordField.value.text.isNotBlank() && repeatedWorkshiftPasswordField.value.text.isNotBlank() && accountPasswordField.value.text.isNotBlank(),
                    loading = selfWorkshiftPasswordSaving,
                    loadingText = localizedStringResource(1142, "Updating workshift password…"),
                    iconPath = stateValues.drawablePathIconSecurity,
                    confirmationRequired = false,
                    onClick = {
                        workshiftPasswordField.checkContentValidity()
                        repeatedWorkshiftPasswordField.checkContentValidity()
                        accountPasswordField.checkContentValidity()

                        if (workshiftPasswordField.isContentValid && repeatedWorkshiftPasswordField.isContentValid && accountPasswordField.isContentValid) {
                            selfWorkshiftPasswordSaving = true
                            updateMyWorkerPassword(
                                workerId = worker.id,
                                workerPassword = workshiftPasswordField.value.text,
                                accountPassword = accountPasswordField.value.text
                            ) { result ->
                                selfWorkshiftPasswordSaving = false
                                if (result is DataState.Success) {
                                    workshiftPasswordField.reset()
                                    repeatedWorkshiftPasswordField.reset()
                                    accountPasswordField.reset()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AppConfiguration.MenuWorkersScreen() {
    val activeStoreId = stateValues.activeStoreId
    val storeAccess = rememberStoreSubscriptionAccess(activeStoreId)
    val canViewStoreWorkers = storeAccess && activeStoreId != null && currentUserCanViewWorkers(activeStoreId)
    val canManageStoreRequests = storeAccess && activeStoreId != null && currentUserCanInviteWorkers(activeStoreId)
    val myMembershipsPayload by myWorkerMembershipsState.payload.collectAsState()
    val myRequestsPayload by myWorkerRequestsState.payload.collectAsState()
    val incomingRequestsPayload by incomingWorkerRequestsState.payload.collectAsState()
    val storeWorkersPayload by storeWorkerMembershipsState.payload.collectAsState()
    val roleTemplatesPayload by storeWorkerRoleTemplatesState.payload.collectAsState()
    var storeIdText by rememberSaveable { mutableStateOf("") }
    val inviteRoleTemplates = roleTemplatesPayload.orEmpty()
    val inviteAssignablePermissions = activeStoreId?.let { currentUserAssignableStorePermissions(it) }.orEmpty()
    fun defaultInvitePermissionsForRole(selectedRoleId: String): List<String> =
        defaultAssignablePermissionsForWorkerRole(selectedRoleId, inviteAssignablePermissions, inviteRoleTemplates)
    var invitedUserId by rememberSaveable(activeStoreId) { mutableStateOf("") }
    var roleId by rememberSaveable(activeStoreId) { mutableStateOf(WORKER_ROLE_STANDARD) }
    var jobTitle by rememberSaveable(activeStoreId) { mutableStateOf("") }
    var salary by rememberSaveable(activeStoreId) { mutableStateOf("") }
    var offerNote by rememberSaveable(activeStoreId) { mutableStateOf("") }
    var permissionsText by rememberSaveable(activeStoreId, inviteAssignablePermissions.sorted().joinToString("|")) {
        mutableStateOf(defaultInvitePermissionsForRole(WORKER_ROLE_STANDARD).joinToString("|"))
    }

    val myMembershipsCount = myMembershipsPayload.orEmpty().size
    val myPendingEmploymentRequestsCount = myRequestsPayload.orEmpty()
        .count { it.direction == WORKER_REQUEST_DIRECTION_USER_TO_STORE && it.status == WORKER_REQUEST_STATUS_PENDING }
    val myInvitesCount = myRequestsPayload.orEmpty()
        .count { !it.isWorkerRemovalRequest() && it.status == WORKER_REQUEST_STATUS_INVITED } +
        myRequestsPayload.orEmpty().count { it.isPendingWorkerRemovalRequest() }
    val myResponsesCount = myRequestsPayload.orEmpty().count { it.isEmploymentResponse() || it.isWorkerRemovalResponse() } +
        (if (canManageStoreRequests) incomingRequestsPayload.orEmpty().count { it.isEmploymentResponse() || it.isWorkerRemovalResponse() } else 0)
    val storeWorkersCount = storeWorkersPayload.orEmpty().size
    val incomingEmploymentRequestsCount = incomingRequestsPayload.orEmpty()
        .count { it.direction == WORKER_REQUEST_DIRECTION_USER_TO_STORE && it.status == WORKER_REQUEST_STATUS_PENDING }

    LaunchedEffect(stateValues.userAccount?.id, activeStoreId, canViewStoreWorkers, canManageStoreRequests) {
        getMyWorkerMemberships()
        getMyWorkerRequests()
        if (canViewStoreWorkers && activeStoreId != null) getStoreWorkers(activeStoreId)
        if (canManageStoreRequests && activeStoreId != null) {
            getIncomingWorkerRequests(activeStoreId)
            getStoreWorkerRoleTemplates(activeStoreId)
        }
    }

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringWorkers,
                iconPath = stateValues.drawablePathIconWorkers,
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        WorkerIdentityCard(Modifier.fillMaxWidth().aitaWidthCap(960.dp)
            .align(Alignment.CenterHorizontally).padding(horizontal=stateValues.marginTextField, vertical=6.dp))
        val selectedTab = tabRowWidget(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Start)
                .padding(horizontal = stateValues.marginTextField, vertical = 2.dp),
            tabs = listOf(
                TabContent("my_work", tabLabelWithCount(localizedStringResource(472, "My work"), myMembershipsCount + myPendingEmploymentRequestsCount)),
                TabContent("invites", tabLabelWithCount(localizedStringResource(651, "Invites"), myInvitesCount)),
                TabContent("responses", tabLabelWithCount(localizedStringResource(1092, "Responses"), myResponsesCount))
            ) + (if (canViewStoreWorkers) listOf(TabContent("store_workers", tabLabelWithCount(localizedStringResource(473, "Store workers"), storeWorkersCount))) else emptyList()) +
                (if (canManageStoreRequests) listOf(TabContent("requests", tabLabelWithCount(localizedStringResource(474, "Requests"), incomingEmploymentRequestsCount))) else emptyList())
        )

        val sectionTabs = when (selectedTab.id) {
            "my_work" -> listOf(
                TabContent("managed", tabLabelWithCount(localizedStringResource(478, "Managed stores"), myMembershipsCount)),
                TabContent("requests", tabLabelWithCount(localizedStringResource(480, "My employment requests"), myPendingEmploymentRequestsCount)),
                TabContent("apply", localizedStringResource(475, "Request employment in a store"))
            )
            "invites" -> listOf(
                TabContent("invitations", localizedStringResource(652, "Incoming invites from stores")),
                TabContent("removals", localizedStringResource(1225, "Removal requests"))
            )
            "responses" -> listOf(
                TabContent("mine", localizedStringResource(1104, "My response history"))
            ) + if (canManageStoreRequests) listOf(TabContent("store", localizedStringResource(1105, "Store response history"))) else emptyList()
            "store_workers" -> buildList {
                add(TabContent("workers", localizedStringResource(473, "Store workers")))
                if (activeStoreId != null && currentUserCanInviteWorkers(activeStoreId)) {
                    add(TabContent("invite", localizedStringResource(505, "Invite worker")))
                }
                if (activeStoreId != null && currentUserCanManageWorkerRoleTemplates(activeStoreId)) {
                    add(TabContent("roles", authUiText("Role templates", "Шаблоны ролей", "Рөл үлгілері", "Роль шаблондору")))
                }
            }
            else -> emptyList()
        }
        val section = sectionTabsWidget(
            stateKey = "workers:${stateValues.userAccount?.id.orEmpty()}:${activeStoreId.orEmpty()}:${selectedTab.id}",
            tabs = sectionTabs,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Start)
                .padding(horizontal = stateValues.marginTextField)
        )


        val workerListState = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Workers, "${selectedTab.id}_$section")
        LazyColumn(
            state = workerListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = stateValues.marginTextField),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            when (selectedTab.id) {
                "my_work" -> {
                    if (section == "apply") {
                        item(key = "MenuWorkersScreen:$section:0") {
                            Text(text = localizedStringResource(475, "Request employment in a store"), color = stateValues.TextColor,
                                fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

                            Spacer(modifier = Modifier.height(stateValues.marginTextField))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                                horizontalAlignment = Alignment.Start
                            ) {
                                SimpleTextInput(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = storeIdText,
                                    placeholder = localizedStringResource(657, "Enter store or branch public ID"),
                                    leadingIconPath = stateValues.drawablePathIconStores,
                                    stateHost = NavigationScreenModel.Menu.Workers,
                                    stateKey = "menu_workers_request_store_id",
                                    onValueChange = { storeIdText = it.trim() }
                                )

                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = storeIdText.isNotBlank(),
                                    text = localizedStringResource(477, "Send request"),
                                    iconPath = stateValues.drawablePathIconCheck,
                                    confirmationRequired = false,
                                    onClick = {
                                        requestStoreEmployment(storeIdText) { result ->
                                            if (result is DataState.Success) storeIdText = ""
                                        }
                                    }
                                )
                            }
                        }
                    }

                    val memberships = myMembershipsPayload.orEmpty()
                    val requests = myRequestsPayload.orEmpty()
                        .filter { it.direction == WORKER_REQUEST_DIRECTION_USER_TO_STORE && it.status == WORKER_REQUEST_STATUS_PENDING }



                    when (section) {
                        "requests" -> {
                            if (requests.isEmpty()) {
                                item(key = "MenuWorkersScreen:$section:1") { MessageText(modifier = Modifier.fillMaxWidth().remainingListSpace(workerListState, "MenuWorkersScreen:$section:1"), text = localizedStringResource(1117, "No pending employment requests")) }
                            } else {
                                items(requests, key = { it.id }) { request ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                                            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                                            .background(stateValues.BackgroundColor)
                                            .padding(stateValues.marginTextFieldGroup)
                                    ) {
                                        Text(
                                            text = request.storeName.extractLocalizedString(stateValues.appLanguage).orEmpty().ifBlank { request.storeId },
                                            color = stateValues.TextColor,
                                            fontSize = stateValues.accentTextSize,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            text = "${localizedStringResource(482, "Status")}: ${workerRequestStatusLabel(request)}",
                                            color = stateValues.TextColor,
                                            fontSize = stateValues.textSize
                                        )

                                        Text(
                                            text = receiptUiDateTime(request.requestedAtMillis),
                                            color = stateValues.PlaceholderTextColor,
                                            fontSize = stateValues.smallTextSize
                                        )

                                        Text(
                                            text = localizedStringResource(1445, "Waiting for store to prepare an offer"),
                                            color = stateValues.AccentColor,
                                            fontSize = stateValues.smallTextSize,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        "managed" -> {
                            if (memberships.isEmpty()) {
                                item(key = "MenuWorkersScreen:$section:2") { MessageText(modifier = Modifier.fillMaxWidth().remainingListSpace(workerListState, "MenuWorkersScreen:$section:2"), text = localizedStringResource(479, "You are not employed in other stores yet")) }
                            } else {
                                items(memberships, key = { it.id }) { worker ->
                                    WorkerMembershipCard(worker = worker, editable = false, showSelfPasswordEditor = true)
                                }
                            }
                        }
                    }
                }


                "invites" -> {
                    val invitations = myRequestsPayload.orEmpty()
                        .filter { !it.isWorkerRemovalRequest() && it.status == WORKER_REQUEST_STATUS_INVITED }
                        .distinctBy { it.id }
                    val removalRequests = myRequestsPayload.orEmpty()
                        .filter { it.isPendingWorkerRemovalRequest() }
                        .distinctBy { it.id }

                    if ((section == "invitations" && invitations.isEmpty()) || (section == "removals" && removalRequests.isEmpty())) {
                        item(key = "MenuWorkersScreen:$section:3") { MessageText(modifier = Modifier.fillMaxWidth().remainingListSpace(workerListState, "MenuWorkersScreen:$section:3"), text = stateValues.stringListEmpty) }
                    } else {
                        if (section == "invitations" && invitations.isNotEmpty()) {
                            item(key = "MenuWorkersScreen:$section:4") {
                                Text(text = localizedStringResource(652, "Incoming invites from stores"), color = stateValues.TextColor,
                                fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }

                            items(invitations, key = { it.id }) { request ->
                                var decisionDialog by rememberSaveable(request.id) { mutableStateOf<String?>(null) }

                                decisionDialog?.let { action ->
                                    val accepting = action == "accept"
                                    WorkerDecisionNoteDialog(
                                        title = if (accepting) localizedStringResource(1441, "Accept job offer?") else localizedStringResource(1442, "Decline job offer?"),
                                        subtitle = listOf(localizedStringResource(1446, "Employment offer from store"), workerRequestStoreTitle(request), workerRequestDirectionLabel(request)).filter { it.isNotBlank() }.joinToString(" • "),
                                        positiveButtonText = if (accepting) localizedStringResource(1443, "Accept job offer") else localizedStringResource(1444, "Decline job offer"),
                                        positiveColor = if (accepting) stateValues.AccentColor else stateValues.ErrorColor,
                                        positiveIconPath = if (accepting) stateValues.drawablePathIconCheck else stateValues.drawablePathIconCancel,
                                        onDismiss = { decisionDialog = null },
                                        onConfirm = { responseNote ->
                                            decisionDialog = null
                                            if (accepting) {
                                                acceptMyStoreWorkerInvitation(request.id, note = responseNote)
                                            } else {
                                                declineMyStoreWorkerInvitation(request.id, note = responseNote)
                                            }
                                        }
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                                        .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                                        .background(stateValues.BackgroundColor)
                                        .padding(stateValues.marginTextFieldGroup)
                                ) {
                                    Text(
                                        text = request.storeName.extractLocalizedString(stateValues.appLanguage).orEmpty().ifBlank { request.storePublicId.ifBlank { request.storeId } },
                                        color = stateValues.TextColor,
                                        fontSize = stateValues.accentTextSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = listOf(request.storePublicId, request.invitedByUserId.orEmpty()).filter { it.isNotBlank() }.joinToString(" • "),
                                        color = stateValues.PlaceholderTextColor,
                                        fontSize = stateValues.smallTextSize
                                    )
                                    Text(
                                        text = "${localizedStringResource(482, "Status")}: ${workerRequestStatusLabel(request)}",
                                        color = stateValues.TextColor,
                                        fontSize = stateValues.textSize
                                    )
                                    Text(
                                        text = receiptUiDateTime(request.requestedAtMillis),
                                        color = stateValues.PlaceholderTextColor,
                                        fontSize = stateValues.smallTextSize
                                    )

                                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                    WorkerOfferDetails(
                                        roleId = request.roleId.ifBlank { WORKER_ROLE_STANDARD },
                                        permissions = normalizeStorePermissionIds(request.permissions),
                                        jobTitle = request.jobTitle,
                                        jobTitleLocalized = request.jobTitleLocalized,
                                        salary = request.salary,
                                        salaryCurrencyCode = request.salaryCurrencyCode.ifBlank { "KZT" },
                                        offerNote = request.offerNoteVisible(stateValues.appLanguage)
                                    )

                                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                                    ) {
                                        actionButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            text = localizedStringResource(1443, "Accept job offer"),
                                            iconPath = stateValues.drawablePathIconCheck,
                                            confirmationRequired = false,
                                            onClick = { decisionDialog = "accept" }
                                        )
                                        actionButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            text = localizedStringResource(1444, "Decline job offer"),
                                            enabledColor = stateValues.ErrorColor,
                                            iconPath = stateValues.drawablePathIconCancel,
                                            confirmationRequired = false,
                                            onClick = { decisionDialog = "decline" }
                                        )
                                    }
                                }
                            }
                        }

                        if (section == "removals" && removalRequests.isNotEmpty()) {
                            item(key = "MenuWorkersScreen:$section:5") {
                                Text(text = localizedStringResource(1225, "Removal requests"), color = stateValues.TextColor,
                                fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }

                            items(removalRequests, key = { "removal_${it.id}" }) { request ->
                                WorkerRemovalRequestCard(request = request)
                            }
                        }
                    }
                }

                "responses" -> {
                    item(key = "MenuWorkersScreen:$section:6") {
                        Text(text = localizedStringResource(1238, "Worker responses"), color = stateValues.TextColor,
                                fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }

                    if (section == "mine") {
                        val myResponses = myRequestsPayload.orEmpty()
                            .filter { it.isEmploymentResponse() || it.isWorkerRemovalResponse() }
                            .distinctBy { it.id }

                        item(key = "MenuWorkersScreen:$section:7") {
                            Text(text = localizedStringResource(1104, "My response history"), color = stateValues.TextColor,
                                fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }

                        if (myResponses.isEmpty()) {
                            item(key = "MenuWorkersScreen:$section:8") {
                                MessageText(
                                    modifier = Modifier.fillMaxWidth().remainingListSpace(workerListState, "MenuWorkersScreen:$section:8"),
                                    text = localizedStringResource(1094, "No employment responses yet")
                                )
                            }
                        } else {
                            items(myResponses, key = { "my_response_${it.id}" }) { request ->
                                WorkerResponseCard(request = request, storePerspective = false)
                            }
                        }
                    }

                    if (section == "store") {
                        item(key = "MenuWorkersScreen:$section:9") {
                            Text(text = localizedStringResource(1105, "Store response history"), color = stateValues.TextColor,
                                fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }

                        if (activeStoreId == null) {
                            item(key = "MenuWorkersScreen:$section:10") { MessageText(modifier = Modifier.fillMaxWidth().remainingListSpace(workerListState, "MenuWorkersScreen:$section:10"), text = stateValues.stringNoActiveStore) }
                        } else if (!currentUserCanDecideWorkerRequests(activeStoreId)) {
                            item(key = "MenuWorkersScreen:$section:11") { MessageText(modifier = Modifier.fillMaxWidth().remainingListSpace(workerListState, "MenuWorkersScreen:$section:11"), text = localizedStringResource(486, "Only store owners and permitted worker managers can accept employment requests")) }
                        } else {
                            val storeResponses = incomingRequestsPayload.orEmpty()
                                .filter { it.isEmploymentResponse() || it.isWorkerRemovalResponse() }
                                .distinctBy { it.id }

                            if (storeResponses.isEmpty()) {
                                item(key = "MenuWorkersScreen:$section:12") {
                                    MessageText(
                                        modifier = Modifier.fillMaxWidth().remainingListSpace(workerListState, "MenuWorkersScreen:$section:12"),
                                        text = localizedStringResource(1094, "No employment responses yet")
                                    )
                                }
                            } else {
                                items(storeResponses, key = { "store_response_${it.id}" }) { request ->
                                    WorkerResponseCard(request = request, storePerspective = true)
                                }
                            }
                        }
                    }
                }

                "store_workers" -> {
                    item(key = "MenuWorkersScreen:$section:13") {
                        Text(text = localizedStringResource(473, "Store workers"), color = stateValues.TextColor,
                                fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }

                    if (activeStoreId == null) {
                        item(key = "MenuWorkersScreen:$section:14") { MessageText(modifier = Modifier.fillMaxWidth().remainingListSpace(workerListState, "MenuWorkersScreen:$section:14"), text = stateValues.stringNoActiveStore) }
                    } else if (!currentUserCanViewWorkers(activeStoreId)) {
                        item(key = "MenuWorkersScreen:$section:15") { MessageText(modifier = Modifier.fillMaxWidth().remainingListSpace(workerListState, "MenuWorkersScreen:$section:15"), text = localizedStringResource(483, "You do not have permission to view workers in this store")) }
                    } else {
                        val roleTemplates = roleTemplatesPayload.orEmpty()

                        if (section == "roles") {
                            if (currentUserCanManageWorkerRoleTemplates(activeStoreId)) {
                                item(key = "worker_role_templates") {
                                    WorkerRoleTemplateManager(
                                        storeId = activeStoreId,
                                        templates = roleTemplates,
                                        assignablePermissions = currentUserAssignableStorePermissions(activeStoreId)
                                    )
                                }
                            }
                        }

                        if (section == "invite") {
                            if (currentUserCanInviteWorkers(activeStoreId)) {
                                item(key = "MenuWorkersScreen:$section:17") {
                                    val assignablePermissions = currentUserAssignableStorePermissions(activeStoreId)
                                    val salaryCurrencyCode = "KZT"
                                    val permissions = permissionsFromSerialized(permissionsText)
                                        .filter { it in assignablePermissions }
                                        .ifEmpty { defaultInvitePermissionsForRole(roleId) }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                                            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                                            .background(stateValues.BackgroundColor)
                                            .padding(stateValues.marginTextFieldGroup)
                                    ) {
                                        Text(text = localizedStringResource(505, "Invite worker"), color = stateValues.TextColor,
                                fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

                                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                        SimpleTextInput(
                                            modifier = Modifier.fillMaxWidth(),
                                            value = invitedUserId,
                                            placeholder = localizedStringResource(506, "Enter user public ID"),
                                            leadingIconPath = stateValues.drawablePathIconPerson,
                                            stateHost = NavigationScreenModel.Menu.Workers,
                                            stateKey = "menu_workers_invited_user_id",
                                            onValueChange = { invitedUserId = it.trim().uppercase() }
                                        )

                                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                        SimpleTextInput(
                                            modifier = Modifier.fillMaxWidth(),
                                            value = jobTitle,
                                            placeholder = localizedStringResource(1432, "Job title"),
                                            leadingIconPath = stateValues.drawablePathIconPerson,
                                            stateHost = NavigationScreenModel.Menu.Workers,
                                            stateKey = "menu_workers_invite_job_title",
                                            onValueChange = { jobTitle = it.take(120) }
                                        )

                                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                        SimpleTextInput(
                                            modifier = Modifier.fillMaxWidth(),
                                            value = salary,
                                            placeholder = localizedStringResource(1433, "Salary"),
                                            keyboardType = KeyboardType.Decimal,
                                            leadingIconPath = stateValues.drawablePathIconFinances,
                                            stateHost = NavigationScreenModel.Menu.Workers,
                                            stateKey = "menu_workers_invite_salary",
                                            onTransformValue = ::normalizeWorkerSalaryInput,
                                            onValueChange = { salary = normalizeWorkerSalaryInput(it) }
                                        )

                                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                        SimpleTextInput(
                                            modifier = Modifier.fillMaxWidth(),
                                            value = offerNote,
                                            placeholder = localizedStringResource(1449, "Offer note"),
                                            singleLine = false,
                                            leadingIconPath = stateValues.drawablePathIconResponse,
                                            stateHost = NavigationScreenModel.Menu.Workers,
                                            stateKey = "menu_workers_invite_offer_note",
                                            onValueChange = { offerNote = it.take(240) }
                                        )

                                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                        WorkerOfferDetails(
                                            roleId = roleId,
                                            permissions = permissions,
                                            jobTitle = jobTitle,
                                            salary = salary,
                                            salaryCurrencyCode = salaryCurrencyCode,
                                            showPermissionPreview = false,
                                            offerNote = offerNote
                                        )

                                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                        SimpleDropdownField(
                                            modifier = Modifier.fillMaxWidth(),
                                            title = localizedStringResource(466, "Role"),
                                            selectedId = roleId,
                                            options = workerRoleOptions(roleTemplates),
                                            placeholder = workerRoleLabel(WORKER_ROLE_STANDARD),
                                            onSelected = { selectedRole ->
                                                roleId = selectedRole
                                                permissionsText = defaultInvitePermissionsForRole(selectedRole).joinToString("|")
                                            }
                                        )

                                        ResetPermissionsText(
                                            roleId = roleId,
                                            assignablePermissions = assignablePermissions,
                                            templates = roleTemplates,
                                            onReset = { permissionsText = it.joinToString("|") }
                                        )

                                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                        WorkerPermissionEditor(
                                            permissions = permissions,
                                            availablePermissions = assignablePermissions,
                                            onChanged = { permissionsText = it.distinct().joinToString("|") }
                                        )

                                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                        Text(
                                            text = localizedStringResource(1087, "Worker will set their shift password after accepting the invite."),
                                            color = stateValues.PlaceholderTextColor,
                                            fontSize = stateValues.smallTextSize
                                        )

                                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                                        actionButton(
                                            text = localizedStringResource(507, "Send invite"),
                                            enabled = invitedUserId.isNotBlank(),
                                            iconPath = stateValues.drawablePathIconCheck,
                                            confirmationRequired = false,
                                            onClick = {
                                                inviteStoreWorker(
                                                    storeId = activeStoreId,
                                                    userId = invitedUserId,
                                                    roleId = roleId,
                                                    permissions = permissions,
                                                    jobTitle = jobTitle,
                                                    salary = salary,
                                                    salaryCurrencyCode = salaryCurrencyCode,
                                                    note = offerNote
                                                ) { result ->
                                                    if (result is DataState.Success) {
                                                        invitedUserId = ""
                                                        jobTitle = ""
                                                        salary = ""
                                                        offerNote = ""
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (section == "workers") {
                            val workers = storeWorkersPayload.orEmpty()
                            if (workers.isEmpty()) {
                                item(key = "MenuWorkersScreen:$section:18") { MessageText(modifier = Modifier.fillMaxWidth().remainingListSpace(workerListState, "MenuWorkersScreen:$section:18"), text = localizedStringResource(484, "No workers in this store yet")) }
                            } else {
                                val editable = currentUserCanEditWorkerPermissions(activeStoreId)
                                val removable = currentUserCanRemoveWorkers(activeStoreId)
                                val assignablePermissions = currentUserAssignableStorePermissions(activeStoreId)
                                val pendingRemovalRequests = incomingRequestsPayload.orEmpty()
                                    .filter { it.isPendingWorkerRemovalRequest() }
                                    .distinctBy { it.id }
                                items(workers, key = { it.id }) { worker ->
                                    val pendingRemovalRequest = pendingRemovalRequests.firstOrNull { request ->
                                        request.storeId == worker.storeId && request.requesterUserId == worker.userId
                                    }
                                    val canEditWorker = editable && worker.permissions.all { it in assignablePermissions }
                                    WorkerMembershipCard(
                                        worker = worker,
                                        editable = canEditWorker,
                                        storeId = worker.storeId.ifBlank { activeStoreId },
                                        pendingRemovalRequest = pendingRemovalRequest,
                                        canRemove = removable
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    item(key = "MenuWorkersScreen:$section:19") {
                        Text(text = localizedStringResource(658, "Requests to this store or branch"), color = stateValues.TextColor,
                                fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }

                    if (activeStoreId == null) {
                        item(key = "MenuWorkersScreen:$section:20") { MessageText(modifier = Modifier.fillMaxWidth().remainingListSpace(workerListState, "MenuWorkersScreen:$section:20"), text = stateValues.stringNoActiveStore) }
                    } else if (!currentUserCanDecideWorkerRequests(activeStoreId)) {
                        item(key = "MenuWorkersScreen:$section:21") { MessageText(modifier = Modifier.fillMaxWidth().remainingListSpace(workerListState, "MenuWorkersScreen:$section:21"), text = localizedStringResource(486, "Only store owners and permitted worker managers can accept employment requests")) }
                    } else {
                        val pendingRequests = incomingRequestsPayload.orEmpty().filter { it.direction == WORKER_REQUEST_DIRECTION_USER_TO_STORE && it.status == WORKER_REQUEST_STATUS_PENDING }
                        if (pendingRequests.isEmpty()) {
                            item(key = "MenuWorkersScreen:$section:22") { MessageText(modifier = Modifier.fillMaxWidth().remainingListSpace(workerListState, "MenuWorkersScreen:$section:22"), text = localizedStringResource(487, "No incoming employment requests")) }
                        } else {
                            items(pendingRequests, key = { it.id }) { request ->
                                WorkerRequestCard(storeId = activeStoreId, request = request)
                            }
                        }

                    }
                }
            }
        }
    }
}

internal fun AppConfiguration.workerRequestStatusLabel(status: String): String {
    return when (status) {
        WORKER_REQUEST_STATUS_ACCEPTED -> localizedStringResource(470, "Accepted")
        WORKER_REQUEST_STATUS_DECLINED -> localizedStringResource(492, "Declined")
        WORKER_REQUEST_STATUS_INVITED -> localizedStringResource(508, "Invited")
        else -> localizedStringResource(488, "Pending")
    }
}

internal fun AppConfiguration.workerRequestStatusLabel(request: StoreWorkerRequestDataModel): String {
    return if (request.isWorkerRemovalRequest()) {
        when (request.status) {
            WORKER_REQUEST_STATUS_ACCEPTED -> localizedStringResource(1222, "Removal confirmed")
            WORKER_REQUEST_STATUS_DECLINED -> localizedStringResource(1223, "Worker kept access")
            else -> localizedStringResource(1224, "Waiting for worker")
        }
    } else {
        workerRequestStatusLabel(request.status)
    }
}


@Composable
internal fun AppConfiguration.WorkerInviteStatusCard(
    request: StoreWorkerRequestDataModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Text(
            text = request.displayName,
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${localizedStringResource(482, "Status")}: ${workerRequestStatusLabel(request)}",
            color = stateValues.TextColor,
            fontSize = stateValues.textSize
        )
        Text(
            text = listOf(request.requesterPublicId, request.phoneNumber.asDisplayPhoneNumber(), request.email).filter { it.isNotBlank() }.joinToString(" • "),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )
        Text(
            text = receiptUiDateTime(request.requestedAtMillis),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )
    }
}

@Composable
fun AppConfiguration.MenuUserAccountScreen() {
    var logoutConfirmationShown by rememberSaveable { mutableStateOf(false) }

    AitaScreenColumn(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringUserAccount,
                iconPath = stateValues.drawablePathIconUserAccount,
                trailingIcons = listOf(
                    Triple(
                        stateValues.drawablePathIconSecurity,
                        stateValues.drawableResIconSecurity.value
                    ) {
                        coroutineScope.launch {
                            Navigation.Menu.go(NavigationScreenModel.Menu.Security, stateValues.isNarrowScreen)
                        }
                    },
                    Triple(
                        stateValues.drawablePathIconExit,
                        stateValues.drawableResIconExit.value
                    ) {
                        logoutConfirmationShown = true
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
        }
    ) {
        if (logoutConfirmationShown) {
            ModalDialogWidget(
                title = localizedStringResource(719, "Log out?"),
                subTitle = localizedStringResource(720, "You will leave this account on this device."),
                negativeButtonText = stateValues.stringCancel,
                positiveButtonText = stateValues.stringQuit,
                onDismiss = { logoutConfirmationShown = false },
                negativeAction = { logoutConfirmationShown = false },
                positiveAction = {
                    logoutConfirmationShown = false
                    logOutUser()
                }
            )
        }

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.UserAccount),
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .padding(horizontal = stateValues.marginTextField, vertical = 24.dp)
        ) {
            item(key = "account-profile-photo") { UserProfilePhotoCard() }
            item {
                val outerSpace = 16.dp
                val innerSpace = 8.dp

                val phoneNumberTextFieldContent = countrySelectionPhoneNumberTextField(
                    valueInitial = stateValues.userAccount?.phoneNumber,
                    stateHost = NavigationScreenModel.Menu.UserAccount,
                    stateKey = NavigationScreenModel.KEY_STATE_PHONE_NUMBER,
                    lockedId = stateValues.globalAppConfiguration.countries
                        .find { it.locale.equals(stateValues.userAccount?.countryLocale, true) }
                        ?.phoneNumberCode
                        ?: stateValues.globalAppConfiguration.countries
                            .find { stateValues.userAccount?.phoneNumber?.removePrefix("+")?.startsWith(it.phoneNumberCode) == true }
                            ?.phoneNumberCode
                )

                Spacer(modifier = Modifier.height(innerSpace))

                val emailTextFieldContent = emailTextField(
                    valueInitial = stateValues.userAccount?.email,
                    stateHost = NavigationScreenModel.Menu.UserAccount,
                    stateKey = NavigationScreenModel.KEY_STATE_EMAIL
                )
                val accountEmailConfirmation = rememberContactEmailConfirmation(
                    kz.aita.auth.AitaContactPurpose.ACCOUNT_CONTACT, stateValues.userAccount?.id.orEmpty(),
                    listOf(emailTextFieldContent.value.text), listOf(stateValues.userAccount?.email.orEmpty()))
                ContactEmailConfirmationContent(accountEmailConfirmation)

                Spacer(modifier = Modifier.height(innerSpace))

                val firstNameTextFieldContent = genericTextField(
                    valueInitial = stateValues.userAccount?.firstName,
                    stateHost = NavigationScreenModel.Menu.UserAccount,
                    stateKey = NavigationScreenModel.KEY_STATE_FIRST_NAME,
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

                val (passwordTextFieldContent: GenericTextFieldContent?, repeatedPasswordTextFieldContent: GenericTextFieldContent?)  = repeatedPasswordTextFieldGroup(
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

                val confirmationPasswordTextFieldContent: GenericTextFieldContent? = passwordTextField(
                    titleText = stateValues.stringConfirmationPassword,
                    placeholderText = stateValues.stringRequiredToEditAccount,
                    contentInvalidText = stateValues.stringRequiredToEditAccount + ". \n" + stateValues.stringPasswordMustBe,
                    imeWithAction = ImeWithAction(ImeAction.Go) {
                        goAction?.invoke()
                    },
                    stateHost = NavigationScreenModel.Menu.UserAccount,
                    stateKey = NavigationScreenModel.Menu.UserAccount.KEY_STATE_CONFIRMATION_PASSWORD
                )

                val profilePhone = stateValues.globalAppConfiguration.countries.run {
                    find { it.locale.equals(phoneNumberTextFieldContent.selectedId, true) } ?: first()
                }.phoneNumberCode + phoneNumberTextFieldContent.value.text.trim()
                val profileEmail = emailTextFieldContent.value.text.trim()
                val protectedProfileChange = kz.aita.auth.normalizeAitaPhoneAlias(profilePhone) !=
                    stateValues.userAccount?.let { kz.aita.auth.normalizeAitaStoredMainPhone(it.phoneNumber, it.countryLocale) } ||
                    kz.aita.auth.normalizeAitaEmail(profileEmail) != kz.aita.auth.normalizeAitaEmail(stateValues.userAccount?.email.orEmpty()) ||
                    passwordTextFieldContent?.value?.text?.isNotEmpty() == true
                val profileConfirmation = if (protectedProfileChange) ProfileSecurityConfirmationInput(
                    kz.aita.auth.aitaProfileSecurityTarget(profilePhone, profileEmail, true),
                    confirmationPasswordTextFieldContent?.value?.text.orEmpty()) else ProfileSecurityConfirmation(true)

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

                    if (passwordTextFieldContent!!.value.text.isNotEmpty())
                        passwordTextFieldContent.checkContentValidity()

                    if (passwordTextFieldContent.value.text.isNotEmpty())
                        repeatedPasswordTextFieldContent!!.checkContentValidity()

                    confirmationPasswordTextFieldContent!!.checkContentValidity()

                    if (
                        phoneNumberTextFieldContent.isContentValid
                        && emailTextFieldContent.isContentValid
                        && firstNameTextFieldContent.isContentValid
                        && lastNameTextFieldContent.isContentValid
                        && (passwordTextFieldContent.value.text.isEmpty() || passwordTextFieldContent.isContentValid)
                        && (passwordTextFieldContent.value.text.isEmpty() || repeatedPasswordTextFieldContent!!.isContentValid)
                        && confirmationPasswordTextFieldContent.isContentValid
                        && profileConfirmation.ready && accountEmailConfirmation.ready
                    ) {
                        updateUser(
                            userAccountUpdate = UserAccountUpdateDataModel(
                                account = UserAccountDataModel(
                                    id = stateValues.userAccount?.id.orEmpty(),
                                    publicId = stateValues.userAccount?.publicId.orEmpty(),
                                    phoneNumber = stateValues.globalAppConfiguration.countries.run {
                                        find { it.locale.equals(phoneNumberTextFieldContent.selectedId, true) } ?: first()
                                    }.phoneNumberCode.lowercase() + phoneNumberTextFieldContent.value.text.trim().lowercase(),
                                    email = emailTextFieldContent.value.text.trim().lowercase(),
                                    firstName = firstNameTextFieldContent.value.text.trim(),
                                    lastName = lastNameTextFieldContent.value.text.trim(),
                                    countryLocale = phoneNumberTextFieldContent.selectedId,
                                    workerAccountIds = stateValues.userAccount?.workerAccountIds,
                                    supplierAccountIds = stateValues.userAccount?.supplierAccountIds,
                                    activeStoreId = stateValues.userAccount?.activeStoreId,
                                    appLanguage = stateValues.appLanguagePreference,
                                    appThemeId = stateValues.appThemeId,
                                    appSizeModeId = stateValues.appSizeModeId,
                                    createdAt = 0L,
                                    isActive = true
                                ),
                                password = confirmationPasswordTextFieldContent!!.value.text,
                                newPassword = passwordTextFieldContent!!.takeIf { it.value.text.isNotEmpty() }?.value?.text,
                                secondFactorCode = profileConfirmation.factor,
                                emailProof = profileConfirmation.emailProof,
                                contactEmailProofs = accountEmailConfirmation.proofs
                            )
                        )

                        confirmationPasswordTextFieldContent.reset()
                        passwordTextFieldContent.reset()
                        repeatedPasswordTextFieldContent?.reset()
                    }
                }

                actionButton(
                    text = stateValues.stringEdit,
                    enabled = stateValues.latestNotification == null && accountEmailConfirmation.ready && profileConfirmation.ready
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

internal const val TRANSACTION_HISTORY_DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

internal fun transactionHistoryServerTypeIndex(type: String): Int {
    return when (type.lowercase()) {
        "purchase", "sale" -> 0
        "return" -> 1
        else -> 2
    }
}

internal fun AppConfiguration.transactionHistoryTypeTitle(type: String): String {
    return when (transactionHistoryServerTypeIndex(type)) {
        0 -> stateValues.stringSale
        1 -> stateValues.stringReturn
        else -> stateValues.stringSupply
    }
}

internal fun AppConfiguration.transactionHistoryDefaultCurrencyCode(): String {
    return stateValues.globalAppConfiguration.countries
        .withTajikistanFallback()
        .find { it.locale.equals(stateValues.userAccount?.countryLocale, true) }
        ?.currencies
        ?.firstOrNull()
        ?.code
        ?: stateValues.globalAppConfiguration.countries
            .firstOrNull()
            ?.currencies
            ?.firstOrNull()
            ?.code
        ?: "KZT"
}

internal fun AppConfiguration.transactionHistoryCurrencySymbol(currencyCode: String): String {
    return stateValues.globalAppConfiguration.countries.getCurrency(currencyCode)?.symbol ?: currencyCode
}

internal fun AppConfiguration.transactionHistoryFindGoodsItem(line: GoodsItemInTransactionDataModel): GoodsItemDataModel? {
    val stock = stateValues.stock.orEmpty()
    line.goodsItemId?.takeIf { it.isNotBlank() }?.let { id ->
        stock.firstOrNull { it.id == id }?.let { return it }
    }

    val barcode = line.barcode.trim()
    if (barcode.isBlank()) return null

    return stock.firstOrNull { item ->
        item.matchesScannedBarcode(barcode) || item.allBarcodeValues().any { storedBarcode ->
            storedBarcodeMatchesScannedTransactionBarcode(storedBarcode, barcode)
        }
    }
}

internal fun AppConfiguration.transactionHistoryLineDisplayName(line: GoodsItemInTransactionDataModel): String {
    val goodsItem = transactionHistoryFindGoodsItem(line)
    return line.name.visibleLocalizedString(stateValues.appLanguage, "")
        .takeIf { it.isNotBlank() }
        ?: goodsItem?.name?.visibleLocalizedString(stateValues.appLanguage, "")
            ?.takeIf { it.isNotBlank() }
        ?: line.barcode.takeIf { it.isNotBlank() }
        ?: localizedStringResource(176, "No name")
}

internal fun AppConfiguration.transactionHistoryLineCurrencyCode(
    transaction: TransactionDataModel,
    line: GoodsItemInTransactionDataModel,
    goodsItem: GoodsItemDataModel?
): String {
    line.currencyCode?.takeIf { it.isNotBlank() }?.let { return it }

    val transactionTypeIndex = transactionHistoryServerTypeIndex(transaction.type)
    return goodsItem
        ?.priceForTransaction(
            transactionTypeIndex = transactionTypeIndex,
            saleMethodId = line.saleMethodId,
            quantityTotal = line.quantity
        )
        ?.currency
        ?.takeIf { it.isNotBlank() }
        ?: transaction.debtor?.currency?.takeIf { it.isNotBlank() }
        ?: transactionHistoryDefaultCurrencyCode()
}

internal fun AppConfiguration.transactionHistoryFallbackQuantity(line: GoodsItemInTransactionDataModel): QuantityDataModel {
    val fallback = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.firstOrNull()
        ?: QuantityDataModel(
            id = "unit",
            immutableUnitName = listOf(LocalizedStringDataModel("main", "unit"), LocalizedStringDataModel("ky", "бирдик")),
            total = 1.0,
            pricedAmount = 1.0,
            roundTotal = false
        )

    return fallback.withTotalValue(line.quantity)
}

internal fun AppConfiguration.transactionHistoryReceiptSnapshot(transaction: TransactionDataModel): TransactionReceiptSnapshotDataModel {
    val transactionTypeIndex = transactionHistoryServerTypeIndex(transaction.type)
    val store = stateValues.stores.findStoreOrBranchForUi(transaction.storeId)
        ?: stateValues.stores.findStoreOrBranchForUi(stateValues.activeStoreId)

    val lines = transaction.goodsInTransaction.mapIndexed { index, sourceLine ->
        val goodsItem = transactionHistoryFindGoodsItem(sourceLine)
        val quantity = sourceLine.quantityUnit
            ?.withTotalValue(sourceLine.quantity)
            ?: goodsItem
                ?.defaultCartQuantity(stateValues.globalAppConfiguration)
                ?.withTotalValue(sourceLine.quantity)
            ?: transactionHistoryFallbackQuantity(sourceLine)
        val currencyCode = sourceLine.currencyCode?.takeIf { it.isNotBlank() }
            ?: transactionHistoryLineCurrencyCode(transaction, sourceLine, goodsItem)

        val lineName = sourceLine.name.takeIf { it.isNotEmpty() }
            ?: goodsItem?.name
            ?: transactionHistoryLineDisplayName(sourceLine).toLocalizedSingleMain()

        TransactionReceiptLineDataModel(
            index = index,
            goodsItemId = sourceLine.goodsItemId?.takeIf { it.isNotBlank() } ?: goodsItem?.id.orEmpty(),
            name = lineName,
            barcode = sourceLine.barcode,
            quantity = quantity,
            pricePerUnit = sourceLine.pricePerUnit.roundMoney(),
            currencyCode = currencyCode,
            currencySymbol = transactionHistoryCurrencySymbol(currencyCode),
            saleMethodId = sourceLine.saleMethodId,
            saleMethodName = saleMethodLocalizedName(sourceLine.saleMethodId)
        )
    }

    val currencyCode = lines.firstOrNull()?.currencyCode
        ?: transaction.debtor?.currency?.takeIf { it.isNotBlank() }
        ?: transactionHistoryDefaultCurrencyCode()
    val currencySymbol = transactionHistoryCurrencySymbol(currencyCode)
    val total = lines.sumOf { it.total }.roundMoney()
    val debtAmount = (total - transaction.paidCash - transaction.paidCard).coerceAtLeast(0.0).roundMoney()
    val debtorForReceipt = transaction.debtor?.copy(
        debtAmount = debtAmount,
        currency = currencyCode
    )
    val paymentModeId = when {
        debtAmount > 0.0 -> "2"
        transaction.paidCash > 0.0 && transaction.paidCard > 0.0 -> "2"
        transaction.paidCash > 0.0 -> "0"
        else -> "1"
    }

    return TransactionReceiptSnapshotDataModel(
        transaction = transaction.copy(debtor = debtorForReceipt),
        store = store,
        lines = lines,
        paymentDraft = TransactionPaymentDraftDataModel(
            transactionTypeIndex = transactionTypeIndex,
            clientId = 0,
            paymentModeId = paymentModeId,
            paidCash = transaction.paidCash,
            paidCard = transaction.paidCard,
            cardPaymentOptionId = transaction.cardPaymentOptionId,
            debtor = debtorForReceipt
        ),
        currencyCode = currencyCode,
        currencySymbol = currencySymbol,
        cashierName = "${stateValues.userAccount?.firstName.orEmpty()} ${stateValues.userAccount?.lastName.orEmpty()}".trim(),
        cashierPhoneNumber = stateValues.userAccount?.phoneNumber.orEmpty(),
        cashierEmail = stateValues.userAccount?.email.orEmpty()
    )
}

internal fun AppConfiguration.transactionHistoryTotal(transaction: TransactionDataModel): Double {
    return transaction.goodsInTransaction.sumOf { (it.quantity * it.pricePerUnit) }.roundMoney()
}

internal fun AppConfiguration.transactionHistoryDebtAmount(transaction: TransactionDataModel): Double {
    return (transactionHistoryTotal(transaction) - transaction.paidCash - transaction.paidCard).coerceAtLeast(0.0).roundMoney()
}

internal fun AppConfiguration.transactionHistoryPaymentSummary(transaction: TransactionDataModel, currencySymbol: String): String {
    val parts = mutableListOf<String>()
    if (transaction.paidCash > 0.0) parts.add("${localizedStringResource(420, "Paid by cash")}: ${transaction.paidCash.moneyText()} $currencySymbol")
    if (transaction.paidCard > 0.0) parts.add("${localizedStringResource(421, "Paid cashless")}: ${transaction.paidCard.moneyText()} $currencySymbol")
    val debt = transactionHistoryDebtAmount(transaction)
    if (debt > 0.0) parts.add("${localizedStringResource(422, "Debt part")}: ${debt.moneyText()} $currencySymbol")
    return parts.ifEmpty { listOf("${localizedStringResource(393, "Payment details")}: 0 $currencySymbol") }.joinToString(" • ")
}

internal fun transactionHistoryLocalDateFromMillis(timeMillis: Long): LocalDate {
    return Instant
        .fromEpochMilliseconds(timeMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

internal fun transactionHistoryPlusDays(date: LocalDate, days: Int): LocalDate {
    return Instant
        .fromEpochMilliseconds(date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + days.toLong() * TRANSACTION_HISTORY_DAY_MILLIS)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

internal fun transactionHistoryShiftMonth(date: LocalDate, delta: Int): LocalDate {
    val monthIndex = (date.year * 12 + (date.monthNumber - 1)) + delta
    val year = monthIndex / 12
    val month = monthIndex % 12 + 1
    return LocalDate(year, month, date.dayOfMonth.coerceAtMost(stockDaysInMonth(year, month)))
}

internal fun transactionHistoryIsoWeekday(year: Int, month: Int, day: Int): Int {
    var adjustedYear = year
    var adjustedMonth = month
    if (adjustedMonth < 3) {
        adjustedMonth += 12
        adjustedYear -= 1
    }

    val k = adjustedYear % 100
    val j = adjustedYear / 100
    val h = (day + (13 * (adjustedMonth + 1)) / 5 + k + k / 4 + j / 4 + 5 * j) % 7

    return when (h) {
        0 -> 6
        1 -> 7
        else -> h - 1
    }
}

internal fun transactionHistoryPresetDates(presetId: String): Pair<String, String> {
    val today = currentStockLocalDate()
    return when (presetId) {
        "today" -> today.toStockDateInputText() to today.toStockDateInputText()
        "7" -> transactionHistoryPlusDays(today, -6).toStockDateInputText() to today.toStockDateInputText()
        "30" -> transactionHistoryPlusDays(today, -29).toStockDateInputText() to today.toStockDateInputText()
        "month" -> LocalDate(today.year, today.monthNumber, 1).toStockDateInputText() to today.toStockDateInputText()
        "year" -> LocalDate(today.year, 1, 1).toStockDateInputText() to today.toStockDateInputText()
        else -> "" to ""
    }
}

internal fun AppConfiguration.transactionHistoryMatchesSearch(transaction: TransactionDataModel, query: String): Boolean {
    val clean = query.trim()
    if (clean.isBlank()) return true

    val typeTitle = transactionHistoryTypeTitle(transaction.type)
    val itemNames = transaction.goodsInTransaction.joinToString(" ") { line ->
        "${transactionHistoryLineDisplayName(line)} ${line.barcode}"
    }
    val debtorText = transaction.debtor?.displayName.orEmpty()

    return listOf(
        transaction.id,
        transaction.receiptNumberTextFallback(),
        typeTitle,
        itemNames,
        debtorText,
        receiptUiDateTime(transaction.timeMillis)
    ).any { it.contains(clean, ignoreCase = true) }
}

internal fun TransactionDataModel.receiptNumberTextFallback(): String {
    return id.take(8).uppercase()
}

@Composable
internal fun AppConfiguration.TransactionHistoryFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(stateValues.textFieldHeight)
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = selected)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(if (selected) stateValues.AccentColor else stateValues.BackgroundColor)
            .border(
                if (selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = if (selected) stateValues.AccentTextColor else stateValues.AccentColor),
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AppConfiguration.TransactionHistoryDateButton(
    modifier: Modifier = Modifier,
    title: String,
    dateText: String,
    onClick: () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(stateValues.textFieldHeight)
                .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.BackgroundColor)
                .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                .aitaClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = stateValues.AccentColor),
                    onClick = onClick
                )
                .padding(horizontal = stateValues.marginTextFieldGroup),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dateText.ifBlank { localizedStringResource(981, "Select date") },
                color = if (dateText.isBlank()) stateValues.PlaceholderTextColor else stateValues.TextColor,
                fontSize = stateValues.textSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            CpImage(
                modifier = Modifier.size(stateValues.iconSize),
                url = stateValues.drawablePathIconTransactionHistory,
                fallbackRes = stateValues.drawableResIconTransactionHistory.value,
                contentDescription = title,
                tintColor = stateValues.TextColor
            )
        }
    }
}

@Composable
internal fun AppConfiguration.TransactionHistoryCalendarDialog(
    title: String,
    selectedDateText: String,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val selectedDate = stockDateInputTextToLocalDate(selectedDateText) ?: currentStockLocalDate()
    var visibleMonth by rememberSaveable(selectedDateText) {
        mutableStateOf(LocalDate(selectedDate.year, selectedDate.monthNumber, 1).toStockDateInputText())
    }
    val visibleMonthDate = stockDateInputTextToLocalDate(visibleMonth) ?: currentStockLocalDate()
    var showMonthYearPicker by rememberSaveable(selectedDateText) { mutableStateOf(false) }
    var yearPickerStart by rememberSaveable(selectedDateText) {
        mutableStateOf((visibleMonthDate.year - 5).coerceIn(1970, 2489))
    }

    LaunchedEffect(visibleMonthDate.year) {
        if (visibleMonthDate.year !in yearPickerStart..(yearPickerStart + 11)) {
            yearPickerStart = (visibleMonthDate.year - 5).coerceIn(1970, 2489)
        }
    }

    val daysInMonth = stockDaysInMonth(visibleMonthDate.year, visibleMonthDate.monthNumber)
    val leadingEmptyDays = transactionHistoryIsoWeekday(visibleMonthDate.year, visibleMonthDate.monthNumber, 1) - 1
    val monthTitle = "${visibleMonthDate.monthNumber.toString().padStart(2, '0')}.${visibleMonthDate.year}"
    val weekDays = listOf(411L, 412L, 413L, 414L, 415L, 416L, 417L)

    Dialog(onDismissRequest = onDismiss) {
        TransactionBarcodeModalGuard()
        Column(
            modifier = Modifier
                .aitaDialogEntrance()
                .fillMaxWidth()
                .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.BackgroundColor)
                .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                .padding(stateValues.marginTextFieldGroup)
        ) {
            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold
            )

            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = localizedStringResource(405, "Tap a day to set the date"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actionButton(
                    text = "", icon = { Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Text("‹", color = stateValues.AccentTextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold) } }, iconContentDescription = localizedStringResource(577, "Previous"),
                    iconPath = null,
                    confirmationRequired = false,
                    onClick = {
                        visibleMonth = transactionHistoryShiftMonth(visibleMonthDate, -1).let { LocalDate(it.year, it.monthNumber, 1) }.toStockDateInputText()
                    }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(stateValues.textFieldHeight)
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = showMonthYearPicker)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(if (showMonthYearPicker) stateValues.AccentColor else stateValues.BackgroundColor)
                        .border(
                            if (showMonthYearPicker) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                            if (showMonthYearPicker) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                            RoundedCornerShape(stateValues.cornerRadius)
                        )
                        .aitaClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = if (showMonthYearPicker) stateValues.AccentTextColor else stateValues.AccentColor),
                            onClick = { showMonthYearPicker = !showMonthYearPicker }
                        )
                        .padding(horizontal = stateValues.marginTextFieldGroup),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = monthTitle,
                        color = if (showMonthYearPicker) stateValues.AccentTextColor else stateValues.TextColor,
                        fontSize = stateValues.accentTextSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                actionButton(
                    text = "", icon = { Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Text("›", color = stateValues.AccentTextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold) } }, iconContentDescription = localizedStringResource(579, "Next"),
                    iconPath = null,
                    confirmationRequired = false,
                    onClick = {
                        visibleMonth = transactionHistoryShiftMonth(visibleMonthDate, 1).let { LocalDate(it.year, it.monthNumber, 1) }.toStockDateInputText()
                    }
                )
            }

            AnimatedVisibility(visible = showMonthYearPicker) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                    Text(
                        text = localizedStringResource(369, "Month"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items((1..12).toList()) { month ->
                            TransactionHistoryFilterChip(
                                text = month.toString().padStart(2, '0'),
                                selected = month == visibleMonthDate.monthNumber,
                                onClick = {
                                    visibleMonth = LocalDate(visibleMonthDate.year, month, 1).toStockDateInputText()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = localizedStringResource(370, "Year"),
                            color = stateValues.TextColor,
                            fontSize = stateValues.smallTextSize,
                            fontWeight = FontWeight.Bold
                        )

                        actionButton(
                            text = "", icon = { Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Text("‹", color = stateValues.AccentTextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold) } }, iconContentDescription = localizedStringResource(577, "Previous"),
                            iconPath = null,
                            confirmationRequired = false,
                            onClick = { yearPickerStart = (yearPickerStart - 12).coerceAtLeast(1970) }
                        )

                        actionButton(
                            text = "", icon = { Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Text("›", color = stateValues.AccentTextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold) } }, iconContentDescription = localizedStringResource(579, "Next"),
                            iconPath = null,
                            confirmationRequired = false,
                            onClick = { yearPickerStart = (yearPickerStart + 12).coerceAtMost(2489) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items((yearPickerStart..(yearPickerStart + 11).coerceAtMost(2500)).toList()) { year ->
                            TransactionHistoryFilterChip(
                                text = year.toString(),
                                selected = year == visibleMonthDate.year,
                                onClick = {
                                    visibleMonth = LocalDate(year, visibleMonthDate.monthNumber, 1).toStockDateInputText()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                weekDays.forEach { id ->
                    Text(
                        modifier = Modifier.weight(1f),
                        text = localizedStringResource(id, ""),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val totalCells = leadingEmptyDays + daysInMonth
            val rows = ((totalCells + 6) / 7).coerceAtLeast(1)
            repeat(rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(7) { column ->
                        val cellIndex = row * 7 + column
                        val day = cellIndex - leadingEmptyDays + 1
                        val enabled = day in 1..daysInMonth
                        val date = if (enabled) LocalDate(visibleMonthDate.year, visibleMonthDate.monthNumber, day) else null
                        val dateText = date?.toStockDateInputText().orEmpty()
                        val selected = dateText == selectedDateText

                        if (!enabled) {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(stateValues.textFieldHeight)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(stateValues.textFieldHeight)
                                    .foregroundSubtleShadow(stateValues.cornerRadius)
                                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                                    .background(if (selected) stateValues.AccentColor else stateValues.BackgroundColor)
                                    .border(
                                        stateValues.unfocusedBorderWidth,
                                        if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                                        RoundedCornerShape(stateValues.cornerRadius)
                                    )
                                    .aitaClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(color = if (selected) stateValues.AccentTextColor else stateValues.AccentColor),
                                        onClick = {
                                            onDateSelected(dateText)
                                            onDismiss()
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.toString(),
                                    color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
                                    fontSize = stateValues.textSize,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabledColor = stateValues.DisabledColor,
                    text = stateValues.stringCancel,
                    confirmationRequired = false,
                    onClick = onDismiss
                )

                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(260, "Today"),
                    confirmationRequired = false,
                    onClick = {
                        onDateSelected(currentStockLocalDate().toStockDateInputText())
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.TransactionHistoryCard(
    transaction: TransactionDataModel,
    onClick: () -> Unit
) {
    val total = transactionHistoryTotal(transaction)
    val currencyCode = transaction.debtor?.currency?.takeIf { it.isNotBlank() }
        ?: transaction.goodsInTransaction.firstOrNull()?.let { line ->
            transactionHistoryLineCurrencyCode(transaction, line, transactionHistoryFindGoodsItem(line))
        }
        ?: transactionHistoryDefaultCurrencyCode()
    val currencySymbol = transactionHistoryCurrencySymbol(currencyCode)
    val typeTitle = transactionHistoryTypeTitle(transaction.type)
    val linePreview = transaction.goodsInTransaction
        .take(3)
        .joinToString(" • ") { line ->
            val quantityText = transactionHistoryFindGoodsItem(line)
                ?.defaultCartQuantity(stateValues.globalAppConfiguration)
                ?.withTotalValue(line.quantity)
                ?.quantityText(stateValues.appLanguage)
                ?: line.quantity.quantityAmountText(roundTotal = false)
            "${transactionHistoryLineDisplayName(line)} × $quantityText"
        }
    val moreCount = (transaction.goodsInTransaction.size - 3).coerceAtLeast(0)
    val itemsText = buildString {
        append("${localizedStringResource(392, "Items")}: ${transaction.goodsInTransaction.size}")
        if (linePreview.isNotBlank()) append(" • $linePreview")
        if (moreCount > 0) append(" • +$moreCount")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.AccentColor),
                onClick = onClick
            )
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$typeTitle • ${transaction.receiptNumberTextFallback()}",
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = receiptUiDateTime(transaction.timeMillis),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${total.moneyText()} $currencySymbol",
                color = stateValues.AccentColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFamily = LocalAitaFontFamily.current, shadow = accentTextShadow(stateValues.AccentColor, stateValues.AccentColor)),
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = itemsText,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = transactionHistoryPaymentSummary(transaction, currencySymbol),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        transaction.debtor?.displayName?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${stateValues.stringDebtor}: $it",
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StorePersonLink(transaction.storeId,transaction.actorUserId)
    }
}

@Composable
fun AppConfiguration.MenuTransactionHistoryScreen() {
    AitaScreenColumn(
        modifier = Modifier
            .fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringTransactionHistory,
                iconPath = stateValues.drawablePathIconTransactionHistory,
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        val transactionsPayload by transactionsState.payload.collectAsState()
        val activeStoreId = stateValues.activeStoreId

        LaunchedEffect(activeStoreId) {
            activeStoreId?.let { getTransactions(it) }
        }

        var periodPresetId by rememberSaveable { mutableStateOf("all") }
        val initialPeriod = remember { transactionHistoryPresetDates("all") }
        var startDateText by rememberSaveable { mutableStateOf(initialPeriod.first) }
        var endDateText by rememberSaveable { mutableStateOf(initialPeriod.second) }
        var transactionTypeFilter by rememberSaveable { mutableStateOf("all") }
        var sortId by rememberSaveable { mutableStateOf("newest") }
        var searchQuery by rememberSaveable { mutableStateOf("") }
        var calendarTarget by rememberSaveable { mutableStateOf<String?>(null) }

        calendarTarget?.let { target ->
            TransactionHistoryCalendarDialog(
                title = if (target == "start") localizedStringResource(403, "Pick start date") else localizedStringResource(404, "Pick end date"),
                selectedDateText = if (target == "start") startDateText else endDateText,
                onDismiss = { calendarTarget = null },
                onDateSelected = { selectedDate ->
                    periodPresetId = "custom"
                    if (target == "start") {
                        startDateText = selectedDate
                        val startMillis = stockDateInputTextToMillis(selectedDate)
                        val endMillis = stockDateInputTextToMillis(endDateText)
                        if (startMillis != null && endMillis != null && startMillis > endMillis) {
                            endDateText = selectedDate
                        }
                    } else {
                        endDateText = selectedDate
                        val startMillis = stockDateInputTextToMillis(startDateText)
                        val endMillis = stockDateInputTextToMillis(selectedDate)
                        if (startMillis != null && endMillis != null && endMillis < startMillis) {
                            startDateText = selectedDate
                        }
                    }
                }
            )
        }

        val periodOptions = listOf(
            "today" to localizedStringResource(260, "Today"),
            "7" to localizedStringResource(261, "7 days"),
            "30" to localizedStringResource(262, "30 days"),
            "month" to localizedStringResource(387, "This month"),
            "year" to localizedStringResource(388, "This year"),
            "all" to localizedStringResource(425, "All period"),
            "custom" to localizedStringResource(386, "Custom period")
        )

        val typeOptions = listOf(
            DropdownOption("all", localizedStringResource(383, "All transaction types")),
            DropdownOption("purchase", stateValues.stringSale),
            DropdownOption("return", stateValues.stringReturn),
            DropdownOption("accept", stateValues.stringSupply)
        )

        val sortOptions = listOf(
            DropdownOption("newest", localizedStringResource(398, "Newest first")),
            DropdownOption("oldest", localizedStringResource(399, "Oldest first")),
            DropdownOption("total_desc", localizedStringResource(400, "Highest total")),
            DropdownOption("total_asc", localizedStringResource(401, "Lowest total"))
        )

        val startMillis = stockDateInputTextToMillis(startDateText)
        val endExclusiveMillis = stockDateInputTextToLocalDate(endDateText)
            ?.let { transactionHistoryPlusDays(it, 1).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() }

        val filteredTransactions = remember(
            transactionsPayload,
            activeStoreId,
            periodPresetId,
            startDateText,
            endDateText,
            transactionTypeFilter,
            sortId,
            searchQuery,
            stateValues.stock,
            stateValues.appLanguage
        ) {
            transactionsPayload.orEmpty()
                .asSequence()
                .filter { transaction ->
                    transactionTypeFilter == "all" || transaction.type.equals(transactionTypeFilter, ignoreCase = true)
                }
                .filter { transaction ->
                    if (periodPresetId == "all") {
                        true
                    } else {
                        val afterStart = startMillis?.let { transaction.timeMillis >= it } ?: true
                        val beforeEnd = endExclusiveMillis?.let { transaction.timeMillis < it } ?: true
                        afterStart && beforeEnd
                    }
                }
                .filter { transaction -> transactionHistoryMatchesSearch(transaction, searchQuery) }
                .toList()
                .let { list ->
                    when (sortId) {
                        "oldest" -> list.sortedBy { it.timeMillis }
                        "total_desc" -> list.sortedByDescending { transactionHistoryTotal(it) }
                        "total_asc" -> list.sortedBy { transactionHistoryTotal(it) }
                        else -> list.sortedByDescending { it.timeMillis }
                    }
                }
        }

        var page by rememberSaveable(periodPresetId, startDateText, endDateText, transactionTypeFilter, sortId, searchQuery, filteredTransactions.size) {
            mutableStateOf(0)
        }
        val pageSize = stateValues.globalAppConfiguration.pagingDefaultPageSize.coerceIn(20, 100)
        val visibleTransactions = filteredTransactions.clientPaged(page, pageSize)

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.TransactionHistory),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.92f)
                .padding(horizontal = stateValues.marginTextField),
            contentPadding = PaddingValues(top = stateValues.marginTextField, bottom = stateValues.screenHeight / 5),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(periodOptions) { option ->
                        TransactionHistoryFilterChip(
                            text = option.second,
                            selected = periodPresetId == option.first,
                            onClick = {
                                periodPresetId = option.first
                                if (option.first != "custom") {
                                    val range = transactionHistoryPresetDates(option.first)
                                    startDateText = range.first
                                    endDateText = range.second
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                    verticalAlignment = Alignment.Top
                ) {
                    TransactionHistoryDateButton(
                        modifier = Modifier.weight(1f),
                        title = localizedStringResource(396, "From"),
                        dateText = startDateText,
                        onClick = {
                            periodPresetId = "custom"
                            calendarTarget = "start"
                        }
                    )

                    TransactionHistoryDateButton(
                        modifier = Modifier.weight(1f),
                        title = localizedStringResource(397, "To"),
                        dateText = endDateText,
                        onClick = {
                            periodPresetId = "custom"
                            calendarTarget = "end"
                        }
                    )
                }

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                SimpleTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    value = searchQuery,
                    placeholder = localizedStringResource(382, "Search transactions"),
                    leadingIconPath = stateValues.drawablePathIconTransactionHistory,
                    stateHost = NavigationScreenModel.Menu.TransactionHistory,
                    stateKey = "menu_transaction_history_search",
                    onValueChange = { searchQuery = it }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                    verticalAlignment = Alignment.Top
                ) {
                    SimpleDropdownField(
                        modifier = Modifier.weight(1f),
                        title = localizedStringResource(419, "Transaction type"),
                        selectedId = transactionTypeFilter,
                        options = typeOptions,
                        placeholder = localizedStringResource(383, "All transaction types"),
                        onSelected = { transactionTypeFilter = it }
                    )

                    SimpleDropdownField(
                        modifier = Modifier.weight(1f),
                        title = localizedStringResource(410, "Sort"),
                        selectedId = sortId,
                        options = sortOptions,
                        placeholder = localizedStringResource(398, "Newest first"),
                        onSelected = { sortId = it }
                    )
                }

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(409, "Refresh history"),
                        iconPath = stateValues.drawablePathIconTransactionHistory,
                        confirmationRequired = false,
                        onClick = { activeStoreId?.let { getTransactions(it) } }
                    )

                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabledColor = stateValues.DisabledColor,
                        text = localizedStringResource(390, "Clear period"),
                        iconPath = stateValues.drawablePathIconCancel,
                        confirmationRequired = false,
                        onClick = {
                            periodPresetId = "all"
                            startDateText = ""
                            endDateText = ""
                            searchQuery = ""
                            transactionTypeFilter = "all"
                            sortId = "newest"
                        }
                    )
                }

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
            }

            if (filteredTransactions.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(stateValues.screenHeight / 3),
                        text = if (searchQuery.isBlank()) localizedStringResource(391, "No transactions in this period") else localizedStringResource(424, "No matching transactions")
                    )
                }
            } else {
                items(visibleTransactions, key = { it.id }) { transaction ->
                    TransactionHistoryCard(
                        transaction = transaction,
                        onClick = {
                            coroutineScope.launch {
                                NavigationScreenModel.Menu.TransactionHistoryReceiptPreview.setState(
                                    NavigationScreenModel.Menu.TransactionHistoryReceiptPreview.KEY_STATE_TRANSACTION_ID to transaction.id
                                )
                                Navigation.Menu.go(
                                    NavigationScreenModel.Menu.TransactionHistoryReceiptPreview,
                                    isNarrowScreen = stateValues.isNarrowScreen,
                                    forceSecond = true
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                }

                if (filteredTransactions.size > pageSize) {
                    item {
                        PagingControls(
                            page = page,
                            totalItems = filteredTransactions.size,
                            pageSize = pageSize,
                            onPageChange = { page = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppConfiguration.MenuTransactionHistoryReceiptPreviewScreen() {
    val state by NavigationScreenModel.Menu.TransactionHistoryReceiptPreview.state.collectAsState()
    val transactionsPayload by transactionsState.payload.collectAsState()
    val transactionId = state[NavigationScreenModel.Menu.TransactionHistoryReceiptPreview.KEY_STATE_TRANSACTION_ID]
    val transaction = transactionsPayload.orEmpty().find { it.id == transactionId }
    val snapshot = remember(transaction, stateValues.stock, stateValues.stores, stateValues.appLanguage) {
        transaction?.let { transactionHistoryReceiptSnapshot(it) }
    }
    val labels = receiptLabels()
    val snapshotForScreen = snapshot
    val receiptScope = rememberCoroutineScope()
    var activeReceiptAction by remember { mutableStateOf<String?>(null) }

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(
                title = localizedStringResource(418, "Receipt preview"),
                iconPath = stateValues.drawablePathIconReceipt,
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        if (snapshotForScreen == null) {
            MessageText(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                text = localizedStringResource(13, "Not found")
            )
            return@AitaScreenColumn
        }

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.TransactionHistoryReceiptPreview),
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

        val receiptLanguage = stateValues.appLanguage
        val pdfCache = remember(snapshotForScreen, receiptLanguage, labels) { mutableStateOf<ByteArray?>(null) }
        val fileName = remember(snapshotForScreen, labels) { snapshotForScreen.receiptPdfFileName(labels) }
        fun runReceiptAction(action: String, successMessage: String) {
            if (activeReceiptAction != null) return
            val actionOwner = captureReceiptActionOwner()
            activeReceiptAction = action
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
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (actionOwner.isCurrent()) postInAppNotification(stateValues.stringReceiptActionFailed, NotificationType.Negative) }
                finally { activeReceiptAction = null }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            ReceiptActionToolbar(activeAction = activeReceiptAction, onAction = ::runReceiptAction)

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}


@Composable
internal fun AppConfiguration.ClipboardCopyButton(
    textToCopy: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    var copyPulse by rememberSaveable(textToCopy) { mutableStateOf(0) }
    var copied by rememberSaveable(textToCopy) { mutableStateOf(false) }

    LaunchedEffect(copyPulse) {
        if (copyPulse > 0) {
            copied = true
            delay(2200L)
            copied = false
        }
    }

    val resolvedContentDescription = contentDescription ?: if (copied) localizedStringResource(501, "Copied to clipboard") else localizedStringResource(503, "Copy")
    val iconRes = if (copied) stateValues.drawableResIconCheck.value else stateValues.drawableResIconClipboard.value
    val iconPath = if (copied) stateValues.drawablePathIconCheck else stateValues.drawablePathIconClipboard
    val borderColor = if (copied) stateValues.OkayColor else stateValues.PlaceholderTextColor
    val tintColor = if (copied) stateValues.OkayColor else stateValues.TextColor

    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (copied) stateValues.OkayColor.copy(alpha = 0.10f) else stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, borderColor, RoundedCornerShape(17.dp))
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.AccentColor),
                onClick = {
                    copyTextToClipboard(textToCopy)
                    copyPulse += 1
                }
            )
            .padding(7.dp),
        contentAlignment = Alignment.Center
    ) {
        CpImage(
            modifier = Modifier.fillMaxSize(),
            url = iconPath,
            fallbackRes = iconRes,
            contentDescription = resolvedContentDescription,
            tintColor = tintColor
        )
    }
}


internal fun SupplierDataModel.visibleSupplierName(language: String): String {
    val fallback = when (language.lowercase()) {
        "ru" -> "Поставщик"
        "kk" -> "Жеткізуші"
        "ky" -> "Жеткирүүчү"
        else -> "Supplier"
    }
    return name.visibleLocalizedString(language, fallback)
}

@Composable
internal fun AppConfiguration.SupplierCard(
    supplier: SupplierDataModel,
    editable: Boolean,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                if (editable || selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .run {
                when {
                    editable -> {
                        aitaClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = stateValues.AccentColor)
                        ) { onEdit() }
                    }
                    onSelect != null -> {
                        aitaClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = stateValues.AccentColor)
                        ) { onSelect.invoke() }
                    }
                    else -> this
                }
            }
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = supplier.visibleSupplierName(stateValues.appLanguage),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (editable) localizedStringResource(618, "My") else localizedStringResource(619, "Generic"),
                    color = if (editable || selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )

                if (selected) {
                    Text(
                        text = localizedStringResource(55, "Select"),
                        color = stateValues.AccentColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (editable) {
                actionButton(
                    text = "",
                    iconPath = stateValues.drawablePathIconEdit,
                    iconContentDescription = stateValues.stringEdit,
                    confirmationRequired = false,
                    onClick = onEdit
                )

                Spacer(modifier = Modifier.width(4.dp))

                actionButton(
                    text = "",
                    iconPath = stateValues.drawablePathIconDelete,
                    iconContentDescription = stateValues.stringDelete,
                    enabledColor = stateValues.ErrorColor,
                    confirmationRequired = true,
                    onClick = onDelete
                )
            }
        }

        supplier.phoneNumbers.orEmpty().takeIf { it.isNotEmpty() }?.let {
            Spacer(modifier = Modifier.height(6.dp))
            StockCardInfoLine(localizedStringResource(620, "Supplier phone number"), it.asDisplayPhoneNumbers().joinToString(", "), stateValues.TextColor)
        }

        supplier.emails.orEmpty().takeIf { it.isNotEmpty() }?.let {
            StockCardInfoLine(localizedStringResource(621, "Supplier email"), it.joinToString(", "), stateValues.TextColor)
        }

    }
}


internal fun String.isTechnicalOperationLogText(): Boolean {
    val value = trim()
    if (value.isBlank()) return true
    val lower = value.lowercase()
    return lower in setOf("operation completed", "операция выполнена", "операция орындалды") ||
        lower.startsWith("get /") ||
        lower.startsWith("post /") ||
        lower.startsWith("put /") ||
        lower.startsWith("delete /") ||
        lower.startsWith("patch /") ||
        lower.startsWith("head /") ||
        lower.startsWith("options /") ||
        lower.contains("user-agent") ||
        lower.contains("useragent") ||
        lower.contains("x-forwarded-for") ||
        lower.contains("http/") ||
        lower.contains("ktor-client") ||
        lower.contains("okhttp")
}

internal fun AppConfiguration.operationLogActionText(action: String): String = when (action) {
    OPERATION_LOG_ACTION_CREATED -> localizedStringResource(990, "Adding")
    OPERATION_LOG_ACTION_UPDATED -> localizedStringResource(991, "Editing")
    OPERATION_LOG_ACTION_DELETED -> localizedStringResource(992, "Deleting")
    OPERATION_LOG_ACTION_COMPLETED -> localizedStringResource(993, "Completion")
    OPERATION_LOG_ACTION_EXTRACTED -> localizedStringResource(994, "Cash extraction")
    OPERATION_LOG_ACTION_STARTED -> localizedStringResource(995, "Workshift start")
    OPERATION_LOG_ACTION_ENDED -> localizedStringResource(996, "Workshift end")
    OPERATION_LOG_ACTION_ACCEPTED -> localizedStringResource(997, "Acceptance")
    OPERATION_LOG_ACTION_DECLINED -> localizedStringResource(998, "Decline")
    OPERATION_LOG_ACTION_INVITED -> localizedStringResource(999, "Invitation")
    OPERATION_LOG_ACTION_MOVED -> localizedStringResource(1000, "Movement")
    else -> localizedStringResource(662, "Operation logs")
}

internal fun AppConfiguration.operationLogEntityText(entityType: String): String = when (entityType) {
    OPERATION_LOG_ENTITY_STORE -> stateValues.stringStore
    OPERATION_LOG_ENTITY_WORKER -> stateValues.stringWorkers
    OPERATION_LOG_ENTITY_WORKSHIFT -> localizedStringResource(661, "Active workshift")
    OPERATION_LOG_ENTITY_STOCK_ITEM -> localizedStringResource(365, "Goods item")
    OPERATION_LOG_ENTITY_STOCK_BATCH -> localizedStringResource(341, "Batch data")
    OPERATION_LOG_ENTITY_TRANSACTION -> localizedStringResource(316, "Transaction")
    OPERATION_LOG_ENTITY_CASH_REGISTER -> localizedStringResource(255, "Cash register")
    OPERATION_LOG_ENTITY_SUPPLIER -> stateValues.stringSupplier
    OPERATION_LOG_ENTITY_SUBSCRIPTION -> stateValues.stringSubscription
    OPERATION_LOG_ENTITY_FINANCE -> stateValues.stringFinances
    else -> localizedStringResource(662, "Operation logs")
}

internal fun AppConfiguration.operationLogFallbackText(log: OperationLogDataModel): String {
    return listOf(operationLogActionText(log.action), operationLogEntityText(log.entityType))
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" • ")
        .ifBlank { localizedStringResource(662, "Operation logs") }
}

internal fun AppConfiguration.operationLogTransactionTypeText(type: String): String = when (type.trim().lowercase()) {
    "purchase", "sale" -> stateValues.stringSale
    "return" -> stateValues.stringReturn
    "supply" -> stateValues.stringSupply
    else -> type
}

internal fun AppConfiguration.localizedOperationLogReadableText(raw: String, fallback: String): String {
    val base = raw
        .takeUnless { it.isTechnicalOperationLogText() }
        .orEmpty()
        .ifBlank { fallback }

    // Unknown text can contain a customer's name (for example "Sale Supply Shop").
    // Translate only a recognized event template, never isolated words inside original facts.
    return base
}

@Composable
internal fun AppConfiguration.OperationLogCard(log: OperationLogDataModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextField),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField / 2)
        ) {
            CpImage(
                modifier = Modifier.size(22.dp),
                url = stateValues.drawablePathIconLog,
                fallbackRes = stateValues.drawableResIconLog.value,
                contentDescription = localizedStringResource(662, "Operation logs"),
                tintColor = stateValues.AccentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedOperationLogReadableText(
                        raw = log.localizedEventTitle(eventPresentationLanguage(), ::eventPresentationResourceValues),
                        fallback = operationLogFallbackText(log)
                    ),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = receiptUiDateTime(log.createdAtMillis),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
        }

        localizedOperationLogReadableText(
            raw = log.localizedEventDetails(eventPresentationLanguage(), ::eventPresentationResourceValues),
            fallback = operationLogFallbackText(log)
        ).let { readableDetails ->
            Text(
                text = readableDetails,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize
            )
        }
        StorePersonLink(log.storeId,log.actorUserId,
            log.actorDisplayName.ifBlank {storePeopleText("view_profile")})
        Text(
            text = "${localizedStringResource(669, "Place")}: ${log.storeName.visibleLocalizedString(stateValues.appLanguage, log.storePublicId.ifBlank { log.storeId })}",
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )
    }
}

@Composable
fun AppConfiguration.MenuOperationLogsScreen() {
    val publishedViews by operationLogViewsState.collectAsState()
    val activeStoreId = stateValues.activeStoreId
    val accountId = stateValues.userAccount?.id
    val views = publishedViews.takeIf { it.storeId == activeStoreId && it.accountId == accountId }
        ?: OperationLogViews()
    var query by rememberSaveable { mutableStateOf("") }
    var showRootScope by rememberSaveable { mutableStateOf(false) }
    val refreshIcon by stateValues.drawableResIconRefresh.collectAsState()

    fun refreshBothScopes() {
        activeStoreId?.let { storeId ->
            getOperationLogs(storeId, OPERATION_LOG_SCOPE_CURRENT)
            getOperationLogs(storeId, OPERATION_LOG_SCOPE_ROOT)
        }
    }

    LaunchedEffect(activeStoreId, accountId, views.ownerEpoch) { refreshBothScopes() }

    fun filterLogs(logs: List<OperationLogDataModel>?): List<OperationLogDataModel>? {
        val q = query.trim().lowercase()
        return logs?.let { source -> if (q.isBlank()) source else source.filter { log ->
            listOf(
                log.action, log.entityType, log.entityId.orEmpty(), log.actorDisplayName, log.actorPublicId, log.storePublicId,
                log.localizedEventTitle(eventPresentationLanguage(), ::eventPresentationResourceValues),
                log.localizedEventDetails(eventPresentationLanguage(), ::eventPresentationResourceValues),
                localizedOperationLogReadableText(log.localizedEventTitle(eventPresentationLanguage(), ::eventPresentationResourceValues), operationLogFallbackText(log)),
                localizedOperationLogReadableText(log.localizedEventDetails(eventPresentationLanguage(), ::eventPresentationResourceValues), operationLogFallbackText(log))
            ).any { it.lowercase().contains(q) }
        } }
    }
    val currentRecords = remember(views.current.records, query, stateValues.appLanguage, stateValues.strings) { filterLogs(views.current.records) }
    val familyRecords = remember(views.family.records, query, stateValues.appLanguage, stateValues.strings) { filterLogs(views.family.records) }
    val selected = if (showRootScope) views.family else views.current
    val filtered = if (showRootScope) familyRecords else currentRecords
    fun scopeLabel(label: String, records: List<OperationLogDataModel>?) =
        records?.let { tabLabelWithCount(label, it.size) } ?: "$label (…)"

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(
                title = localizedStringResource(662, "Operation logs"),
                iconPath = stateValues.drawablePathIconLog,
                trailingIcons = listOf(Triple(stateValues.drawablePathIconRefresh, refreshIcon, ::refreshBothScopes)),
                onBack = { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } }
            )
        }
    ) {
        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.OperationLogs),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(stateValues.marginTextField),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = query,
                        placeholder = localizedStringResource(663, "Search logs"),
                        leadingIconPath = stateValues.drawablePathIconSearch,
                        stateHost = NavigationScreenModel.Menu.OperationLogs,
                        stateKey = "menu_operation_logs_search",
                        onValueChange = { query = it }
                    )
                    tabRowWidget(
                        modifier = Modifier.fillMaxWidth(),
                        tabs = listOf(
                            TabContent(
                                "current",
                                scopeLabel(localizedStringResource(664, "Current place"), currentRecords)
                            ) { showRootScope = false },
                            TabContent(
                                "parent",
                                scopeLabel(localizedStringResource(666, "Parent and branches"), familyRecords)
                            ) { showRootScope = true }
                        ),
                        selectedIndexInitial = if (showRootScope) "parent" else "current",
                        textSize = stateValues.smallTextSize
                    )
                }
            }

            if (activeStoreId == null) {
                item {
                    Text(
                        text = localizedStringResource(130, "No active store"),
                        color = stateValues.PlaceholderTextColor,
                        modifier = Modifier.padding(stateValues.marginTextField)
                    )
                }
            } else if (!currentUserCanViewLogs(activeStoreId)) {
                item {
                    Text(
                        text = localizedStringResource(665, "You do not have permission for this action"),
                        color = stateValues.ErrorColor,
                        modifier = Modifier.padding(stateValues.marginTextField)
                    )
                }
            } else if (selected.accessDenied) {
                item {
                    Text(
                        text = localizedStringResource(665, "You do not have permission for this action"),
                        color = stateValues.ErrorColor,
                        modifier = Modifier.padding(stateValues.marginTextField)
                    )
                }
            } else if (filtered == null) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxSize().padding(stateValues.marginTextField),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (selected.failure == null) LoadingSkeleton(Modifier.fillMaxWidth(), layout = LoadingLayout.Activity, rows = 4)
                        else Text(
                            text = selected.failure?.extractLocalizedString(stateValues.appLanguage)
                                ?: localizedStringResource(1141, "Please wait…"),
                            color = if (selected.failure != null) stateValues.ErrorColor else stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize, textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(stateValues.marginTextField),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = localizedStringResource(667, "No operation logs yet"),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.textSize,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                selected.failure?.extractLocalizedString(stateValues.appLanguage)?.let { message ->
                    item {
                        Text(message, color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize, modifier = Modifier.padding(stateValues.marginTextField))
                    }
                }
                items(filtered, key = { it.id }) { log ->
                    Box(modifier = Modifier.padding(horizontal = stateValues.marginTextField)) { OperationLogCard(log) }
                }
            }
        }
    }
}

@Composable
fun AppConfiguration.MenuSuppliersScreen() {
    val activeStoreId = stateValues.activeStoreId
    LaunchedEffect(activeStoreId) {
        getSuppliers()
    }

    val suppliers by suppliersState.payload.collectAsState()
    val supplierContracts by supplierPartnershipContractsState.payload.collectAsState()
    val currentUserId = stateValues.userAccount?.id
    var selectedTab by rememberSaveable { mutableStateOf("mine") }
    var search by rememberSaveable { mutableStateOf("") }
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var sortId by rememberSaveable { mutableStateOf("name") }
    var sortAscending by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == "contracts") sortMenuExpanded = false
    }

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = if (selectedTab == "contracts") {
                    localizedStringResource(1479, "Supplier contracts")
                } else {
                    stateValues.stringSuppliers
                },
                iconPath = if (selectedTab == "contracts") {
                    stateValues.drawablePathIconSupplierContracts
                } else {
                    stateValues.drawablePathIconSuppliers
                },
                iconRes = if (selectedTab == "contracts") {
                    stateValues.drawableResIconSupplierContracts.value
                } else {
                    stateValues.drawableResIconSuppliers.value
                },
                trailingIcons = if (selectedTab == "contracts") emptyList() else listOf(
                    Triple(sortActionIconPath(), sortActionIconFallback()) {
                        sortMenuExpanded = !sortMenuExpanded
                    },
                    Triple(stateValues.drawablePathIconAdd, stateValues.drawableResIconAdd.value) {
                        coroutineScope.launch {
                            NavigationScreenModel.Menu.AddEditSupplier.setState("edited_supplier_id" to "")
                            Navigation.Menu.go(NavigationScreenModel.Menu.AddEditSupplier, stateValues.isNarrowScreen)
                        }
                    }
                ),
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        AnimatedVisibility(visible = sortMenuExpanded && selectedTab != "contracts") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(stateValues.BackgroundColor)
                    .padding(horizontal = stateValues.marginTextField, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = localizedStringResource(512, "Sort by"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold
                )

                tabRowWidget(
                    modifier = Modifier.fillMaxWidth(),
                    tabs = listOf(
                        TabContent("name", stateValues.stringName) { sortId = it },
                        TabContent("added", localizedStringResource(514, "Time added")) { sortId = it }
                    ),
                    selectedIndexInitial = sortId,
                    compact = true, textSize = stateValues.smallTextSize
                )

                tabRowWidget(
                    modifier = Modifier.fillMaxWidth(),
                    tabs = listOf(
                        TabContent("asc", localizedStringResource(515, "Ascending")) { sortAscending = true },
                        TabContent("desc", localizedStringResource(516, "Descending")) { sortAscending = false }
                    ),
                    selectedIndexInitial = if (sortAscending) "asc" else "desc",
                    compact = true, textSize = stateValues.smallTextSize
                )
            }
        }

        val mineSuppliers = suppliers.orEmpty().filter { it.isMineForUser(currentUserId) && it.isActive }
        val genericSuppliers = suppliers.orEmpty().filter { it.isGenericSupplier() && it.isActive }
        val q = search.trim()
        fun supplierMatchesSearch(supplier: SupplierDataModel): Boolean =
            q.isBlank() || listOf(
                supplier.id,
                supplier.visibleSupplierName(stateValues.appLanguage),
                supplier.phoneNumbers.orEmpty().asDisplayPhoneNumbers().joinToString(" "),
                supplier.emails.orEmpty().joinToString(" ")
            ).any { it.contains(q, ignoreCase = true) }

        Column(
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            if (selectedTab != "contracts") {
                TransactionPlainTextField(
                    title = "",
                    value = search,
                    placeholder = stateValues.stringSearchByAnyData,
                    leadingIconPath = stateValues.drawablePathIconSearch,
                    stateHost = NavigationScreenModel.Menu.Suppliers,
                    stateKey = "menu_suppliers_search",
                    onValueChange = { search = it }
                )
            }

            val cleanActiveStoreId = activeStoreId.orEmpty().trim()
            val activeStoreContractCount = if (cleanActiveStoreId.isBlank()) {
                0
            } else {
                supplierContracts.orEmpty().count { contract ->
                    contract.isActive &&
                        contract.storeId.trim().equals(cleanActiveStoreId, ignoreCase = true)
                }
            }

            tabRowWidget(
                modifier = Modifier.fillMaxWidth(),
                tabs = listOf(
                    TabContent("mine", tabLabelWithCount(localizedStringResource(629, "My suppliers"), mineSuppliers.count { supplierMatchesSearch(it) })) { selectedTab = it },
                    TabContent("generic", tabLabelWithCount(localizedStringResource(628, "Generic suppliers"), genericSuppliers.count { supplierMatchesSearch(it) })) { selectedTab = it },
                    TabContent("contracts", tabLabelWithCount(localizedStringResource(1479, "Supplier contracts"), activeStoreContractCount)) { selectedTab = it }
                ),
                selectedIndexInitial = selectedTab
            )
        }

        val shownSuppliers = (if (selectedTab == "mine") mineSuppliers else genericSuppliers)
            .filter { supplier -> supplierMatchesSearch(supplier) }
            .let { list ->
                val sorted = when (sortId) {
                    "added" -> list.sortedBy { it.addedAt }
                    else -> list.sortedBy { it.visibleSupplierName(stateValues.appLanguage).lowercase() }
                }
                if (sortAscending) sorted else sorted.reversed()
            }

        if (selectedTab == "contracts") {
            SupplierContractsBoardContent(
                modifier = Modifier.weight(1f),
                actorSide = SUPPLIER_CONTRACT_SIDE_STORE,
                fixedStoreId = activeStoreId,
                showAppBar = false
            )
        } else {
            LazyColumn(
                state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Suppliers, listOf(selectedTab, sortId, if (sortAscending) "asc" else "desc").joinToString("_")),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                    .padding(horizontal = stateValues.marginTextField),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
            ) {
                if (shownSuppliers.isEmpty()) {
                    item {
                        MessageText(
                            modifier = Modifier.fillParentMaxSize().fillMaxWidth(),
                            text = if (q.isBlank()) localizedStringResource(626, "No suppliers yet") else stateValues.stringNoMatches
                        )
                    }
                } else {
                    items(shownSuppliers, key = { it.id }) { supplier ->
                        SupplierCard(
                            supplier = supplier,
                            editable = selectedTab == "mine",
                            onEdit = {
                                coroutineScope.launch {
                                    NavigationScreenModel.Menu.AddEditSupplier.setState("edited_supplier_id" to supplier.id)
                                    Navigation.Menu.go(NavigationScreenModel.Menu.AddEditSupplier, stateValues.isNarrowScreen)
                                }
                            },
                            onDelete = { deleteSupplier(supplier.id) }
                        )
                    }
                }
            }
        }
    }
}

internal fun AppConfiguration.subscriptionStatusText(status: String): String {
    return when (status) {
        SUBSCRIPTION_STATUS_ACTIVE -> localizedStringResource(812, "active")
        SUBSCRIPTION_STATUS_INACTIVE -> localizedStringResource(917, "inactive")
        SUBSCRIPTION_STATUS_PAST_DUE -> localizedStringResource(918, "past due")
        SUBSCRIPTION_STATUS_CANCELLED -> localizedStringResource(919, "cancelled")
        else -> status.replace('_', ' ')
    }
}

@Composable
fun AppConfiguration.MenuStoresScreen() {
    val selectedLocationHasAccess = rememberStoreSubscriptionAccess()
    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringStores,
                iconPath = stateValues.drawablePathIconStores,
                trailingIcons = listOf(
                    Triple(
                        stateValues.drawablePathIconAdd,
                        stateValues.drawableResIconAdd.value
                    ) {
                        coroutineScope.launch {
                            NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID)
                            NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_PARENT_STORE_ID)
                            Navigation.Menu.go(NavigationScreenModel.Menu.AddEditStore, stateValues.isNarrowScreen)
                        }
                    },
                ),
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        val storesStateValue = stateValues.storesState
        val currentUserId = stateValues.userAccount?.id.orEmpty()
        var storeTabId by rememberSaveable { mutableStateOf("owned") }
        var storeSearchQuery by rememberSaveable { mutableStateOf("") }

        val storesListState = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Stores, storeTabId)
        LazyColumn(
            state = storesListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.7f)
                .padding(horizontal = stateValues.marginTextField),
            contentPadding = PaddingValues(vertical = stateValues.marginTextField),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            when (storesStateValue) {
                is DataState.Success -> {
                    if (storesStateValue.payload.isEmpty()) {
                        item(key = "stores-empty-3") {
                            MessageText(
                                modifier = Modifier.fillMaxWidth().remainingListSpace(storesListState, "stores-empty-3"),
                                stateValues.stringListEmpty
                            )
                        }
                    } else {
                        item {
                            SimpleTextInput(
                                modifier = Modifier.fillMaxWidth(),
                                value = storeSearchQuery,
                                placeholder = stateValues.stringSearchByAnyData,
                                leadingIconPath = stateValues.drawablePathIconSearch,
                                stateHost = NavigationScreenModel.Menu.Stores,
                                stateKey = "menu_stores_search",
                                onValueChange = { storeSearchQuery = it }
                            )
                        }

                        val searchState = storeSearchQuery
                        val allStores = storesStateValue.payload.flattenStoresWithBranches()
                        val searchedItems = searchState
                            .takeIf { it.isNotBlank() }
                            ?.let { storesStateValue.payload.search<StoreDataModel>(it).first.flattenStoresWithBranches() }
                            ?: allStores
                        val topLevelStores = searchedItems
                            .filter { it.parentStoreId.isNullOrBlank() }
                            .distinctBy { it.id }

                        item {
                            tabRowWidget(
                                modifier = Modifier.fillMaxWidth(),
                                tabs = listOf(
                                    TabContent("owned", tabLabelWithCount(localizedStringResource(489, "My stores"), topLevelStores.count { currentUserId in it.userIds })) { storeTabId = it },
                                    TabContent("managed", tabLabelWithCount(localizedStringResource(478, "Managed stores"), topLevelStores.count { currentUserId !in it.userIds })) { storeTabId = it }
                                ),
                                selectedIndexInitial = storeTabId
                            )
                        }

                        val selectedTabId = storeTabId

                        val filteredTopLevelStores = if (selectedTabId == "managed") {
                            topLevelStores.filter { currentUserId !in it.userIds }
                        } else {
                            topLevelStores.filter { currentUserId in it.userIds }
                        }

                        if (searchedItems.isEmpty()) {
                            item(key = "stores-empty-2") {
                                MessageText(
                                    modifier = Modifier.fillMaxWidth().remainingListSpace(storesListState, "stores-empty-2"),
                                    stateValues.stringNoMatches
                                )
                            }
                        } else if (filteredTopLevelStores.isEmpty()) {
                            item(key = "stores-empty-1") {
                                MessageText(
                                    modifier = Modifier.fillMaxWidth().remainingListSpace(storesListState, "stores-empty-1"),
                                    if (selectedTabId == "managed") localizedStringResource(479, "You are not employed in other stores yet") else stateValues.stringListEmpty
                                )
                            }
                        } else {
                            items(filteredTopLevelStores, key = { it.id }) { store ->
                                val isOwner = currentUserId in store.userIds
                                val canManageStore = isOwner || currentUserHasStorePermission(store.id, STORE_PERMISSION_STORE_MANAGE)
                                val canManageBranches = canManageStore || currentUserHasStorePermission(store.id, STORE_PERMISSION_BRANCHES_MANAGE)
                                val isActive = store.id == stateValues.activeStoreId
                                val canBeActive = store.canBeSelectedAsActiveStore()
                                val hasBranches = store.branches.isNotEmpty()

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .run {
                                            if (hasBranches) {
                                                foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                                                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                                                    .background(stateValues.BackgroundColor)
                                                    .border(
                                                        stateValues.focusedBorderWidth,
                                                        stateValues.AccentColor,
                                                        RoundedCornerShape(stateValues.cornerRadius)
                                                    )
                                                    .padding(8.dp)
                                            } else {
                                                this
                                            }
                                        }
                                ) {
                                    StoreWidget(
                                        store = store,
                                        onDelete = if (isOwner && isActive && selectedLocationHasAccess) { { storeToDelete -> deleteStore(storeToDelete) } } else null,
                                        onEdit = if (canManageStore && isActive && selectedLocationHasAccess) {
                                            {
                                                coroutineScope.launch {
                                                    NavigationScreenModel.Menu.AddEditStore.setState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID to store.id)
                                                    NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_PARENT_STORE_ID)
                                                    Navigation.Menu.go(NavigationScreenModel.Menu.AddEditStore)
                                                }
                                            }
                                        } else null,
                                        onSetActive = if (!isActive && canBeActive) {
                                            { setActiveStoreId(it.id) }
                                        } else null,
                                        onSetInactive = if (isActive) {
                                            { setActiveStoreId(null) }
                                        } else null
                                    )

                                    if (store.branches.isNotEmpty()) {
                                        StoreBranchHeader(store)
                                        store.branches.forEach { branch ->
                                            val branchIsActive = branch.id == stateValues.activeStoreId
                                            StoreWidget(
                                                modifier = Modifier.padding(start = stateValues.marginTextField),
                                                store = branch,
                                                onDelete = if (isOwner && branchIsActive && selectedLocationHasAccess) { { branchToDelete -> deleteStore(branchToDelete) } } else null,
                                                onEdit = if (canManageBranches && branchIsActive && selectedLocationHasAccess) {
                                                    {
                                                        coroutineScope.launch {
                                                            NavigationScreenModel.Menu.AddEditStore.setState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID to branch.id)
                                                            NavigationScreenModel.Menu.AddEditStore.setState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_PARENT_STORE_ID to store.id)
                                                            Navigation.Menu.go(NavigationScreenModel.Menu.AddEditStore)
                                                        }
                                                    }
                                                } else null,
                                                onSetActive = if (!branchIsActive) {
                                                    { setActiveStoreId(branch.id) }
                                                } else null,
                                                onSetInactive = if (branchIsActive) {
                                                    { setActiveStoreId(null) }
                                                } else null
                                            )
                                        }
                                    }

                                    if (canManageBranches) {
                                        actionButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = stateValues.marginTextField, top = 4.dp),
                                            text = localizedStringResource(533, "Add branch"),
                                            iconPath = stateValues.drawablePathIconAdd,
                                            confirmationRequired = false,
                                            onClick = {
                                                coroutineScope.launch {
                                                    NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID)
                                                    NavigationScreenModel.Menu.AddEditStore.setState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_PARENT_STORE_ID to store.id)
                                                    Navigation.Menu.go(NavigationScreenModel.Menu.AddEditStore)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is DataState.Empty -> {
                    item(key = "stores-empty-0") {
                        MessageText(
                            modifier = Modifier.fillMaxWidth().remainingListSpace(storesListState, "stores-empty-0"),
                            stateValues.stringListEmpty
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppConfiguration.MenuScreen() {
    val subscriptionAccess = rememberStoreSubscriptionAccess()
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        if (stateValues.isNarrowScreen) {
            AitaLiveMenuPane(
                modifier = Modifier
                    .weight(1f),
                navigationStack = stateValues.navigationScreensMenuLeft,
                label = "menuNavigationNarrow"
            ) { navigationStack ->
                val model = navigationStack.last()
                if (menuDestinationRequiresStoreSubscription(model) && !subscriptionAccess) SubscriptionRequiredPane()
                else when (model) {
                    is NavigationScreenModel.Menu.List -> {
                        MenuListScreen()
                    }
                    is NavigationScreenModel.Menu.ShopWindow -> { MarketPublicationScreen() }
                    is NavigationScreenModel.Menu.AppMode -> {
                        MenuAppModeScreen()
                    }
                    is NavigationScreenModel.Menu.Tutorials -> { TutorialsScreen() }
                    is NavigationScreenModel.Menu.ClientUpdate -> { ClientUpdatesScreen() }
                    is NavigationScreenModel.Menu.About -> { AboutScreen() }
                    is NavigationScreenModel.Menu.UserAccount -> {
                        MenuUserAccountScreen()
                    }
                    is NavigationScreenModel.Menu.Notifications -> {
                        NotificationsScreen(
                            onBack = { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } }
                        )
                    }
                    is NavigationScreenModel.Menu.Finances -> {
                        MenuFinancesScreen()
                    }
                    is NavigationScreenModel.Menu.GoodsCategories -> {
                        MenuGoodsCategoriesScreen()
                    }
                    is NavigationScreenModel.Menu.AddEditGoodsCategory -> {
                        MenuAddEditGoodsCategoryScreen()
                    }
                    is NavigationScreenModel.Menu.Stores -> {
                        MenuStoresScreen()
                    }
                    is NavigationScreenModel.Menu.AddEditStore -> {
                        MenuAddEditStoreScreen()
                    }
                    is NavigationScreenModel.Menu.StoreSubscription -> {
                        MenuStoreSubscriptionPlansScreen()
                    }
                    is NavigationScreenModel.Menu.StoreSubscriptionPlans -> {
                        MenuStoreSubscriptionPlansScreen()
                    }
                    is NavigationScreenModel.Menu.TransactionHistory -> {
                        MenuTransactionHistoryScreen()
                    }
                    is NavigationScreenModel.Menu.TransactionHistoryReceiptPreview -> {
                        MenuTransactionHistoryReceiptPreviewScreen()
                    }
                    is NavigationScreenModel.Menu.OperationLogs -> {
                        MenuOperationLogsScreen()
                    }
                    is NavigationScreenModel.Menu.Analytics -> {
                        MenuAnalyticsScreen()
                    }
                    is NavigationScreenModel.Menu.Workers -> {
                        MenuWorkersScreen()
                    }
                    is NavigationScreenModel.Menu.AddEditWorker -> {
                        MenuAddEditWorkerScreen()
                    }
                    is NavigationScreenModel.Menu.Suppliers -> {
                        MenuSuppliersScreen()
                    }
                    is NavigationScreenModel.Menu.AddEditSupplier -> {
                        MenuAddEditSupplierScreen()
                    }
                    is NavigationScreenModel.Menu.Debtors -> {
                        MenuDebtorsScreen()
                    }
                    is NavigationScreenModel.Menu.CloseDebt -> {
                        MenuCloseDebtScreen()
                    }
                    is NavigationScreenModel.Menu.Devices -> {
                        MenuDevicesScreen()
                    }
                    is NavigationScreenModel.Menu.Security -> {
                        MenuSecurityScreen()
                    }
                    is NavigationScreenModel.Menu.Support -> {
                        MenuSupportScreen()
                    }
                    is NavigationScreenModel.Menu.AppLanguage -> {
                        MenuAppLanguageScreen()
                    }
                    is NavigationScreenModel.Menu.AppFont -> { MenuAppFontScreen() }
                    is NavigationScreenModel.Menu.AppTheme -> {
                        MenuAppThemeScreen()
                    }
                    is NavigationScreenModel.Menu.AppState -> { MenuSettingsScreen(legacyAppState = true) }
                    is NavigationScreenModel.Menu.Settings -> { MenuSettingsScreen() }
                    is NavigationScreenModel.Menu.Downloads -> { DownloadsScreen() }
                    is NavigationScreenModel.Menu.AppScale -> {
                        MenuAppScaleScreen()
                    }

                    else -> {}
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
            ) {
                AitaLiveMenuPane(
                    modifier = Modifier
                        .widthIn(min = 220.dp, max = 280.dp)
                        .fillMaxWidth(0.26f),
                    navigationStack = stateValues.navigationScreensMenuLeft,
                    label = "menuNavigationLeft"
                ) { navigationStack ->
                    val model = navigationStack.last()
                    if (menuDestinationRequiresStoreSubscription(model) && !subscriptionAccess) SubscriptionRequiredPane()
                    else when (model) {
                        is NavigationScreenModel.Menu.List -> {
                            MenuListScreen()
                        }
                        is NavigationScreenModel.Menu.ShopWindow -> { MarketPublicationScreen() }
                        is NavigationScreenModel.Menu.AppMode -> {
                            MenuAppModeScreen()
                        }
                        is NavigationScreenModel.Menu.Tutorials -> { TutorialsScreen() }
                        is NavigationScreenModel.Menu.ClientUpdate -> { ClientUpdatesScreen() }
                        is NavigationScreenModel.Menu.About -> { AboutScreen() }
                        is NavigationScreenModel.Menu.UserAccount -> {
                            MenuUserAccountScreen()
                        }
                        is NavigationScreenModel.Menu.Notifications -> {
                            NotificationsScreen(
                                onBack = { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } }
                            )
                        }
                        is NavigationScreenModel.Menu.Finances -> {
                            MenuFinancesScreen()
                        }
                        is NavigationScreenModel.Menu.GoodsCategories -> {
                            MenuGoodsCategoriesScreen()
                        }
                        is NavigationScreenModel.Menu.AddEditGoodsCategory -> {
                            MenuAddEditGoodsCategoryScreen()
                        }
                        is NavigationScreenModel.Menu.Stores -> {
                            MenuStoresScreen()
                        }
                        is NavigationScreenModel.Menu.AddEditStore -> {
                            MenuAddEditStoreScreen()
                        }
                        is NavigationScreenModel.Menu.StoreSubscription -> {
                            MenuStoreSubscriptionPlansScreen()
                        }
                        is NavigationScreenModel.Menu.StoreSubscriptionPlans -> {
                            MenuStoreSubscriptionPlansScreen()
                        }
                        is NavigationScreenModel.Menu.TransactionHistory -> {
                            MenuTransactionHistoryScreen()
                        }
                        is NavigationScreenModel.Menu.TransactionHistoryReceiptPreview -> {
                            MenuTransactionHistoryReceiptPreviewScreen()
                        }
                        is NavigationScreenModel.Menu.OperationLogs -> {
                            MenuOperationLogsScreen()
                        }
                        is NavigationScreenModel.Menu.Analytics -> {
                            MenuAnalyticsScreen()
                        }
                        is NavigationScreenModel.Menu.Workers -> {
                            MenuWorkersScreen()
                        }
                        is NavigationScreenModel.Menu.AddEditWorker -> {
                            MenuAddEditWorkerScreen()
                        }
                        is NavigationScreenModel.Menu.Suppliers -> {
                            MenuSuppliersScreen()
                        }
                        is NavigationScreenModel.Menu.AddEditSupplier -> {
                            MenuAddEditSupplierScreen()
                        }
                        is NavigationScreenModel.Menu.Debtors -> {
                            MenuDebtorsScreen()
                        }
                        is NavigationScreenModel.Menu.CloseDebt -> {
                            MenuCloseDebtScreen()
                        }
                        is NavigationScreenModel.Menu.Devices -> {
                            MenuDevicesScreen()
                        }
                        is NavigationScreenModel.Menu.Security -> {
                            MenuSecurityScreen()
                        }
                        is NavigationScreenModel.Menu.Support -> {
                            MenuSupportScreen()
                        }
                        is NavigationScreenModel.Menu.AppLanguage -> {
                            MenuAppLanguageScreen()
                        }
                        is NavigationScreenModel.Menu.AppFont -> { MenuAppFontScreen() }
                    is NavigationScreenModel.Menu.AppTheme -> {
                            MenuAppThemeScreen()
                        }
                        is NavigationScreenModel.Menu.AppState -> { MenuSettingsScreen(legacyAppState = true) }
                        is NavigationScreenModel.Menu.Settings -> { MenuSettingsScreen() }
                        is NavigationScreenModel.Menu.Downloads -> { DownloadsScreen() }
                        is NavigationScreenModel.Menu.AppScale -> {
                            MenuAppScaleScreen()
                        }

                        else -> {}
                    }
                }

                AitaLiveMenuPane(
                    modifier = Modifier
                        .weight(1f),
                    navigationStack = stateValues.navigationScreensMenuRight,
                    label = "menuNavigationRight"
                ) { navigationStack ->
                    val model = navigationStack.last()
                    if (menuDestinationRequiresStoreSubscription(model) && !subscriptionAccess) SubscriptionRequiredPane()
                    else when (model) {
                        is NavigationScreenModel.Menu.List -> {
                            MenuListScreen()
                        }
                        is NavigationScreenModel.Menu.ShopWindow -> { MarketPublicationScreen() }
                        is NavigationScreenModel.Menu.AppMode -> {
                            MenuAppModeScreen()
                        }
                        is NavigationScreenModel.Menu.Tutorials -> { TutorialsScreen() }
                        is NavigationScreenModel.Menu.ClientUpdate -> { ClientUpdatesScreen() }
                        is NavigationScreenModel.Menu.About -> { AboutScreen() }
                        is NavigationScreenModel.Menu.UserAccount -> {
                            MenuUserAccountScreen()
                        }
                        is NavigationScreenModel.Menu.Notifications -> {
                            NotificationsScreen(
                                onBack = { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } }
                            )
                        }
                        is NavigationScreenModel.Menu.Finances -> {
                            MenuFinancesScreen()
                        }
                        is NavigationScreenModel.Menu.GoodsCategories -> {
                            MenuGoodsCategoriesScreen()
                        }
                        is NavigationScreenModel.Menu.AddEditGoodsCategory -> {
                            MenuAddEditGoodsCategoryScreen()
                        }
                        is NavigationScreenModel.Menu.Stores -> {
                            MenuStoresScreen()
                        }
                        is NavigationScreenModel.Menu.AddEditStore -> {
                            MenuAddEditStoreScreen()
                        }
                        is NavigationScreenModel.Menu.StoreSubscription -> {
                            MenuStoreSubscriptionPlansScreen()
                        }
                        is NavigationScreenModel.Menu.StoreSubscriptionPlans -> {
                            MenuStoreSubscriptionPlansScreen()
                        }
                        is NavigationScreenModel.Menu.TransactionHistory -> {
                            MenuTransactionHistoryScreen()
                        }
                        is NavigationScreenModel.Menu.TransactionHistoryReceiptPreview -> {
                            MenuTransactionHistoryReceiptPreviewScreen()
                        }
                        is NavigationScreenModel.Menu.OperationLogs -> {
                            MenuOperationLogsScreen()
                        }
                        is NavigationScreenModel.Menu.Analytics -> {
                            MenuAnalyticsScreen()
                        }
                        is NavigationScreenModel.Menu.Workers -> {
                            MenuWorkersScreen()
                        }
                        is NavigationScreenModel.Menu.AddEditWorker -> {
                            MenuAddEditWorkerScreen()
                        }
                        is NavigationScreenModel.Menu.Suppliers -> {
                            MenuSuppliersScreen()
                        }
                        is NavigationScreenModel.Menu.AddEditSupplier -> {
                            MenuAddEditSupplierScreen()
                        }
                        is NavigationScreenModel.Menu.Debtors -> {
                            MenuDebtorsScreen()
                        }
                        is NavigationScreenModel.Menu.CloseDebt -> {
                            MenuCloseDebtScreen()
                        }
                        is NavigationScreenModel.Menu.Devices -> {
                            MenuDevicesScreen()
                        }
                        is NavigationScreenModel.Menu.Security -> {
                            MenuSecurityScreen()
                        }
                        is NavigationScreenModel.Menu.Support -> {
                            MenuSupportScreen()
                        }
                        is NavigationScreenModel.Menu.AppLanguage -> {
                            MenuAppLanguageScreen()
                        }
                        is NavigationScreenModel.Menu.AppFont -> { MenuAppFontScreen() }
                    is NavigationScreenModel.Menu.AppTheme -> {
                            MenuAppThemeScreen()
                        }
                        is NavigationScreenModel.Menu.AppState -> { MenuSettingsScreen(legacyAppState = true) }
                        is NavigationScreenModel.Menu.Settings -> { MenuSettingsScreen() }
                        is NavigationScreenModel.Menu.Downloads -> { DownloadsScreen() }
                        is NavigationScreenModel.Menu.AppScale -> {
                            MenuAppScaleScreen()
                        }

                        else -> {}
                    }
                }

//        LazyColumn(
//          modifier = Modifier
//            .weight(0.5f)
//            .background(stateValues.DisabledColor)
//            .fillMaxHeight()
//        ) {
//
//        }
            }
        }
    }
}

internal fun AppConfiguration.currentUserOwnsActiveStoreForUi(): Boolean {
    val activeStoreId = stateValues.activeStoreId?.takeIf { it.isNotBlank() } ?: return false
    val currentUserId = stateValues.userAccount?.id.orEmpty()
    val activeStore = stateValues.stores.findStoreOrBranchForUi(activeStoreId)

    if (currentUserId.isNotBlank() && activeStore?.userIds?.contains(currentUserId) == true) {
        return true
    }

    if (currentUserOwnsStore(activeStoreId)) {
        return true
    }

    return stateValues.stores.orEmpty().isEmpty()
}

/** Billing is separate from role authorization: even an owner needs the selected location's access. */
internal fun menuDestinationRequiresStoreSubscription(model: NavigationScreenModel): Boolean = when (model) {
    NavigationScreenModel.Menu.TransactionHistory,
    NavigationScreenModel.Menu.TransactionHistoryReceiptPreview,
    NavigationScreenModel.Menu.OperationLogs, NavigationScreenModel.Menu.Analytics,
    NavigationScreenModel.Menu.AddEditWorker,
    NavigationScreenModel.Menu.Suppliers, NavigationScreenModel.Menu.AddEditSupplier,
    NavigationScreenModel.Menu.Debtors, NavigationScreenModel.Menu.CloseDebt,
    NavigationScreenModel.Menu.GoodsCategories, NavigationScreenModel.Menu.AddEditGoodsCategory,
    NavigationScreenModel.Menu.Devices, NavigationScreenModel.Menu.ShopWindow -> true
    else -> false // Account, store selection/creation, recovery and billing remain reachable.
}

internal fun AppConfiguration.canOpenMenuDestination(model: NavigationScreenModel.Menu): Boolean {
    if (menuDestinationRequiresStoreSubscription(model) && !currentStoreHasSubscriptionAccess(stateValues.activeStoreId)) return false
    val activeStoreId = stateValues.activeStoreId
    val activeOwnerFallback = currentUserOwnsActiveStoreForUi()
    return when (model) {
        NavigationScreenModel.Menu.AppMode -> !model.isTemporarilyHiddenFromUi()
        NavigationScreenModel.Menu.TransactionHistory -> activeOwnerFallback || currentUserCanViewTransactionHistory(activeStoreId)
        NavigationScreenModel.Menu.OperationLogs -> activeOwnerFallback || currentUserCanViewLogs(activeStoreId)
        NavigationScreenModel.Menu.Analytics -> activeOwnerFallback || currentUserCanViewAnalytics(activeStoreId)
        NavigationScreenModel.Menu.Workers -> true // Personal employment remains available without a store subscription.
        NavigationScreenModel.Menu.ShopWindow -> currentUserOwnsStore(activeStoreId)
        NavigationScreenModel.Menu.Stores -> true
        NavigationScreenModel.Menu.Suppliers -> activeOwnerFallback || currentUserCanViewSuppliers(activeStoreId) || currentUserCanViewSupplierOrders(activeStoreId) || currentUserCanManageSupplierOrders(activeStoreId) || currentUserCanReceiveSupplierOrders(activeStoreId)
        NavigationScreenModel.Menu.Debtors -> activeOwnerFallback || currentUserCanViewDebtors(activeStoreId) || currentUserCanManageDebtorPayments(activeStoreId)
        NavigationScreenModel.Menu.GoodsCategories -> activeOwnerFallback || currentUserCanViewStock(activeStoreId) || currentUserOwnsStore(activeStoreId)
        NavigationScreenModel.Menu.StoreSubscription,
        NavigationScreenModel.Menu.StoreSubscriptionPlans -> true // Members see access; only billing managers can change it.
        else -> true
    }
}

internal fun AppConfiguration.menuDestinationsForCurrentMode(): List<NavigationScreenModel.Menu> =
    menuDestinationsForAppMode(stateValues.appModeId)

internal fun menuDestinationsForAppMode(modeId: Int): List<NavigationScreenModel.Menu> = when (modeId) {
    APP_MODE_SUPPLIER, APP_MODE_MANUFACTURER -> listOf(
        NavigationScreenModel.Menu.UserAccount,
        NavigationScreenModel.Menu.AppMode,
        NavigationScreenModel.Menu.Workers,
        NavigationScreenModel.Menu.Notifications,
        NavigationScreenModel.Menu.Finances,
        NavigationScreenModel.Menu.Security,
        NavigationScreenModel.Menu.Tutorials,
        NavigationScreenModel.Menu.Support,
        NavigationScreenModel.Menu.AppLanguage,
        NavigationScreenModel.Menu.AppTheme,
        NavigationScreenModel.Menu.AppScale,
        NavigationScreenModel.Menu.AppFont,
        NavigationScreenModel.Menu.Settings,
        NavigationScreenModel.Menu.Downloads,
        NavigationScreenModel.Menu.ClientUpdate,
        NavigationScreenModel.Menu.About
    )
    APP_MODE_BUYER -> listOf(
        NavigationScreenModel.Menu.UserAccount,
        NavigationScreenModel.Menu.AppMode,
        NavigationScreenModel.Menu.Workers,
        NavigationScreenModel.Menu.Notifications,
        NavigationScreenModel.Menu.Finances,
        NavigationScreenModel.Menu.Security,
        NavigationScreenModel.Menu.Tutorials,
        NavigationScreenModel.Menu.Support,
        NavigationScreenModel.Menu.AppLanguage,
        NavigationScreenModel.Menu.AppTheme,
        NavigationScreenModel.Menu.AppScale,
        NavigationScreenModel.Menu.AppFont,
        NavigationScreenModel.Menu.Settings,
        NavigationScreenModel.Menu.Downloads,
        NavigationScreenModel.Menu.ClientUpdate,
        NavigationScreenModel.Menu.About
    )
    else -> Navigation.Menu.listScreens
}

internal fun AppConfiguration.filteredMenuDestinations(): List<NavigationScreenModel.Menu> {
    return menuDestinationsForCurrentMode()
        .filterNot { it.isTemporarilyHiddenFromUi() }
        .filter { canOpenMenuDestination(it) }
}

internal fun AppConfiguration.filteredMainBottomDestinations(
    hasSubscriptionAccess: Boolean = currentStoreHasSubscriptionAccess(stateValues.activeStoreId)
): List<NavigationScreenModel> {
    if (!hasSubscriptionAccess) return listOf(NavigationScreenModel.Menu.Main)
    val activeStoreId = stateValues.activeStoreId
    val activeOwnerFallback = currentUserOwnsActiveStoreForUi()
    return Navigation.bottomNavBarScreensStore.filter { model ->
        when (model) {
            NavigationScreenModel.Transaction.MainSale -> activeOwnerFallback || currentUserCanUseTransactionType(activeStoreId, 0)
            NavigationScreenModel.Transaction.MainReturn -> activeOwnerFallback || currentUserCanUseTransactionType(activeStoreId, 1)
            NavigationScreenModel.Transaction.MainSupply -> activeOwnerFallback || currentUserCanUseTransactionType(activeStoreId, 2)
            NavigationScreenModel.Stock.Main -> activeOwnerFallback || currentUserCanViewStock(activeStoreId)
            else -> true
        }
    }.ifEmpty { listOf(NavigationScreenModel.Menu.Main) }
}

@Composable
internal fun AppConfiguration.NoActiveWorkshiftMenuTileText(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.widthIn(min = 0.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = localizedStringResource(133, "No active workshift"),
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = localizedStringResource(645, "Start a workshift before taking payments or changing cash. You can still browse the store and prepare work without starting a shift."),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AppConfiguration.NoActiveWorkshiftMenuTile() {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2)
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextField)
    ) {
        val compact = stateValues.isNarrowScreen || maxWidth < 420.dp

        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                NoActiveWorkshiftMenuTileText(modifier = Modifier.fillMaxWidth())
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1091, "Start shift"),
                    iconPath = stateValues.drawablePathIconWorkers,
                    confirmationRequired = false,
                    onClick = { showWorkshiftStartDialog() }
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                NoActiveWorkshiftMenuTileText(modifier = Modifier.fillMaxWidth())
                actionButton(
                    autoLoading = false,
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1091, "Start shift"),
                    iconPath = stateValues.drawablePathIconWorkers,
                    confirmationRequired = false,
                    onClick = { showWorkshiftStartDialog() }
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.ActiveWorkshiftMenuTileText(
    workshift: WorkshiftDataModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.widthIn(min = 0.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = localizedStringResource(661, "Active workshift"),
            color = stateValues.AccentColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${workshift.workerDisplayName} • ${receiptUiDateTime(workshift.startedAtMillis)}",
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AppConfiguration.ActiveWorkshiftMenuTile(workshift: WorkshiftDataModel) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.AccentColor.copy(alpha = 0.12f))
            .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextField)
    ) {
        val compact = stateValues.isNarrowScreen || maxWidth < 420.dp

        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                ActiveWorkshiftMenuTileText(workshift = workshift, modifier = Modifier.fillMaxWidth())
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(650, "End workshift"),
                    iconPath = stateValues.drawablePathIconCancel,
                    enabledColor = stateValues.ErrorColor,
                    confirmationRequired = true,
                    onClick = { endCurrentWorkshift() }
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                ActiveWorkshiftMenuTileText(workshift = workshift, modifier = Modifier.fillMaxWidth())
                actionButton(
                    autoLoading = false,
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(650, "End workshift"),
                    iconPath = stateValues.drawablePathIconCancel,
                    enabledColor = stateValues.ErrorColor,
                    confirmationRequired = true,
                    onClick = { endCurrentWorkshift() }
                )
            }
        }
    }
}


@Composable
internal fun AppConfiguration.SupplierWorkspaceMenuTile() {
    val destinations = (Navigation.bottomNavBarScreensSupplier.filterNot { it is NavigationScreenModel.Menu } +
        listOf(
            NavigationScreenModel.Supplier.Analytics.Main,
            NavigationScreenModel.Supplier.Identity.Main
        ))
        .distinctBy { it.route }
    val currentRoute = stateValues.navigationScreensMain.last().route
    val manufacturerMode = stateValues.appModeId == APP_MODE_MANUFACTURER
    val orders by supplierOrdersState.payload.collectAsState()
    val lines by supplierOrderLinesState.payload.collectAsState()
    val supplierPrices by supplierGoodsPricesState.payload.collectAsState()
    val supplierContracts by supplierPartnershipContractsState.payload.collectAsState()
    val supplierDashboard by supplierModeDashboardState.payload.collectAsState()
    val activeSupplierProfileId by activeSupplierProfileIdState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val localProfiles = stateValues.suppliers.orEmpty()
        .supplierProfilesOwnedBy(stateValues.userAccount?.id)
    val focusedSupplierId = resolveSupplierProfileFocus(activeSupplierProfileId, localProfiles)
    val identityPresentation = buildSupplierIdentityPresentation(
        localProfiles = localProfiles,
        dashboard = supplierDashboard,
        activeSupplierId = focusedSupplierId,
        localProfilesLoaded = stateValues.suppliers != null
    )

    LaunchedEffect(stateValues.userAccount?.id, stateValues.appModeId) {
        if (stateValues.userAccount != null && (stateValues.appModeId == APP_MODE_SUPPLIER || stateValues.appModeId == APP_MODE_MANUFACTURER)) {
            refreshSupplierModeWorkspace(includeContracts = true)
        }
    }

    val activeOrders = remember(orders, focusedSupplierId) {
        orders.orEmpty()
            .supplierOrdersForIdentity(focusedSupplierId)
            .filter { order ->
                order.isActive && order.status != SupplierOrderStatusDataModel.Draft
            }
    }
    val activeLines = remember(lines, activeOrders) {
        val activeOrderIds = activeOrders.map { it.id }.toSet()
        lines.orEmpty().filter { line -> line.isActive && line.orderId in activeOrderIds }
    }
    val activePrices = remember(supplierPrices, focusedSupplierId) {
        supplierPrices.orEmpty()
            .supplierPricesForIdentity(focusedSupplierId)
            .normalizedSupplierGoodsPriceBook()
    }
    val activeContracts = remember(supplierContracts, focusedSupplierId) {
        supplierContracts.orEmpty()
            .supplierContractsForIdentity(focusedSupplierId)
            .filter { it.isActive }
    }
    val linesByOrder = remember(activeLines) { activeLines.groupBy { it.orderId } }
    val fallbackOpenOrdersCount = remember(activeOrders) {
        activeOrders.count { !it.status.isClosedForSupplierDesk() }
    }
    val fallbackActionQueueCount = remember(activeOrders, linesByOrder) {
        activeOrders.count { order ->
            order.needsSupplierActionForSupplierDesk(linesByOrder[order.id].orEmpty())
        }
    }
    val fallbackPartnerCount = remember(
        activeOrders,
        activeLines,
        activePrices,
        activeContracts,
        stateValues.suppliers,
        stateValues.stores,
        stateValues.appLanguage
    ) {
        buildSupplierPartnerItems(
            orders = activeOrders,
            lines = activeLines,
            supplierPrices = activePrices,
            contracts = activeContracts,
            dashboard = null
        ).size
    }
    val fallbackCatalogSkuCount = remember(activeLines, activePrices, activeContracts) {
        buildSet {
            activeLines.forEach { line ->
                line.goodsItemId.trim().lowercase().takeIf { it.isNotBlank() }?.let(::add)
                line.substituteGoodsItemId
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
            activePrices.forEach { price ->
                price.goodsItemId.trim().lowercase().takeIf { it.isNotBlank() }?.let(::add)
            }
            activeContracts.forEach { contract ->
                contract.goodsItemIds
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .forEach(::add)
                contract.priceTerms
                    .map { it.goodsItemId.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .forEach(::add)
            }
        }.size
    }
    val openOrdersCount = if (orders != null) {
        fallbackOpenOrdersCount
    } else {
        supplierDashboard?.openOrderCount ?: 0
    }
    val actionQueueCount = if (orders != null && lines != null) {
        fallbackActionQueueCount
    } else {
        supplierDashboard?.actionRequiredOrderCount ?: fallbackActionQueueCount
    }
    val partnerDetailsLoaded = orders != null && supplierPrices != null && supplierContracts != null
    val partnerCount = if (partnerDetailsLoaded) {
        fallbackPartnerCount
    } else {
        supplierDashboard?.partnerCount ?: fallbackPartnerCount
    }
    val catalogDetailsLoaded = lines != null && supplierPrices != null && supplierContracts != null
    val catalogSkuCount = if (catalogDetailsLoaded) {
        fallbackCatalogSkuCount
    } else {
        supplierDashboard?.catalogSkuCount ?: fallbackCatalogSkuCount
    }
    val supplierProfileCount = if (focusedSupplierId.isNullOrBlank()) {
        identityPresentation.profileCount
    } else {
        identityPresentation.profileCount.coerceAtMost(1)
    }
    val priceCoveragePercent = supplierDashboard?.readiness?.priceBookCoveragePercent
    val manufacturerBridgeCount = supplierDashboard?.manufacturerBridge?.size ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.AccentColor.copy(alpha = 0.10f))
            .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            CpImage(
                modifier = Modifier.size(38.dp),
                url = if (manufacturerMode) stateValues.drawablePathIconAppModeManufacturer else stateValues.drawablePathIconAppModeSupplier,
                fallbackRes = if (manufacturerMode) stateValues.drawableResIconAppModeManufacturer.value else stateValues.drawableResIconAppModeSupplier.value,
                contentDescription = localizedStringResource(1400, "Workspace"),
                tintColor = null
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (manufacturerMode) localizedStringResource(1401, "Producer workspace") else localizedStringResource(1402, "Supplier workspace"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (manufacturerMode) {
                        localizedStringResource(1403, "Producer mode starts with catalog and demand bridge screens while the production layer grows.")
                    } else {
                        localizedStringResource(1404, "Jump between order inbox, catalog, customers and insights without returning to the bottom bar.")
                    },
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (identityPresentation.profileCount > 1) {
            SupplierIdentityFocusSelector(
                presentation = identityPresentation,
                onIdentitySelected = { supplierId ->
                    coroutineScope.launch {
                        setActiveSupplierProfileId(supplierId)
                        postInAppNotification(
                            localizedStringResource(2489, "Supplier identity changed"),
                            NotificationType.Positive,
                            transient = true
                        )
                    }
                }
            )
        }

        val workspaceMetrics = listOf(
            Triple(
                localizedStringResource(1619, "Supplier profiles"),
                supplierProfileCount.toString(),
                stateValues.drawablePathIconSuppliers to stateValues.drawableResIconSuppliers.value
            ),
            Triple(
                localizedStringResource(1648, "Supplier action queue"),
                actionQueueCount.toString(),
                stateValues.drawablePathIconResponse to stateValues.drawableResIconResponse.value
            ),
            Triple(
                localizedStringResource(1690, "Price coverage"),
                priceCoveragePercent?.let { "$it%" } ?: "—",
                stateValues.drawablePathIconSupplierCatalog to stateValues.drawableResIconSupplierCatalog.value
            ),
            Triple(
                localizedStringResource(1710, "Manufacturer bridge"),
                manufacturerBridgeCount.toString(),
                stateValues.drawablePathIconAppModeManufacturer to stateValues.drawableResIconAppModeManufacturer.value
            ),
            Triple(
                localizedStringResource(1533, "Open pipeline"),
                openOrdersCount.toString(),
                stateValues.drawablePathIconAppModeSupplier to stateValues.drawableResIconAppModeSupplier.value
            ),
            Triple(
                localizedStringResource(1408, "Catalog SKUs"),
                catalogSkuCount.toString(),
                stateValues.drawablePathIconSupplierCatalog to stateValues.drawableResIconSupplierCatalog.value
            ),
            Triple(
                localizedStringResource(1453, "Partner stores"),
                partnerCount.toString(),
                stateValues.drawablePathIconSupplierPartners to stateValues.drawableResIconSupplierPartners.value
            )
        )

        workspaceMetrics.chunked(if (stateValues.isNarrowScreen) 1 else 3).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                rowMetrics.forEach { metric ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 54.dp)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(stateValues.BackgroundColor.copy(alpha = 0.72f))
                            .border(
                                stateValues.unfocusedBorderWidth,
                                stateValues.PlaceholderTextColor.copy(alpha = 0.45f),
                                RoundedCornerShape(stateValues.cornerRadius)
                            )
                            .padding(horizontal = stateValues.marginTextField, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CpImage(
                            modifier = Modifier.size(22.dp),
                            url = metric.third.first,
                            fallbackRes = metric.third.second,
                            contentDescription = metric.first,
                            tintColor = stateValues.AccentColor
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = metric.second,
                                color = stateValues.AccentColor,
                                fontSize = stateValues.textSize,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = metric.first,
                                color = stateValues.TextColor,
                                fontSize = stateValues.smallTextSize,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (!stateValues.isNarrowScreen && rowMetrics.size < 3) {
                    repeat(3 - rowMetrics.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }

        if (!manufacturerMode && supplierProfileCount == 0) {
            SupplierProfileIdentityCard(dashboard = supplierDashboard, compact = true)
        }

        destinations.chunked(if (stateValues.isNarrowScreen) 1 else 2).forEach { rowDestinations ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                rowDestinations.forEach { destination ->
                    val active = destination.route == currentRoute
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(if (active) stateValues.AccentColor.copy(alpha = 0.16f) else stateValues.BackgroundColor)
                            .border(
                                if (active) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                                if (active) stateValues.AccentColor else stateValues.PlaceholderTextColor.copy(alpha = 0.55f),
                                RoundedCornerShape(stateValues.cornerRadius)
                            )
                            .aitaClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = stateValues.AccentColor)
                            ) {
                                coroutineScope.launch { Navigation.goMain(destination) }
                            }
                            .padding(horizontal = stateValues.marginTextField, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CpImage(
                            modifier = Modifier.size(22.dp),
                            url = destination.iconPath,
                            fallbackRes = destination.iconRes,
                            contentDescription = destination.name,
                            tintColor = if (active) stateValues.AccentColor else stateValues.IconTintColor
                        )
                        Text(
                            text = destination.name,
                            color = if (active) stateValues.AccentColor else stateValues.TextColor,
                            fontSize = stateValues.smallTextSize,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!stateValues.isNarrowScreen && rowDestinations.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun AppConfiguration.MenuListScreen() {
    val clientUpdate by AppUpdateWorkspace.state.collectAsState()
    val unreadCount = rememberRemoteUnreadCount()
    val updateGlow = updateAttentionGlow(clientUpdate.hasUpdate)
    val subscriptionAccess = rememberStoreSubscriptionAccess()
    AitaScreenColumn(
        modifier = Modifier
            .fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringMenu,
                iconPath = stateValues.drawablePathIconMenu
            )
        }
    ) {
        if (subscriptionAccess && stateValues.appModeId == APP_MODE_STORE) {
            stateValues.activeWorkshift?.takeIf { it.isActive && it.endedAtMillis == null && it.storeId == stateValues.activeStoreId }?.let { workshift ->
                ActiveWorkshiftMenuTile(workshift = workshift)
            }

            if (subscriptionAccess && shouldBlockAppForWorkshift()) {
                NoActiveWorkshiftMenuTile()
            }
        }

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.List),
            modifier = Modifier
                .weight(1f)
        ) {
            val groups = orderedMenuDestinations(filteredMenuDestinations(), clientUpdate.hasUpdate).groupBy(::menuSection)
            menuSectionOrder.forEach { section ->
                val destinations = groups[section].orEmpty()
                if (destinations.isNotEmpty() && section.isNotEmpty()) item("heading:$section") {
                    Text(visualText(section), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth()
                            .padding(start = stateValues.marginTextField, end = stateValues.marginTextField, top = 18.dp, bottom = 6.dp))
                }
            items(
                items = destinations,
                key = { model -> model.route }
            ) { model ->
                val updateItem = model == NavigationScreenModel.Menu.ClientUpdate
                val unreadItem = model == NavigationScreenModel.Menu.Notifications && unreadCount > 0
                val attentionText = if (updateItem) Color(0xFF062D35) else if (unreadItem) Color(0xFF102D50) else null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (updateItem) Color(0xFF2BD3CD) else if (unreadItem) Color(0xFF9CC8FF) else Color.Transparent)
                        .defaultMinSize(minHeight = stateValues.textFieldHeight)
                        .aitaClickable(
                            interactionSource = remember {
                                MutableInteractionSource()
                            },
                            indication = ripple(color = stateValues.TextColor)
                        ) {
                            if (canOpenMenuDestination(model)) {
                                if (unreadItem) requestUnreadNotifications()
                                coroutineScope.launch { Navigation.Menu.go(model, stateValues.isNarrowScreen) }
                            } else {
                                postInAppNotification(currentUserPermissionDeniedMessage(), NotificationType.Negative)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isActive = stateValues.run {
                        if (isNarrowScreen)
                            navigationScreensMenuLeft
                        else
                            navigationScreensMenuRight
                    }.last().route == model.route

                    Box(
                        modifier = Modifier
                            .width(stateValues.textFieldHeight)
                            .padding(horizontal = stateValues.marginTextField),
                        contentAlignment = Alignment.Center
                    ) {
                        CpImage(
                            modifier = Modifier.size(stateValues.iconSize),
                            url = model.iconPath,
                            fallbackRes = model.iconRes,
                            contentDescription = model.name,
                            tintColor = attentionText ?: if (isActive)
                                stateValues.AccentColor
                            else
                                stateValues.TextColor
                        )
                    }

                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = stateValues.marginTextFieldGroup, top = stateValues.marginTextField, bottom = stateValues.marginTextField),
                        text = if (unreadItem) "${model.name} · $unreadCount" else model.name,
                        color = attentionText ?: if (isActive)
                            stateValues.AccentColor
                        else
                            stateValues.TextColor,
                        style = androidx.compose.ui.text.TextStyle(fontFamily = LocalAitaFontFamily.current, shadow = if (updateItem) androidx.compose.ui.graphics.Shadow(Color.White.copy(alpha = updateGlow), blurRadius = 10f * updateGlow) else null),
                        fontWeight = if (isActive || updateItem || unreadItem)
                            FontWeight.Bold
                        else
                            FontWeight.Normal,
                        fontSize = stateValues.textSize,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            }
        }
    }
}

@Composable
fun AppConfiguration.MenuGoodsCategoriesScreen() {
    AitaScreenColumn(
        modifier = Modifier
            .fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringGoodsCategories,
                iconPath = stateValues.drawablePathIconGoodsCategories,
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
    }
}

@Composable
fun AppConfiguration.MenuFinancesScreen() {
    val financeState by userFinanceDashboardState.value.collectAsState()
    val walletState by userWalletState.value.collectAsState()
    val ledgerState by userWalletLedgerState.value.collectAsState()
    val intentsState by paymentIntentsState.value.collectAsState()

    var topUpAmount by rememberSaveable { mutableStateOf("") }
    var selectedProviderId by rememberSaveable { mutableStateOf(PAYMENT_PROVIDER_MANUAL_DEVELOPMENT) }
    var ledgerPage by rememberSaveable { mutableStateOf(0) }
    var intentPage by rememberSaveable { mutableStateOf(0) }
    var creatingInvoice by remember(stateValues.userAccount?.id) { mutableStateOf(false) }
    val pageSize = stateValues.globalAppConfiguration.pagingDefaultPageSize.coerceIn(10, 80)

    LaunchedEffect(Unit) {
        getUserFinanceDashboard()
    }

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringFinances,
                iconPath = stateValues.drawablePathIconFinances,
                onBack = { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } }
            )
        }
    ) {
        val section = sectionTabsWidget(
            stateKey = "finances:${stateValues.userAccount?.id.orEmpty()}",
            tabs = listOf(
                TabContent("balance", localizedStringResource(588, "Balance")),
                TabContent("top_up", localizedStringResource(580, "Top up balance")),
                TabContent("invoices", localizedStringResource(584, "Payment invoices")),
                TabContent("history", localizedStringResource(585, "Balance history"))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Start)
                .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2),
        )

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Finances, section),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .align(Alignment.CenterHorizontally)
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            contentPadding = PaddingValues(vertical = stateValues.marginTextField)
        ) {
            val wallet = (walletState as? DataState.Success<UserWalletDataModel>)?.payload
            val dashboard = (financeState as? DataState.Success<UserFinanceDashboardDataModel>)?.payload
            val ledger = (ledgerState as? DataState.Success<List<WalletLedgerEntryDataModel>>)?.payload.orEmpty()
            val intents = (intentsState as? DataState.Success<List<TopUpPaymentIntentDataModel>>)?.payload.orEmpty()
            val providers = dashboard?.paymentProviders.orEmpty().ifEmpty { stateValues.globalAppConfiguration.paymentProviders }

            if (section == "balance") {
                item(key = "MenuFinancesScreen:$section:0") {
                    FinanceBalanceCard(wallet)
                }
            }

            if (section == "top_up") {
                item(key = "MenuFinancesScreen:$section:1") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(stateValues.BackgroundColor)
                            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                            .padding(stateValues.marginTextFieldGroup),
                        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        Text(
                            text = localizedStringResource(580, "Top up balance"),
                            color = stateValues.TextColor,
                            fontSize = stateValues.titleTextSize,
                            fontWeight = FontWeight.Bold
                        )
                        SimpleTextInput(
                            modifier = Modifier.fillMaxWidth(),
                            value = topUpAmount,
                            placeholder = localizedStringResource(581, "Amount"),
                            keyboardType = KeyboardType.Number,
                            leadingIconPath = stateValues.drawablePathIconFinances,
                            stateHost = NavigationScreenModel.Menu.Finances,
                            stateKey = "menu_finances_top_up_amount",
                            onTransformValue = { raw -> raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.') },
                            onValueChange = { topUpAmount = it }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            providers.filter { it.enabled || it.id == PAYMENT_PROVIDER_KASPI_INVOICE }.forEach { provider ->
                                AnalyticsPill(
                                    modifier = Modifier.weight(1f),
                                    text = provider.name.visibleLocalizedString(stateValues.appLanguage, provider.id),
                                    selected = selectedProviderId == provider.id
                                ) { selectedProviderId = provider.id }
                            }
                        }
                        Text(
                            text = localizedStringResource(582, "Kaspi call is prepared but disabled until merchant API credentials are connected."),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize
                        )
                        actionButton(
                            text = localizedStringResource(583, "Create invoice"),
                            iconPath = stateValues.drawablePathIconCheck,
                            confirmationRequired = false,
                            enabled = !creatingInvoice && topUpAmount.toDoubleOrNull()?.let { it > 0.0 } == true,
                            loading = creatingInvoice,
                            autoLoading = false,
                            onClick = {
                                if (creatingInvoice) return@actionButton
                                val amount = topUpAmount.toDoubleOrNull() ?: return@actionButton
                                creatingInvoice = true
                                createTopUpPayment(
                                    TopUpCreateRequestDataModel(
                                        amount = amount,
                                        currencyCode = wallet?.currencyCode ?: stateValues.userAccount?.countryLocale?.let { locale ->
                                            if (locale.equals("tj", true)) "TJS" else "KZT"
                                        } ?: "KZT",
                                        providerId = selectedProviderId
                                    )
                                ) { creatingInvoice = false }
                            }
                        )
                    }
                }
            }

            if (section == "invoices") {
                if (intents.isEmpty()) {
                    item(key = "MenuFinancesScreen:$section:2") { MessageText(Modifier.fillParentMaxSize(), stateValues.stringListEmpty) }
                } else {
                    item(key = "MenuFinancesScreen:$section:3") {
                        Text(
                            text = localizedStringResource(584, "Payment invoices"),
                            color = stateValues.TextColor,
                            fontSize = stateValues.titleTextSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val visibleIntentPage = boundedSectionPage(intentPage, intents.size, pageSize)
                    val pagedIntents = intents.clientPaged(visibleIntentPage, pageSize)
                    items(pagedIntents, key = { it.id }) { intent ->
                        PaymentIntentCard(intent)
                    }
                    item(key = "MenuFinancesScreen:$section:4") { PagingControls(page = visibleIntentPage, totalItems = intents.size, pageSize = pageSize, onPageChange = { intentPage = it }) }
                }
            }

            if (section == "history") {
                item(key = "MenuFinancesScreen:$section:5") {
                    Text(
                        text = localizedStringResource(585, "Balance history"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = stateValues.marginTextFieldGroup)
                    )
                }

                if (ledger.isEmpty()) {
                    item(key = "MenuFinancesScreen:$section:6") { MessageText(Modifier.fillParentMaxSize(), localizedStringResource(586, "No balance operations yet")) }
                } else {
                    val visibleLedgerPage = boundedSectionPage(ledgerPage, ledger.size, pageSize)
                    val pagedLedger = ledger.clientPaged(visibleLedgerPage, pageSize)
                    items(pagedLedger, key = { it.id }) { entry ->
                        FinanceLedgerCard(
                            title = when (entry.type) {
                                WALLET_LEDGER_TOP_UP -> localizedStringResource(590, "Top-up")
                                WALLET_LEDGER_SUBSCRIPTION_CHARGE -> localizedStringResource(591, "Subscription charge")
                                else -> entry.type
                            },
                            subtitle = entry.note,
                            amountText = (entry.amountMinor.fromMinorCurrencyUnits()).aitaMoney(entry.currencyCode),
                            timeMillis = entry.createdAtMillis
                        )
                    }
                    item(key = "MenuFinancesScreen:$section:7") { PagingControls(page = visibleLedgerPage, totalItems = ledger.size, pageSize = pageSize, onPageChange = { ledgerPage = it }) }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.FinanceBalanceCard(wallet: UserWalletDataModel?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Text(
            text = localizedStringResource(592, "AITA balance"),
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = (wallet?.available ?: 0.0).aitaMoney(wallet?.currencyCode ?: "KZT"),
            color = stateValues.AccentColor,
            fontSize = stateValues.titleTextSize,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = localizedStringResource(593, "1 AITA unit equals 1 unit of your national currency"),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )
    }
}

@Composable
internal fun AppConfiguration.PaymentIntentCard(intent: TopUpPaymentIntentDataModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(intent.amount.money(intent.currencyCode), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                Text(intent.providerId + " · " + intent.status, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            }
            if (intent.status != PAYMENT_STATUS_PAID) {
                actionButton(
                    autoLoading = false,
                    text = localizedStringResource(594, "Confirm test payment"),
                    iconPath = stateValues.drawablePathIconCheck,
                    confirmationRequired = true,
                    onClick = { confirmDevelopmentTopUpPayment(intent.id) }
                )
            }
        }
        if (intent.providerInvoiceId.isNotBlank()) {
            Text(intent.providerInvoiceId, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        }
    }
}

@Composable
internal fun AppConfiguration.FinanceLedgerCard(
    title: String,
    subtitle: String,
    amountText: String,
    timeMillis: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            Text(securitySessionDateTimeText(timeMillis), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        }
        Text(amountText, color = stateValues.AccentColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
    }
}

internal fun AppConfiguration.subscriptionPeriodText(unit: String, count: Int): String {
    val unitText = when (unit.trim().lowercase()) {
        SUBSCRIPTION_PERIOD_MONTH -> localizedStringResource(369, "Month")
        SUBSCRIPTION_PERIOD_YEAR -> localizedStringResource(370, "Year")
        else -> unit
    }

    return if (count <= 1) unitText else "$count $unitText"
}

internal fun securitySessionDateTimeText(millis: Long): String {
    if (millis <= 0L) return "—"
    return runCatching {
        val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        "${dt.dayOfMonth.toString().padStart(2, '0')}.${dt.monthNumber.toString().padStart(2, '0')}.${dt.year} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    }.getOrElse { millis.toString() }
}
