package kz.aita

internal fun AppConfiguration.supplierPickingText(key: String): String {
    val words = when (key) {
        "title" -> listOf("Prepare the delivery", "Комплектация поставки", "Жеткізілімді жинақтау", "Жеткирүүнү комплекттөө", "Омодасозии таҳвил", "Yetkazmani tayyorlash")
        "help" -> listOf("Count or scan each agreed item. Progress stays on this device, works offline and resets when the agreed product or quantity changes.", "Пересчитайте или сканируйте согласованные товары. Прогресс хранится на этом устройстве, доступен офлайн и сбрасывается при изменении товара или согласованного количества.", "Келісілген тауарларды санаңыз немесе сканерлеңіз. Прогресс осы құрылғыда офлайн сақталады; тауар не келісілген саны өзгерсе, қайта тексеріледі.", "Макулдашылган товарларды санаңыз же сканерлеңиз. Прогресс бул түзмөктө офлайн сакталат; товар же саны өзгөрсө кайра текшерилет.", "Молҳои мувофиқашударо ҳисоб ё скан кунед. Пешрафт дар ин дастгоҳ офлайн нигоҳ дошта мешавад; бо тағйири мол ё миқдор аз нав санҷед.", "Kelishilgan mahsulotlarni sanang yoki skanerlang. Jarayon shu qurilmada oflayn saqlanadi; mahsulot yoki miqdor o‘zgarsa qayta tekshiriladi.")
        "scan" -> listOf("Scan a picking barcode", "Сканируйте штрихкод для сборки", "Жинақтау штрихкодын сканерлеңіз", "Комплекттөө штрихкодун сканерлеңиз", "Штрихкодро барои ҷамъоварӣ скан кунед", "Yig‘ish uchun shtrix-kodni skanerlang")
        "apply_scan" -> listOf("Add scanned quantity", "Добавить отсканированное", "Сканерленген санын қосу", "Сканерленген санды кошуу", "Миқдори сканшударо илова кардан", "Skanerlangan miqdorni qo‘shish")
        "missing" -> listOf("This barcode is not an agreed item in this delivery. Search by name or check the order.", "Этот штрихкод не относится к согласованным товарам этой поставки. Найдите товар по имени или проверьте заказ.", "Бұл штрихкод жеткізілімдегі келісілген тауарға сәйкес емес. Атауы бойынша іздеңіз не тапсырысты тексеріңіз.", "Бул штрихкод жеткирүүдөгү макулдашылган товарга туура келбейт. Аты менен издеңиз же буйрутманы текшериңиз.", "Ин штрихкод ба моли мувофиқашудаи ин таҳвил рост намеояд. Аз рӯи ном ҷӯед ё фармоишро санҷед.", "Bu shtrix-kod yetkazmadagi kelishilgan mahsulotga tegishli emas. Nom bo‘yicha qidiring yoki buyurtmani tekshiring.")
        "choose" -> listOf("Choose the order line for this scan", "Выберите строку заказа для этого сканирования", "Осы сканға тапсырыс жолын таңдаңыз", "Бул скан үчүн буйрутма сабын тандаңыз", "Сатри фармоишро барои ин скан интихоб кунед", "Ushbu skan uchun buyurtma qatorini tanlang")
        "weight" -> listOf("Check the weight and enter the physical quantity; this barcode does not identify one unambiguous scale format.", "Проверьте вес и введите фактическое количество: формат весового штрихкода неоднозначен.", "Салмағын тексеріп, нақты санын енгізіңіз: салмақ штрихкодының пішімі бірмәнді емес.", "Салмагын текшерип, чыныгы санды киргизиңиз: салмак штрихкодунун форматы так эмес.", "Вазнро санҷида, миқдори воқеиро ворид кунед: шакли штрихкоди вазн якмаъно нест.", "Vaznni tekshirib, haqiqiy miqdorni kiriting: vazn shtrix-kodi formati aniq emas.")
        "excess" -> listOf("This would exceed the agreed quantity. Check the picked amount or choose another order line.", "Будет превышено согласованное количество. Проверьте собранное количество или выберите другую строку заказа.", "Келісілген саннан асып кетеді. Жиналған санды тексеріңіз не басқа жолды таңдаңыз.", "Макулдашылган сандан ашат. Жыйналган санды текшериңиз же башка сапты тандаңыз.", "Миқдори мувофиқашуда зиёд мешавад. Миқдори ҷамъшударо санҷед ё сатри дигарро интихоб кунед.", "Kelishilgan miqdordan oshib ketadi. Yig‘ilgan miqdorni tekshiring yoki boshqa qatorni tanlang.")
        "quantity" -> listOf("Quantity prepared", "Собрано", "Жиналды", "Жыйналды", "Ҷамъ шуд", "Yig‘ilgan miqdor")
        "edit" -> listOf("Count quantity", "Указать количество", "Санын көрсету", "Санын көрсөтүү", "Миқдорро нишон додан", "Miqdorni ko‘rsatish")
        "invalid" -> listOf("Enter a quantity from zero up to the agreed amount, using whole units for piece goods.", "Введите количество от нуля до согласованного; для штучного товара — целое число.", "Нөлден келісілген санға дейін енгізіңіз; даналы тауар үшін бүтін сан қажет.", "Нөлдөн макулдашылган санга чейин киргизиңиз; даана товар үчүн бүтүн сан керек.", "Аз сифр то миқдори мувофиқашуда ворид кунед; барои моли донагӣ адади бутун лозим аст.", "Noldan kelishilgan miqdorgacha kiriting; donali mahsulot uchun butun son kerak.")
        "save_error" -> listOf("Picking progress could not be saved on this device. Nothing was marked complete. Check free storage and retry.", "Не удалось сохранить сборку на этом устройстве. Готовность не отмечена. Проверьте свободное место и повторите.", "Жинақтау осы құрылғыда сақталмады. Дайын деп белгіленген жоқ. Бос орынды тексеріп қайталаңыз.", "Жыйноо бул түзмөктө сакталган жок. Даяр деп белгиленген жок. Бош орунду текшерип кайталаңыз.", "Пешрафт дар ин дастгоҳ нигоҳ дошта нашуд. Омода қайд нашуд. Ҷои холиро санҷида такрор кунед.", "Jarayon bu qurilmada saqlanmadi. Tayyor deb belgilanmadi. Bo‘sh joyni tekshirib qayta urining.")
        "ready" -> listOf("Fully checked orders", "Полностью проверенные заказы", "Толық тексерілген тапсырыстар", "Толук текшерилген буйрутмалар", "Фармоишҳои пурра санҷидашуда", "To‘liq tekshirilgan buyurtmalar")
        "pack_help" -> listOf("Only fully checked orders are marked packed. Finish their checklist first; incomplete orders stay unchanged.", "Собранными отмечаются только полностью проверенные заказы. Сначала завершите их чек-лист; незавершённые заказы не изменятся.", "Тек толық тексерілген тапсырыстар жиналды деп белгіленеді. Алдымен тізімді аяқтаңыз; қалғандары өзгермейді.", "Толук текшерилген буйрутмалар гана жыйналды деп белгиленет. Алгач тизмени бүтүрүңүз; калгандары өзгөрбөйт.", "Танҳо фармоишҳои пурра санҷидашуда ҷамъшуда қайд мешаванд. Аввал рӯйхатро пур кунед; боқимонда тағйир намеёбад.", "Faqat to‘liq tekshirilgan buyurtmalar yig‘ildi deb belgilanadi. Avval ro‘yxatni tugating; qolganlari o‘zgarmaydi.")
        "loading" -> listOf("Loading saved picking progress…", "Загружаем сохранённую сборку…", "Сақталған жинақтау жүктелуде…", "Сакталган жыйноо жүктөлүүдө…", "Пешрафти захирашуда бор мешавад…", "Saqlangan yig‘ish jarayoni yuklanmoqda…")
        "saved" -> listOf("Picking progress saved", "Сборка сохранена", "Жинақтау сақталды", "Жыйноо сакталды", "Пешрафт нигоҳ дошта шуд", "Yig‘ish jarayoni saqlandi")
        "pending" -> listOf("The supplier has not agreed a positive quantity for this line", "Для этой строки ещё не согласовано положительное количество", "Бұл жолға оң сан әлі келісілмеген", "Бул сап үчүн оң сан макулдашыла элек", "Барои ин сатр миқдори мусбат мувофиқ нашудааст", "Bu qator uchun musbat miqdor hali kelishilmagan")
        "page" -> listOf("Page", "Страница", "Бет", "Бет", "Саҳифа", "Sahifa")
        "previous" -> listOf("Previous", "Назад", "Артқа", "Артка", "Пешина", "Oldingi")
        "next" -> listOf("Next", "Далее", "Келесі", "Кийинки", "Навбатӣ", "Keyingi")
        "order" -> listOf("Order", "Заказ", "Тапсырыс", "Буйрутма", "Фармоиш", "Buyurtma")
        else -> listOf(key, key, key, key, key, key)
    }
    return words[listOf("en", "ru", "kk", "ky", "tg", "uz").indexOf(effectiveAppLanguage(stateValues.appLanguage)).coerceAtLeast(0)]
}

