package kz.aita

// Tax-ID shapes: government tax identifier reference and country tax authorities.
// Format validation does not assert registration or perform a registry lookup.

private val defaultStoreCountriesCatalogue: List<CountryDataModel> by lazy { listOf(CountryDataModel(
        locale = "kz",
        language = "kk",
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "Kazakhstan",
            ), LocalizedStringDataModel(
                language = "en",
                value = "Kazakhstan",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "Казахстан",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "Қазақстан",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Казакстан",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Қазоқистон",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "Qozogʻiston",
            )),
        flagDrawablePath = "png/flag_kz.png",
        cities = listOf(CityDataModel(
                name = listOf(LocalizedStringDataModel(
                        language = "en",
                        value = "Astana",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Астана",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Астана",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Ostona",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Остона",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Астана",
                    )),
                centerLatitude = 51.1667,
                centerLongitude = 71.4333,
                swLatitude = 51.023,
                swLongitude = 71.266,
                neLatitude = 51.250071,
                neLongitude = 71.55,
            ), CityDataModel(
                name = listOf(LocalizedStringDataModel(
                        language = "en",
                        value = "Almaty",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Алматы",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Алматы",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Olmaota",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Алмаато",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Алматы",
                    )),
                centerLatitude = 43.2775,
                centerLongitude = 76.8936,
                swLatitude = 43.0975,
                swLongitude = 76.795,
                neLatitude = 43.36,
                neLongitude = 77.08,
            )),
        phoneNumberCode = "7",
        phoneNumberSize = 10,
        currencies = listOf(CurrencyDataModel(
                code = "KZT",
                symbol = "₸",
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "Tenge",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "Tenge",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Тенге",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Tеңге",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Tenge",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Тенге",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Теңге",
                    )),
            )),
        cashlessPaymentOptions = listOf(PaymentOptionDataModel(
                id = "0",
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "Card",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "Card",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Карта",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Карта",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Karta",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Корт",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Карта",
                    )),
            ), PaymentOptionDataModel(
                id = "1",
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "QR",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "QR",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "QR",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "QR",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "QR",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "QR",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "QR",
                    )),
            ), PaymentOptionDataModel(
                id = "2",
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "Kaspi RED",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "Kaspi RED",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Каспи RED",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Каспи RED",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Kaspi RED",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Kaspi RED",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Kaspi RED",
                    )),
            ), PaymentOptionDataModel(
                id = "3",
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "Rakhmet",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "Rakhmet",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Рахмет",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Рахмет",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Rakhmet",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Rakhmet",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Rakhmet",
                    )),
            )),
        preferredCashlessPaymentOptionId = "1",
    ), CountryDataModel(
        locale = "tj",
        language = "tg",
        name = listOf(LocalizedStringDataModel(
                language = "en",
                value = "Tajikistan",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "Таджикистан",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "Тәжікстан",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "Tojikiston",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Тоҷикистон",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Тажикстан",
            )),
        flagDrawablePath = "png/flag_tj.png",
        cities = listOf(CityDataModel(
                name = listOf(LocalizedStringDataModel(
                        language = "en",
                        value = "Dushanbe",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Душанбе",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Душанбе",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Dushanbe",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Душанбе",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Душанбе",
                    )),
                centerLatitude = 38.5606,
                centerLongitude = 68.7778,
                swLatitude = 38.495,
                swLongitude = 68.68,
                neLatitude = 38.61,
                neLongitude = 68.88,
            ), CityDataModel(
                name = listOf(LocalizedStringDataModel(
                        language = "en",
                        value = "Khujand",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Худжанд",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Худжанд",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Xoʻjand",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Хуҷанд",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Хужанд",
                    )),
                centerLatitude = 40.2894,
                centerLongitude = 69.627,
                swLatitude = 40.256,
                swLongitude = 69.59,
                neLatitude = 40.305,
                neLongitude = 69.73,
            )),
        phoneNumberCode = "992",
        phoneNumberSize = 9,
        currencies = listOf(CurrencyDataModel(
                code = "TJS",
                symbol = "SM",
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "Somoni",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "Somoni",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Сомони",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Сомони",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Сомони",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Сомонӣ",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Somoniy",
                    )),
            )),
        cashlessPaymentOptions = listOf(PaymentOptionDataModel(
                id = "0",
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "Card",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "Card",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Карта",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Карта",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Karta",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Корт",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Карта",
                    )),
            )),
        preferredCashlessPaymentOptionId = "0",
    ), CountryDataModel(
        locale = "kg",
        language = "ky",
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "Kyrgyzstan",
            ), LocalizedStringDataModel(
                language = "en",
                value = "Kyrgyzstan",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "Кыргызстан",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "Қырғызстан",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Кыргызстан",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Қирғизистон",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "Qirgʻiziston",
            )),
        flagDrawablePath = "png/flag_kg.png",
        cities = emptyList(),
        phoneNumberCode = "996",
        phoneNumberSize = 9,
        currencies = listOf(CurrencyDataModel(
                code = "KGS",
                symbol = "сом",
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "Kyrgyz som",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "Kyrgyz som",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Кыргызский сом",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Қырғыз сомы",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Кыргыз сому",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Соми қирғизӣ",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Qirgʻiz somi",
                    )),
            )),
        cashlessPaymentOptions = listOf(PaymentOptionDataModel(
                id = "0",
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "Card",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "Card",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Карта",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Карта",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Karta",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Корт",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Карта",
                    )),
            )),
        preferredCashlessPaymentOptionId = "0",
    ), CountryDataModel(
        locale = "uz",
        language = "uz",
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "Uzbekistan",
            ), LocalizedStringDataModel(
                language = "en",
                value = "Uzbekistan",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "Узбекистан",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "Өзбекстан",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Өзбекстан",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Ӯзбекистон",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "Oʻzbekiston",
            )),
        flagDrawablePath = "png/flag_uz.png",
        cities = emptyList(),
        phoneNumberCode = "998",
        phoneNumberSize = 9,
        currencies = listOf(CurrencyDataModel(
                code = "UZS",
                symbol = "soʻm",
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "Uzbek sum",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "Uzbek sum",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Узбекский сум",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Өзбек сомы",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Өзбек суму",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Сӯми ӯзбекӣ",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Oʻzbek soʻmi",
                    )),
            )),
        cashlessPaymentOptions = listOf(PaymentOptionDataModel(
                id = "0",
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "Card",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "Card",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "Карта",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Карта",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Karta",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Корт",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Карта",
                    )),
            )),
        preferredCashlessPaymentOptionId = "0",
    )) }
