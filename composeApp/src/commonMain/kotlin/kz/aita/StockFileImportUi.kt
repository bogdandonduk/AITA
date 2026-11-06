package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
internal fun AppConfiguration.StockFileMappingContent(
    file: StockImportFile,
    onApply: (List<ScreenStockRow>) -> Unit,
) {
    val language = stateValues.appLanguage
    fun text(key: String) = stockFileText(key, language)
    val scope = rememberCoroutineScope()
    var workbook by remember(file) { mutableStateOf<StockImportWorkbook?>(null) }
    var sheetIndex by remember(file) { mutableStateOf(0) }
    var header by remember(file) { mutableStateOf(0) }
    var encoding by remember(file) { mutableStateOf(StockTextEncoding.UTF8) }
    var separator by remember(file) { mutableStateOf("auto") }
    var parsing by remember(file) { mutableStateOf(false) }
    var error by remember(file) { mutableStateOf("") }
    val config = stateValues.globalAppConfiguration
    var currency by
        remember(file) {
            mutableStateOf(
                config.countries
                    .withSupportedCountries()
                    .firstOrNull {
                        it.locale.equals(
                            stateValues.stores
                                .findStoreOrBranchForUi(stateValues.activeStoreId.orEmpty())
                                ?.countryLocales
                                ?.firstOrNull(),
                            true,
                        )
                    }
                    ?.currencies
                    ?.firstOrNull()
                    ?.code
                    .orEmpty()
            )
        }
    var defaultUnitId by remember(file) { mutableStateOf("") }
    var supplySame by remember(file) { mutableStateOf(false) }
    val table = workbook?.tables?.getOrNull(sheetIndex)
    var mapping by
        remember(table, header) {
            mutableStateOf(
                table
                    ?.let { selected ->
                        List(selected.rows.maxOfOrNull { it.size } ?: 0) {
                            stockImportHeaderField(
                                selected.rows.getOrNull(header)?.getOrNull(it).orEmpty()
                            )
                        }
                    }
                    .orEmpty()
            )
        }
    LaunchedEffect(file, encoding, separator) {
        parsing = true
        error = ""
        workbook = null
        try {
            workbook =
                withTimeout(45_000) {
                    withContext(Dispatchers.Default) {
                        readStockImportFile(
                            file,
                            when (separator) {
                                "tab" -> '\t'
                                "semicolon" -> ';'
                                "comma" -> ','
                                else -> null
                            },
                            encoding,
                        )
                    }
                }
            sheetIndex = 0
            header = 0
        } catch (_: TimeoutCancellationException) {
            error = text("file_error")
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (problem: StockFileProblem) {
            error = text(problem.code)
        } catch (_: Exception) {
            error = text("file_error")
        } finally {
            parsing = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    file.name.take(160),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!file.name.endsWith(".xlsx", true)) {
                item {
                    SimpleDropdownField(
                        title = text("encoding"),
                        selectedId = encoding.name,
                        placeholder = text("encoding"),
                        restoreLastChoice = false,
                        options =
                            StockTextEncoding.entries.map {
                                DropdownOption(
                                    it.name,
                                    when (it) {
                                        StockTextEncoding.UTF8 -> "UTF-8"
                                        StockTextEncoding.WINDOWS1251 -> "Windows-1251"
                                        StockTextEncoding.UTF16LE -> "UTF-16 LE"
                                        StockTextEncoding.UTF16BE -> "UTF-16 BE"
                                    },
                                )
                            },
                        onSelected = { encoding = StockTextEncoding.valueOf(it) },
                    )
                }
                item {
                    SimpleDropdownField(
                        title = text("delimiter"),
                        selectedId = separator,
                        placeholder = text("auto"),
                        restoreLastChoice = false,
                        options =
                            listOf(
                                DropdownOption("auto", text("auto")),
                                DropdownOption("comma", ","),
                                DropdownOption("semicolon", ";"),
                                DropdownOption("tab", "Tab (⇥)"),
                            ),
                        onSelected = { separator = it },
                    )
                }
            }
            workbook?.let { book ->
                if (book.tables.size > 1)
                    item {
                        SimpleDropdownField(
                            title = text("sheet"),
                            selectedId = sheetIndex.toString(),
                            placeholder = text("sheet"),
                            restoreLastChoice = false,
                            options =
                                book.tables.mapIndexed { i, sheet ->
                                    DropdownOption(i.toString(), sheet.name)
                                },
                            onSelected = {
                                sheetIndex = it.toInt()
                                header = 0
                            },
                        )
                    }
            }
            table?.let { selected ->
                item {
                    SimpleDropdownField(
                        title = text("header"),
                        selectedId = header.toString(),
                        placeholder = text("header"),
                        restoreLastChoice = false,
                        options =
                            selected.rows.take(20).mapIndexed { i, row ->
                                DropdownOption(
                                    i.toString(),
                                    "${i+1} · ${row.joinToString(" | ").take(120)}",
                                )
                            },
                        onSelected = { header = it.toInt() },
                    )
                }
                item {
                    Text(
                        "${text("rows")}: ${(selected.rows.size - header - 1).coerceAtLeast(0)}",
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                    )
                }
                items(mapping.indices.toList()) { index ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SimpleDropdownField(
                            title =
                                selected.rows
                                    .getOrNull(header)
                                    ?.getOrNull(index)
                                    .orEmpty()
                                    .ifBlank { "${index+1}" }
                                    .take(100),
                            selectedId = mapping[index].name,
                            placeholder = text("ignore"),
                            restoreLastChoice = false,
                            options =
                                ScreenStockField.entries.map {
                                    DropdownOption(
                                        it.name,
                                        stockScreenFieldTitle(it.name, this@StockFileMappingContent),
                                    )
                                },
                            onSelected = { value ->
                                mapping = mapping.mapIndexed { i, old ->
                                    if (i == index) ScreenStockField.valueOf(value) else old
                                }
                            },
                        )
                        Text(
                            selected.rows
                                .drop(header + 1)
                                .take(2)
                                .mapNotNull { it.getOrNull(index) }
                                .joinToString(" · ")
                                .take(150),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                        )
                    }
                }
                item {
                    SimpleDropdownField(
                        title = localizedStringResource(268, "Currency"),
                        selectedId = currency,
                        placeholder = localizedStringResource(269, "Select currency"),
                        restoreLastChoice = false,
                        options =
                            config.countries
                                .withSupportedCountries()
                                .flatMap { it.currencies }
                                .distinctBy { it.code }
                                .map { DropdownOption(it.code, it.code) },
                        onSelected = { currency = it },
                    )
                }
                item {
                    SimpleDropdownField(
                        title = text("default_unit"),
                        selectedId = defaultUnitId,
                        placeholder = stateValues.stringMeasurementUnit,
                        restoreLastChoice = false,
                        options =
                            listOf(DropdownOption("", text("ignore"))) +
                                config.goodsItemsQuantityUnits.map {
                                    DropdownOption(
                                        it.id,
                                        it.immutableUnitName
                                            .extractLocalizedString(language)
                                            .orEmpty(),
                                    )
                                },
                        onSelected = { defaultUnitId = it },
                    )
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = supplySame, onCheckedChange = { supplySame = it })
                        Text(
                            text("supply_same"),
                            color = stateValues.TextColor,
                            fontSize = stateValues.smallTextSize,
                        )
                    }
                }
            }
        }
        if (error.isNotBlank())
            Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        actionButton(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            text = text("preview"),
            loading = parsing,
            enabled = !parsing,
            autoLoading = false,
            confirmationRequired = false,
            onClick = {
                val selected = table
                if (selected == null) {
                    if (error.isBlank()) error = text("file_empty")
                } else {
                    parsing = true
                    error = ""
                    scope.launch {
                        try {
                            val rows =
                                withContext(Dispatchers.Default) {
                                    stockFileRows(
                                        selected,
                                        header,
                                        mapping,
                                        currency,
                                        config.goodsItemsQuantityUnits.firstOrNull {
                                            it.id == defaultUnitId
                                        },
                                        supplySame,
                                    )
                                }
                            onApply(rows)
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (problem: StockFileProblem) {
                            error = text(problem.code)
                        } catch (_: Exception) {
                            error = text("file_error")
                        } finally {
                            parsing = false
                        }
                    }
                }
            },
        )
    }
}
