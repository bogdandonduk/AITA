package kz.aita

/** Records an actual bank payment against an invoice, never marks an unpaid invoice as paid. */
const val INVOICE_BANK_PAYMENT_OPTION_ID = "90"
fun invoiceBankPaymentOption() = PaymentOptionDataModel(INVOICE_BANK_PAYMENT_OPTION_ID,
    listOf("main" to "Paid invoice · bank transfer", "en" to "Paid invoice · bank transfer", "ru" to "Оплаченный счёт · Перевод",
        "kk" to "Төленген шот · Аударым", "ky" to "Төлөнгөн эсеп · Которуу", "tg" to "Ҳисоби пардохтшуда · Интиқол", "uz" to "To‘langan hisob · O‘tkazma")
        .map { LocalizedStringDataModel(it.first,it.second) })
