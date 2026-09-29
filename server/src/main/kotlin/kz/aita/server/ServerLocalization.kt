package kz.aita.server

import kz.aita.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*

/** Add missing shipped translations only; never change prices, URLs, credentials or country data. */
internal fun mergeConfigurationLanguageValues(primary: JsonElement, bundled: JsonElement): JsonElement {
    if (primary is JsonObject && bundled is JsonObject) return JsonObject(primary.mapValues { (key, value) ->
        bundled[key]?.let { mergeConfigurationLanguageValues(value, it) } ?: value
    })
    if (primary !is JsonArray || bundled !is JsonArray || primary.isEmpty()) return primary
    fun JsonElement.field(key: String): String? = (this as? JsonObject)?.get(key)?.let { (it as? JsonPrimitive)?.contentOrNull }
    if ((primary + bundled).all { it.field("language") != null && it.field("value") != null }) {
        val old = primary.associateBy { canonicalLanguageCode(it.field("language")) }
        val additions = bundled.filter { value ->
            val code = canonicalLanguageCode(value.field("language"))
            code in setOf("tg", "ky", "uz") && old[code]?.field("value").isNullOrBlank()
        }
        // A locally authored label must not acquire the translation of a different shipped label.
        val sameMeaning = primary.filter { canonicalLanguageCode(it.field("language")) in setOf("main", "en", "ru", "kk") }
            .all { value -> bundled.firstOrNull { it.field("language") == value.field("language") }
                ?.field("value")?.let { it == value.field("value") } ?: true }
        if (!sameMeaning || additions.isEmpty()) return primary
        val addedCodes = additions.map { canonicalLanguageCode(it.field("language")) }.toSet()
        return JsonArray(primary.filterNot { canonicalLanguageCode(it.field("language")) in addedCodes } + additions)
    }
    // Country identity is its country code, NOT its language. Never append a country/provider.
    val key = listOf("id", "locale", "code", "language").firstOrNull { field ->
        (primary + bundled).all { it.field(field) != null }
    }
    return if (key != null) {
        val indexed = bundled.associateBy { it.field(key) }
        JsonArray(primary.map { value -> indexed[value.field(key)]?.let { mergeConfigurationLanguageValues(value, it) } ?: value })
    } else if (primary.size == bundled.size) {
        JsonArray(primary.mapIndexed { index, value -> mergeConfigurationLanguageValues(value, bundled[index]) })
    } else primary
}

private val bundledConfigurationForLanguages: JsonElement? by lazy {
    val stream = aitaServerRuntimeClassLoader.getResourceAsStream("config/app/global.json")
        ?: aitaServerRuntimeClassLoader.getResourceAsStream("app/global.json")
    stream?.use { Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)) }
}

internal fun enrichGlobalConfigurationLanguages(raw: String, bundled: JsonElement? = bundledConfigurationForLanguages): String {
    val parsed = Json.parseToJsonElement(raw)
    val root = (bundled?.let { mergeConfigurationLanguageValues(parsed, it) } ?: parsed) as? JsonObject ?: return raw
    val payload = root["payload"] as? JsonObject ?: return raw
    val languages = payload["languages"]?.let { jsonBase.decodeFromJsonElement(ListSerializer(AppLanguageDataModel.serializer()), it) }.orEmpty()
    val enriched = jsonBase.encodeToJsonElement(ListSerializer(AppLanguageDataModel.serializer()), languages.withBundledAppLanguages())
    return JsonObject(root + ("payload" to enrichStoreCountryConfiguration(JsonObject(payload + ("languages" to enriched))))).toString()
}

/** Managed installations retain their own global.json; append newly supported countries explicitly. */
internal fun enrichStoreCountryConfiguration(payload: JsonObject): JsonObject {
    fun appendMissing(field: String, key: String, shipped: JsonArray): JsonArray {
        val existing = payload[field] as? JsonArray ?: JsonArray(emptyList())
        val ids = existing.mapNotNull { (it as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull?.lowercase() }.toSet()
        return JsonArray(existing + shipped.filter { it.jsonObject.getValue(key).jsonPrimitive.content.lowercase() !in ids })
    }
    val countries = jsonBase.encodeToJsonElement(ListSerializer(CountryDataModel.serializer()), defaultStoreCountries()).jsonArray
    val forms = jsonBase.encodeToJsonElement(ListSerializer(CompanyFormDataModel.serializer()), defaultCompanyForms()).jsonArray
    val formats = jsonBase.encodeToJsonElement(ListSerializer(LegalIdFormatDataModel.serializer()), defaultLegalIdFormats()).jsonArray
    val indexedForms = forms.associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }
    val completeForms = JsonArray(appendMissing("companyForms", "id", forms).map { element ->
        val form = element as? JsonObject ?: return@map element
        val shipped = indexedForms[form["id"]?.jsonPrimitive?.contentOrNull]?.jsonObject ?: return@map form
        JsonObject(form.toMutableMap().apply {
            if ((form["countryLocales"] as? JsonArray).isNullOrEmpty()) put("countryLocales", shipped.getValue("countryLocales"))
            if ((form["legalIdFormatId"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()) put("legalIdFormatId", shipped.getValue("legalIdFormatId"))
        })
    })
    return JsonObject(payload + mapOf(
        "goodsItemsQuantityUnits" to appendMissing("goodsItemsQuantityUnits", "id", jsonBase.encodeToJsonElement(ListSerializer(QuantityDataModel.serializer()), listOf(gramsQuantityUnit())).jsonArray),
        "countries" to appendMissing("countries", "locale", countries),
        "companyForms" to completeForms,
        "legalIdFormats" to appendMissing("legalIdFormats", "id", formats)
    ))
}

private val bundledResponseLanguageValues: Map<String, List<LocalizedStringDataModel>> by lazy {
    val stream = aitaServerRuntimeClassLoader.getResourceAsStream("config/app/responses.json")
        ?: aitaServerRuntimeClassLoader.getResourceAsStream("app/responses.json")
    stream?.use { jsonBase.decodeFromString(ListSerializer(RemoteResponseDataModel.serializer()),
        it.readBytes().toString(Charsets.UTF_8)).associate { row -> row.id to row.message } }.orEmpty()
}

internal fun completeResponseLanguages(response: RemoteResponseDataModel): RemoteResponseDataModel {
    val bundled = bundledResponseLanguageValues[response.id] ?: return response
    val sameMeaning = response.message.filter { it.language in setOf("main", "en", "ru", "kk") }.all { old ->
        bundled.firstOrNull { it.language == old.language }?.value?.let { it == old.value } ?: true
    }
    return if (!sameMeaning) response else response.copy(message = response.message.withMissingLocalizedValues(
        bundled.filter { it.language in setOf("tg", "ky", "uz") }))
}