fun defaultStoreCountries(): List<CountryDataModel> = defaultStoreCountriesCatalogue

private val defaultCompanyFormsCatalogue: List<CompanyFormDataModel> by lazy { listOf(CompanyFormDataModel(
        id = "0",
        countryLocales = listOf("kz"),
        legalIdFormatId = "kz_bin",
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "LLP",
            ), LocalizedStringDataModel(
                language = "en",
                value = "LLP",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ТОО",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "ЖШС",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "ЖЧШ",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "ҶДММ",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "MChJ",
            )),
        parameters = listOf(ParameterDataModel(
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "BIN",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "BIN",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "БИН",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "БИН",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "BIN",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "БИН",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "БИН",
                    )),
                value = "",
                length = 12,
                number = true,
                nonLetterSymbolsEnabled = false,
            )),
    ), CompanyFormDataModel(
        id = "kz_ip",
        countryLocales = listOf("kz"),
        legalIdFormatId = "kz_iin",
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "Individual entrepreneur",
            ), LocalizedStringDataModel(
                language = "en",
                value = "Individual entrepreneur",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ИП",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "ЖК",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Жеке ишкер",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Соҳибкори инфиродӣ",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "Yakka tartibdagi tadbirkor",
            )),
        parameters = listOf(ParameterDataModel(
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "IIN",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "IIN",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "ИИН",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "ЖСН",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Жеке идентификациялык номер",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Рақами мушаххаси инфиродӣ",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "Shaxsiy identifikatsiya raqami",
                    )),
                value = "",
                length = 12,
                number = true,
                nonLetterSymbolsEnabled = false,
            )),
    ), CompanyFormDataModel(
        id = "kz_joint_ip",
        countryLocales = listOf("kz"),
        legalIdFormatId = "kz_bin",
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "Joint entrepreneurship",
            ), LocalizedStringDataModel(
                language = "en",
                value = "Joint entrepreneurship",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ИП (совместное предпринимательство)",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "Бірлескен кәсіпкерлік",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Биргелешкен ишкердик",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Соҳибкории муштарак",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "Birgalikdagi tadbirkorlik",
            )),
        parameters = listOf(ParameterDataModel(
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "BIN",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "BIN",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "БИН",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "БИН",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "BIN",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "БИН",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "БИН",
                    )),
                value = "",
                length = 12,
                number = true,
                nonLetterSymbolsEnabled = false,
            )),
    ), CompanyFormDataModel(
        id = "tj_llc",
        countryLocales = listOf("tj"),
        legalIdFormatId = "tj_tin",
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "LLC",
            ), LocalizedStringDataModel(
                language = "en",
                value = "LLC",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ООО",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "ЖШС",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "ЖЧК",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "ҶДММ",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "MChJ",
            )),
        parameters = listOf(ParameterDataModel(
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "TIN",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "TIN",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "ИНН / РМА",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "СТН / РМА",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "TIN",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "РМА",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "ИНН",
                    )),
                value = "",
                length = 9,
                number = true,
                nonLetterSymbolsEnabled = false,
            )),
    ), CompanyFormDataModel(
        id = "tj_ip",
        countryLocales = listOf("tj"),
        legalIdFormatId = "tj_tin",
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "Individual entrepreneur",
            ), LocalizedStringDataModel(
                language = "en",
                value = "Individual entrepreneur",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ИП",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "ЖК",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Жеке ишкер",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Соҳибкори инфиродӣ",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "Yakka tartibdagi tadbirkor",
            )),
        parameters = listOf(ParameterDataModel(
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "TIN",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "TIN",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "ИНН / РМА",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "СТН / РМА",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "TIN",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "РМА",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "ИНН",
                    )),
                value = "",
                length = 9,
                number = true,
                nonLetterSymbolsEnabled = false,
            )),
    ), CompanyFormDataModel(
        id = "kg_llc",
        countryLocales = listOf("kg"),
        legalIdFormatId = "kg_tin",
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "LLC",
            ), LocalizedStringDataModel(
                language = "en",
                value = "LLC",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ОсОО",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "ЖШС",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "ЖЧК",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "ҶДММ",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "MChJ",
            )),
        parameters = listOf(ParameterDataModel(
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "TIN",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "TIN",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "ИНН",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "СТН",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "ИСН",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "РМА",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "STIR",
                    )),
                value = "",
                length = 14,
                number = true,
                nonLetterSymbolsEnabled = false,
            )),
    ), CompanyFormDataModel(
        id = "kg_ip",
        countryLocales = listOf("kg"),
        legalIdFormatId = "kg_tin",
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "Individual entrepreneur",
            ), LocalizedStringDataModel(
                language = "en",
                value = "Individual entrepreneur",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ИП",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "ЖК",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Жеке ишкер",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Соҳибкори инфиродӣ",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "Yakka tartibdagi tadbirkor",
            )),
        parameters = listOf(ParameterDataModel(
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "TIN",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "TIN",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "ИНН",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "СТН",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "ИСН",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "РМА",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "STIR",
                    )),
                value = "",
                length = 14,
                number = true,
                nonLetterSymbolsEnabled = false,
            )),
    ), CompanyFormDataModel(
        id = "uz_llc",
        countryLocales = listOf("uz"),
        legalIdFormatId = "uz_tin",
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "LLC",
            ), LocalizedStringDataModel(
                language = "en",
                value = "LLC",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ООО",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "ЖШС",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "ЖЧК",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "ҶДММ",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "MChJ",
            )),
        parameters = listOf(ParameterDataModel(
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "TIN",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "TIN",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "ИНН",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "СТН",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "ИСН",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "РМА",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "STIR",
                    )),
                value = "",
                length = 9,
                number = true,
                nonLetterSymbolsEnabled = false,
            )),
    ), CompanyFormDataModel(
        id = "uz_ip",
        countryLocales = listOf("uz"),
        legalIdFormatId = "uz_pinfl",
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "Individual entrepreneur",
            ), LocalizedStringDataModel(
                language = "en",
                value = "Individual entrepreneur",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ИП",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "ЖК",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Жеке ишкер",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Соҳибкори инфиродӣ",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "YTT",
            )),
        parameters = listOf(ParameterDataModel(
                name = listOf(LocalizedStringDataModel(
                        language = "main",
                        value = "PINFL",
                    ), LocalizedStringDataModel(
                        language = "en",
                        value = "PINFL",
                    ), LocalizedStringDataModel(
                        language = "ru",
                        value = "ПИНФЛ",
                    ), LocalizedStringDataModel(
                        language = "kk",
                        value = "Жеке сәйкестендіру нөмірі",
                    ), LocalizedStringDataModel(
                        language = "ky",
                        value = "Жеке идентификациялык номер",
                    ), LocalizedStringDataModel(
                        language = "tg",
                        value = "Рақами шахсии мушаххас",
                    ), LocalizedStringDataModel(
                        language = "uz",
                        value = "JSHSHIR",
                    )),
                value = "",
                length = 14,
                number = true,
                nonLetterSymbolsEnabled = false,
            )),
    )) }
