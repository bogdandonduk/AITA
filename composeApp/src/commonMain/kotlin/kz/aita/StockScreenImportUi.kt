package kz.aita

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString

@Composable
internal fun AppConfiguration.StockScreenImportEntry(modifier: Modifier = Modifier) {
    val openTool = stockWorkspaceToolLauncher()
    if (
        !currentUserHasStorePermission(
            stateValues.activeStoreId,
            STORE_PERMISSION_STOCK_ITEM_CREATE,
        )
    )
        return
    actionButton(
        modifier = modifier,
        text = stockFileText("title", stateValues.appLanguage),
        iconPath = stateValues.drawablePathIconStock,
        autoLoading = false,
        confirmationRequired = false,
        onClick = { openTool(StockWorkspaceTool.IMPORT) },
    )
}

@Composable
internal fun AppConfiguration.StockScreenImportSheet(
    recorder: StockScreenRecorder,
    onDismiss: () -> Unit,
) {
    val language = stateValues.appLanguage
    fun text(key: String) = stockScreenText(key, language)
    val scope = rememberCoroutineScope()
    val owner = remember { captureReceiptActionOwner() }
    val viewScope = remember { inventoryViewScopeKey() }
    val inventoryStatus by stockLoadStatusState.collectAsState()
    val accountId = remember { stateValues.userAccount?.id.orEmpty() }
    val storeId = remember { stateValues.activeStoreId.orEmpty() }
    val config = stateValues.globalAppConfiguration
    val reviewScroll = rememberLazyListState()
    val parser = remember { StockScreenRecognizer() }
    val parserLock = remember { Mutex() }
    var parserRevision by remember { mutableStateOf(0) }
    val writer = remember { Mutex() }
    var rows by remember { mutableStateOf<List<ScreenStockRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var restoreFailed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var stopImport by remember { mutableStateOf(false) }
    var windows by remember { mutableStateOf<List<StockCaptureWindow>>(emptyList()) }
    var windowId by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf<String?>(null) }
    var mapping by remember { mutableStateOf(false) }
    var fileToMap by remember { mutableStateOf<StockImportFile?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var captureControls by remember { mutableStateOf(false) }
    val filePicker =
        rememberStockFilePicker(stockFileText("file", language)) { selection ->
            if (
                owner.isCurrent() &&
                    stateValues.userAccount?.id == accountId &&
                    stateValues.activeStoreId == storeId &&
                    inventoryViewScopeKey() == viewScope &&
                    !busy
            ) {
                recorder.stop()
                if (selection.error != null) error = stockFileText(selection.error, language)
                else if (selection.bytes != null) {
                    error = ""
                    confirmation = null
                    fileToMap = selection
                }
            }
        }
    var lastFrame by remember { mutableStateOf<StockScreenFrame?>(null) }
    var recognized by remember { mutableStateOf<ScreenStockRecognition?>(null) }
    val capture by recorder.state.collectAsState()
    fun current() =
        owner.isCurrent() &&
            inventoryViewScopeKey() == viewScope &&
            currentUserHasStorePermission(storeId, STORE_PERMISSION_STOCK_ITEM_CREATE) &&
            stateValues.userAccount?.id == accountId &&
            stateValues.activeStoreId == storeId
    suspend fun persist(snapshot: List<ScreenStockRow> = rows) {
        writer.withLock {
            withContext(Dispatchers.ourIo) {
                writeStockImportDraft(
                    accountId,
                    storeId,
                    stockScreenImportJson.encodeToString(
                        StockScreenImportDraft(
                            accountId = accountId,
                            storeId = storeId,
                            rows = snapshot,
                        )
                    ),
                )
            }
        }
    }
    suspend fun checkpoint(row: ScreenStockRow) =
        withContext(Dispatchers.ourIo) {
            writeStockImportProgress(
                accountId,
                storeId,
                row.id,
                StockImportProgress(row.importStarted, row.itemSaved, row.complete),
            )
        }
    fun refreshWindows() {
        if (refreshing || busy) return
        refreshing = true
        error = ""
        scope.launch {
            try {
                windows = recorder.windows()
                if (windowId !in windows.map { it.id }) windowId = ""
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = stockCaptureStatusMessage("helper_unavailable", language)
            } finally {
                refreshing = false
            }
        }
    }
    fun dismiss() {
        if (busy) return
        recorder.stop()
        busy = true
        scope.launch {
            try {
                if (loaded && !restoreFailed) persist()
                onDismiss()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = text("draft_error")
            } finally {
                busy = false
            }
        }
    }
    DisposableEffect(recorder) { onDispose { recorder.stop() } }
    LaunchedEffect(Unit) {
        try {
            val saved = withContext(Dispatchers.ourIo) { readStockImportDraft(accountId, storeId) }
            if (saved != null) {
                val draft =
                    withContext(Dispatchers.Default) {
                        stockScreenImportJson.decodeFromString<StockScreenImportDraft>(saved).also {
                            validateStockScreenImportDraft(it, accountId, storeId)
                        }
                    }
                val progress =
                    withContext(Dispatchers.ourIo) {
                        readStockImportProgress(accountId, storeId, draft.rows.map { it.id })
                    }
                val restored =
                    withContext(Dispatchers.Default) {
                        draft.rows
                            .map { row ->
                                progress[row.id]?.let { saved ->
                                    row.copy(
                                        importStarted = row.importStarted || saved.started,
                                        itemSaved = row.itemSaved || saved.itemSaved,
                                        complete = row.complete || saved.complete,
                                        selected = row.selected && !saved.complete,
                                    )
                                } ?: row
                            }
                            .also {
                                validateStockScreenImportDraft(
                                    draft.copy(rows = it),
                                    accountId,
                                    storeId,
                                )
                            }
                    }
                rows = restored
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            restoreFailed = true
            error = text("restore_error")
        } finally {
            loaded = true
        }
    }
    LaunchedEffect(rows, loaded, busy) {
        if (!loaded || restoreFailed || busy) return@LaunchedEffect
        delay(500)
        try {
            persist()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            recorder.stop()
            error = text("draft_error")
        }
    }
    LaunchedEffect(stateValues.activeStoreId, stateValues.userAccount?.id, inventoryStatus) {
        if (!current()) {
            recorder.stop()
            onDismiss()
        }
    }
    LaunchedEffect(recorder) {
        recorder.frames.collect { frame ->
            if (!current() || busy || !loaded || restoreFailed) return@collect
            val revision = parserRevision
            val result =
                withContext(Dispatchers.Default) { parserLock.withLock { parser.read(frame) } }
            if (revision != parserRevision || busy || !current()) return@collect
            lastFrame = frame
            recognized = result
            val defaultCurrency =
                config.countries
                    .withSupportedCountries()
                    .firstOrNull {
                        it.locale.equals(
                            stateValues.stores
                                .findStoreOrBranchForUi(storeId)
                                ?.countryLocales
                                ?.firstOrNull(),
                            true,
                        )
                    }
                    ?.currencies
                    ?.firstOrNull()
                    ?.code
                    .orEmpty()
            val next =
                mergeScreenStockRows(
                    rows,
                    result.rows.map { row ->
                        val category =
                            stateValues.goodsCategories.orEmpty().singleOrNull { category ->
                                row.category.isNotBlank() &&
                                    (category.name + category.alias.orEmpty()).any {
                                        it.value.equals(row.category, true)
                                    }
                            }
                        row.copy(
                            currency = row.currency.ifBlank { defaultCurrency },
                            categoryId = category?.id.orEmpty(),
                        )
                    },
                )
            val fits =
                withContext(Dispatchers.Default) { stockScreenDraftFits(accountId, storeId, next) }
            if (revision != parserRevision || busy || !current()) return@collect
            if (fits) rows = next
            else {
                recorder.stop()
                notice = text("limit")
            }
            if (rows.size >= 3000 || result.truncated) {
                recorder.stop()
                notice = text("limit")
            }
        }
    }

    fun update(row: ScreenStockRow) {
        rows = rows.map { if (it.id == row.id) row else it }
    }
    fun startImport() {
        if (busy || !loaded || restoreFailed) return
        recorder.stop()
        error = ""
        notice = ""
        if (!current()) {
            error = text("owner_changed")
            return
        }
        val selected = rows.filter { it.selected && !it.complete }
        if (selected.isEmpty()) {
            error = text("no_selection")
            return
        }
        val bad = selected.firstOrNull { !it.reviewed || it.problems(config).isNotEmpty() }
        if (bad != null) {
            error = text("review_needed")
            editId = bad.id
            return
        }
        val barcodeOwners = stockImportBarcodeOwners(stockState.payloadValue.orEmpty(), storeId)
        val duplicate = selected.firstOrNull { it.hasExistingBarcode(barcodeOwners) }
        if (duplicate != null) {
            error = text("duplicate")
            editId = duplicate.id
            return
        }
        if (selected.map { it.barcode.normalizedBarcodeToken() }.distinct().size != selected.size) {
            error = text("duplicate")
            return
        }
        if (
            selected.any { (screenStockNumber(it.quantity)?.toDoubleOrNull() ?: 0.0) > 0 } &&
                !currentUserHasStorePermission(storeId, STORE_PERMISSION_STOCK_BATCH_CREATE)
        ) {
            error = currentUserPermissionDeniedMessage().extractLocalizedString(language).orEmpty()
            return
        }
        busy = true
        importing = true
        stopImport = false
        scope.launch {
            try {
                // Stable item/batch identifiers are durable BEFORE the first write, including
                // offline writes.
                persist()
                for (source in selected) {
                    if (stopImport) break
                    if (!current()) {
                        error = text("owner_changed")
                        break
                    }
                    var row = rows.first { it.id == source.id }
                    if (row.hasExistingBarcode(stockState.payloadValue.orEmpty(), storeId)) {
                        error = text("duplicate")
                        break
                    }
                    row = row.copy(importStarted = true)
                    checkpoint(row)
                    update(row)
                    // Read the journal projection first: an acknowledgement projects accepted stock
                    // before removing its pending command, so either side safely owns a retry.
                    val pendingItem =
                        row.pendingItemWrite(inventoryCreatePendingState.value, storeId)
                    if (pendingItem?.rejected == true) {
                        error =
                            pendingItem.failure?.extractLocalizedString(language)?.takeIf {
                                it.isNotBlank()
                            } ?: text("save_error")
                        break
                    }
                    if (
                        !row.itemSaved &&
                            pendingItem == null &&
                            stockState.payloadValue.orEmpty().none {
                                it.id == row.itemId && it.storeId == storeId
                            }
                    ) {
                        val result = CompletableDeferred<DataState<GoodsItemDataModel>>()
                        addGoodsItem(row.toStockItem(accountId, storeId, language, config)) {
                            result.complete(it)
                        }
                        val response = withTimeoutOrNull(90_000) { result.await() }
                        if (response !is DataState.Success) {
                            if (
                                row.canCorrectRejectedCreate(
                                    response != null,
                                    current(),
                                    stockState.payloadValue.orEmpty(),
                                    inventoryCreatePendingState.value,
                                )
                            ) {
                                // A confirmed rejection has no accepted/pending write. Remove the
                                // frozen checkpoint before persisting editable fields; a crash must
                                // not re-freeze an unreviewed row behind that obsolete checkpoint.
                                withContext(Dispatchers.ourIo) {
                                    clearStockImportRowProgress(accountId, storeId, row.id)
                                }
                                row = row.copy(importStarted = false, reviewed = false)
                                update(row)
                                persist()
                            }
                            error =
                                response?.message?.extractLocalizedString(language)?.takeIf {
                                    it.isNotBlank()
                                } ?: text("save_error")
                            break
                        }
                    }
                    row = row.copy(itemSaved = true)
                    checkpoint(row)
                    update(row)
                    if (stopImport) break
                    if (!current()) {
                        error = text("owner_changed")
                        break
                    }
                    val batch = row.toStockBatch(accountId, storeId, config)
                    val pendingBatch =
                        row.pendingBatchWrite(inventoryCreatePendingState.value, storeId)
                    if (pendingBatch?.rejected == true) {
                        error =
                            pendingBatch.failure?.extractLocalizedString(language)?.takeIf {
                                it.isNotBlank()
                            } ?: text("save_error")
                        break
                    }
                    if (
                        batch != null &&
                            pendingBatch == null &&
                            stockBatchesState.payloadValue.orEmpty().none {
                                it.id == batch.id && it.storeId == storeId
                            }
                    ) {
                        val result = CompletableDeferred<DataState<List<GoodsBatchDataModel>>>()
                        addGoodsBatches(listOf(batch)) { result.complete(it) }
                        val response = withTimeoutOrNull(90_000) { result.await() }
                        if (response !is DataState.Success) {
                            error =
                                response?.message?.extractLocalizedString(language)?.takeIf {
                                    it.isNotBlank()
                                } ?: text("save_error")
                            break
                        }
                    }
                    row = row.copy(complete = true, selected = false)
                    checkpoint(row)
                    update(row)
                }
                persist()
                withContext(Dispatchers.ourIo) { clearStockImportProgress(accountId, storeId) }
                if (error.isEmpty()) notice = text(if (stopImport) "import_paused" else "saved")
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = text("draft_error")
            } finally {
                busy = false
                importing = false
            }
        }
    }

    fun discardReview() {
        if (busy) return
        busy = true
        parserRevision++
        recorder.stop()
        scope.launch {
            try {
                writer.withLock {
                    withContext(Dispatchers.ourIo) { deleteStockImportDraft(accountId, storeId) }
                }
                rows = emptyList()
                error = ""
                notice = ""
                restoreFailed = false
                parserLock.withLock { parser.reset() }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = text("draft_error")
            } finally {
                busy = false
            }
        }
    }

    AitaBottomSheet(
        title =
            when {
                editId != null ->
                    rows.firstOrNull { it.id == editId }?.name.orEmpty().ifBlank { text("title") }
                mapping -> text("map")
                fileToMap != null -> stockFileText("mapping", language)
                else -> stockFileText("title", language)
            },
        iconPath = stateValues.drawablePathIconStock,
        onDismiss = {
            when {
                busy -> Unit
                editId != null -> editId = null
                mapping -> mapping = false
                fileToMap != null -> fileToMap = null
                confirmation != null -> confirmation = null
                else -> dismiss()
            }
        },
    ) {
        rows
            .firstOrNull { it.id == editId }
            ?.let { row ->
                StockScreenRowEditor(
                    row,
                    storeId,
                    onApply = {
                        update(it)
                        editId = null
                    },
                )
            }
        if (mapping && recognized != null && lastFrame != null) {
            StockScreenColumnMapper(recognized!!) { columns ->
                if (busy || !current()) return@StockScreenColumnMapper
                parserRevision++
                busy = true
                val frame = lastFrame!!
                scope.launch {
                    try {
                        val result =
                            withContext(Dispatchers.Default) {
                                parserLock.withLock {
                                    parser.useColumns(columns, frame.width)
                                    parser.read(frame)
                                }
                            }
                        if (!current()) return@launch
                        // Preserve earlier screens. A new layout never silently discards captured
                        // candidates.
                        val next = mergeScreenStockRows(rows, result.rows)
                        val fits =
                            withContext(Dispatchers.Default) {
                                stockScreenDraftFits(accountId, storeId, next)
                            }
                        if (!current()) return@launch
                        if (fits) rows = next else notice = text("limit")
                        recognized = result
                        mapping = false
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        error = text("map_error")
                    } finally {
                        busy = false
                    }
                }
            }
        }
        fileToMap?.let { file ->
            StockFileMappingContent(file) { incoming ->
                if (!current() || busy) return@StockFileMappingContent
                val prepared = incoming.map { row ->
                    val category =
                        stateValues.goodsCategories.orEmpty().singleOrNull { category ->
                            row.category.isNotBlank() &&
                                (category.name + category.alias.orEmpty()).any {
                                    it.value.equals(row.category, true)
                                }
                        }
                    row.copy(
                        categoryId = category?.id.orEmpty(),
                        warnings =
                            if (category != null) row.warnings - "file_category" else row.warnings,
                    )
                }
                // Do not silently truncate a larger second file at the review limit.
                val next = mergeScreenStockRows(rows, prepared, limit = 6001)
                if (stockScreenDraftFits(accountId, storeId, next)) {
                    rows = next
                    fileToMap = null
                    notice = ""
                    error = ""
                } else {
                    fileToMap = null
                    error = stockFileText("file_limit", language)
                }
            }
        }
        if (fileToMap == null && editId == null && !mapping) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = reviewScroll,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        stockFileText("help", language),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        actionButton(
                            Modifier.weight(1f),
                            text = stockFileText("file", language),
                            // Preserve the focused button's semantics while the OS picker opens.
                            // Replacing its click node with a loading-only node can break the
                            // current Compose web accessibility tree when focus returns.
                            icon =
                                if (filePicker.busy) {
                                    {
                                        AitaBusyIndicator(
                                            Modifier.size(20.dp),
                                            stateValues.AccentTextColor,
                                        )
                                    }
                                } else null,
                            enabled = !busy && loaded && !restoreFailed,
                            autoLoading = false,
                            confirmationRequired = false,
                            onClick = {
                                if (!filePicker.busy) {
                                    recorder.stop()
                                    parserRevision++
                                    filePicker.launch()
                                }
                            },
                        )
                        if (recorder.supported)
                            actionButton(
                                Modifier.weight(1f),
                                text = stockFileText("capture", language),
                                enabled = !busy && !filePicker.busy,
                                autoLoading = false,
                                confirmationRequired = false,
                                onClick = {
                                    captureControls = !captureControls
                                    if (!captureControls) recorder.stop() else refreshWindows()
                                },
                            )
                    }
                }
                if (captureControls && recorder.supported) {
                    item {
                        Text(
                            text("help"),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                        )
                    }
                    item {
                        SimpleDropdownField(
                            title = text("window"),
                            selectedId = windowId,
                            placeholder = text("choose"),
                            options = windows.map { DropdownOption(it.id, it.title) },
                            restoreLastChoice = false,
                            onSelected = {
                                if (!busy) {
                                    recorder.stop()
                                    parserRevision++
                                    windowId = it
                                    scope.launch { parserLock.withLock { parser.reset() } }
                                }
                            },
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            actionButton(
                                Modifier.weight(1f),
                                text = text("refresh"),
                                loading = refreshing,
                                enabled = !busy && !refreshing,
                                autoLoading = false,
                                confirmationRequired = false,
                                onClick = ::refreshWindows,
                            )
                            actionButton(
                                Modifier.weight(1f),
                                text = text("controls"),
                                enabled = !busy && loaded && !restoreFailed,
                                autoLoading = false,
                                confirmationRequired = false,
                                onClick = {
                                    if (windowId.isBlank()) error = text("choose")
                                    else if (!current()) error = text("owner_changed")
                                    else scope.launch { recorder.start(windowId, language) }
                                },
                            )
                        }
                    }
                }
                if (
                    capture.status in setOf(StockCaptureStatus.RECORDING, StockCaptureStatus.PAUSED)
                )
                    item {
                        actionButton(
                            Modifier.fillMaxWidth(),
                            text = text("review"),
                            autoLoading = false,
                            confirmationRequired = false,
                            onClick = { recorder.stop() },
                        )
                    }
                if (recorder.supported)
                    capture.messageCode?.let { code ->
                        item {
                            Text(
                                stockCaptureStatusMessage(code, language),
                                color =
                                    if (capture.status == StockCaptureStatus.ERROR)
                                        stateValues.ErrorColor
                                    else stateValues.PlaceholderTextColor,
                                fontSize = stateValues.smallTextSize,
                            )
                        }
                    }
                if (recognized != null)
                    item {
                        actionButton(
                            Modifier.fillMaxWidth(),
                            text = text("map"),
                            enabled = !busy,
                            autoLoading = false,
                            confirmationRequired = false,
                            onClick = {
                                recorder.pause()
                                mapping = true
                            },
                        )
                    }
                item {
                    Text(
                        "${text("reviewed")}: ${rows.count { it.reviewed }} / ${rows.size} · ${text("done")}: ${rows.count { it.complete }}",
                        color = stateValues.TextColor,
                        fontSize = stateValues.textSize,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text("quantity_help"),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                    )
                }
                if (rows.isEmpty())
                    item { Text(text("empty"), color = stateValues.PlaceholderTextColor) }
                if (rows.any { !it.complete })
                    item {
                        actionButton(
                            Modifier.fillMaxWidth(),
                            text = text("check_all"),
                            autoLoading = false,
                            enabled = !busy,
                            confirmationRequired = false,
                            onClick = {
                                val barcodeOwners =
                                    stockImportBarcodeOwners(stateValues.stock.orEmpty(), storeId)
                                rows = rows.map { row ->
                                    if (
                                        row.selected &&
                                            !row.complete &&
                                            row.problems(config).isEmpty() &&
                                            row.warnings.none { it !in setOf("check_ocr") } &&
                                            !row.hasExistingBarcode(barcodeOwners)
                                    )
                                        row.copy(reviewed = true)
                                    else row
                                }
                            },
                        )
                        Text(
                            text("check_all_help"),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                        )
                    }
                item {
                    actionButton(
                        Modifier.fillMaxWidth(),
                        text = text("discard"),
                        iconPath = stateValues.drawablePathIconDelete,
                        enabledColor = stateValues.BackgroundColor,
                        textColor = stateValues.TextColor,
                        subTextColor = stateValues.PlaceholderTextColor,
                        autoLoading = false,
                        confirmationRequired = false,
                        subText = text("discard_help"),
                        enabled = !busy,
                        onClick = {
                            confirmation = "discard"
                        },
                    )
                }
                items(rows, key = { it.id }) { row ->
                    val duplicate = row.hasExistingBarcode(stateValues.stock.orEmpty(), storeId)
                    Row(
                        Modifier.fillMaxWidth()
                            .border(
                                1.dp,
                                if (duplicate || (!row.reviewed && row.warnings.isNotEmpty()))
                                    stateValues.ErrorColor.copy(alpha = .55f)
                                else stateValues.AccentColor.copy(alpha = .4f),
                                RoundedCornerShape(stateValues.cornerRadius),
                            )
                            .clickable(enabled = !busy && !row.complete) {
                                recorder.pause()
                                editId = row.id
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = row.selected,
                            enabled = !busy && !row.complete,
                            onCheckedChange = { update(row.copy(selected = it)) },
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                row.name,
                                color = stateValues.TextColor,
                                fontSize = stateValues.textSize,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                listOf(
                                        row.barcode,
                                        "${row.sale} ${row.currency}",
                                        "${row.quantity} ${row.unit}",
                                    )
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.smallTextSize,
                            )
                            if (row.complete || row.reviewed)
                                Text(
                                    text(if (row.complete) "done" else "reviewed"),
                                    color = stateValues.AccentColor,
                                    fontSize = stateValues.smallTextSize,
                                )
                            if (duplicate)
                                Text(
                                    text("duplicate"),
                                    color = stateValues.ErrorColor,
                                    fontSize = stateValues.smallTextSize,
                                )
                            else if (!row.reviewed && row.warnings.isNotEmpty())
                                Text(
                                    row.warnings.joinToString(" · ") {
                                        stockFileText(it, language)
                                    },
                                    color = stateValues.ErrorColor,
                                    fontSize = stateValues.smallTextSize,
                                )
                        }
                    }
                }
            }
            if (notice.isNotBlank())
                Text(notice, color = stateValues.AccentColor, fontSize = stateValues.smallTextSize)
            if (error.isNotBlank())
                Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            if (importing)
                actionButton(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    text = text(if (stopImport) "finishing_save" else "pause_import"),
                    autoLoading = false,
                    confirmationRequired = false,
                    onClick = { stopImport = true },
                )
            if (confirmation != null) {
                val target =
                    stateValues.stores
                        .findStoreOrBranchForUi(storeId)
                        ?.name
                        ?.extractLocalizedString(language)
                        .orEmpty()
                val message =
                    if (confirmation == "discard") text("discard_help")
                    else
                        stockFileText("confirm_import", language)
                            .replace(
                                "{count}",
                                rows.count { it.selected && !it.complete }.toString(),
                            )
                            .replace("{store}", target)
                StockPlanConfirmation(
                    message,
                    onCancel = { confirmation = null },
                    onConfirm = {
                        val action = confirmation
                        confirmation = null
                        if (action == "discard") discardReview() else startImport()
                    },
                )
            } else
                actionButton(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    text = text("confirm"),
                    loading = busy,
                    enabled = !busy && loaded,
                    autoLoading = false,
                    confirmationRequired = false,
                    onClick = { if (!filePicker.busy) confirmation = "import" },
                )
        }
    }
}

