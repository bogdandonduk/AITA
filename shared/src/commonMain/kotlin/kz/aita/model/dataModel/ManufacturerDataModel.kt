package kz.aita.model.dataModel

data class ManufacturerDataModel(
  val id: String,
  val name: List<LocalizedStringDataModel>,
  val alias: List<LocalizedStringDataModel>?,
  val description: List<LocalizedStringDataModel>?
)