package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.*

/** Exercises the production rail, without application sessions, file pickers or server requests. */
class HorizontalAitaTabsRenderTest {
    private fun onUiThread(block:()->Unit) {
        val failure=AtomicReference<Throwable?>()
        SwingUtilities.invokeAndWait {try {block()}catch(error:Throwable){failure.set(error)}}
        failure.get()?.let {throw it}
    }
    @Test fun narrowTabsStayOnOneHorizontalLine()=onUiThread {
        val bounds=mutableMapOf<String,Rect>();var rail:Rect?=null
        val scene=ImageComposeScene(230,300,Density(1f)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().onGloballyPositioned {rail=it.boundsInRoot()}) {
                    HorizontalAitaTabRail(listOf("mine","generic","contracts"),"mine") {id->
                        Box(Modifier.width(150.dp).height(48.dp).onGloballyPositioned {bounds[id]=it.boundsInRoot()})
                    }
                }
            }
        }
        try {
            repeat(16){scene.render(it*16_000_000L).close()}
            assertEquals(56f,assertNotNull(rail).height,1f)
            assertTrue(bounds.values.all {kotlin.math.abs(it.top-4f)<1f})
        } finally {scene.close()}
    }
    @Test fun compactSortRailRemovesOnlyExternalVerticalPadding()=onUiThread {
        var rail:Rect?=null
        val scene=ImageComposeScene(400,200,Density(1f)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.onGloballyPositioned {rail=it.boundsInRoot()}) {
                    HorizontalAitaTabRail(listOf("name","added"),"name",compact=true) {
                        Box(Modifier.width(100.dp).height(48.dp))
                    }
                }
            }
        }
        try {
            repeat(16){scene.render(it*16_000_000L).close()}
            assertEquals(48f,assertNotNull(rail).height,1f)
        } finally {scene.close()}
    }
    @Test fun selectedConversationIsBroughtIntoViewAfterResize()=onUiThread {
        val bounds=mutableMapOf<String,Rect>();val selected=mutableStateOf("one")
        val scene=ImageComposeScene(700,200,Density(1f)) {
            Column(Modifier.fillMaxSize()) {
                HorizontalAitaTabRail(listOf("one","two","three","four"),selected.value) {id->
                    Box(Modifier.width(130.dp).height(48.dp).onGloballyPositioned {bounds[id]=it.boundsInRoot()})
                }
            }
        }
        try {
            repeat(16){scene.render(it*16_000_000L).close()}
            selected.value="four";scene.constraints=Constraints(maxWidth=250,maxHeight=200)
            repeat(24){scene.render((it+16)*16_000_000L).close()}
            val last=assertNotNull(bounds["four"])
            assertTrue(last.left>=-1f && last.right<=251f,"Selected tab must remain visible: $last")
        } finally {scene.close()}
    }
}