@Composable
private fun AppConfiguration.StockScreenRowEditor(
    original: ScreenStockRow,
    storeId: String,
    onApply: (ScreenStockRow) -> Unit,
) {
    var row by remember(original.id) { mutableStateOf(original) }
    var error by remember { mutableStateOf("") }
    val language = stateValues.appLanguage
    fun text(key: String) = stockScreenText(key, language)
    val config = stateValues.globalAppConfiguration
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "${text("source")}: ${original.source}",
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                )
            }
            items(original.alternatives) { alternative ->
                Text(
                    alternative,
                    color = stateValues.ErrorColor,
                    fontSize = stateValues.smallTextSize,
                )
            }
            if (!original.reviewed)
                item {
                    Text(
                        original.warnings.joinToString(" · ") { stockFileText(it, language) },
                        color = stateValues.ErrorColor,
                        fontSize = stateValues.smallTextSize,
                    )
                }
            if (!row.importStarted && !row.itemSaved) {
                item {
                    ScreenImportInput(stateValues.stringName, row.name) {
                        row = row.copy(name = it.take(300))
                    }
                }
                item {
                    ScreenImportInput(stateValues.stringBarcode, row.barcode) {
                        row = row.copy(barcode = it.take(64))
                    }
                }
                item {
                    ScreenImportNumber(
                        stateValues.stringSalePrice,
                        row.sale,
                        "SALE" in row.resolvedNumbers,
                    ) {
                        row =
                            row.copy(
                                sale = it.take(40),
                                resolvedNumbers = row.resolvedNumbers + "SALE",
                            )
                    }
                }
                item {
                    ScreenImportNumber(
                        stateValues.stringSupplyPrice,
                        row.supply,
                        "SUPPLY" in row.resolvedNumbers,
                    ) {
                        row =
                            row.copy(
                                supply = it.take(40),
                                resolvedNumbers = row.resolvedNumbers + "SUPPLY",
                            )
                    }
                }
                item {
                    SimpleDropdownField(
                        title = localizedStringResource(268, "Currency"),
                        selectedId = row.currency,
                        placeholder = localizedStringResource(269, "Select currency"),
                        restoreLastChoice = false,
                        options =
                            config.countries
                                .withSupportedCountries()
                                .flatMap { it.currencies }
                                .distinctBy { it.code }
                                .map { DropdownOption(it.code, it.code) },
                        onSelected = { row = row.copy(currency = it) },
                    )
                }
                item {
                    SimpleDropdownField(
                        title = stateValues.stringMeasurementUnit,
                        selectedId = row.resolveUnit(config.goodsItemsQuantityUnits)?.id.orEmpty(),
                        placeholder = stateValues.stringMeasurementUnit,
                        restoreLastChoice = false,
                        options =
                            config.goodsItemsQuantityUnits.map {
                                DropdownOption(
                                    it.id,
                                    it.immutableUnitName.extractLocalizedString(language).orEmpty(),
                                )
                            },
                        onSelected = { id ->
                            val unit = config.goodsItemsQuantityUnits.first { it.id == id }
                            row =
                                row.copy(
                                    unitId = id,
                                    unit =
                                        unit.immutableUnitName
                                            .extractLocalizedString(language)
                                            .orEmpty(),
                                )
                        },
                    )
                }
                item {
                    SimpleDropdownField(
                        title =
                            "${stateValues.stringCategory}${row.category.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
                        selectedId = row.categoryId,
                        placeholder = stateValues.stringSelectCategory,
                        restoreLastChoice = false,
                        options =
                            listOf(DropdownOption("", text("ignore"))) +
                                stateValues.goodsCategories.orEmpty().map {
                                    DropdownOption(
                                        it.id,
                                        it.name.visibleGoodsCategoryName(language, it.id),
                                    )
                                },
                        onSelected = { row = row.copy(categoryId = it) },
                    )
                }
                item {
                    ScreenImportInput(stateValues.stringDescription, row.description) {
                        row = row.copy(description = it.take(2000))
                    }
                }
            }
            if (!row.importStarted)
                item {
                    ScreenImportNumber(
                        localizedStringResource(271, "Quantity"),
                        row.quantity,
                        "QUANTITY" in row.resolvedNumbers,
                    ) {
                        row =
                            row.copy(
                                quantity = it.take(40),
                                resolvedNumbers = row.resolvedNumbers + "QUANTITY",
                            )
                    }
                }
            else
                item {
                    Text(
                        text("save_error"),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                    )
                }
            item {
                Text(
                    text("quantity_help"),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                )
            }
        }
        if (error.isNotBlank())
            Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        actionButton(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            text = text("checked"),
            autoLoading = false,
            confirmationRequired = false,
            onClick = {
                val problems = row.problems(config)
                when {
                    problems.isNotEmpty() ->
                        error =
                            text("invalid") +
                                "\n" +
                                problems.joinToString(" · ") {
                                    stockScreenFieldTitle(it, this@StockScreenRowEditor)
                                }
                    row.hasExistingBarcode(stateValues.stock.orEmpty(), storeId) ->
                        error = text("duplicate")
                    else -> onApply(row.copy(reviewed = true))
                }
            },
        )
    }
}

