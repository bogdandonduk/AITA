package kz.aita

/** Request-only receipts must not become durable model/cache data, even with an older echoing server. */
fun StoreDataModel.withoutContactVerification(): StoreDataModel = copy(
    contactVerificationId = "", contactEmailProofs = emptyList(),
    branches = branches.map { it.withoutContactVerification() }
)

fun SupplierDataModel.withoutContactVerification(): SupplierDataModel = copy(
    contactVerificationId = "", contactEmailProofs = emptyList()
)
