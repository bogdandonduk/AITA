package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kz.aita.help.*

internal fun AppConfiguration.tutorialText(key:String,vararg args:Pair<String,String>) =
    eventMessage("help.$key",*args).extractLocalizedString(stateValues.appLanguage).orEmpty()
internal fun AppConfiguration.tutorialIconPath()=uiAppearanceResourcesState.value.catalog.drawable(212L,stateValues.appThemeId)
internal fun AppConfiguration.tutorialIconResource()=if(isDarkAppTheme(stateValues.appThemeId)) Res.drawable._212_1 else Res.drawable._212_0
private fun HelpCategory.tabIcon()=when(this) {
    HelpCategory.START->AitaTabIcon.Checklist; HelpCategory.ACCOUNT->AitaTabIcon.Security
    HelpCategory.STOCK->AitaTabIcon.Stock; HelpCategory.SALES->AitaTabIcon.CashRegister
    HelpCategory.MONEY->AitaTabIcon.Money; HelpCategory.TEAM->AitaTabIcon.Workers
    HelpCategory.MARKETPLACE->AitaTabIcon.Basket; HelpCategory.PARTNERS->AitaTabIcon.Contract
    HelpCategory.ORDERS->AitaTabIcon.Truck; HelpCategory.DEVICES->AitaTabIcon.Settings
    HelpCategory.SETTINGS->AitaTabIcon.Help
}

@Composable internal fun AppConfiguration.TutorialsScreen() {
    AitaScreenColumn(Modifier.fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally, appBar={
        ScreenAppBarWidget(title=tutorialText("title"),iconPath=tutorialIconPath(),iconRes=tutorialIconResource(),
            onBack=if(Navigation.Menu.isVeryFirstScreen(stateValues.isNarrowScreen)) null else {
                { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } }
            })
    }) { HelpBookPane(Modifier.weight(1f),faq=false) }
}

private data class HelpSearchResult(val tutorials:List<HelpTutorial> = emptyList(),val faqs:List<HelpFaq> = emptyList())
@Composable internal fun AppConfiguration.HelpBookPane(modifier:Modifier,faq:Boolean) {
    val mode=HelpMode.fromId(stateValues.appModeId)
    key(mode,faq) {
        val state by remember(mode) { TutorialWorkspace.state(mode) }.collectAsState()
        val book=state.catalogue
        val language=stateValues.appLanguage
        var search by rememberSaveable { mutableStateOf("") }
        var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
        var fromFaq by rememberSaveable { mutableStateOf<String?>(null) }
        val category=HelpCategory.entries.firstOrNull { it.name==selectedCategory }
        val categories=remember(book,mode) { book?.tutorials.orEmpty().filter { mode in it.modes }.map { it.category }.distinct() }
        LaunchedEffect(mode) { TutorialWorkspace.load(mode) }
        val results by produceState(HelpSearchResult(),book,mode,language,search,category,faq) {
            if(search.isNotBlank()) delay(100)
            value=withContext(Dispatchers.Default) {
                if(book==null) HelpSearchResult() else HelpSearchResult(
                    filterHelpTutorials(book,mode,language,search,category), filterHelpFaqs(book,mode,language,search))
            }
        }
        val selected=book?.tutorials?.firstOrNull { it.id==fromFaq && mode in it.modes }
        Column(modifier.widthIn(max=940.dp).fillMaxWidth()) {
            if(state.remoteUnavailable) Text(tutorialText("offline"),modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),
                color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            if(selected!=null) {
                actionButton(modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),text=tutorialText("back_faq"),
                    autoLoading=false,confirmationRequired=false,onClick={fromFaq=null})
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(16.dp)) {
                    item(selected.id) { TutorialCard(selected,initiallyExpanded=true) }
                }
            } else {
                SimpleTextInput(Modifier.fillMaxWidth().padding(horizontal=8.dp),search,tutorialText(if(faq) "faq_search" else "search"),
                    autoFocus=false,leadingIconPath=stateValues.drawablePathIconSearch,onValueChange={search=it.take(200)})
                if(!faq && categories.size>1) tabRowWidget(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),
                    tabs=listOf(TabContent("all",tutorialText("all"),AitaTabIcon.All){selectedCategory=null})+categories.map { cat ->
                        TabContent(cat.name,tutorialText("category.${cat.name.lowercase()}"),cat.tabIcon()){selectedCategory=cat.name}
                    },selectedIndexInitial=selectedCategory ?: "all",textSize=stateValues.smallTextSize)
                val empty=if(faq) results.faqs.isEmpty() else results.tutorials.isEmpty()
                if(book==null || empty) Box(Modifier.weight(1f).fillMaxWidth().padding(28.dp),contentAlignment=Alignment.Center) {
                    Text(tutorialText(if(book==null) "loading" else "empty"),color=stateValues.PlaceholderTextColor,
                        fontSize=stateValues.textSize,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                } else LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(faq) items(results.faqs,key={it.id}) { entry ->
                        TutorialFaqCard(entry,onGuide={ id -> fromFaq=id })
                    } else items(results.tutorials,key={it.id}) { entry -> TutorialCard(entry) }
                }
            }
        }
    }
}

