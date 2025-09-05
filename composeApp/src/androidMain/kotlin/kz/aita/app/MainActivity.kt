package kz.aita.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kz.aita.core.configuration.AppUIConfiguration
import kz.aita.screen.UserAuthLogInScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppUIConfiguration {
                UserAuthLogInScreen()
            }
        }
    }
}