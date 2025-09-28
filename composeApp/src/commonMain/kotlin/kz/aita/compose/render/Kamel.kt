package kz.aita.compose.render

import io.kamel.core.config.Core
import io.kamel.core.config.KamelConfig
import io.kamel.core.config.takeFrom
import io.kamel.image.config.imageBitmapDecoder
import io.kamel.image.config.svgDecoder

val kamelConfig = KamelConfig {
  takeFrom(KamelConfig.Core)

  svgDecoder()
  imageBitmapDecoder()
}