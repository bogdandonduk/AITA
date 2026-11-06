@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package kz.aita

/**
 * Install IANA rules in js-joda before any browser date is sent to the server or cached. Official
 * integration: github.com/Kotlin/kotlinx-datetime#note-about-time-zones-in-wasmjs. Keeping real
 * zone IDs also preserves daylight-saving rules for future reminders.
 */
@kotlin.js.JsModule("@js-joda/timezone") private external object BrowserTimeZoneRules

fun initializeBrowserTimeZones() {
    // Referencing the module retains its registration side effect in optimized Wasm builds.
    BrowserTimeZoneRules
}
