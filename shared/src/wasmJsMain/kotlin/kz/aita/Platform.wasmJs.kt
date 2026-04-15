package kz.aita

class WasmPlatform : Platform {
  override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatformName(): Platform = WasmPlatform()