package kz.aita.help
import kotlin.test.*

class HelpCatalogueTest {
    private fun tutorial(mode:HelpMode=HelpMode.BUYER,id:String="buyer.details")=HelpTutorial(id,setOf(mode),HelpCategory.MARKETPLACE,
        mapOf("en" to "Read product details","ru" to "Сведения о товаре"),mapOf("en" to "Review the offer","ru" to "Проверьте предложение"),
        listOf(HelpStep("step-1",mapOf("en" to "Open the offer and check availability","ru" to "Откройте товар и проверьте наличие")),HelpStep("step-2",mapOf("en" to "Check the shop and unit","ru" to "Проверьте магазин и единицу"))))
    @Test fun validTextOnlyBookNeedsNoImages() { assertTrue(validHelpCatalogue(HelpCatalogue(tutorials=listOf(tutorial())))) }
    @Test fun filterAndValidationAreBothModeScoped() {
        val book=HelpCatalogue(tutorials=listOf(tutorial(),tutorial(HelpMode.STORE,"sale.pay")))
        assertEquals(listOf("buyer.details"),book.forMode(HelpMode.BUYER).tutorials.map {it.id})
        assertFalse(validHelpCatalogue(book,HelpMode.BUYER));assertTrue(validHelpCatalogue(book.forMode(HelpMode.BUYER),HelpMode.BUYER))
    }
    @Test fun searchIncludesStepsAndAllSearchTerms() {
        val book=HelpCatalogue(tutorials=listOf(tutorial()))
        assertEquals(1,filterHelpTutorials(book,HelpMode.BUYER,"en","offer availability").size)
        assertTrue(filterHelpTutorials(book,HelpMode.STORE,"en","offer").isEmpty())
        assertEquals(1,filterHelpTutorials(book,HelpMode.BUYER,"ru","товар наличие").size)
        assertTrue(filterHelpTutorials(book,HelpMode.BUYER,"en","unmatched").isEmpty())
    }
    @Test fun invalidScreenshotPathsAreRejected() {
        val t=tutorial()
        for(asset in listOf("../secret.png","https://example.org/a.png","local.png","<script>")) {
            val s=t.steps[0].copy(screenshots=listOf(HelpScreenshot(asset,mapOf("en" to "Picture"),width=100,height=100)))
            assertFalse(validHelpCatalogue(HelpCatalogue(tutorials=listOf(t.copy(steps=listOf(s,t.steps[1]))))))
        }
    }
    @Test fun validFutureImageRoundTripsWithCaptionAndAlternateText() {
        val image=HelpScreenshot("a".repeat(64)+".png",mapOf("en" to "The highlighted field"),mapOf("en" to "Check this value"),400,800,"en")
        assertEquals(image,helpJson.decodeFromString<HelpScreenshot>(helpJson.encodeToString(HelpScreenshot.serializer(),image)))
    }
    @Test fun duplicateIdsAndBrokenFaqLinksFailClosed() {
        val t=tutorial();assertFalse(validHelpCatalogue(HelpCatalogue(tutorials=listOf(t,t))))
        val faq=HelpFaq("faq.invalid",setOf(HelpMode.STORE),HelpCategory.START,mapOf("en" to "Question"),mapOf("en" to "Answer"),t.id)
        assertFalse(validHelpCatalogue(HelpCatalogue(tutorials=listOf(t),faqs=listOf(faq))))
    }
    @Test fun unknownSchemaAndFutureModeNamesAreNotGuessed() {
        assertFalse(validHelpCatalogue(HelpCatalogue(schema=2,tutorials=listOf(tutorial()))))
        assertNull(HelpMode.fromSlug("admin"));assertEquals(HelpMode.MANUFACTURER,HelpMode.fromId(3))
    }
    @Test fun localeFallbackDoesNotPretendEnglishIsTranslated() {
        assertEquals("en",mapOf("en" to "Text").languageFor("uz"))
        assertEquals("Text",mapOf("en" to "Text").localized("ky"))
    }
    @Test fun incompleteTranslationFallsBackAsAWholeArticle() {
        val t=tutorial().copy(introduction=mapOf("en" to "Review the offer"))
        assertEquals("en",t.contentLanguageFor("ru"))
        assertEquals("ru",tutorial().contentLanguageFor("ru"))
        assertEquals("en",tutorial().contentLanguageFor("uz"))
        assertTrue(filterHelpTutorials(HelpCatalogue(tutorials=listOf(t)),HelpMode.BUYER,"ru","товар").isEmpty())
    }
}
