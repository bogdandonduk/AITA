package kz.aita.compose

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import io.kamel.core.config.*
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.kamel.image.config.imageBitmapDecoder
import io.kamel.image.config.svgDecoder
import io.ktor.client.plugins.*
import io.ktor.http.*
import kz.aita.cacheSize
import kz.aita.getFullDrawableRemoteResourceUrl
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

val kamelConfig = KamelConfig {
  httpUrlFetcher {
    httpCache(cacheSize)

    defaultRequest {
      headers.append(HttpHeaders.CacheControl, "no-cache")
      headers.append(HttpHeaders.Pragma, "no-cache") // legacy proxies
    }

    install(HttpRequestRetry) {
      maxRetries = 2
      retryIf { _, response -> !response.status.isSuccess() }
    }
  }
  fileFetcher()
  takeFrom(KamelConfig.Core)

  svgDecoder()
  imageBitmapDecoder()
}

@Composable
fun CpImage(
  modifier: Modifier = Modifier,
  url: String,
  fallbackRes: DrawableResource?,
  contentDescription: String?,
  tintColor: Color? = null
) {
  var failed by rememberSaveable {
    mutableStateOf(false)
  }

  val cf = tintColor?.let { ColorFilter.tint(it) }

  CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
    if (failed) {
      if (fallbackRes != null)
        Image(
          modifier = modifier,
          painter = painterResource(fallbackRes),
          contentDescription = contentDescription,
          contentScale = ContentScale.FillWidth,
          colorFilter = cf,
        )
    } else {
      KamelImage(
        modifier = modifier,
        resource = {
          asyncPainterResource(
            data = Url(getFullDrawableRemoteResourceUrl(url))
          )
        },
        contentScale = ContentScale.FillWidth,
        contentDescription = contentDescription,
        colorFilter = cf,
        onFailure = {
          failed = true
        }
      )
    }
  }
}