internal fun AppConfiguration.supplierWorkspaceCacheText(warning: Boolean): String {
    val values = if (warning) listOf(
        "Supplier data is visible but could not be saved on this device. Check free storage before working offline.",
        "Данные поставщика видны, но не сохранены на этом устройстве. Проверьте свободное место перед работой офлайн.",
        "Жеткізуші деректері көрінеді, бірақ құрылғыда сақталмады. Офлайн жұмыс алдында бос орынды тексеріңіз.",
        "Жеткирүүчүнүн маалыматы көрүнөт, бирок түзмөктө сакталбады. Офлайн иштөөдөн мурда бош орунду текшериңиз.",
        "Маълумоти таъминкунанда намоён аст, вале дар дастгоҳ нигоҳ дошта нашуд. Пеш аз кори офлайн ҷои холиро санҷед.",
        "Yetkazib beruvchi ma’lumotlari ko‘rinadi, lekin qurilmada saqlanmadi. Oflayn ishlashdan oldin bo‘sh joyni tekshiring.")
    else listOf("Showing saved supplier data while the latest changes are checked. Physical picking remains available offline.",
        "Показаны сохранённые данные поставщика; проверяем изменения. Комплектация доступна офлайн.",
        "Жеткізушінің сақталған деректері көрсетілуде; өзгерістер тексерілуде. Жинақтау офлайн қолжетімді.",
        "Жеткирүүчүнүн сакталган маалыматы көрсөтүлүүдө; өзгөрүүлөр текшерилүүдө. Комплекттөө офлайн жеткиликтүү.",
        "Маълумоти захирашудаи таъминкунанда намоиш дода мешавад; тағйирот санҷида мешавад. Ҷамъоварӣ офлайн дастрас аст.",
        "Yetkazib beruvchining saqlangan ma’lumotlari ko‘rsatilmoqda; o‘zgarishlar tekshirilmoqda. Yig‘ish oflayn ishlaydi.")
    return values[listOf("en", "ru", "kk", "ky", "tg", "uz").indexOf(effectiveAppLanguage(stateValues.appLanguage)).coerceAtLeast(0)]
}
