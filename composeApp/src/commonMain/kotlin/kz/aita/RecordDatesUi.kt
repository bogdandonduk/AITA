package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

private val recordOptionsSaver = listSaver<RecordListOptions, String>(
    save = { listOf(it.dateField,it.fromDate,it.throughDate,it.sort,it.ascending.toString()) },
    restore = { RecordListOptions(it[0],it[1],it[2],it[3],it[4].toBoolean()) }
)

@Composable
internal fun rememberRecordOptions(vararg keys: Any?): MutableState<RecordListOptions> =
    rememberSaveable(*keys, stateSaver = recordOptionsSaver) { mutableStateOf(RecordListOptions()) }

@Composable
internal fun AppConfiguration.RecordDates(created: Long, modified: Long, registration: Boolean = false) {
    fun label(key: String) = recordDateText(key, stateValues.appLanguage)
    fun time(value: Long) = if (value > 0) receiptUiDateTime(value) else label("unknown")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${label(if (registration) "registered" else "created")}: ${time(created)}",
            color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
        Text("${label("modified")}: ${time(modified)}",
            color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
    }
}

@Composable
internal fun AppConfiguration.RecordListTools(options: RecordListOptions, onChange: (RecordListOptions) -> Unit,
                                               includeSort: Boolean = true, collapsible: Boolean = true) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var calendarTarget by rememberSaveable { mutableStateOf<String?>(null) }
    fun label(key: String) = recordDateText(key, stateValues.appLanguage)
    val active = options.fromDate.isNotBlank() || options.throughDate.isNotBlank() || options.sort != "default"
    calendarTarget?.let { target ->
        TransactionHistoryCalendarDialog(
            title = label(if (target == "from") "from" else "through"),
            selectedDateText = if (target == "from") options.fromDate else options.throughDate,
            onDismiss = { calendarTarget = null },
            onDateSelected = { date ->
                onChange(if (target == "from") options.copy(fromDate = date) else options.copy(throughDate = date))
                calendarTarget = null
            }
        )
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if (collapsible) actionButton(text=label("tools") + if(active) " •" else "",iconPath=sortActionIconPath(),
            confirmationRequired=false,autoLoading=false,onClick={expanded=!expanded})
        if (!collapsible || expanded) {
            SimpleDropdownField(title=label("field"),selectedId=options.dateField,
                options=listOf("created","modified").map { DropdownOption(it,label(it)) },
                placeholder="",restoreLastChoice=false,onSelected={onChange(options.copy(dateField=it))})
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField), verticalAlignment = Alignment.Bottom) {
                TransactionHistoryDateButton(Modifier.weight(1f), label("from"), options.fromDate) { calendarTarget = "from" }
                TransactionHistoryDateButton(Modifier.weight(1f), label("through"), options.throughDate) { calendarTarget = "through" }
            }
            if (options.fromDate.isNotBlank() || options.throughDate.isNotBlank()) actionButton(text = localizedStringResource(950, "Clear period"),
                iconPath = stateValues.drawablePathIconCancel, confirmationRequired = false, autoLoading = false,
                onClick = { onChange(options.copy(fromDate = "", throughDate = "")) })
            if (!options.valid()) Text(label("invalid"),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize)
            if (includeSort) {
                SimpleDropdownField(title=label("sort"),selectedId=options.sort,
                    options=listOf("default","created","modified").map { DropdownOption(it,label(it)) },
                    placeholder="",restoreLastChoice=false,onSelected={onChange(options.copy(sort=it))})
                if(options.sort!="default") tabRowWidget(modifier=Modifier.fillMaxWidth(),compact=true,
                    tabs=listOf(TabContent("descending",label("descending"),icon=AitaTabIcon.Descending) {onChange(options.copy(ascending=false))},
                        TabContent("ascending",label("ascending"),icon=AitaTabIcon.Ascending) {onChange(options.copy(ascending=true))}),
                    selectedIndexInitial=if(options.ascending) "ascending" else "descending")
            }
        }
    }
}
