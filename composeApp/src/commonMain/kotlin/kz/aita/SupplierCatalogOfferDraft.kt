package kz.aita

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
internal data class SupplierCatalogOfferFields(
    val price: String = "",
    val minimum: String = "",
    val packageSize: String = "",
    val name: String = "",
    val barcode: String = "",
    val currency: String = "KZT",
    val minimumTemplate: QuantityDataModel? = null,
    val packageTemplate: QuantityDataModel? = null
)

/** Only editable text lives here. A draft is never a queued supplier-price mutation. */
@Serializable
internal data class SupplierCatalogOfferDraft(
    val owner: String,
    val baseline: SupplierCatalogOfferFields,
    val fields: SupplierCatalogOfferFields = baseline
) {
    val edited: Boolean get() = fields != baseline

    fun refreshed(latest: SupplierCatalogOfferFields): SupplierCatalogOfferDraft =
        if (edited && fields != latest) this else copy(baseline = latest, fields = latest)

    /** Preserve every field changed after Save, even when the server normalizes the submitted price. */
    fun acknowledged(
        submitted: SupplierCatalogOfferFields,
        saved: SupplierCatalogOfferFields
    ): SupplierCatalogOfferDraft = copy(
        baseline = saved,
        fields = SupplierCatalogOfferFields(
            price = if (fields.price == submitted.price) saved.price else fields.price,
            minimum = if (fields.minimum == submitted.minimum) saved.minimum else fields.minimum,
            packageSize = if (fields.packageSize == submitted.packageSize) saved.packageSize else fields.packageSize,
            name = if (fields.name == submitted.name) saved.name else fields.name,
            barcode = if (fields.barcode == submitted.barcode) saved.barcode else fields.barcode,
            currency = if (fields.currency == submitted.currency) saved.currency else fields.currency,
            minimumTemplate = if (fields.minimum == submitted.minimum) saved.minimumTemplate else fields.minimumTemplate,
            packageTemplate = if (fields.packageSize == submitted.packageSize) saved.packageTemplate else fields.packageTemplate
        )
    )
}

internal fun supplierCatalogOfferDraftKey(account: String, supplier: String, store: String, goods: String): String =
    "supplier-offer-draft:" + listOf(account, supplier, store, goods).joinToString(":") { it.trim().lowercase() }

internal fun decodeSupplierCatalogOfferDraft(value: String?, owner: String): SupplierCatalogOfferDraft? =
    value?.takeIf { it.length <= 8192 }?.let { encoded ->
        runCatching { jsonBase.decodeFromString<SupplierCatalogOfferDraft>(encoded) }.getOrNull()
            ?.takeIf { it.owner == owner && it.baseline.currency.isNotBlank() && it.fields.currency.isNotBlank() }
    }

internal fun SupplierCatalogOfferDraft.encoded(): String = jsonBase.encodeToString(this)

internal fun AppConfiguration.supplierOfferDraftText(key: String): String {
    val language = when (stateValues.appLanguage.lowercase()) {
        "ru" -> 1; "kk" -> 2; "ky" -> 3; "tg", "tj" -> 4; "uz" -> 5; else -> 0
    }
    return supplierOfferDraftWords.getValue(key)[language]
}

private val supplierOfferDraftWords = mapOf(
    "overview" to listOf("Overview", "Обзор", "Шолу", "Сереп", "Шарҳ", "Umumiy ko‘rinish"),
    "draft" to listOf("Unsaved offer", "Несохранённое предложение", "Сақталмаған ұсыныс", "Сакталбаган сунуш", "Пешниҳоди захиранашуда", "Saqlanmagan taklif"),
    "changed" to listOf(
        "The saved offer changed. Your draft is kept; review it before saving.",
        "Сохранённое предложение изменилось. Ваш черновик сохранён; проверьте его перед сохранением.",
        "Сақталған ұсыныс өзгерді. Жобаңыз сақталды; сақтамас бұрын тексеріңіз.",
        "Сакталган сунуш өзгөрдү. Долбооруңуз сакталды; сактоодон мурун текшериңиз.",
        "Пешниҳоди захирашуда тағйир ёфт. Пешнависи шумо нигоҳ дошта шуд; пеш аз захира санҷед.",
        "Saqlangan taklif o‘zgardi. Qoralamangiz saqlandi; saqlashdan oldin tekshiring."
    ),
    "reset" to listOf("Use saved offer", "Вернуть сохранённое предложение", "Сақталған ұсынысты қолдану", "Сакталган сунушту колдонуу", "Истифодаи пешниҳоди захирашуда", "Saqlangan taklifni ishlatish"),
    "kept" to listOf(
        "Offer saved. Your newer edits are still unsaved.",
        "Предложение сохранено. Более поздние правки ещё не сохранены.",
        "Ұсыныс сақталды. Кейінгі өзгерістер әлі сақталмады.",
        "Сунуш сакталды. Кийинки өзгөртүүлөр али сактала элек.",
        "Пешниҳод захира шуд. Тағйироти навтари шумо ҳанӯз захира нашудаанд.",
        "Taklif saqlandi. Keyinroq kiritilgan o‘zgarishlaringiz hali saqlanmagan."
    )
)
