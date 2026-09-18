package kz.aita

internal fun AppConfiguration.returnFlowText(key: String): String {
    val words = returnFlowWords[key] ?: return key
    val index = when (stateValues.appLanguage.lowercase()) { "ru" -> 1; "kk" -> 2; "ky" -> 3; "tg", "tj" -> 4; "uz" -> 5; else -> 0 }
    return words[index]
}
private val returnFlowWords = mapOf(
    "items" to listOf("Items", "Товары", "Тауарлар", "Товарлар", "Молҳо", "Tovarlar"),
    "search" to listOf("Enter transaction id", "Введите ID транзакции", "Транзакция ID енгізіңіз", "Транзакция ID киргизиңиз", "ID-и амалиётро ворид кунед", "Tranzaksiya ID sini kiriting"),
    "batches" to listOf("Return destination", "Куда вернуть товар", "Тауарды қайда қайтару", "Товарды кайда кайтаруу", "Ҷойи баргардонидани мол", "Tovarni qaytarish joyi"),
    "batch_kind" to listOf("Batch type", "Тип партии", "Партия түрі", "Партиянын түрү", "Навъи партия", "Partiya turi"),
    "normal" to listOf("Standard", "Обычная", "Қалыпты", "Кадимки", "Одатӣ", "Oddiy"),
    "returned" to listOf("Returned", "Возвращённые", "Қайтарылған", "Кайтарылган", "Баргардонидашуда", "Qaytarilgan"),
    "universal" to listOf("Universal", "Универсальная", "Әмбебап", "Универсалдуу", "Универсалӣ", "Universal"),
    "universal_detail" to listOf("A simple stock pool. Quantities and units remain separate for each item.", "Общий запас без сложного учёта партий. Количество и единицы сохраняются отдельно для каждого товара.", "Қарапайым ортақ қор. Әр тауардың саны мен өлшем бірлігі бөлек сақталады.", "Жөнөкөй жалпы запас. Ар бир товардын саны жана бирдиги өзүнчө сакталат.", "Захираи одӣ. Миқдор ва воҳидҳои ҳар мол алоҳида нигоҳ дошта мешаванд.", "Oddiy umumiy zaxira. Har bir tovar miqdori va birliklari alohida saqlanadi."),
    "original" to listOf("Original sale batch", "Партия исходной продажи", "Бастапқы сату партиясы", "Баштапкы сатуу партиясы", "Партияи фурӯши аслӣ", "Asl sotuv partiyasi"),
    "shelf_then" to listOf("On the shelf at sale", "На полке при продаже", "Сату кезінде сөреде", "Сатууда текчеде болгон", "Дар раф ҳангоми фурӯш", "Sotuv paytida tokchada"),
    "unknown" to listOf("Original batch not recorded on this receipt", "Исходная партия в этом чеке не записана", "Бұл чекте бастапқы партия жазылмаған", "Бул чекте баштапкы партия жазылган эмес", "Партияи аслӣ дар ин расид сабт нашудааст", "Bu chekda asl partiya qayd etilmagan"),
    "new_returned" to listOf("Create a Returned batch", "Создать партию «Возвращённые»", "«Қайтарылған» партиясын жасау", "«Кайтарылган» партиясын түзүү", "Эҷоди партияи «Баргардонидашуда»", "«Qaytarilgan» partiya yaratish"),
    "choose" to listOf("Choose a destination for every item before payment", "Выберите партию для каждого товара перед оплатой", "Төлемге дейін әр тауарға партия таңдаңыз", "Төлөмгө чейин ар бир товарга партия тандаңыз", "Пеш аз пардохт барои ҳар мол партия интихоб кунед", "To‘lovdan oldin har bir tovar uchun partiya tanlang"),
    "minimum" to listOf("Enter at least 3 characters", "Введите хотя бы 3 символа", "Кемінде 3 таңба енгізіңіз", "Кеминде 3 белги киргизиңиз", "Камаш 3 аломат ворид кунед", "Kamida 3 belgi kiriting"),
    "searching" to listOf("Finding receipts…", "Поиск чеков…", "Чектер ізделуде…", "Чектер изделүүдө…", "Ҷустуҷӯи расидҳо…", "Cheklar qidirilmoqda…"),
    "not_found" to listOf("No matching receipt in this store", "Подходящий чек в этом магазине не найден", "Бұл дүкенде сәйкес чек табылмады", "Бул дүкөндө дал келген чек табылган жок", "Дар ин мағоза расиди мувофиқ ёфт нашуд", "Bu do‘konda mos chek topilmadi"),
    "offline" to listOf("Offline: showing saved receipts only", "Нет связи: только сохранённые чеки", "Байланыс жоқ: тек сақталған чектер", "Байланыш жок: сакталган чектер гана", "Офлайн: танҳо расидҳои захирашуда", "Oflayn: faqat saqlangan cheklar"),
    "lookup_error" to listOf("Receipt lookup is unavailable. Check your connection and access, then retry.", "Поиск чеков недоступен. Проверьте подключение и права, затем повторите.", "Чек іздеу қолжетімсіз. Байланыс пен рұқсаттарды тексеріп, қайталаңыз.", "Чек издөө жеткиликсиз. Байланышты жана уруксаттарды текшерип, кайталаңыз.", "Ҷустуҷӯи расид дастнорас аст. Пайваст ва иҷозатҳоро санҷед ва такрор кунед.", "Chek qidirish mavjud emas. Aloqa va ruxsatlarni tekshirib, qayta urining."),
    "add_return" to listOf("Add to return", "Добавить к возврату", "Қайтаруға қосу", "Кайтарууга кошуу", "Ба баргардонӣ илова кардан", "Qaytarishga qo‘shish"),
    "added" to listOf("Added to return cart; adjust the quantity in the cart", "Добавлено в корзину возврата; измените количество в корзине", "Қайтару себетіне қосылды; санын себетте өзгертіңіз", "Кайтаруу себетине кошулду; санын себетте өзгөртүңүз", "Ба сабади баргардонӣ илова шуд; миқдорро дар сабад тағйир диҳед", "Qaytarish savatiga qo‘shildi; miqdorni savatda o‘zgartiring"),
    "already_added" to listOf("This item is already in the cart. Adjust or remove it there first.", "Товар уже в корзине. Сначала измените или удалите его там.", "Тауар себетте бар. Алдымен оны сол жерде өзгертіңіз не жойыңыз.", "Товар себетте бар. Адегенде аны ал жерде өзгөртүңүз же өчүрүңүз.", "Мол аллакай дар сабад аст. Аввал онро тағйир ё хориҷ кунед.", "Tovar savatda bor. Avval uni o‘zgartiring yoki olib tashlang."),
    "unavailable_item" to listOf("This receipt item is no longer available in this store", "Товар из чека больше недоступен в этом магазине", "Чектегі тауар бұл дүкенде енді қолжетімсіз", "Чектеги товар бул дүкөндө жеткиликсиз", "Моли расид дар ин мағоза дигар дастрас нест", "Chekdagi tovar bu do‘konda endi mavjud emas"),
    "invalid_barcode" to listOf("Scan the transaction barcode at the bottom of an AITA receipt", "Отсканируйте штрихкод транзакции внизу чека AITA", "AITA чегінің төменгі жағындағы транзакция штрихкодын сканерлеңіз", "AITA чегинин ылдыйындагы транзакция штрихкодун сканерлеңиз", "Штрихкоди амалиётро дар поёни расиди AITA скан кунед", "AITA cheki ostidagi tranzaksiya shtrix-kodini skanerlang")
)
