@file:OptIn(kotlin.time.ExperimentalTime::class)
package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private fun supportDate(time: Long): String = Instant.fromEpochMilliseconds(time).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
private fun supportTime(time: Long): String = Instant.fromEpochMilliseconds(time).toLocalDateTime(TimeZone.currentSystemDefault()).let {
    "${it.hour.toString().padStart(2,'0')}:${it.minute.toString().padStart(2,'0')}"
}

@Composable
fun AppConfiguration.MenuSupportScreen() {
    val account=stateValues.userAccount?.id
    val employment by CompanyEmployment.state.collectAsState(initial=null)
    val window=LocalWindowInfo.current
    LaunchedEffect(account,window.isWindowFocused) {
        if(account==null) { CompanyEmployment.clear(); return@LaunchedEffect }
        var ticks=0
        while(isActive) {
            CompanyEmployment.expire()
            if(window.isWindowFocused && ticks%25==0) CompanyEmployment.refresh()
            delay(1000); ticks++
        }
    }
    key(account) {
        var selected by remember { mutableStateOf("chat") }
        val canAgent=companyCanOpenSupport(employment,account)
        val tab=selected.takeUnless { it=="agent" && !canAgent } ?: "chat"
        LaunchedEffect(canAgent) { if(!canAgent && selected=="agent") selected="chat" }
        AitaScreenColumn(
            Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,
            appBar = {
                ScreenAppBarWidget(title=localizedStringResource(813,"Support"),iconPath=stateValues.drawablePathIconSupport,
                    onBack={ coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } })
            }
        ) {
            tabRowWidget(Modifier.widthIn(max=960.dp).fillMaxWidth().padding(horizontal=stateValues.marginTextField,vertical=4.dp),
                tabs=buildList {
                    add(TabContent("faq",localizedStringResource(814,"FAQ")) { selected=it })
                    add(TabContent("chat",authUiText("Chats","Чаты","Чаттар", "Чаттар")) { selected=it })
                    if(canAgent) add(TabContent("agent",authUiText("Agent","Специалист","Маман", "Агент")) { selected=it })
                },selectedIndexInitial=tab)
            if(account==null) MessageText(text=authUiText("Sign in to contact support","Войдите для связи с поддержкой","Қолдауға хабарласу үшін кіріңіз", "Колдоого кайрылуу үчүн кириңиз"))
            else if(tab=="faq") SupportHelpPane(Modifier.weight(1f))
            else key(account,tab) {
                SupportInboxPane(account,tab=="agent",employment?.capabilities.orEmpty(),Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AppConfiguration.SupportHelpPane(modifier: Modifier) {
    var search by remember { mutableStateOf("") }
    val entries=supportFaqEntries().filter { entry -> search.isBlank() ||
        localizedStringResource(entry.questionId,entry.questionFallback).contains(search,true) ||
        localizedStringResource(entry.answerId,entry.answerFallback).contains(search,true) }
    Column(modifier.widthIn(max=900.dp).fillMaxWidth()) {
        SimpleTextInput(Modifier.fillMaxWidth(),search,localizedStringResource(816,"Search FAQ"),autoFocus=false,
            leadingIconPath=stateValues.drawablePathIconSearch,onValueChange={ search=it })
        LazyColumn(Modifier.fillMaxSize().padding(stateValues.marginTextField),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(entries,key={ it.questionId }) { SupportFaqCard(it) }
        }
    }
}

@Composable
private fun AppConfiguration.SupportInboxPane(account: String,agent: Boolean,capabilities: Set<String>,modifier: Modifier) {
    var tickets by remember { mutableStateOf<List<SupportTicketDataModel>>(emptyList()) }
    var page by remember { mutableStateOf<SupportTicketPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadedFilter by remember { mutableStateOf<String?>(null) }
    var expandedHistory by remember { mutableStateOf(false) }
    var pageEpoch by remember { mutableStateOf(0) }
    var feedback by remember { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var filter by remember { mutableStateOf(if(agent) "open" else "all") }
    var selected by remember { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf(false) }
    var metrics by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    val signals by SupportWorkspaceSignals.revision.collectAsState()
    val window=LocalWindowInfo.current
    val scope=rememberCoroutineScope()
    val split=stateValues.screenWidth>=840.dp
    LaunchedEffect(account,agent,filter,signals,refresh) {
        if(agent && !CompanyEmployment.has(CompanyCapability.SUPPORT_QUEUE)) return@LaunchedEffect
        val generation=currentAuthenticatedSessionGeneration()
        if(loadedFilter!=filter) {
            tickets=emptyList(); page=null; expandedHistory=false; pageEpoch++; loadedFilter=filter
        }
        loading=tickets.isEmpty(); feedback=null
        try {
            val response=networkRequest<SupportTicketPage,Unit>(HttpMethod.Get,endpointUrl="support/workspace/tickets",
                query=mapOf("agent" to agent,"filter" to filter),expectedSessionGeneration=generation)
            if(userAccountState.payloadValue?.id!=account || !authenticatedSessionGenerationIsCurrent(generation)) return@LaunchedEffect
            val loaded=response.payload
            if(!response.negative && loaded!=null) {
                val oldest=loaded.tickets.lastOrNull()
                val tail=if(expandedHistory && loaded.nextBeforeId!=null && oldest!=null)
                    tickets.filter { it.updatedAtMillis<oldest.updatedAtMillis ||
                        (it.updatedAtMillis==oldest.updatedAtMillis && it.id<oldest.id) } else emptyList()
                tickets=(loaded.tickets+tail).distinctBy { it.id }
                if(!expandedHistory || loaded.nextBeforeId==null) page=loaded
            }
            else { feedback=response.message; if(agent && response.httpStatusCode==403) CompanyEmployment.clear() }
        } finally { loading=false }
    }
    LaunchedEffect(window.isWindowFocused) {
        while(isActive && window.isWindowFocused) { delay(15_000); refresh++ }
    }
    Column(modifier.widthIn(max=1080.dp).fillMaxWidth()) {
        if(agent) tabRowWidget(Modifier.fillMaxWidth().padding(horizontal=8.dp),tabs=buildList {
            add(TabContent("open",authUiText("Open","Открытые","Ашық", "Ачуу")) { filter=it; metrics=false })
            add(TabContent("unassigned",authUiText("Unassigned","Без специалиста","Тағайындалмаған", "Дайындалган эмес")) { filter=it; metrics=false })
            add(TabContent("mine",authUiText("Mine","Мои","Менікі", "Менин")) { filter=it; metrics=false })
            add(TabContent("closed",authUiText("Closed","Закрытые","Жабық", "Жабык")) { filter=it; metrics=false })
            if(CompanyCapability.SUPPORT_METRICS in capabilities)
                add(TabContent("metrics",authUiText("Metrics","Показатели","Көрсеткіштер", "Көрсөткүчтөр")) { metrics=true })
        },selectedIndexInitial=if(metrics) "metrics" else filter,textSize=stateValues.smallTextSize)
        if(metrics && CompanyCapability.SUPPORT_METRICS in capabilities) {
            SupportMetricsPane(Modifier.weight(1f),account); return@Column
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if(split || (selected==null && !composing)) Column(
                if(split) Modifier.width(300.dp).fillMaxHeight() else Modifier.fillMaxSize()
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(agent) authUiText("Support inbox","Обращения","Өтініштер", "Колдоонун кирген маектери") else authUiText("Your conversations","Ваши диалоги","Сіздің диалогтарыңыз", "Маектериңиз"),
                        Modifier.weight(1f),color=stateValues.TextColor,fontWeight=FontWeight.Bold,fontSize=stateValues.textSize)
                    if(!agent) actionButton(text="",iconPath=stateValues.drawablePathIconAdd,
                        iconContentDescription=authUiText("New conversation","Новый диалог","Жаңа диалог", "Жаңы маек"),autoLoading=false,
                        onClick={ composing=true; selected=null })
                }
                feedback?.let { SupportInlineError(it) }
                if(loading && tickets.isEmpty()) LoadingSkeleton(layout = LoadingLayout.Conversation, modifier = Modifier.fillMaxWidth(), rows =4)
                else LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(tickets.isEmpty()) item {
                        MessageText(Modifier.fillParentMaxSize().padding(24.dp),authUiText("No conversations here yet","Здесь пока нет диалогов","Мұнда әзірге диалог жоқ", "Бул жерде маектер азырынча жок"))
                    }
                    items(tickets,key={ it.id }) { ticket ->
                        val unread=if(agent) ticket.unreadForAgentCount else ticket.unreadForUserCount
                        Column(Modifier.fillMaxWidth().padding(horizontal=6.dp).clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(if(selected==ticket.id) stateValues.AccentColor.copy(alpha=0.1f) else Color.Transparent)
                            .clickable { selected=ticket.id; composing=false }.padding(12.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(ticket.subject,Modifier.weight(1f),color=stateValues.TextColor,fontWeight=FontWeight.Bold,
                                    fontSize=stateValues.textSize,maxLines=1,overflow=TextOverflow.Ellipsis)
                                if(unread>0) Text(unread.toString(),Modifier.padding(start=6.dp).clip(RoundedCornerShape(20.dp))
                                    .background(stateValues.AccentColor).padding(horizontal=7.dp,vertical=2.dp),
                                    color=stateValues.AccentTextColor,fontSize=stateValues.smallTextSize)
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(ticket.lastMessage,color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.height(5.dp))
                            Text(supportDate(ticket.lastMessageAtMillis)+" · "+supportTime(ticket.lastMessageAtMillis),
                                color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                        }
                    }
                    if(page?.nextBeforeId!=null) item {
                        actionButton(text=authUiText("Older conversations","Более ранние диалоги","Бұрынғы диалогтар", "Эски маектер"),loading=loadingMore,autoLoading=false,
                            enabled=!loadingMore,onClick={
                                val cursor=page ?: return@actionButton
                                val generation=currentAuthenticatedSessionGeneration(); val requestedFilter=filter; val requestedEpoch=pageEpoch
                                loadingMore=true
                                scope.launch {
                                    try {
                                        val result=networkRequest<SupportTicketPage,Unit>(HttpMethod.Get,endpointUrl="support/workspace/tickets",
                                            query=mapOf("agent" to agent,"filter" to requestedFilter,"before_millis" to cursor.nextBeforeMillis,"before_id" to cursor.nextBeforeId),
                                            expectedSessionGeneration=generation)
                                        val loaded=result.payload
                                        if(userAccountState.payloadValue?.id==account && authenticatedSessionGenerationIsCurrent(generation) && filter==requestedFilter && pageEpoch==requestedEpoch) {
                                            if(!result.negative && loaded!=null) { tickets=(tickets+loaded.tickets).distinctBy { it.id }; page=loaded; expandedHistory=true }
                                            else feedback=result.message
                                        }
                                    } finally { loadingMore=false }
                                }
                            })
                    }
                }
            }
            if(split) Box(Modifier.width(1.dp).fillMaxHeight().background(stateValues.PlaceholderTextColor.copy(alpha=0.15f)))
            if(selected!=null || composing) key(account,agent,selected,composing) {
                SupportConversationPane(account,agent,selected,capabilities,
                    if(split) Modifier.weight(1f).fillMaxHeight() else Modifier.fillMaxSize(),
                    onBack={ selected=null; composing=false },onCreated={ selected=it; composing=false; refresh++ })
            } else if(split) Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.Center) {
                MessageText(Modifier.padding(32.dp),authUiText("Choose a conversation","Выберите диалог","Диалогты таңдаңыз", "Маекти тандаңыз"),
                    authUiText("Messages stay together, from the first question to the solution.","От первого вопроса до решения — всё в одном диалоге.","Алғашқы сұрақтан шешімге дейін — барлығы бір диалогта.", "Алгачкы суроодон чечимге чейин билдирүүлөр бир жерде сакталат."))
            }
        }
    }
}

@Composable
private fun AppConfiguration.SupportInlineError(message: List<LocalizedStringDataModel>) {
    Text(message.visibleLocalizedString(stateValues.appLanguage,""),Modifier.fillMaxWidth().padding(10.dp),
        color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize)
}

@Composable
private fun AppConfiguration.SupportConversationPane(account: String,agent: Boolean,ticketId: String?,capabilities: Set<String>,
    modifier: Modifier,onBack: ()->Unit,onCreated: (String)->Unit) {
    var ticket by remember { mutableStateOf<SupportTicketDataModel?>(null) }
    var messages by remember { mutableStateOf<List<SupportMessageDataModel>>(emptyList()) }
    var before by remember { mutableStateOf<Long?>(null) }
    var loading by remember { mutableStateOf(ticketId!=null) }
    var loadingMore by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var feedback by remember { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var draft by remember { mutableStateOf("") }
    var draftRevision by remember { mutableStateOf(newClientSideUuidString()) }
    var draftLoaded by remember { mutableStateOf(false) }
    var edited by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingSupportMessage?>(null) }
    var sending by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("general") }
    var hasNew by remember { mutableStateOf(false) }
    val list=rememberLazyListState()
    val signals by SupportWorkspaceSignals.revision.collectAsState()
    val scope=rememberCoroutineScope()
    val window=LocalWindowInfo.current
    val latestMessages by rememberUpdatedState(messages)
    val latestTicket by rememberUpdatedState(ticket)
    LaunchedEffect(account,agent,ticketId) {
        try {
            val saved=SupportDelivery.journal(account,agent,ticketId)
            if(!edited) { draft=saved.draft; draftRevision=saved.draftRevision.ifBlank { newClientSideUuidString() } }
            pending=saved.pending
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { feedback=eventMessage("support.save_failed") }
        finally { draftLoaded=true }
    }
    LaunchedEffect(draftRevision,draftLoaded) {
        if(draftLoaded) {
            delay(400)
            try { SupportDelivery.saveDraft(account,agent,ticketId,draft,draftRevision) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { feedback=eventMessage("support.save_failed") }
        }
    }
    LaunchedEffect(ticketId,signals,refresh) {
        if(ticketId==null) return@LaunchedEffect
        val generation=currentAuthenticatedSessionGeneration()
        val wasNearEnd=list.firstVisibleItemIndex<=1
        try {
            val result=networkRequest<SupportMessagePage,Unit>(HttpMethod.Get,endpointUrl="support/workspace/messages",
                query=mapOf("ticket_id" to ticketId,"agent" to agent),expectedSessionGeneration=generation)
            val data=result.payload
            if(userAccountState.payloadValue?.id!=account || !authenticatedSessionGenerationIsCurrent(generation)) return@LaunchedEffect
            if(!result.negative && data?.ticket?.id==ticketId) {
                val newLast=data.messages.lastOrNull()?.sequence ?: 0
                val oldLast=messages.lastOrNull()?.sequence ?: 0
                ticket=data.ticket
                messages=(data.messages+messages).distinctBy { it.id }.sortedBy { it.sequence }
                if(before==null && messages.size<=100) before=data.nextBeforeSequence
                if(newLast>oldLast) { if(wasNearEnd) list.scrollToItem(0) else hasNew=true }
            } else { feedback=result.message; if(agent && result.httpStatusCode==403) CompanyEmployment.clear() }
        } finally { loading=false }
    }
    LaunchedEffect(window.isWindowFocused,ticketId) {
        while(isActive && ticketId!=null && window.isWindowFocused) { delay(15_000); refresh++ }
    }
    // A read receipt means a message was visible in this focused dialogue, not merely fetched.
    LaunchedEffect(ticketId,window.isWindowFocused,ticket?.assignedAgentUserId) {
        if(ticketId==null || !window.isWindowFocused) return@LaunchedEffect
        var lastMarked=0L
        snapshotFlow {
            val visible=list.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
            latestMessages.filter { it.id in visible && it.senderUserId!=account }.maxOfOrNull { it.sequence } ?: 0L
        }.collectLatest { cursor ->
            if(cursor>lastMarked && (!agent || latestTicket?.assignedAgentUserId==account)) {
                delay(450)
                val generation=currentAuthenticatedSessionGeneration()
                val result=networkRequest<Pair<SupportTicketDataModel,Boolean>,SupportReadCursorRequest>(HttpMethod.Post,
                    endpointUrl="support/workspace/read",query=mapOf("agent" to agent),body=SupportReadCursorRequest(ticketId,cursor),
                    expectedSessionGeneration=generation)
                if(!result.negative && authenticatedSessionGenerationIsCurrent(generation)) lastMarked=cursor
            }
        }
    }
    fun act(action: String) {
        val current=ticket ?: return
        if(acting) return
        acting=true
        scope.launch {
            try {
                val generation=currentAuthenticatedSessionGeneration()
                val result=networkRequest<SupportTicketDataModel,SupportAgentActionRequest>(HttpMethod.Post,endpointUrl="support/workspace/action",
                    query=mapOf("agent" to agent),body=SupportAgentActionRequest(current.id,action,current.revision),expectedSessionGeneration=generation)
                if(userAccountState.payloadValue?.id==account && authenticatedSessionGenerationIsCurrent(generation)) {
                    val data=result.payload
                    if(!result.negative && data!=null) { ticket=data; feedback=null; SupportWorkspaceSignals.changed() }
                    else { feedback=result.message; refresh++ }
                }
            } finally { acting=false }
        }
    }
    Column(modifier.imePadding()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actionButton(text=authUiText("Chats","Чаты","Чаттар", "Чаттар"),autoLoading=false,onClick=onBack)
            Column(Modifier.fillMaxWidth()) {
                Text(ticket?.subject ?: authUiText("New conversation","Новый диалог","Жаңа диалог", "Жаңы маек"),color=stateValues.TextColor,
                    fontSize=stateValues.textSize,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                ticket?.let { Text(if(it.status=="closed") authUiText("Closed","Закрыт","Жабық", "Жабык")
                    else if(it.assignedAgentUserId==null) authUiText("Waiting for an agent","Ожидает специалиста","Маман күтілуде", "Агентти күтүүдө")
                    else authUiText("Assigned to an agent","Назначен специалист","Маман тағайындалды", "Агентке дайындалды"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize) }
            }
        }
        ticket?.let { current ->
            val mine=current.assignedAgentUserId==account
            val manager=CompanyCapability.SUPPORT_MANAGE in capabilities
            val canResolve=!agent || (current.userId!=account && CompanyCapability.SUPPORT_RESOLVE in capabilities && (mine || manager))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal=12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if(agent && !mine && current.status!="closed" && CompanyCapability.SUPPORT_CLAIM in capabilities && current.userId!=account)
                    actionButton(text=authUiText("Take conversation","Взять в работу","Жұмысқа алу", "Маекти өзүңүзгө алуу"),autoLoading=false,enabled=!acting,loading=acting,onClick={ act("claim") })
                if(agent && mine && CompanyCapability.SUPPORT_CLAIM in capabilities) actionButton(text=authUiText("Release","Освободить","Босату", "Бошотуу"),autoLoading=false,enabled=!acting,onClick={ act("release") })
                if(canResolve) actionButton(text=if(current.status=="closed") authUiText("Reopen","Открыть снова","Қайта ашу", "Кайра ачуу") else authUiText("Resolve","Решено","Шешілді", "Чечүү"),
autoLoading=false,enabled=!acting,confirmationRequired=current.status!="closed",onClick={ act(if(current.status=="closed") "reopen" else "close") })
            }
        }
        feedback?.let { SupportInlineError(it) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if(loading && messages.isEmpty() && pending == null) LoadingSkeleton(layout = LoadingLayout.Message, modifier = Modifier.fillMaxWidth().padding(16.dp), rows =4)
            else if(ticketId==null && pending==null) Column(Modifier.align(Alignment.Center).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(authUiText("Let’s work it out","Давайте разберёмся","Бірге шешейік", "Бирге чечели"),color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(authUiText("Tell us what happened. Never send passwords, sign-in codes or payment secrets.","Расскажите, что случилось. Не отправляйте пароли, коды входа и платёжные секреты.","Не болғанын айтыңыз. Құпия сөздерді, кіру кодтарын және төлем құпияларын жібермеңіз.", "Эмне болгонун айтып бериңиз. Сырсөздөрдү, кирүү коддорун же төлөмдүн жашыруун маалыматын эч качан жөнөтпөңүз."),
                    color=stateValues.PlaceholderTextColor,fontSize=stateValues.textSize)
            } else LazyColumn(state=list,reverseLayout=true,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                pending?.takeUnless { waiting -> messages.any { it.clientMessageId=="v2:$account:${waiting.commandId}" } }
                    ?.let { waiting -> item(key="pending:${waiting.commandId}") {
                    Column(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalAlignment=Alignment.End) {
                        SelectionContainer { Text(waiting.body,Modifier.widthIn(max=520.dp).padding(10.dp),color=stateValues.TextColor,fontSize=stateValues.textSize) }
                        Text(if(sending) authUiText("Sending…","Отправка…","Жіберілуде…", "Жөнөтүлүүдө…") else authUiText("Not confirmed · retry below","Не подтверждено · повторите ниже","Расталмады · төменде қайталаңыз", "Ырасталган жок · төмөндөн кайталаңыз"),
                            color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                    }
                } }
                itemsIndexed(messages.asReversed(),key={ _,message -> message.id }) { index,message ->
                    val older=messages.getOrNull(messages.lastIndex-index-1)
                    Column {
                        if(older==null || supportDate(older.createdAtMillis)!=supportDate(message.createdAtMillis))
                            Box(Modifier.fillMaxWidth().padding(vertical=12.dp),contentAlignment=Alignment.Center) {
                                Text(supportDate(message.createdAtMillis),Modifier.clip(RoundedCornerShape(16.dp))
                                    .background(stateValues.PlaceholderTextColor.copy(alpha=0.09f)).padding(horizontal=12.dp,vertical=4.dp),
                                    color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                            }
                        SupportConversationBubble(message,account)
                    }
                }
                before?.let { cursor -> item(key="older") {
                    actionButton(text=authUiText("Earlier messages","Более ранние сообщения","Бұрынғы хабарламалар", "Мурунку билдирүүлөр"),autoLoading=false,loading=loadingMore,enabled=!loadingMore,onClick={
                        loadingMore=true
                        scope.launch {
                            try {
                                val generation=currentAuthenticatedSessionGeneration()
                                val result=networkRequest<SupportMessagePage,Unit>(HttpMethod.Get,endpointUrl="support/workspace/messages",
                                    query=mapOf("ticket_id" to ticketId,"agent" to agent,"before_sequence" to cursor),expectedSessionGeneration=generation)
                                val data=result.payload
                                if(userAccountState.payloadValue?.id==account && authenticatedSessionGenerationIsCurrent(generation)) {
                                    if(!result.negative && data!=null) { messages=(messages+data.messages).distinctBy { it.id }.sortedBy { it.sequence }; before=data.nextBeforeSequence }
                                    else feedback=result.message
                                }
                            } finally { loadingMore=false }
                        }
                    })
                } }
            }
            if(hasNew) Box(Modifier.align(Alignment.BottomCenter).padding(10.dp)) {
                actionButton(text=authUiText("New messages ↓","Новые сообщения ↓","Жаңа хабарламалар ↓", "Жаңы билдирүүлөр ↓"),autoLoading=false,
                    onClick={ hasNew=false; scope.launch { list.animateScrollToItem(0) } })
            }
        }
        if(ticketId==null) tabRowWidget(Modifier.fillMaxWidth().padding(horizontal=8.dp),tabs=listOf(
            TabContent("general",authUiText("General","Общее","Жалпы", "Жалпы")) { category=it },
            TabContent("technical",authUiText("Technical","Техника","Техника", "Техникалык")) { category=it },
            TabContent("billing",authUiText("Billing","Оплата","Төлем", "Төлөм жана жазылуу")) { category=it },
            TabContent("account",authUiText("Account","Аккаунт","Аккаунт", "Аккаунт")) { category=it },
            TabContent("operations",authUiText("Operations","Работа магазина","Дүкен жұмысы", "Операциялар")) { category=it }
        ),selectedIndexInitial=category,textSize=stateValues.smallTextSize)
        val canSend=if(ticketId==null) !agent else ticket?.let { supportCanReply(it,account,agent,capabilities) }==true
        if(!canSend) Text(authUiText("Reopen or take this conversation to reply.","Откройте диалог или возьмите его в работу, чтобы ответить.","Жауап беру үшін диалогты ашыңыз немесе жұмысқа алыңыз.", "Жооп берүү үчүн бул маекти кайра ачыңыз же өзүңүзгө алыңыз."),
            Modifier.padding(12.dp),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            genericTextField(modifier=Modifier.weight(1f).heightIn(max=160.dp),valueInitial=draft,
                placeholderText=localizedStringResource(824,"Type your message"),singleLine=false,autoFocus=false,
                wide=true,adaptiveMultiline=true,showClearButton=true,parentOwnsValue=true,
                retainTextAcrossRecreation=false,persistTextDraft=false,identityKey="support:$account:$agent:$ticketId",
                onValueChange={ value,applyChange ->
                    val limited=value.take(4000).let { if(it.lastOrNull()?.isHighSurrogate()==true) it.dropLast(1) else it }
                    if(limited!=draft) { edited=true; draft=limited; draftRevision=newClientSideUuidString() }
                    applyChange()
                })
            actionButton(text="",iconPath=supportSendIconPath(),iconRes=supportSendIconFallback(),autoLoading=false,confirmationRequired=false,
                iconContentDescription=if(pending!=null) authUiText("Retry saved message","Повторить сохранённое сообщение","Сақталған хабарламаны қайталау", "Сакталган билдирүүнү кайра жөнөтүү") else localizedStringResource(825,"Send"),
                enabled=canSend && draftLoaded && !sending && (pending!=null || (draft.isNotBlank() && '\u0000' !in draft)),loading=sending,onClick={
                    if(sending) return@actionButton
                    val command=pending ?: PendingSupportMessage(account,newClientSideUuidString(),ticketId,agent,draft.trim(),category,
                        stateValues.activeStoreId.takeIf { !agent },stateValues.appLanguage,draftRevision)
                    val sendGeneration=currentAuthenticatedSessionGeneration()
                    val draftAtClick=draft; val revisionAtClick=draftRevision
                    sending=true; feedback=null; pending=command
                    scope.launch {
                        try {
                            SupportDelivery.saveDraft(account,agent,ticketId,draftAtClick,revisionAtClick)
                            val result=SupportDelivery.send(command)
                            if(userAccountState.payloadValue?.id!=account || !authenticatedSessionGenerationIsCurrent(sendGeneration)) return@launch
                            pending=SupportDelivery.pending(account,agent,ticketId)
                            feedback=result.error
                            val confirmedTicketId=result.ticketId
                            if(confirmedTicketId!=null && !result.uncertain) {
                                if(draftRevision==command.draftRevision) { draft=""; draftRevision=newClientSideUuidString() }
                                if(ticketId==null) onCreated(confirmedTicketId) else { refresh++; list.scrollToItem(0); hasNew=false }
                            }
                        } catch(cancelled: CancellationException) { throw cancelled }
                        catch(_: Exception) {
                            feedback=eventMessage("support.send_unknown")
                            try { pending=SupportDelivery.pending(account,agent,ticketId) }
                            catch(cancelled: CancellationException) { throw cancelled }
                            catch(_: Exception) { /* Keep the in-memory identity when storage cannot be read. */ }
                        }
                        finally { sending=false }
                    }
                })
        }
        if(pending!=null && !sending) Text(authUiText("Send retries the saved message. Your newer draft is kept separately.","Кнопка повторит сохранённое сообщение. Новый черновик остаётся отдельно.","Батырма сақталған хабарламаны қайталайды. Жаңа мәтін бөлек сақталады.", "«Жөнөтүү» сакталган билдирүүнү кайра жөнөтөт. Жаңы долбооруңуз өзүнчө сакталат."),
            Modifier.padding(horizontal=14.dp,vertical=4.dp),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
    }
}

@Composable
private fun AppConfiguration.SupportConversationBubble(message: SupportMessageDataModel,account: String) {
    val mine=message.senderUserId==account
    val agentMessage=message.senderRole=="agent"
    val shape=RoundedCornerShape(topStart=18.dp,topEnd=18.dp,bottomStart=if(mine) 18.dp else 5.dp,bottomEnd=if(mine) 5.dp else 18.dp)
    val foreground=if(agentMessage) stateValues.AccentTextColor else stateValues.TextColor
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=if(mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(Modifier.widthIn(max=maxWidth*0.86f).clip(shape).background(if(agentMessage) stateValues.AccentColor else Color.Transparent)
            .border(if(agentMessage) 0.dp else stateValues.unfocusedBorderWidth,stateValues.PlaceholderTextColor.copy(alpha=0.2f),shape).padding(horizontal=12.dp,vertical=9.dp)) {
            if(!mine) Text(message.senderDisplayName.ifBlank { authUiText("Support team","Поддержка","Қолдау", "Колдоо тобу") },color=foreground,fontWeight=FontWeight.Bold,fontSize=stateValues.smallTextSize)
            SelectionContainer { Text(message.body,color=foreground,fontSize=stateValues.textSize) }
            if(message.attachments.isNotEmpty()) SelectionContainer {
                Text(authUiText("Attachments","Вложения","Тіркемелер", "Тиркемелер")+"\n"+message.attachments.joinToString("\n"),
                    Modifier.padding(top=6.dp),color=foreground,fontSize=stateValues.smallTextSize)
            }
            val read=if(agentMessage) message.readByCustomerAtMillis!=null else message.readByAgentAtMillis!=null
            val receipt=if(!mine) "" else " · "+
                (if(read) authUiText("Read","Прочитано","Оқылды", "Окуу") else authUiText("Sent","Отправлено","Жіберілді", "Жөнөтүлдү"))
            Text(supportTime(message.createdAtMillis)+receipt,
                Modifier.align(Alignment.End).padding(top=5.dp),color=foreground.copy(alpha=0.75f),fontSize=stateValues.smallTextSize)
        }
    }
}

@Composable
private fun AppConfiguration.SupportMetricsPane(modifier: Modifier,account: String) {
    var data by remember(account) { mutableStateOf<SupportTeamMetrics?>(null) }
    var error by remember { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    val signals by SupportWorkspaceSignals.revision.collectAsState()
    LaunchedEffect(account,signals) {
        val generation=currentAuthenticatedSessionGeneration()
        val result=networkRequest<SupportTeamMetrics,Unit>(HttpMethod.Get,endpointUrl="support/agent/metrics",expectedSessionGeneration=generation)
        if(authenticatedSessionGenerationIsCurrent(generation) && userAccountState.payloadValue?.id==account) { if (!result.negative && result.payload != null) data=result.payload; error=result.message.takeIf { result.negative } }
    }
    Column(modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        error?.let { SupportInlineError(it) }
        val metrics=data
        if(metrics==null && error==null) LoadingSkeleton(layout = LoadingLayout.Metrics, rows =1)
        else if(metrics!=null) {
            Text(authUiText("Team · last 30 days","Команда · последние 30 дней","Топ · соңғы 30 күн", "Команда · акыркы 30 күн"),color=stateValues.TextColor,fontWeight=FontWeight.Bold,fontSize=stateValues.accentTextSize)
            listOf(
                authUiText("Open now","Открыто сейчас","Қазір ашық", "Азыр ачуу") to metrics.open.toString(),
                authUiText("Unassigned now","Без специалиста сейчас","Қазір тағайындалмаған", "Азыр дайындалган эмес") to metrics.unassigned.toString(),
                authUiText("Replies","Ответов","Жауаптар", "Жооптор") to metrics.repliesLast30Days.toString(),
                authUiText("Resolutions","Закрытий","Шешілгендер", "Чечимдер") to metrics.resolvedLast30Days.toString(),
                authUiText("Average first reply (minutes)","Первый ответ в среднем (минуты)","Орташа алғашқы жауап (минут)", "Биринчи жооптун орточо убактысы (мүнөт)") to
                    (metrics.averageFirstResponseMillis?.let { (it/60_000).toString() } ?: "—")
            ).forEach { (label,value) -> Row(Modifier.fillMaxWidth()) {
                Text(label,Modifier.weight(1f),color=stateValues.TextColor,fontSize=stateValues.textSize)
                Text(value,color=stateValues.AccentColor,fontWeight=FontWeight.Bold,fontSize=stateValues.textSize)
            } }
            Text(authUiText("Only recorded agent activity is counted. Historical work is not estimated.","Учитываются только записанные действия специалистов. Старые показатели не выдумываются.","Тек жазылған маман әрекеттері есептеледі. Бұрынғы көрсеткіштер болжанбайды.", "Агенттин катталган аракеттери гана эсептелет. Мурунку иш болжол менен эсептелбейт."),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
        }
    }
}