fun defaultCompanyForms(): List<CompanyFormDataModel> = defaultCompanyFormsCatalogue

private val defaultLegalIdFormatsCatalogue: List<LegalIdFormatDataModel> by lazy { listOf(LegalIdFormatDataModel(
        id = "kz_bin",
        countryLocales = listOf("kz"),
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "BIN",
            ), LocalizedStringDataModel(
                language = "en",
                value = "BIN",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "БИН",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "БИН",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "BIN",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "БИН",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "БИН",
            )),
        label = listOf(LocalizedStringDataModel(
                language = "main",
                value = "Business Identification Number",
            ), LocalizedStringDataModel(
                language = "en",
                value = "Business Identification Number",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "Бизнес-идентификационный номер",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "Бизнес сәйкестендіру нөмірі",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "Biznes identifikatsiya raqami",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Рақами мушаххаси соҳибкорӣ",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Бизнес идентификациялык номери",
            )),
        placeholder = listOf(LocalizedStringDataModel(
                language = "main",
                value = "12 digits",
            ), LocalizedStringDataModel(
                language = "en",
                value = "12 digits",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "12 цифр",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "12 сан",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "12 ta raqam",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "12 рақам",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "12 цифра",
            )),
        required = true,
        length = 12,
        minLength = null,
        maxLength = null,
        digitsOnly = true,
        regex = "^[0-9]{12}$",
    ), LegalIdFormatDataModel(
        id = "tj_tin",
        countryLocales = listOf("tj"),
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "TIN",
            ), LocalizedStringDataModel(
                language = "en",
                value = "TIN",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ИНН / РМА",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "СТН / РМА",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "TIN",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "РМА",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "ИНН",
            )),
        label = listOf(LocalizedStringDataModel(
                language = "main",
                value = "Taxpayer Identification Number",
            ), LocalizedStringDataModel(
                language = "en",
                value = "Taxpayer Identification Number",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "Идентификационный номер налогоплательщика",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "Салық төлеушінің сәйкестендіру нөмірі",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "Soliq toʻlovchining identifikatsiya raqami",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Рақами мушаххаси андозсупоранда",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Салык төлөөчүнүн идентификациялык номери",
            )),
        placeholder = listOf(LocalizedStringDataModel(
                language = "main",
                value = "9 digits",
            ), LocalizedStringDataModel(
                language = "en",
                value = "9 digits",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "9 цифр",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "9 сан",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "9 ta raqam",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "9 рақам",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "9 цифра",
            )),
        required = true,
        length = 9,
        minLength = null,
        maxLength = null,
        digitsOnly = true,
        regex = "^[0-9]{9}$",
    ), LegalIdFormatDataModel(
        id = "kz_iin",
        countryLocales = listOf("kz"),
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "IIN",
            ), LocalizedStringDataModel(
                language = "en",
                value = "IIN",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ИИН",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "ЖСН",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Жеке идентификациялык номер",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Рақами мушаххаси инфиродӣ",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "Shaxsiy identifikatsiya raqami",
            )),
        label = listOf(LocalizedStringDataModel(
                language = "main",
                value = "IIN",
            ), LocalizedStringDataModel(
                language = "en",
                value = "IIN",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ИИН",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "ЖСН",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Жеке идентификациялык номер",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Рақами мушаххаси инфиродӣ",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "Shaxsiy identifikatsiya raqami",
            )),
        placeholder = listOf(LocalizedStringDataModel(
                language = "main",
                value = "12 digits",
            ), LocalizedStringDataModel(
                language = "en",
                value = "12 digits",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "12 цифр",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "12 сан",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "12 цифра",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "12 рақам",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "12 ta raqam",
            )),
        required = true,
        length = 12,
        digitsOnly = true,
        regex = "^[0-9]{12}$",
    ), LegalIdFormatDataModel(
        id = "kg_tin",
        countryLocales = listOf("kg"),
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "TIN",
            ), LocalizedStringDataModel(
                language = "en",
                value = "TIN",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ИНН",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "СТН",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "ИСН",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "РМА",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "STIR",
            )),
        label = listOf(LocalizedStringDataModel(
                language = "main",
                value = "TIN",
            ), LocalizedStringDataModel(
                language = "en",
                value = "TIN",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ИНН",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "СТН",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "ИСН",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "РМА",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "STIR",
            )),
        placeholder = listOf(LocalizedStringDataModel(
                language = "main",
                value = "14 digits",
            ), LocalizedStringDataModel(
                language = "en",
                value = "14 digits",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "14 цифр",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "14 сан",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "14 цифра",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "14 рақам",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "14 ta raqam",
            )),
        required = true,
        length = 14,
        digitsOnly = true,
        regex = "^[0-9]{14}$",
    ), LegalIdFormatDataModel(
        id = "uz_tin",
        countryLocales = listOf("uz"),
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "TIN",
            ), LocalizedStringDataModel(
                language = "en",
                value = "TIN",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ИНН",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "СТН",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "ИСН",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "РМА",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "STIR",
            )),
        label = listOf(LocalizedStringDataModel(
                language = "main",
                value = "TIN",
            ), LocalizedStringDataModel(
                language = "en",
                value = "TIN",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ИНН",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "СТН",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "ИСН",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "РМА",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "STIR",
            )),
        placeholder = listOf(LocalizedStringDataModel(
                language = "main",
                value = "9 digits",
            ), LocalizedStringDataModel(
                language = "en",
                value = "9 digits",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "9 цифр",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "9 сан",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "9 цифра",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "9 рақам",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "9 ta raqam",
            )),
        required = true,
        length = 9,
        digitsOnly = true,
        regex = "^[0-9]{9}$",
    ), LegalIdFormatDataModel(
        id = "uz_pinfl",
        countryLocales = listOf("uz"),
        name = listOf(LocalizedStringDataModel(
                language = "main",
                value = "PINFL",
            ), LocalizedStringDataModel(
                language = "en",
                value = "PINFL",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ПИНФЛ",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "Жеке сәйкестендіру нөмірі",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Жеке идентификациялык номер",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Рақами шахсии мушаххас",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "JSHSHIR",
            )),
        label = listOf(LocalizedStringDataModel(
                language = "main",
                value = "PINFL",
            ), LocalizedStringDataModel(
                language = "en",
                value = "PINFL",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "ПИНФЛ",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "Жеке сәйкестендіру нөмірі",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "Жеке идентификациялык номер",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "Рақами шахсии мушаххас",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "JSHSHIR",
            )),
        placeholder = listOf(LocalizedStringDataModel(
                language = "main",
                value = "14 digits",
            ), LocalizedStringDataModel(
                language = "en",
                value = "14 digits",
            ), LocalizedStringDataModel(
                language = "ru",
                value = "14 цифр",
            ), LocalizedStringDataModel(
                language = "kk",
                value = "14 сан",
            ), LocalizedStringDataModel(
                language = "ky",
                value = "14 цифра",
            ), LocalizedStringDataModel(
                language = "tg",
                value = "14 рақам",
            ), LocalizedStringDataModel(
                language = "uz",
                value = "14 ta raqam",
            )),
        required = true,
        length = 14,
        digitsOnly = true,
        regex = "^[0-9]{14}$",
    )) }
