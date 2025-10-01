package kz.aita.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kz.aita.AppUIConfiguration
import kz.aita.compose.screen.MainScreen

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      AppUIConfiguration(
        {
          MainScreen()
        }
      )
    }
  }
}