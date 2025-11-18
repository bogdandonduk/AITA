package kz.aita.model.dataModel

import kotlinx.serialization.Serializable
import kz.aita.core.Searchable

@Serializable
data class GoodsItemDataModel(
  val id: String,
  val userId: String,
  val storeId: String,
  val barcode: List<String>,
  val name: List<LocalizedStringDataModel>,
  val quantity: QuantityDataModel,
  val categoryIds: List<String>,
  val salePrices: List<PriceDataModel>,
  val returnPrices: List<PriceDataModel> = salePrices,
  val supplyPrices: List<PriceDataModel>,
  val isQuickItem: Boolean,
  val createdAt: Long,
  val isActive: Boolean
): Searchable {

  override val exactSearchOperands: List<String>
    get() = mutableListOf<String>().apply {
      addAll(barcode)
      addAll(name.map { it.value })
      addAll(salePrices.map { it.price })
      addAll(returnPrices.map { it.price })
      addAll(supplyPrices.map { it.price })
      addAll(salePrices.map { it.currency })
      addAll(returnPrices.map { it.currency })
      addAll(supplyPrices.map { it.currency })
    }
  override val containsSearchOperands: List<String>
    get() = mutableListOf<String>().apply {
      addAll(barcode)
      addAll(name.map { it.value })
      addAll(salePrices.map { it.price })
      addAll(returnPrices.map { it.price })
      addAll(supplyPrices.map { it.price })
      addAll(salePrices.map { it.currency })
      addAll(returnPrices.map { it.currency })
      addAll(supplyPrices.map { it.currency })
    }
  override val uniqueSearchOperands: List<String>
    get() = mutableListOf<String>().apply {
      addAll(barcode)
    }
}
