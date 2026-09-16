package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals

class DebtorPhoneInputTest {
    @Test fun selectingCountryDoesNotCreateContact() { assertEquals("", debtorPhoneValue("+7", "")) }
    @Test fun nationalNumberGetsExactlyOnePrefix() { assertEquals("77011234567", debtorPhoneValue("+7", "7011234567")) }
    @Test fun savedFullNumberRestoresOnlyNationalPart() { assertEquals("7011234567", debtorPhoneNationalNumber("+77011234567", "+7")) }
    @Test fun longerCallingCodeRestoresCorrectly() { assertEquals("901234567", debtorPhoneNationalNumber("+998901234567", "+998")) }
    @Test fun countryChangeKeepsNationalDigits() {
        val national = debtorPhoneNationalNumber("77011234567", "+7")
        assertEquals("447011234567", debtorPhoneValue("+44", national))
    }
    @Test fun blankPhoneStaysBlankAfterCountryChange() { assertEquals("", debtorPhoneValue("+998", debtorPhoneNationalNumber("", "+7"))) }
}
