package kz.aita.core.remote

object RemoteConfiguration {
  const val SERVER_PORT: String = "8080"
  const val SERVER_URL: String = "http://127.0.0.1:$SERVER_PORT/"

  const val GLOBAL_CONFIGURATION_PATH: String = "config/Global.json"
  var STRING_RESOURCES_PATH: String = "res/values/string/"

  var DRAWABLE_CONFIGURATION_PATH: String = "config/Drawable.json"
  var DRAWABLE_RESOURCES_PATH: String = "res/drawable/"
  var DRAWABLE_SVG_RESOURCES_PATH: String = "res/drawable/svg/"
  var DRAWABLE_PNG_RESOURCES_PATH: String = "res/drawable/png/"
}
