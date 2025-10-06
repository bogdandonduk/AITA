package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class QuantityDataModel(
  val id: Long,
  val immutableUnitName: List<LocalizedStringDataModel>,
  val total: Double = 1.0,
  val pricedAmount: Double = 1.0,
  val roundTotal: Boolean
) {

  fun matchesName(name: String): Boolean {
    return immutableUnitName.any {
      it.value.equals(name, true)
    }
  }
}
