package kz.aita

import kotlin.test.*

class SpokenNumbersTest {
    @Test fun cardinalNumbersAcrossAllAppLanguages() {
        val examples = listOf(
            Triple("en-US", "one hundred and twenty-five", "125"),
            Triple("ru-RU", "сто двадцать пять", "125"),
            Triple("kk-KZ", "бір жүз жиырма бес", "125"),
            Triple("ky-KG", "жүз жыйырма беш", "125"),
            Triple("tg-TJ", "саду бисту панҷ", "125"),
            Triple("uz-UZ", "bir yuz yigirma besh", "125"),
            Triple("uz", "бир юз йигирма беш", "125"),
            Triple("kk", "он бес", "15"), Triple("ky", "он бир", "11"),
            Triple("uz", "oʻn to‘rt", "14"), Triple("tg", "ёздаҳ", "11"),
            Triple("ru", "две тысячи триста сорок пять", "2345"),
            Triple("en", "two million three thousand four hundred five", "2003405")
        )
        examples.forEach { (language, words, digits) -> assertEquals(digits, spokenNumbersToDigits(words, language), words) }
    }

    @Test fun digitByDigitCodesKeepLeadingZerosAndAreNotSummed() {
        for ((language, words) in listOf("en" to "zero zero one two three", "ru" to "ноль ноль один два три",
            "kk" to "нөл нөл бір екі үш", "ky" to "нөл нөл бир эки үч", "tg" to "сифр сифр як ду се", "uz" to "nol nol bir ikki uch")) {
            assertEquals("00123", spokenNumbersToDigits(words, language))
        }
        assertEquals("007", spokenNumbersToDigits("00 seven", "en"))
        assertEquals("00123", voiceTextForField("0 0 1 2 3", "en", VoiceNumberField.Digits))
        assertEquals("+007", voiceTextForField("+zero zero seven", "en", VoiceNumberField.Digits))
    }

    @Test fun decimalsRetainFractionalZerosWithoutFloatingPointRounding() {
        val examples = listOf("en" to "twelve point zero five", "ru" to "двенадцать запятая ноль пять",
            "kk" to "он екі үтір нөл бес", "ky" to "он эки үтүр нөл беш", "tg" to "дувоздаҳ нуқта сифр панҷ", "uz" to "o'n ikki vergul nol besh")
        examples.forEach { (language, words) -> assertEquals("12.05", spokenNumbersToDigits(words, language)) }
        assertEquals("0.5", spokenNumbersToDigits("point five", "en"))
        assertEquals("-125.50", spokenNumbersToDigits("minus one hundred twenty five point five zero", "en"))
        assertEquals("1.25", spokenNumbersToDigits("one point twenty five", "en"))
        assertEquals("1.000000001", spokenNumbersToDigits("one point zero zero zero zero zero zero zero zero one", "en"))
    }

    @Test fun surroundingTextPunctuationAndExistingNumericTextRemainIntact() {
        assertEquals("Buy 2 apples, then 25 pears.", spokenNumbersToDigits("Buy two apples, then twenty-five pears.", "en"))
        assertEquals("Цена 125 тенге", spokenNumbersToDigits("Цена сто двадцать пять тенге", "ru"))
        for (text in listOf("0.05", "00123", "1,25", "iPhone12", "A-52", "Oh hello", "twentyfive")) assertEquals(text, spokenNumbersToDigits(text, "en"))
        assertEquals("он купил 2 яблока", spokenNumbersToDigits("он купил два яблока", "ru"))
        assertEquals("10", spokenNumbersToDigits("он", "kk"))
        assertEquals("one hundred", spokenNumbersToDigits("one hundred", "de-DE"))
    }

    @Test fun malformedOrAmbiguousAmountsAreNeverGuessed() {
        for (text in listOf("one and two", "twelve thirteen", "twenty thirty", "one hundred two three",
            "one million thousand", "one thousand one million", "one point five point six", "one point hundred", "minus")) {
            assertEquals(text, spokenNumbersToDigits(text, "en"), text)
            assertNull(voiceTextForField(text, "en", VoiceNumberField.Decimal), text)
        }
        assertEquals("one and apples", spokenNumbersToDigits("one and apples", "de"))
        assertNull(voiceTextForField("five apples", "en", VoiceNumberField.Integer))
        assertNull(voiceTextForField("one point five", "en", VoiceNumberField.Integer))
        assertNull(voiceTextForField("minus five", "en", VoiceNumberField.Digits))
        assertEquals("12,05", voiceTextForField("12,05", "ru", VoiceNumberField.Decimal))
    }

    @Test fun largeOrUnsupportedSpeechIsPreserved() {
        val tooLarge = "999999999999999 billion"
        assertEquals(tooLarge, spokenNumbersToDigits(tooLarge, "en"))
        val tooLong = "one ".repeat(2000)
        assertEquals(tooLong, spokenNumbersToDigits(tooLong, "en"))
        assertNull(voiceTextForField("9".repeat(129), "en", VoiceNumberField.Integer))
    }
}
