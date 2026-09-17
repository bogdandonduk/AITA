@file:OptIn(kotlin.time.ExperimentalTime::class)
package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
    val sessionGeneration = currentAuthenticatedSessionGeneration()
    key(account, sessionGeneration) {
        val book = remember(account,sessionGeneration) {supportConversationBooks.forOwner(account,sessionGeneration)}
        var selected by book::selected
        val conversations = book.conversations
        val screenScope = rememberCoroutineScope()
        var tabsError by remember { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
        var confirming by remember { mutableStateOf<String?>(null) }
        val canAgent = companyCanOpenSupport(employment, account)
        val capabilities = employment?.capabilities.orEmpty()
        fun owned() = userAccountState.payloadValue?.id == account && authenticatedSessionGenerationIsCurrent(sessionGeneration)
        fun openConversation(agent: Boolean, ticket: SupportTicketDataModel?) {
            val id = supportConversationTabId(agent, ticket?.id)
            if (conversations.none { it.key == id }) {
                if (conversations.size >= 16) { tabsError = eventMessage("support.tabs.limit"); return }
                conversations.add(SupportConversationTab(agent, ticket?.id, SupportConversationMemory(ticket)))
            }
            selected = id; tabsError = null
        }
        fun closeConversation(tab: SupportConversationTab) {
            val memory = tab.memory
            if (account == null || memory.sending || memory.acting || memory.closing) return
            memory.closing = true
            screenScope.launch {
                try {
                    if (supportDraftNeedsSaving(memory.draftLoaded,memory.edited)) SupportDelivery.saveDraft(account, tab.agent, tab.ticketId, memory.draft, memory.draftRevision)
                    if (!owned()) return@launch
                    conversations.remove(tab)
                    if (selected == tab.key) selected = if (tab.agent && canAgent) "agent" else "chat"
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { if (owned()) tabsError = eventMessage("support.save_failed") }
                finally { memory.closing = false }
            }
        }
        fun resolveConversation(tab: SupportConversationTab) {
            if (account == null || !owned() || !supportTabCanResolve(tab.memory.ticket, account, tab.agent, capabilities)) return
            val memory = tab.memory
            val ticket = memory.ticket ?: return
            if (memory.acting || memory.sending) return
            memory.acting = true
            screenScope.launch {
                try {
                    val result = networkRequest<SupportTicketDataModel,SupportAgentActionRequest>(HttpMethod.Post,
                        endpointUrl="support/workspace/action", query=mapOf("agent" to tab.agent),
                        body=SupportAgentActionRequest(ticket.id,"close",ticket.revision),expectedSessionGeneration=sessionGeneration)
                    if (!owned()) return@launch
                    val confirmed = result.payload
                    if (!result.negative && confirmed?.id == ticket.id && confirmed.status == "closed" && confirmed.revision > ticket.revision) {
                        memory.ticket = newestSupportTabTicket(memory.ticket,confirmed,ticket.id); memory.feedback = null; SupportWorkspaceSignals.changed()
                    } else {
                        memory.feedback = result.message ?: eventMessage("support.tabs.resolve_failed")
                        memory.refresh++
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { if (owned()) { memory.feedback = eventMessage("support.tabs.resolve_failed"); memory.refresh++ } }
                finally { memory.acting = false }
            }
        }
        LaunchedEffect(canAgent) {
            if (!canAgent) {
                val removed = conversations.filter { it.agent }
                // Losing employment is not permission to retain an agent conversation onscreen.
                conversations.removeAll(removed.toSet())
                if (selected == "agent" || removed.any { it.key == selected }) selected = "chat"
            }
        }
        val tab = selected.takeIf { it in listOf("faq","chat","diagnostics") || (it == "agent" && canAgent) || conversations.any { c -> c.key == it } } ?: "chat"
        AitaScreenColumn(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,appBar={
            ScreenAppBarWidget(title=localizedStringResource(813,"Support"),iconPath=stateValues.drawablePathIconSupport,
                onBack={coroutineScope.launch{Navigation.Menu.pop(stateValues.isNarrowScreen)}})
        }) {
            tabRowWidget(Modifier.widthIn(max=1080.dp).fillMaxWidth().padding(horizontal=stateValues.marginTextField,vertical=4.dp),
                tabs=buildList {
                    add(TabContent("diagnostics",diagnosticsText("title")){selected=it})
                    add(TabContent("faq",localizedStringResource(814,"FAQ")){selected=it})
                    add(TabContent("chat",authUiText("Chats","Чаты","Чаттар","Чаттар")){selected=it})
                    if(canAgent) add(TabContent("agent",authUiText("Agent","Специалист","Маман","Агент")){selected=it})
                    conversations.forEach { conversation ->
                        val memory=conversation.memory
                        val title=memory.ticket?.subject ?: eventMessage("support.tabs.new").extractLocalizedString(stateValues.appLanguage).orEmpty()
                        add(TabContent(conversation.key,title,AitaTabIcon.Chat,actions=buildList {
                            if(conversation.ticketId!=null) add(AitaTabAction("resolve",
                                eventMessage(if(memory.ticket?.status=="closed") "support.tabs.resolved" else "support.tabs.resolve").extractLocalizedString(stateValues.appLanguage).orEmpty(),
                                AitaTabIcon.Check,enabled=account!=null && supportTabCanResolve(memory.ticket,account,conversation.agent,capabilities) && !memory.acting && !memory.sending && !memory.closing) {
                                    selected=conversation.key; confirming=conversation.key
                                })
                            add(AitaTabAction("close",eventMessage("support.tabs.close").extractLocalizedString(stateValues.appLanguage).orEmpty(),AitaTabIcon.Cancel,
                                enabled=!memory.acting && !memory.sending && !memory.closing){closeConversation(conversation)})
                        }){selected=it})
                    }
                },selectedIndexInitial=tab,persistSelection=false)
            tabsError?.let { SupportInlineError(it) }
            when {
                tab=="diagnostics" -> DiagnosticsSettingsPane(Modifier.weight(1f))
                tab=="faq" -> SupportHelpPane(Modifier.weight(1f))
                account==null -> MessageText(Modifier.weight(1f).fillMaxWidth(),authUiText("Sign in to contact support","Войдите для связи с поддержкой","Қолдауға хабарласу үшін кіріңіз","Колдоого кайрылуу үчүн кириңиз"))
                tab=="chat" || tab=="agent" -> key(account,tab) {
                    SupportInboxPane(account,tab=="agent",capabilities,Modifier.weight(1f),
                        onOpen={openConversation(tab=="agent",it)},onNew={openConversation(false,null)})
                }
                else -> conversations.firstOrNull { it.key==tab }?.let { conversation ->
                    key(conversation.key) {
                        SupportConversationPane(account,conversation.agent,conversation.ticketId,capabilities,
                            Modifier.weight(1f).widthIn(max=1080.dp).fillMaxWidth(),conversation.memory,
                            onCreated={ created ->
                                val index=conversations.indexOf(conversation)
                                if(index>=0 && owned()) {
                                    val next=conversation.copy(ticketId=created)
                                    val existing=conversations.firstOrNull{it.key==next.key}
                                    if(existing==null) conversations[index]=next else conversations.removeAt(index)
                                    if(selected==conversation.key)selected=next.key
                                }
                            })
                    }
                }
            }
        }
        conversations.firstOrNull{it.key==confirming}?.let { conversation ->
            androidx.compose.ui.window.Dialog(onDismissRequest={confirming=null}) {
                Column(Modifier.widthIn(max=520.dp).fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.BackgroundColor).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(eventMessage("support.tabs.confirm").extractLocalizedString(stateValues.appLanguage).orEmpty(),
                        color=stateValues.TextColor,fontSize=stateValues.textSize)
                    actionButton(text=eventMessage("support.tabs.resolve").extractLocalizedString(stateValues.appLanguage).orEmpty(),
                        autoLoading=false,confirmationRequired=false,enabled=!conversation.memory.acting && !conversation.memory.sending,
                        onClick={confirming=null;resolveConversation(conversation)})
                    actionButton(text=stateValues.stringCancel,autoLoading=false,confirmationRequired=false,onClick={confirming=null})
                }
            }
        }
    }
}

@Composable
private fun AppConfiguration.SupportHelpPane(modifier: Modifier) {
    HelpBookPane(modifier, faq = true)

}

@Composable
private fun AppConfiguration.SupportInboxPane(account: String,agent: Boolean,capabilities: Set<String>,modifier: Modifier,
    onOpen: (SupportTicketDataModel)->Unit, onNew: ()->Unit) {
    var tickets by remember { mutableStateOf<List<SupportTicketDataModel>>(emptyList()) }
    var page by remember { mutableStateOf<SupportTicketPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadedFilter by remember { mutableStateOf<String?>(null) }
    var expandedHistory by remember { mutableStateOf(false) }
    var pageEpoch by remember { mutableStateOf(0) }
    var feedback by remember { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var filter by remember { mutableStateOf(if(agent) "open" else "all") }
    var metrics by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    val signals by SupportWorkspaceSignals.revision.collectAsState()
    val window=LocalWindowInfo.current
    val scope=rememberCoroutineScope()
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
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(agent) authUiText("Support inbox","Обращения","Өтініштер", "Колдоонун кирген маектери") else authUiText("Your conversations","Ваши диалоги","Сіздің диалогтарыңыз", "Маектериңиз"),
                        Modifier.weight(1f),color=stateValues.TextColor,fontWeight=FontWeight.Bold,fontSize=stateValues.textSize)
                    if(!agent) actionButton(text="",iconPath=stateValues.drawablePathIconAdd,
                        iconContentDescription=authUiText("New conversation","Новый диалог","Жаңа диалог", "Жаңы маек"),autoLoading=false,
                        onClick=onNew)
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
                            .background(Color.Transparent)
                            .clickable { onOpen(ticket) }.padding(12.dp)) {
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
    modifier: Modifier,memory: SupportConversationMemory,onCreated: (String)->Unit) {
    with(memory) {
    val signals by SupportWorkspaceSignals.revision.collectAsState()
    val scope=rememberCoroutineScope()
    val window=LocalWindowInfo.current
    val ownerGeneration=remember(account,agent,ticketId) {currentAuthenticatedSessionGeneration()}
    fun owned()=userAccountState.payloadValue?.id==account && authenticatedSessionGenerationIsCurrent(ownerGeneration)
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
    DisposableEffect(memory, ticketId) {
        onDispose {
            if(supportDraftNeedsSaving(draftLoaded,edited)) {
                val text=draft; val revision=draftRevision
                coroutineScope.launch {
                    try { SupportDelivery.saveDraft(account,agent,ticketId,text,revision) }
                    catch(cancel:CancellationException){throw cancel}
                    catch(_:Exception){postInAppNotification(eventMessage("support.save_failed"),NotificationType.Neutral,transient=true)}
                }
            }
        }
    }
    LaunchedEffect(draftRevision,draftLoaded) {
        if(supportDraftNeedsSaving(draftLoaded,edited)) {
            delay(400)
            try { SupportDelivery.saveDraft(account,agent,ticketId,draft,draftRevision) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { feedback=eventMessage("support.save_failed") }
        }
    }
    LaunchedEffect(ticketId,signals,refresh) {
        if(ticketId==null || !owned()) return@LaunchedEffect
        if(messages.isEmpty())loading=true
        val generation=ownerGeneration
        val wasNearEnd=list.firstVisibleItemIndex<=1
        try {
            val result=networkRequest<SupportMessagePage,Unit>(HttpMethod.Get,endpointUrl="support/workspace/messages",
                query=mapOf("ticket_id" to ticketId,"agent" to agent),expectedSessionGeneration=generation)
            val data=result.payload
            if(userAccountState.payloadValue?.id!=account || !authenticatedSessionGenerationIsCurrent(generation)) return@LaunchedEffect
            if(!result.negative && data != null && data.ticket.id==ticketId) {
                val newLast=data.messages.lastOrNull()?.sequence ?: 0
                val oldLast=messages.lastOrNull()?.sequence ?: 0
                ticket=newestSupportTabTicket(ticket,data.ticket,ticketId)
                messages=(data.messages.filter {it.ticketId==ticketId}+messages).distinctBy { it.id }.sortedBy { it.sequence }
                if(before==null && messages.size<=100) before=data.nextBeforeSequence
                if(newLast>oldLast) { if(wasNearEnd) list.scrollToItem(0) else hasNew=true }
            } else { feedback=result.message; if(agent && result.httpStatusCode==403) CompanyEmployment.clear() }
        } catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){feedback=eventMessage("support.send_unknown")}
        finally { loading=false }
    }
    LaunchedEffect(window.isWindowFocused,ticketId) {
        while(isActive && owned() && ticketId!=null && window.isWindowFocused) { delay(15_000); refresh++ }
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
                if(!owned())return@collectLatest
                val generation=ownerGeneration
                try {
                    val result=networkRequest<Pair<SupportTicketDataModel,Boolean>,SupportReadCursorRequest>(HttpMethod.Post,
                        endpointUrl="support/workspace/read",query=mapOf("agent" to agent),body=SupportReadCursorRequest(ticketId,cursor),
                        expectedSessionGeneration=generation)
                    if(!result.negative && owned())lastMarked=cursor
                } catch(cancel:CancellationException){throw cancel}
                catch(_:Exception){ /* A failed receipt is not a successfully read acknowledgement. */ }
            }
        }
    }
    fun act(action: String) {
        val current=ticket ?: return
        if(acting || !owned()) return
        acting=true
        scope.launch {
            try {
                if(!owned())return@launch
                val generation=ownerGeneration
                val result=networkRequest<SupportTicketDataModel,SupportAgentActionRequest>(HttpMethod.Post,endpointUrl="support/workspace/action",
                    query=mapOf("agent" to agent),body=SupportAgentActionRequest(current.id,action,current.revision),expectedSessionGeneration=generation)
                if(userAccountState.payloadValue?.id==account && authenticatedSessionGenerationIsCurrent(generation)) {
                    val data=result.payload
                    if(!result.negative && data?.id==current.id && data.revision>current.revision) {
                        ticket=newestSupportTabTicket(ticket,data,current.id); feedback=null; SupportWorkspaceSignals.changed()
                    } else { feedback=result.message ?: eventMessage("support.tabs.resolve_failed"); refresh++ }
                }
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){feedback=eventMessage("support.tabs.resolve_failed");refresh++}
            finally { acting=false }
        }
    }
    Column(modifier.imePadding()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                if(canResolve && current.status=="closed") AuthQuietAction(authUiText("Reopen","Открыть снова","Қайта ашу","Кайра ачуу"),!acting) { act("reopen") }
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
                        if(!owned())return@actionButton
                        loadingMore=true
                        scope.launch {
                            try {
                                if(!owned())return@launch
                                val generation=ownerGeneration
                                val result=networkRequest<SupportMessagePage,Unit>(HttpMethod.Get,endpointUrl="support/workspace/messages",
                                    query=mapOf("ticket_id" to ticketId,"agent" to agent,"before_sequence" to cursor),expectedSessionGeneration=generation)
                                val data=result.payload
                                if(userAccountState.payloadValue?.id==account && authenticatedSessionGenerationIsCurrent(generation)) {
                                    if(!result.negative && data != null && data.ticket.id==ticketId) {
                                        messages=(messages+data.messages.filter {it.ticketId==ticketId}).distinctBy { it.id }.sortedBy { it.sequence }
                                        before=data.nextBeforeSequence
                                    }
                                    else feedback=result.message
                                }
                            } catch(cancel:CancellationException){throw cancel}
                            catch(_:Exception){if(owned())feedback=eventMessage("support.send_unknown")}
                            finally { loadingMore=false }
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
                retainTextAcrossRecreation=false,persistTextDraft=false,enabled=!closing,identityKey="support:$account:$agent:$ticketId",
                onValueChange={ value,applyChange ->
                    val limited=value.take(4000).let { if(it.lastOrNull()?.isHighSurrogate()==true) it.dropLast(1) else it }
                    if(limited!=draft) { edited=true; draft=limited; draftRevision=newClientSideUuidString() }
                    applyChange()
                })
            actionButton(text="",iconPath=supportSendIconPath(),iconRes=supportSendIconFallback(),autoLoading=false,confirmationRequired=false,
                iconContentDescription=if(pending!=null) authUiText("Retry saved message","Повторить сохранённое сообщение","Сақталған хабарламаны қайталау", "Сакталган билдирүүнү кайра жөнөтүү") else localizedStringResource(825,"Send"),
                enabled=canSend && draftLoaded && !sending && !closing && !acting && (pending!=null || (draft.isNotBlank() && '\u0000' !in draft)),loading=sending,onClick={
                    if(sending || closing || acting || !owned()) return@actionButton
                    val command=pending ?: PendingSupportMessage(account,newClientSideUuidString(),ticketId,agent,draft.trim(),category,
                        stateValues.activeStoreId.takeIf { !agent },stateValues.appLanguage,draftRevision)
                    val sendGeneration=ownerGeneration
                    val draftAtClick=draft; val revisionAtClick=draftRevision
                    sending=true; feedback=null; pending=command
                    scope.launch {
                        try {
                            SupportDelivery.saveDraft(account,agent,ticketId,draftAtClick,revisionAtClick)
                            if(!owned())return@launch
                            val result=SupportDelivery.send(command,sendGeneration)
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
