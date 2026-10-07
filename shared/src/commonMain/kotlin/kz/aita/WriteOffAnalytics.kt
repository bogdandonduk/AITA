package kz.aita

fun scopedWriteOffs(records:List<StockWriteOff>,storeId:String,start:Long,end:Long,goodsId:String?=null,supplierId:String?=null,categoryId:String?=null):List<StockWriteOff> =
    records.filter {it.command.storeId==storeId && it.timeMillis>=start && it.timeMillis<end &&
        (goodsId.isNullOrBlank() || it.goodsItemId==goodsId) && (supplierId.isNullOrBlank() || it.supplierId==supplierId) &&
        (categoryId.isNullOrBlank() || categoryId in it.categoryIds)}.distinctBy {it.command.id}

fun StoreAnalyticsDashboardDataModel.withWriteOffs(records:List<StockWriteOff>?):StoreAnalyticsDashboardDataModel {
    if(records==null) return copy(writeOffCount=null,writeOffCosts=emptyMap())
    val rows=scopedWriteOffs(records,storeId,startMillis,endMillisExclusive,goodsItemIdFilter,supplierIdFilter,categoryIdFilter)
    return copy(writeOffCount=rows.size,writeOffCosts=rows.groupBy {it.supplyPrice.currency.uppercase()}.mapValues {(_,v)->v.sumOf {it.cost}.roundMoney()})
}
