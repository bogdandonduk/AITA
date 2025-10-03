package kz.aita.model.dataModel.response

data class GoodsItemDataModel(
    val id: Long,
    val barcode: String,
    val name: String,
    val isQuickGoodsItem: Boolean,
    val salePrice: String,
    val saleCurrency: String,
    val supplyPrice: String,
    val supplyCurrency: String
)