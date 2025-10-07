package kz.aita.model.dataModel

import kotlinx.serialization.Serializable
import kz.aita.core.Searchable

@Serializable
data class GoodsItemDataModel(
  val id: String,
  val barcode: String,
  val name: String,
  val quantity: QuantityDataModel,
  val categoryId: String,
  val supplierId: String,
  val salePrice: Double,
  val returnPrice: Double = salePrice,
  val supplyPrice: Double,
  val saleCurrency: String,
  val returnCurrency: String = saleCurrency,
  val supplyCurrency: String
): Searchable {

  override fun searchExact(query: String): Boolean {
    return barcode.equals(query, true)
        || name.equals(query, true)
        || categoryId.equals(query, true)
        || supplierId.equals(query, true)
        || salePrice.toString().equals(query, true)
        || returnPrice.toString().equals(query, true)
        || supplyPrice.toString().equals(query, true)
        || saleCurrency.equals(query, true)
        || returnCurrency.equals(query, true)
        || supplyCurrency.equals(query, true)
  }

  override fun searchContains(query: String): Boolean {
    return barcode.contains(query, true)
        || name.contains(query, true)
        || categoryId.contains(query, true)
        || supplierId.contains(query, true)
        || salePrice.toString().contains(query, true)
        || returnPrice.toString().contains(query, true)
        || supplyPrice.toString().contains(query, true)
        || saleCurrency.contains(query, true)
        || returnCurrency.contains(query, true)
        || supplyCurrency.contains(query, true)
  }

  override fun searchUnique(query: String): Boolean {
    return barcode.equals(query, true)
  }
}