@Composable
private fun AppConfiguration.ScreenImportNumber(
    title: String,
    value: String,
    resolved: Boolean,
    change: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenImportInput(title, value, number = true, change = change)
        if (!resolved && screenStockNumberAmbiguous(value)) {
            Text(
                stockScreenText("ambiguous_number", stateValues.appLanguage),
                color = stateValues.ErrorColor,
                fontSize = stateValues.smallTextSize,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(screenStockNumber(value), screenStockGroupedNumber(value))
                    .distinct()
                    .forEach { choice ->
                        actionButton(
                            Modifier.weight(1f),
                            text = choice,
                            autoLoading = false,
                            confirmationRequired = false,
                            onClick = { change(choice) },
                        )
                    }
            }
        }
    }
}

@Composable
private fun AppConfiguration.ScreenImportInput(
    title: String,
    value: String,
    number: Boolean = false,
    change: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold,
        )
        SimpleTextInput(
            Modifier.fillMaxWidth(),
            value = value,
            placeholder = title,
            autoFocus = false,
            keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
            onValueChange = change,
        )
    }
}

internal fun stockScreenFieldTitle(field: String, app: AppConfiguration): String =
    with(app) {
        when (field) {
            "NAME" -> stateValues.stringName
            "BARCODE" -> stateValues.stringBarcode
            "QUANTITY" -> localizedStringResource(271, "Quantity")
            "UNIT" -> stateValues.stringMeasurementUnit
            "SALE" -> stateValues.stringSalePrice
            "SUPPLY" -> stateValues.stringSupplyPrice
            "CATEGORY" -> stateValues.stringCategory
            "DESCRIPTION" -> stateValues.stringDescription
            "CURRENCY" -> localizedStringResource(268, "Currency")
            else -> stockScreenText("ignore", stateValues.appLanguage)
        }
    }