@Composable private fun AppConfiguration.GuideLanguageNotice(language:String) {
    if(language!=canonicalLanguageCode(effectiveAppLanguage(stateValues.appLanguage)))
        Text(tutorialText("translation"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
}

@Composable private fun AppConfiguration.BookCard(title:String,subtitle:String,expanded:Boolean,onClick:()->Unit,content:@Composable ColumnScope.()->Unit) {
    val border by animateColorAsState(if(expanded) stateValues.AccentColor else stateValues.PlaceholderTextColor.copy(alpha=.45f),
        animationSpec=tween(AITA_MOTION_NORMAL_MILLIS),label="tutorialBorder")
    Column(Modifier.fillMaxWidth().foregroundTactileShadow(stateValues.cornerRadius,elevated=false)
        .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor)
        .border(stateValues.unfocusedBorderWidth,border,RoundedCornerShape(stateValues.cornerRadius))) {
        Row(Modifier.fillMaxWidth().semantics { stateDescription=tutorialText(if(expanded) "expanded" else "collapsed") }
            .clickable(role=Role.Button,onClick=onClick).padding(18.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(subtitle,color=stateValues.AccentColor,fontSize=stateValues.smallTextSize)
                Text(title,color=stateValues.TextColor,fontSize=stateValues.textSize,fontWeight=FontWeight.Bold)
            }
            CpImage(Modifier.size(22.dp),if(expanded) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore,
                if(expanded) stateValues.drawableResIconExpandLess.value else stateValues.drawableResIconExpandMore.value,null,stateValues.AccentColor)
        }
        AnimatedVisibility(expanded) { Column(Modifier.fillMaxWidth().padding(start=18.dp,end=18.dp,bottom=18.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp),content=content) }
    }
}

@Composable private fun AppConfiguration.TutorialCard(article:HelpTutorial,initiallyExpanded:Boolean=false) {
    var expanded by rememberSaveable(article.id) { mutableStateOf(initiallyExpanded) }
    val language=article.contentLanguageFor(stateValues.appLanguage) // Keep an entire article in one available language.
    BookCard(article.title.localized(language),tutorialText("category.${article.category.name.lowercase()}")+" · "+tutorialText("steps","count" to article.steps.size.toString()),expanded,{expanded=!expanded}) {
        GuideLanguageNotice(language)
        SelectionContainer { Text(article.introduction.localized(language),color=stateValues.TextColor,fontSize=stateValues.textSize) }
        article.steps.forEachIndexed { index,step ->
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
                    Box(Modifier.size(29.dp).clip(CircleShape).background(stateValues.AccentColor.copy(alpha=.14f)),contentAlignment=Alignment.Center) {
                        Text((index+1).toString(),color=stateValues.AccentColor,fontWeight=FontWeight.Bold,fontSize=stateValues.smallTextSize)
                    }
                    SelectionContainer(Modifier.weight(1f)) { Text(step.text.localized(language),color=stateValues.TextColor,fontSize=stateValues.textSize,
                        lineHeight=(stateValues.textSize.value*1.4f).sp) }
                }
                step.screenshots.filter { it.language==null || it.language==language }.forEach { screenshot -> TutorialScreenshot(screenshot,language) }
            }
        }
        if(article.caution.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(stateValues.AccentColor.copy(alpha=.07f)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(tutorialText("before"),color=stateValues.AccentColor,fontSize=stateValues.smallTextSize,fontWeight=FontWeight.Bold)
            SelectionContainer { Text(article.caution.localized(language),color=stateValues.TextColor,fontSize=stateValues.smallTextSize) }
        }
    }
}

@Composable private fun AppConfiguration.TutorialFaqCard(entry:HelpFaq,onGuide:(String)->Unit) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val language=entry.contentLanguageFor(stateValues.appLanguage)
    BookCard(entry.question.localized(language),tutorialText("category.${entry.category.name.lowercase()}"),expanded,{expanded=!expanded}) {
        GuideLanguageNotice(language)
        SelectionContainer { Text(entry.answer.localized(language),color=stateValues.TextColor,fontSize=stateValues.textSize,lineHeight=(stateValues.textSize.value*1.4f).sp) }
        entry.tutorialId?.let { id -> actionButton(text=tutorialText("open_guide"),iconPath=tutorialIconPath(),iconRes=tutorialIconResource(),
            autoLoading=false,confirmationRequired=false,onClick={onGuide(id)}) }
    }
}

@Composable private fun AppConfiguration.TutorialScreenshot(image:HelpScreenshot,language:String) {
    var enlarged by remember(image.asset) { mutableStateOf(false) }
    val url=globalAppConfigurationState.payloadValue.serverUrl.first.trimEnd('/')+"/help/screenshots/"+image.asset
    @Composable fun Picture(modifier:Modifier) {
        CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
            KamelImage(resource={asyncPainterResource(data=Url(url))},contentDescription=image.alt.localized(language),
                modifier=modifier,contentScale=ContentScale.Fit,
                onLoading={LoadingSkeleton(Modifier.fillMaxSize(),layout=LoadingLayout.ProductPhoto,rows=1)},
                onFailure={Text(tutorialText("image_unavailable"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)})
        }
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(max=420.dp).aspectRatio(image.width.toFloat()/image.height)
            .clip(RoundedCornerShape(12.dp)).background(stateValues.PlaceholderTextColor.copy(alpha=.06f))
            .clickable(role=Role.Button,onClickLabel=tutorialText("image_open")){enlarged=true},contentAlignment=Alignment.Center) { Picture(Modifier.fillMaxSize()) }
        if(image.caption.isNotEmpty()) Text(image.caption.localized(language),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
    }
    if(enlarged) Dialog(onDismissRequest={enlarged=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.padding(16.dp).widthIn(max=900.dp).fillMaxWidth().fillMaxHeight(.9f)
            .clip(RoundedCornerShape(18.dp)).background(stateValues.BackgroundColor).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Picture(Modifier.weight(1f).fillMaxWidth())
            actionButton(text=tutorialText("close"),autoLoading=false,confirmationRequired=false,onClick={enlarged=false})
        }
    }
}