fun defaultLegalIdFormats(): List<LegalIdFormatDataModel> = defaultLegalIdFormatsCatalogue

/** Restore built-in countries missing from an older cached/server configuration. */
fun List<CountryDataModel>.withSupportedCountries(): List<CountryDataModel> =
    this + defaultStoreCountries().filter { fallback -> none { it.locale.equals(fallback.locale, true) } }

private fun List<CompanyFormDataModel>.withSupportedCompanyForms(): List<CompanyFormDataModel> =
    (this + defaultCompanyForms()).distinctBy { it.id }.map { form ->
        val shipped = defaultCompanyForms().find { it.id == form.id }
        if (shipped == null) form else form.copy(
            countryLocales = form.countryLocales.ifEmpty { shipped.countryLocales },
            legalIdFormatId = form.legalIdFormatId.ifBlank { shipped.legalIdFormatId }
        )
    }

/** Upgrade older cached catalogues without replacing operator labels, prices or country settings. */
fun GlobalAppConfigurationDataModel.withStoreCountryConfiguration(): GlobalAppConfigurationDataModel = copy(
    countries = countries.withSupportedCountries(),
    companyForms = companyForms.withSupportedCompanyForms(),
    legalIdFormats = (legalIdFormats + defaultLegalIdFormats()).distinctBy { it.id }
)

