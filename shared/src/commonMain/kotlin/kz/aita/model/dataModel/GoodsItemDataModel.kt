package kz.aita.model.dataModel

import kotlinx.serialization.Serializable
import kz.aita.core.Searchable

@Serializable
data class GoodsItemDataModel(
  val id: String,
  val userId: String,
  val storeId: String,
  val barcode: String,
  val name: String,
  val quantity: QuantityDataModel,
  val categoryIds: List<String>?,
  val supplierIds: List<String>?,
  val salePricesToSupplierIds: List<Pair<Double, String>>,
  val returnPricesToSupplierIds: List<Pair<Double, String>> = salePricesToSupplierIds,
  val supplyPricesToSupplierIds: List<Pair<Double, String>>,
  val saleCurrencyToSupplierIds: List<Pair<String, String>>,
  val returnCurrencyToSupplierIds: List<Pair<String, String>> = saleCurrencyToSupplierIds,
  val supplyCurrencyToSupplierIds: List<Pair<String, String>> = saleCurrencyToSupplierIds
): Searchable {

  override val exactSearchOperands: List<String>
    get() = listOf(barcode, name)
  override val containsSearchOperands: List<String>
    get() = listOf(barcode, name)
  override val uniqueSearchOperands: List<String>
    get() = listOf(barcode)
}
