package kz.aita.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.TextFieldValue
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.Url
import kz.aita.core.configuration.AppUIConfiguration
import kz.aita.core.remote.RemoteConfiguration
import kz.aita.model.store.ConfigurationStore
import kz.aita.render.kamelConfig
import kz.aita.widget.GenericTextField
import kz.aita.widget.LargeIconWithTitleWidget

@Composable
fun AppUIConfiguration.UserAuthLogInScreenNarrow() {
  LazyColumn(
    modifier = Modifier
      .background(BackgroundColor)
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    item {
      Spacer(
        modifier = Modifier
          .height(stateValues.screenHeight / 6)
      )

      println(stateValues.strings)

      LargeIconWithTitleWidget(
        imageUrl = "${RemoteConfiguration.SERVER_URL}${RemoteConfiguration.DRAWABLE_SVG_RESOURCES_PATH}10.svg",
        title = stateValues.strings.find { it.id == 1L }?.values?.find { it.language == "kk" }?.value ?: "Not found"
      )
    }

    item {
      Spacer(
        modifier = Modifier
          .height(stateValues.screenHeight / 6)
      )

      val tv by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
      }

      GenericTextField(
        value = tv,
        onValueChange = {

        }
      )
    }
  }
}
