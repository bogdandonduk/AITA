package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class LocalizedStringGroupDataModel(
  val id: Long,
  val values: List<LocalizedStringDataModel>
)

private val ins = listOf(
  LocalizedStringGroupDataModel(
    id = 0,
    values = listOf(
      LocalizedStringDataModel(
        "en",
        "AITA"
      ),
      LocalizedStringDataModel(
        "ru",
        "AITA"
      ),
      LocalizedStringDataModel(
        "kk",
        "AITA"
      ),
    ),
  ),
  LocalizedStringGroupDataModel(
    id = 1,
    values = listOf(
      LocalizedStringDataModel(
        "en",
        "Log In"
      ),
      LocalizedStringDataModel(
        "ru",
        "Войти"
      ),
      LocalizedStringDataModel(
        "kk",
        "Кіру"
      )
    )
  )
)