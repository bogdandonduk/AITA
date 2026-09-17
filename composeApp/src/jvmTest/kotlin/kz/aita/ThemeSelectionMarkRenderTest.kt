package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.*

class ThemeSelectionMarkRenderTest {
    private fun verify(width:Int,density:Float,fontScale:Float) {
        val failure=AtomicReference<Throwable?>()
        SwingUtilities.invokeAndWait {
            try {
                var mark:Rect?=null;var row:Rect?=null
                val scene=ImageComposeScene(width,900,Density(density,fontScale)) {
                    Row(Modifier.fillMaxWidth().heightIn(min=52.dp).onGloballyPositioned {row=it.boundsInRoot()},verticalAlignment=Alignment.CenterVertically) {
                        Text("Deep purple theme with a longer translated title",Modifier.weight(1f).padding(12.dp),fontSize=18.sp)
                        ThemeSelectionMark {m->Box(m.onGloballyPositioned {mark=it.boundsInRoot()})}
                    }
                }
                try {
                    repeat(16){scene.render(it*16000000L).close()}
                    val m=assertNotNull(mark);val r=assertNotNull(row)
                    assertEquals(22f*density,m.width,1f);assertEquals(22f*density,m.height,1f)
                    assertEquals(r.center.y,m.center.y,1f);assertTrue(m.left>=0 && m.right<=width)
                } finally {scene.close()}
            } catch(error:Throwable){failure.set(error)}
        }
        failure.get()?.let {throw it}
    }
    @Test fun narrowLargeTextNeverStretchesCheckmark()=verify(280,1f,1.5f)
    @Test fun wideParentDoesNotGrowCheckmark()=verify(1200,1f,1f)
    @Test fun denseLargeTextKeepsLogicalIconSize()=verify(600,2f,2f)
}
