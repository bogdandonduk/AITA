package kz.aita.server.util

import kotlinx.serialization.json.Json
import kz.aita.model.dataModel.ExceptionDataModel
import java.nio.file.Files
import java.nio.file.Path

const val serverFilesPath = "AITA/server"
const val configAppPath = "$serverFilesPath/config/app"

fun getExceptions(): List<ExceptionDataModel> {
  return Json.decodeFromString(Files.readString(Path.of(configAppPath).resolve("exceptions.json")))
}

fun getException(id: Long): ExceptionDataModel? {
  return getExceptions().find { it.id == id }
}