@Composable
private fun AppConfiguration.StockScreenColumnMapper(
    result: ScreenStockRecognition,
    onApply: (List<ScreenStockColumn>) -> Unit,
) {
    var sample by remember { mutableStateOf<ScreenStockLine?>(null) }
    var columns by remember { mutableStateOf<List<ScreenStockColumn>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    val language = stateValues.appLanguage
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stockScreenText("map_help", language),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                )
            }
            if (sample == null)
                items(result.preview.filter { it.words.size >= 2 }.take(60)) { line ->
                    Text(
                        line.text,
                        color = stateValues.TextColor,
                        fontSize = stateValues.smallTextSize,
                        modifier =
                            Modifier.fillMaxWidth()
                                .border(
                                    1.dp,
                                    stateValues.AccentColor.copy(alpha = .4f),
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    sample = line
                                    columns =
                                        line.words.map { word ->
                                            val center = word.x + word.width / 2
                                            ScreenStockColumn(
                                                result.columns
                                                    .minByOrNull {
                                                        kotlin.math.abs(it.center - center)
                                                    }
                                                    ?.field ?: ScreenStockField.IGNORE,
                                                center,
                                                word.text,
                                            )
                                        }
                                }
                                .padding(12.dp),
                    )
                }
            else
                items(columns.indices.toList()) { index ->
                    SimpleDropdownField(
                        title = columns[index].title,
                        selectedId = columns[index].field.name,
                        placeholder = stockScreenText("ignore", language),
                        restoreLastChoice = false,
                        options =
                            ScreenStockField.entries.map {
                                DropdownOption(
                                    it.name,
                                    stockScreenFieldTitle(it.name, this@StockScreenColumnMapper),
                                )
                            },
                        onSelected = { selected ->
                            columns = columns.mapIndexed { i, column ->
                                if (i == index)
                                    column.copy(field = ScreenStockField.valueOf(selected))
                                else column
                            }
                        },
                    )
                }
        }
        if (error.isNotBlank())
            Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        actionButton(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            text = stockScreenText("save_map", language),
            autoLoading = false,
            confirmationRequired = false,
            onClick = {
                val chosen =
                    columns
                        .filter { it.field != ScreenStockField.IGNORE }
                        .map { it.field }
                        .distinct()
                if (ScreenStockField.NAME !in chosen || chosen.size < 2)
                    error = stockScreenText("map_help", language)
                else onApply(columns)
            },
        )
    }
}
