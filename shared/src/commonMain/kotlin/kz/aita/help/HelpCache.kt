package kz.aita.help
import kz.aita.readJsonCacheText
import kz.aita.writeJsonCacheText
suspend fun readHelpCatalogueCache(mode:HelpMode): HelpCatalogue? = readJsonCacheText("help.book.v1:${mode.slug}")?.let { text ->
    if(text.length>HELP_CATALOGUE_MAX_BYTES) null else runCatching { helpJson.decodeFromString<HelpCatalogue>(text) }.getOrNull()?.takeIf { validHelpCatalogue(it,mode) }
}
suspend fun writeHelpCatalogueCache(mode:HelpMode,book:HelpCatalogue) {
    require(validHelpCatalogue(book,mode))
    val text=helpJson.encodeToString(HelpCatalogue.serializer(),book)
    require(text.encodeToByteArray().size<=HELP_CATALOGUE_MAX_BYTES)
    writeJsonCacheText("help.book.v1:${mode.slug}",text)
}
