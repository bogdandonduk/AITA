package kz.aita.compose.util

import aita.composeapp.generated.resources.Res
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM

actual suspend fun getResourceDrawableSvgPainter(name: String): Painter {
  val surface = Surface.makeRasterN32Premul(
    128,
    128
  )

  SVGDOM(Data.makeFromBytes(Res.readBytes("files/assets/drawable/svg/$name.svg"))).apply {
    setContainerSize(
      128f,
      128f
    )
  }.render(surface.canvas)

  val image = surface.makeImageSnapshot()
  val bitmap = image.toComposeImageBitmap()
  return BitmapPainter(bitmap)
}