package kz.aita.compose.util

import aita.composeapp.generated.resources.Res
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.caverock.androidsvg.SVG

actual suspend fun getResourceDrawableSvgPainter(name: String): Painter {
  val bytes = Res.readBytes("files/assets/drawable/svg/$name.svg")
  val svg = SVG.getFromString(bytes.decodeToString())

  // 2) Decide raster size (you can change this)
  val width = (svg.documentWidth.takeIf { it > 0 } ?: 128f).toInt()
  val height = (svg.documentHeight.takeIf { it > 0 } ?: 128f).toInt()

  // 3) Render into bitmap
  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  svg.renderToCanvas(canvas)

  // 4) Wrap as Painter
  return BitmapPainter(bitmap.asImageBitmap())
}