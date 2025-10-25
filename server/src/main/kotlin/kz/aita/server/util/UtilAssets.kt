package kz.aita.server.util

import kotlinx.serialization.json.Json
import kz.aita.model.dataModel.RemoteResponseDataModel
import java.nio.file.Files
import java.nio.file.Path

const val serverFilesPath = "AITA/server"
const val configAppPath = "$serverFilesPath/config/app"

fun getResponses(): List<RemoteResponseDataModel> {
  return Json.decodeFromString(Files.readString(Path.of(configAppPath).resolve("responses.json")))
}

fun getResponse(id: String): RemoteResponseDataModel {
  return getResponses().find { it.id == id }!!
}
