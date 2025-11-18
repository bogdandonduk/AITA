package kz.aita.compose.util

import aita.composeapp.generated.resources.Res
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM

actual suspend fun getResourceDrawableSvgPainter(name: String): Painter {
  val targetWidth = 128
  val targetHeight = 128

  val surface = Surface.makeRasterN32Premul(targetWidth, targetHeight)
  val canvas = surface.canvas

  val bytes = Res.readBytes("files/assets/drawable/svg/$name.svg")

  SVGDOM(Data.makeFromBytes(bytes)).apply {
    setContainerSize(targetWidth.toFloat(), targetHeight.toFloat())
    render(canvas)
  }

  val image = surface.makeImageSnapshot()
  val bitmap = image.toComposeImageBitmap()
  return BitmapPainter(bitmap)
}