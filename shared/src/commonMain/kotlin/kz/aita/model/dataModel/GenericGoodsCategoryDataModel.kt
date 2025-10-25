package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GenericGoodsCategoryDataModel(
  val id: String,
  val typeIds: List<String>?,
  val name: List<LocalizedStringDataModel>,
  val quantityUnitId: String,
  val imagePaths: List<StylizedDrawablePathsGroupDataModel>?
)