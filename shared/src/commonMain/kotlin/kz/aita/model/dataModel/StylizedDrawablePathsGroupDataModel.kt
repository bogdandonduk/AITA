package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class StylizedDrawablePathsGroupDataModel(
  val id: Long,
  val values: List<StylizedDrawablePathsDataModel>
)

private val ins = listOf(
  StylizedDrawablePathsGroupDataModel(
    id = 0,
    values = listOf(
      StylizedDrawablePathsDataModel(
        locale = "",
        theme = 0,
        path = "svg/110.svg"
      ),
      StylizedDrawablePathsDataModel(
        locale = "",
        theme = 1,
        path = "svg/111.svg"
      )
    )
  )
)