fun GlobalAppConfigurationDataModel.companyFormsForCountry(locale: String): List<CompanyFormDataModel> =
    companyForms.withSupportedCompanyForms().filter { form -> form.countryLocales.any { it.equals(locale, true) } }

fun GlobalAppConfigurationDataModel.storeLegalIdFormat(country: String, companyFormId: String?): LegalIdFormatDataModel? {
    val form = companyFormsForCountry(country).find { it.id == companyFormId } ?: return null
    return (legalIdFormats + defaultLegalIdFormats()).firstOrNull {
        it.id == form.legalIdFormatId && it.countryLocales.any { locale -> locale.equals(country, true) }
    }
}

/** Resolve complete international STORE contacts; never infer from an account's locale or phone. */
fun storeCountryFromPhones(phones: List<String>, countries: List<CountryDataModel> = defaultStoreCountries()): CountryDataModel? {
    val matches = phones.filter { it.isNotBlank() }.map { raw ->
        if (raw.any { it !in "+ ()-.0123456789" } || raw.count { it == '+' } > 1 ||
            ('+' in raw && !raw.trim().startsWith('+'))) return null
        val digits = raw.filter { it in '0'..'9' }
        countries.singleOrNull { country ->
            digits.startsWith(country.phoneNumberCode) && digits.length == country.phoneNumberCode.length + country.phoneNumberSize &&
                // +7 is shared with Russia: Kazakhstan uses national prefixes 6 and 7.
                (country.locale != "kz" || digits.getOrNull(1) in listOf('6', '7'))
        } ?: return null
    }.distinctBy { it.locale }
    return matches.singleOrNull()
}

fun countryCurrency(locale: String): String = defaultStoreCountries()
    .firstOrNull { it.locale.equals(locale, true) }?.currencies?.firstOrNull()?.code ?: "KZT"
