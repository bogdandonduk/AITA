// THIS IS WasmMainCompose.kt - place in composeApp/src/wasmJsMain/kotlin/kz/aita/WasmMainCompose.kt
package kz.aita

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun installWasmComposePlatformBridges() {
    setClipboardText = { text ->
        runCatching { window.prompt("Copy", text) }
    }

    openExternalUrlPlatformAction = { rawUrl ->
        runCatching {
            val cleanUrl = rawUrl.trim()
            val allowed = cleanUrl.startsWith("https://", ignoreCase = true) ||
                cleanUrl.startsWith("http://", ignoreCase = true) ||
                cleanUrl.startsWith("geo:", ignoreCase = true)
            if (!allowed) {
                ReceiptPlatformActionResult(false, "Unsupported link")
            } else {
                val opened = window.open(cleanUrl, "_blank")
                if (opened != null) {
                    ReceiptPlatformActionResult(true, "Opened map")
                } else {
                    ReceiptPlatformActionResult(false, "The browser blocked the new tab")
                }
            }
        }.getOrElse { throwable ->
            ReceiptPlatformActionResult(false, throwable.message ?: "Could not open the link")
        }
    }

    openPlatformAppSettings = { _ ->
        ReceiptPlatformActionResult(false, "Browser app permissions are controlled by the browser and operating system")
    }

    openSystemDevicesSettings = {
        ReceiptPlatformActionResult(false, "Browser device settings are controlled by the browser and operating system")
    }

    saveReceiptPdfFile = { _, _ ->
        withContext(Dispatchers.ourIo) {
            ReceiptPlatformActionResult(false, "Browser PDF saving bridge is not enabled yet")
        }
    }

    shareReceiptPdfFile = { _, _, _ ->
        withContext(Dispatchers.ourIo) {
            ReceiptPlatformActionResult(false, "Browser sharing bridge is not enabled yet")
        }
    }

    installBrowserPrinting()

    getCameraScannerPermissionState = { PlatformPermissionState.Granted }

    requestCameraScannerPermission = { _, onGranted, _ ->
        onGranted()
    }

    barcodeCameraScannerContent = { modifier, _, onClose ->
        BarcodeCameraScannerFallbackPane(
            modifier = modifier,
            onClose = onClose
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    installWebRuntimeDiagnostics()
    installWasmCommonPlatformBridges()
    installWasmComposePlatformBridges()

    MainScope().launch {
        try {
            initializeBrowserDatabase()
            init()
            document.getElementById("aita-startup")?.remove()
            ComposeViewport(document.body!!) {
                AppConfiguration({ MainScreen() })
            }
        } catch (failure: Throwable) {
            val alreadyOpen = failure.message.orEmpty().contains("already open in another tab")
            document.getElementById("aita-startup-message")?.textContent = browserStartupFailureText(alreadyOpen)
        }
    }
}


private fun browserStartupFailureText(alreadyOpen: Boolean): String = when (getSystemLocaleLanguage()) {
    "ru" -> if (alreadyOpen) "AITA уже открыта в другой вкладке. Закройте её и обновите эту страницу."
        else "Не удалось открыть хранилище AITA. Разрешите хранение данных в браузере и обновите страницу. Сохранённые данные не удалены."
    "kk" -> if (alreadyOpen) "AITA басқа қойындыда ашық. Оны жауып, осы бетті жаңартыңыз."
        else "AITA қоймасын ашу мүмкін болмады. Браузерде деректерді сақтауға рұқсат беріп, бетті жаңартыңыз. Сақталған деректер жойылған жоқ."
    "ky" -> if (alreadyOpen) "AITA башка өтмөктө ачык. Аны жаап, бул баракты жаңыртыңыз."
        else "AITA сактагычы ачылган жок. Браузерде маалымат сактоого уруксат берип, баракты жаңыртыңыз. Сакталган маалымат өчүрүлгөн жок."
    "tg" -> if (alreadyOpen) "AITA дар ҷадвали дигар кушода аст. Онро пӯшед ва ин саҳифаро нав кунед."
        else "Анбори AITA кушода нашуд. Ба браузер иҷозаи захираи маълумот диҳед ва саҳифаро нав кунед. Маълумоти захирашуда нест нашудааст."
    "uz" -> if (alreadyOpen) "AITA boshqa varaqda ochiq. Uni yoping va bu sahifani yangilang."
        else "AITA xotirasi ochilmadi. Brauzerda maʼlumot saqlashga ruxsat bering va sahifani yangilang. Saqlangan maʼlumotlar o‘chirilmagan."
    else -> if (alreadyOpen) "AITA is already open in another tab. Close that tab and reload this page."
        else "AITA could not open local storage. Allow browser storage, then reload this page. Your saved data has not been cleared."
}
