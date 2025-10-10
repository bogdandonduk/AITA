package kz.aita.jvm.app.system.core

import java.nio.file.Files
import java.nio.file.Paths

fun getOrCreateCacheDirPath(): String {
  return Files.createDirectories(
    System.getProperty("os.name").lowercase().run {
      when {
        contains("win") -> {
          val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
          Paths.get(
            base,
            ".aita",
            "Cache"
          )
        }

        contains("mac") -> {
          Paths.get(
            System.getProperty("user.home"),
            ".aita",
            "Caches",
            "AITA"
          )
        }

        else -> {
          Paths.get(
            System.getenv("XDG_CACHE_HOME") ?: "${System.getProperty("user.home")}/.cache",
            ".aita"
          )
        }
      }
    }
  ).toFile().absolutePath
}