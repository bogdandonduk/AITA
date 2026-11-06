package kz.aita

fun replenishmentMessage(key: String): List<LocalizedStringDataModel> =
    listOf("en", "ru", "kk", "ky", "tg", "uz").map {
        LocalizedStringDataModel(it, replenishmentText(key, it))
    }

fun replenishmentText(key: String, language: String): String {
    val values =
        when (key) {
            "intro" ->
                listOf(
                    "Review quantities and send a supplier order.",
                    "Проверьте количества и отправьте заказ поставщику.",
                    "Санын тексеріп, жеткізушіге тапсырыс жіберіңіз.",
                    "Санын текшерип, жеткирүүчүгө тапшырык бериңиз.",
                    "Миқдорро санҷида, ба таъминкунанда фармоиш диҳед.",
                    "Miqdorni tekshirib, yetkazuvchiga buyurtma yuboring.",
                )
            "calculation" ->
                listOf(
                    "How the plan is calculated",
                    "Как рассчитан план",
                    "Жоспар қалай есептеледі",
                    "План кантип эсептелет",
                    "Нақша чӣ гуна ҳисоб мешавад",
                    "Reja qanday hisoblanadi",
                )
            "reminder_title" ->
                listOf(
                    "Seasonal reminder",
                    "Сезонное напоминание",
                    "Маусымдық еске салу",
                    "Сезондук эскертүү",
                    "Ёдрасии мавсимӣ",
                    "Mavsumiy eslatma",
                )
            "reminder_help" ->
                listOf(
                    "Save a season and date in Planning settings. The server creates a personal reminder before the order deadline. If AITA is closed, you will see it when you next connect. No automatic order.",
                    "Задайте сезон и дату в параметрах плана. Сервер создаст личное напоминание до срока заказа. Если AITA закрыта, вы увидите его при следующем подключении. Заказ автоматически не отправляется.",
                    "Жоспардан маусым мен күнді таңдаңыз. Сервер тапсырыс мерзіміне дейін жеке еске салу жасайды. AITA жабық болса, оны келесі қосылғанда көресіз. Автоматты тапсырыс жоқ.",
                    "Планда сезон менен күндү тандаңыз. Сервер мөөнөткө чейин жеке эскертүү түзөт. AITA жабык болсо, кийинки туташууда көрөсүз. Автоматтык тапшырык жок.",
                    "Мавсим ва санаро дар нақша интихоб кунед. Сервер пеш аз муҳлат ёдрасии шахсӣ месозад. Агар AITA баста бошад, онро ҳангоми пайвасти навбатӣ мебинед. Фармоиши худкор нест.",
                    "Rejada mavsum va sanani belgilang. Server buyurtma muddatidan oldin shaxsiy eslatma yaratadi. AITA yopiq bo‘lsa, keyingi ulanishda ko‘rasiz. Avtomatik buyurtma yo‘q.",
                )
            "reminder_days" ->
                listOf(
                    "Remind before order deadline · days (0–30)",
                    "Напомнить до срока заказа · дней (0–30)",
                    "Мерзімге дейін еске салу · күн (0–30)",
                    "Мөөнөткө чейин эскертүү · күн (0–30)",
                    "Ёдрасӣ пеш аз муҳлат · рӯз (0–30)",
                    "Muddatdan oldin eslatish · kun (0–30)",
                )
            "reminder_save" ->
                listOf(
                    "Save reminder",
                    "Сохранить напоминание",
                    "Еске салуды сақтау",
                    "Эскертүүнү сактоо",
                    "Сабти ёдрасӣ",
                    "Eslatmani saqlash",
                )
            "reminder_disable" ->
                listOf(
                    "Turn off reminder",
                    "Отключить напоминание",
                    "Еске салуды өшіру",
                    "Эскертүүнү өчүрүү",
                    "Хомӯш кардани ёдрасӣ",
                    "Eslatmani o‘chirish",
                )
            "reminder_restore" ->
                listOf(
                    "Use saved plan settings",
                    "Применить сохранённые параметры плана",
                    "Сақталған жоспарды қолдану",
                    "Сакталган планды колдонуу",
                    "Истифодаи нақшаи сабтшуда",
                    "Saqlangan reja sozlamalarini qo‘llash",
                )
            "reminder_off" ->
                listOf(
                    "Reminder is off",
                    "Напоминание отключено",
                    "Еске салу өшірулі",
                    "Эскертүү өчүк",
                    "Ёдрасӣ хомӯш аст",
                    "Eslatma o‘chiq",
                )
            "reminder_on" ->
                listOf(
                    "Reminder scheduled",
                    "Напоминание запланировано",
                    "Еске салу жоспарланған",
                    "Эскертүү пландалды",
                    "Ёдрасӣ ба нақша гирифта шуд",
                    "Eslatma rejalashtirildi",
                )
            "reminder_sent" ->
                listOf(
                    "Reminder delivered to Notifications",
                    "Напоминание доставлено в уведомления",
                    "Еске салу хабарландыруларға жеткізілді",
                    "Эскертүү билдирүүлөргө жеткирилди",
                    "Ёдрасӣ ба огоҳиҳо расид",
                    "Eslatma bildirishnomalarga yetkazildi",
                )
            "reminder_load" ->
                listOf(
                    "Refresh reminder status before saving.",
                    "Обновите состояние напоминания перед сохранением.",
                    "Сақтаудан бұрын еске салуды жаңартыңыз.",
                    "Сактоодон мурда эскертүүнү жаңыртыңыз.",
                    "Пеш аз сабт ҳолати ёдрасиро нав кунед.",
                    "Saqlashdan oldin eslatma holatini yangilang.",
                )
            "reminder_invalid" ->
                listOf(
                    "Choose a seasonal event and a date within the next year in Planning settings. Days before order must be 0–30.",
                    "В параметрах плана выберите сезон и дату в пределах следующего года. Напоминание до заказа — от 0 до 30 дней.",
                    "Жоспардан келесі жыл ішіндегі маусым мен күнді таңдаңыз. Еске салу — 0–30 күн бұрын.",
                    "Пландан кийинки жыл ичиндеги сезон менен күндү тандаңыз. Эскертүү — 0–30 күн мурда.",
                    "Мавсим ва санаи соли ояндаро дар нақша интихоб кунед. Ёдрасӣ 0–30 рӯз пеш.",
                    "Rejada kelgusi yil ichidagi mavsum va sanani tanlang. Eslatma 0–30 kun oldin.",
                )
            "reminder_conflict" ->
                listOf(
                    "This reminder changed on another device. Refresh it before saving again.",
                    "Напоминание изменилось на другом устройстве. Обновите его перед повторным сохранением.",
                    "Еске салу басқа құрылғыда өзгерді. Қайта сақтамас бұрын жаңартыңыз.",
                    "Эскертүү башка түзмөктө өзгөрдү. Кайра сактоодон мурда жаңыртыңыз.",
                    "Ёдрасӣ дар дастгоҳи дигар тағйир ёфт. Пеш аз сабти такрорӣ нав кунед.",
                    "Eslatma boshqa qurilmada o‘zgardi. Qayta saqlashdan oldin yangilang.",
                )
            "reminder_unavailable" ->
                listOf(
                    "Could not confirm reminder status. Connect to the server and refresh or retry; your purchasing draft is unchanged.",
                    "Не удалось подтвердить состояние напоминания. Подключитесь к серверу и обновите или повторите действие; черновик закупки сохранён.",
                    "Еске салу күйі расталмады. Серверге қосылып, жаңартыңыз не қайталаңыз; сатып алу жобасы сақталған.",
                    "Эскертүүнүн абалы ырасталган жок. Серверге туташып жаңыртыңыз же кайталаңыз; сатып алуу долбоору сакталган.",
                    "Ҳолати ёдрасӣ тасдиқ нашуд. Ба сервер пайваст шуда нав ё такрор кунед; нақшаи харид сабт аст.",
                    "Eslatma holati tasdiqlanmadi. Serverga ulanib yangilang yoki qayta urining; xarid qoralamasi saqlangan.",
                )
            "reminder_pending" ->
                listOf(
                    "Confirmation was interrupted. Retry the same change or refresh to check its status.",
                    "Подтверждение прервалось. Повторите то же изменение или обновите, чтобы проверить результат.",
                    "Растау үзілді. Сол өзгерісті қайталаңыз не күйін жаңартыңыз.",
                    "Ырастоо үзүлдү. Ошол өзгөртүүнү кайталаңыз же абалын жаңыртыңыз.",
                    "Тасдиқ қатъ шуд. Ҳамон тағйирро такрор ё ҳолатро нав кунед.",
                    "Tasdiq uzildi. Shu o‘zgarishni takrorlang yoki holatni yangilang.",
                )
            "reminder_limit" ->
                listOf(
                    "At most 100 active store reminders per account. Turn off an older reminder first.",
                    "На аккаунт доступно до 100 активных напоминаний магазинов. Сначала отключите одно из прежних.",
                    "Аккаунтқа 100 белсенді еске салуға дейін. Алдымен ескісін өшіріңіз.",
                    "Аккаунтка 100 активдүү эскертүүгө чейин. Адегенде эскисин өчүрүңүз.",
                    "То 100 ёдрасии фаъол барои ҳисоб. Аввал яке аз кӯҳнаро хомӯш кунед.",
                    "Hisobga ko‘pi bilan 100 faol eslatma. Avval eskisini o‘chiring.",
                )
            "loading" ->
                listOf(
                    "Loading the saved draft. Please wait a moment.",
                    "Загружаем сохранённый черновик. Подождите немного.",
                    "Сақталған жоба жүктелуде. Сәл күтіңіз.",
                    "Сакталган долбоор жүктөлүүдө. Бир аз күтүңүз.",
                    "Сиёҳнависи сабтшуда бор мешавад. Каме интизор шавед.",
                    "Saqlangan qoralama yuklanmoqda. Biroz kuting.",
                )
            "title" ->
                listOf(
                    "Purchase planner",
                    "План закупок",
                    "Сатып алу жоспары",
                    "Сатып алуу планы",
                    "Нақшаи харид",
                    "Xarid rejasi",
                )
            "help" ->
                listOf(
                    "Plan from your store’s completed sales days, returns, usable stock and dated supplier promises. Your draft stays on this device. Review quantities before sending an order.",
                    "План по завершённым дням продаж, возвратам, годным остаткам и подтверждённым срокам поставок вашего магазина. Черновик сохраняется на устройстве. Проверьте количества перед отправкой заказа.",
                    "Дүкеннің аяқталған сату күндері, қайтарымдары, жарамды қоры және жеткізу мерзімдері бойынша жоспар. Тапсырыс жобасын жасамас бұрын санын тексеріңіз.",
                    "Дүкөндүн бүткөн сатуу күндөрү, кайтаруулар, жарактуу запас жана жеткирүү мөөнөттөрү боюнча план. Тапшырык долбоорунан мурда санын текшериңиз.",
                    "Нақша аз рӯзҳои анҷомшудаи фурӯш, баргардонӣ, захираи солим ва муҳлатҳои таъминот. Пеш аз фармоиш миқдорро санҷед.",
                    "Tugallangan savdo kunlari, qaytarishlar, yaroqli zaxira va yetkazish muddatlari asosida reja. Buyurtma qoralamasidan oldin miqdorlarni tekshiring.",
                )
            "regular" ->
                listOf(
                    "Regular replenishment",
                    "Обычное пополнение",
                    "Қорды толықтыру",
                    "Запасты толуктоо",
                    "Пуркунии захира",
                    "Zaxirani to‘ldirish",
                )
            "event" ->
                listOf(
                    "Season or event",
                    "Сезон или событие",
                    "Маусым не оқиға",
                    "Сезон же окуя",
                    "Мавсим ё рӯйдод",
                    "Mavsum yoki tadbir",
                )
            "new_year" ->
                listOf("New Year", "Новый год", "Жаңа жыл", "Жаңы жыл", "Соли нав", "Yangi yil")
            "nauryz" -> listOf("Nauryz", "Наурыз", "Наурыз", "Нооруз", "Наврӯз", "Navro‘z")
            "school" ->
                listOf(
                    "Back to school",
                    "Школьный сезон",
                    "Мектеп маусымы",
                    "Мектеп сезону",
                    "Мавсими мактаб",
                    "Maktab mavsumi",
                )
            "kurban" ->
                listOf(
                    "Kurban Ait · choose date",
                    "Курбан айт · укажите дату",
                    "Құрбан айт · күнін таңдаңыз",
                    "Курман айт · күнүн тандаңыз",
                    "Қурбон · санаро интихоб кунед",
                    "Qurbon hayiti · sanani tanlang",
                )
            "custom" ->
                listOf(
                    "My event",
                    "Моё событие",
                    "Менің оқиғам",
                    "Менин окуям",
                    "Рӯйдоди ман",
                    "Mening tadbirim",
                )
            "arrival" ->
                listOf(
                    "Stock needed by",
                    "Товар нужен к",
                    "Тауар қажет күні",
                    "Товар керек күн",
                    "Санаи зарурати мол",
                    "Tovar kerak bo‘lgan sana",
                )
            "date_help" ->
                listOf(
                    "Season dates are editable planning dates, not guaranteed demand. Enter Kurban Ait’s locally confirmed date each year.",
                    "Даты сезонов можно менять: это ориентир для планирования, не гарантия спроса. Для Курбан айта укажите подтверждённую в вашей стране дату на нужный год.",
                    "Маусым күндері өзгертіледі; сұранысқа кепіл емес. Құрбан айттың жергілікті расталған күнін жыл сайын енгізіңіз.",
                    "Сезон күндөрү өзгөртүлөт; суроо-талап кепилденбейт. Курман айттын жергиликтүү ырасталган күнүн киргизиңиз.",
                    "Санаҳои мавсим тағйирпазиранд; талабот кафолат надорад. Санаи маҳаллии тасдиқшудаи Қурбонро ворид кунед.",
                    "Mavsum sanalari o‘zgartiriladi; talab kafolatlanmaydi. Qurbon hayitining mahalliy tasdiqlangan sanasini kiriting.",
                )
            "lead" ->
                listOf(
                    "Delivery lead time · days",
                    "Срок поставки · дней",
                    "Жеткізу мерзімі · күн",
                    "Жеткирүү мөөнөтү · күн",
                    "Муҳлати таъминот · рӯз",
                    "Yetkazish muddati · kun",
                )
            "cover" ->
                listOf(
                    "Stock coverage · days",
                    "Запас после поставки · дней",
                    "Жеткізілгеннен кейінгі қор · күн",
                    "Жеткиргенден кийинки запас · күн",
                    "Захира пас аз таъминот · рӯз",
                    "Yetkazishdan keyingi zaxira · kun",
                )
            "buffer" ->
                listOf(
                    "Extra reserve · %",
                    "Дополнительный резерв · %",
                    "Қосымша резерв · %",
                    "Кошумча резерв · %",
                    "Захираи иловагӣ · %",
                    "Qo‘shimcha zaxira · %",
                )
            "uplift" ->
                listOf(
                    "Expected seasonal increase · %",
                    "Ожидаемый сезонный рост · %",
                    "Күтілетін маусымдық өсім · %",
                    "Күтүлгөн сезондук өсүш · %",
                    "Афзоиши мавсимӣ · %",
                    "Kutilgan mavsumiy o‘sish · %",
                )
            "lookback" ->
                listOf(
                    "Sales history · days",
                    "История продаж · дней",
                    "Сату тарихы · күн",
                    "Сатуу тарыхы · күн",
                    "Таърихи фурӯш · рӯз",
                    "Savdo tarixi · kun",
                )
            "settings" ->
                listOf(
                    "Planning settings",
                    "Параметры плана",
                    "Жоспар параметрлері",
                    "План жөндөөлөрү",
                    "Танзимоти нақша",
                    "Reja sozlamalari",
                )
            "apply" -> listOf("Apply", "Применить", "Қолдану", "Колдонуу", "Истифода", "Qo‘llash")
            "refresh" ->
                listOf("Refresh", "Обновить", "Жаңарту", "Жаңыртуу", "Навсозӣ", "Yangilash")
            "selected" ->
                listOf("Selected", "Выбрано", "Таңдалды", "Тандалды", "Интихоб шуд", "Tanlandi")
            "report_limit" ->
                listOf(
                    "For a readable report, select or filter 1–200 items first.",
                    "Для удобного отчёта сначала выберите или отфильтруйте 1–200 товаров.",
                    "Ыңғайлы есеп үшін 1–200 тауарды таңдаңыз не сүзіңіз.",
                    "Ыңгайлуу отчет үчүн 1–200 товарды тандаңыз же чыпкалаңыз.",
                    "Барои ҳисоботи қулай 1–200 молро интихоб ё полоиш кунед.",
                    "Qulay hisobot uchun 1–200 mahsulotni tanlang yoki filtrlang.",
                )
            "report" ->
                listOf(
                    "View / print plan",
                    "Просмотр / печать плана",
                    "Жоспарды көру / басу",
                    "Планды көрүү / басуу",
                    "Дидан / чопи нақша",
                    "Rejani ko‘rish / chop etish",
                )
            "snapshot" ->
                listOf(
                    "Data as of",
                    "Данные на",
                    "Деректер күні",
                    "Маалымат күнү",
                    "Маълумот то",
                    "Ma’lumot vaqti",
                )
            "cached" ->
                listOf(
                    "Saved snapshot. Refresh when online before placing an order: stock and supplier promises may have changed.",
                    "Сохранённый снимок. Перед заказом обновите при подключении: остатки и обещанные поставки могли измениться.",
                    "Сақталған деректер. Тапсырыс алдында онлайн жаңартыңыз: қор мен жеткізу өзгеруі мүмкін.",
                    "Сакталган маалымат. Тапшырыктан мурда онлайн жаңыртыңыз: запас жана жеткирүү өзгөрүшү мүмкүн.",
                    "Маълумоти захирашуда. Пеш аз фармоиш онлайн нав кунед: захира ва таъминот метавонад дигар шавад.",
                    "Saqlangan ma’lumot. Buyurtmadan oldin onlayn yangilang: zaxira va yetkazish o‘zgargan bo‘lishi mumkin.",
                )
            "offline_empty" ->
                listOf(
                    "No saved purchasing data for this period yet. Connect once and refresh; your draft remains saved.",
                    "Для этого периода ещё нет сохранённых данных закупки. Подключитесь и обновите один раз; черновик сохранён.",
                    "Бұл кезеңнің деректері сақталмаған. Бір рет онлайн жаңартыңыз; жоба сақталған.",
                    "Бул мезгилдин маалыматы сакталган эмес. Бир жолу онлайн жаңыртыңыз; долбоор сакталды.",
                    "Маълумоти ин давра ҳоло сабт нашудааст. Як бор онлайн нав кунед; сиёҳнавис мемонад.",
                    "Bu davr ma’lumoti hali saqlanmagan. Bir marta onlayn yangilang; qoralama saqlanadi.",
                )
            "storage" ->
                listOf(
                    "Could not save on this device. Check available storage and retry before closing.",
                    "Не удалось сохранить на устройстве. Проверьте свободное место и повторите перед закрытием.",
                    "Құрылғыға сақталмады. Бос орынды тексеріп, жабу алдында қайталаңыз.",
                    "Түзмөккө сакталган жок. Бош орунду текшерип, жабуудан мурда кайталаңыз.",
                    "Дар дастгоҳ сабт нашуд. Ҷойро санҷида пеш аз пӯшидан такрор кунед.",
                    "Qurilmaga saqlanmadi. Bo‘sh joyni tekshirib yopishdan oldin takrorlang.",
                )
            "access" ->
                listOf(
                    "Stock, analytics and supplier-order viewing permissions are required for this store.",
                    "Нужны права просмотра склада, аналитики и заказов поставщикам этого магазина.",
                    "Дүкен қоры, талдауы мен жеткізуші тапсырыстарын көру құқығы қажет.",
                    "Дүкөндүн кампасын, аналитикасын жана жеткирүүчү тапшырыктарын көрүү укугу керек.",
                    "Ҳуқуқи дидани анбор, таҳлил ва фармоишҳои таъминот лозим аст.",
                    "Ombor, tahlil va yetkazuvchi buyurtmalarini ko‘rish huquqi kerak.",
                )
            "too_large" ->
                listOf(
                    "Too much data for one plan. Try the 7-day period; no partial forecast was shown.",
                    "Слишком много данных для одного плана. Попробуйте период 7 дней; прогноз по неполным данным не показан.",
                    "Дерек тым көп. 7 күндік кезеңді таңдаңыз; толық емес болжам көрсетілмейді.",
                    "Маалымат өтө көп. 7 күндү тандаңыз; толук эмес божомол көрсөтүлбөйт.",
                    "Маълумот зиёд аст. 7 рӯзро интихоб кунед; пешгӯии нопурра намоиш намеёбад.",
                    "Ma’lumot ko‘p. 7 kunni tanlang; to‘liq bo‘lmagan prognoz ko‘rsatilmaydi.",
                )
            "invalid_settings" ->
                listOf(
                    "Check the date and numbers: lead 0–180 days, coverage 1–90, reserve 0–100%, increase 0–300%. The date must be within the next year.",
                    "Проверьте дату и числа: поставка 0–180 дней, запас 1–90, резерв 0–100%, рост 0–300%. Дата — не раньше сегодня и не дальше года.",
                    "Күн мен сандарды тексеріңіз: жеткізу 0–180 күн, қор 1–90, резерв 0–100%, өсім 0–300%. Күн алдағы бір жылда болуы керек.",
                    "Күн жана сандар: жеткирүү 0–180, запас 1–90 күн, резерв 0–100%, өсүш 0–300%. Күн алдыдагы бир жылда болсун.",
                    "Сана ва рақамҳо: таъминот 0–180, захира 1–90 рӯз, эҳтиёт 0–100%, афзоиш 0–300%. Сана дар як соли оянда бошад.",
                    "Sana va sonlar: yetkazish 0–180, zaxira 1–90 kun, rezerv 0–100%, o‘sish 0–300%. Sana kelgusi yil ichida bo‘lsin.",
                )
            "sold" -> listOf("Sold", "Продано", "Сатылды", "Сатылды", "Фурӯхта шуд", "Sotildi")
            "returns" ->
                listOf(
                    "Returned",
                    "Возвращено",
                    "Қайтарылды",
                    "Кайтарылды",
                    "Баргардонда шуд",
                    "Qaytarildi",
                )
            "daily" ->
                listOf(
                    "Net per day",
                    "Нетто в день",
                    "Күніне нетто",
                    "Күнүнө нетто",
                    "Соф дар рӯз",
                    "Kunlik sof",
                )
            "stock" ->
                listOf(
                    "Usable stock",
                    "Годный запас",
                    "Жарамды қор",
                    "Жарактуу запас",
                    "Захираи солим",
                    "Yaroqli zaxira",
                )
            "incoming" ->
                listOf(
                    "Confirmed arrivals",
                    "Подтверждённые поставки",
                    "Расталған жеткізу",
                    "Ырасталган жеткирүү",
                    "Таъминоти тасдиқшуда",
                    "Tasdiqlangan yetkazish",
                )
            "expiring" ->
                listOf(
                    "Expires before coverage ends",
                    "Истечёт до конца периода запаса",
                    "Қор кезеңі біткенше мерзімі өтеді",
                    "Запас мезгили бүткөнчө мөөнөтү өтөт",
                    "То охири давра муҳлаташ мегузарад",
                    "Zaxira davri oxirigacha muddati tugaydi",
                )
            "undated" ->
                listOf(
                    "Late / undated supply · excluded",
                    "Просроченные / без даты · не учтены",
                    "Кешіккен / күнсіз · есептелмеді",
                    "Кечиккен / күнсүз · эсептелбейт",
                    "Дер / бесана · ҳисоб нашуд",
                    "Kech / sanasiz · hisoblanmadi",
                )
            "suggested" ->
                listOf(
                    "Suggested purchase",
                    "К закупке",
                    "Сатып алуға",
                    "Сатып алууга",
                    "Барои харид",
                    "Xaridga",
                )
            "order_by" ->
                listOf(
                    "Order by",
                    "Заказать до",
                    "Тапсырыс беру күні",
                    "Тапшырык берүү күнү",
                    "Фармоиш то",
                    "Buyurtma muddati",
                )
            "low_history" ->
                listOf(
                    "Limited sales history · check manually",
                    "Мало истории продаж · проверьте вручную",
                    "Сату тарихы аз · тексеріңіз",
                    "Сатуу тарыхы аз · текшериңиз",
                    "Таърихи фурӯш кам · санҷед",
                    "Savdo tarixi kam · tekshiring",
                )
            "unlimited" ->
                listOf(
                    "Unlimited batch · no automatic quantity",
                    "Безлимитная партия · количество не рассчитывается",
                    "Шексіз партия · саны есептелмейді",
                    "Чексиз партия · саны эсептелбейт",
                    "Партияи бемаҳдуд · ҳисоб нест",
                    "Cheksiz partiya · miqdor hisoblanmaydi",
                )
            "units" ->
                listOf(
                    "Check unit / history before ordering",
                    "Проверьте единицу и историю перед заказом",
                    "Тапсырыс алдында өлшем мен тарихты тексеріңіз",
                    "Тапшырыктан мурда бирдикти жана тарыхты текшериңиз",
                    "Пеш аз фармоиш воҳид ва таърихро санҷед",
                    "Buyurtmadan oldin birlik va tarixni tekshiring",
                )
            "no_history" ->
                listOf(
                    "No completed sales days · enter quantity manually",
                    "Нет завершённых дней продаж · введите количество вручную",
                    "Аяқталған сату күндері жоқ · санды енгізіңіз",
                    "Бүткөн сатуу күндөрү жок · санын киргизиңиз",
                    "Рӯзҳои фурӯш нестанд · миқдорро ворид кунед",
                    "Tugallangan savdo kunlari yo‘q · miqdorni kiriting",
                )
            "assumptions" ->
                listOf(
                    "Recent sales are a baseline, not a demand guarantee. Stockouts are not measured. Reserve and seasonal increase are your assumptions. Stock expiring within the coverage window is excluded.",
                    "Недавние продажи — ориентир, а не гарантия спроса. Дни отсутствия товара не измеряются. Резерв и сезонный рост задаёте вы. Остатки со сроком годности внутри периода запаса исключены.",
                    "Соңғы сату — бағдар, кепіл емес. Тауарсыз күндер өлшенбейді. Резерв пен өсімді өзіңіз бересіз. Кезең ішінде мерзімі өтетін қор есептелмейді.",
                    "Акыркы сатуу — багыт, кепил эмес. Товарсыз күндөр өлчөнбөйт. Резерв менен өсүштү өзүңүз бересиз. Мезгил ичинде мөөнөтү өткөн запас эсептелбейт.",
                    "Фурӯши охир роҳнамо аст, на кафолат. Рӯзҳои бе мол чен намешаванд. Захира ва афзоишро шумо муайян мекунед. Моли муҳлаташгузар хориҷ аст.",
                    "So‘nggi savdo yo‘nalish, kafolat emas. Tovarsiz kunlar o‘lchanmaydi. Rezerv va o‘sishni o‘zingiz belgilaysiz. Davrda muddati tugaydigan zaxira chiqariladi.",
                )
            "unmatched" ->
                listOf(
                    "Historical lines could not be matched to active items",
                    "Строк истории не удалось связать с активными товарами",
                    "Тарих жолдары белсенді тауарларға сәйкестенбеді",
                    "Тарых саптары активдүү товарларга дал келбеди",
                    "Сатрҳои таърих ба молҳои фаъол мувофиқ нашуданд",
                    "Tarix qatorlari faol mahsulotga moslanmadi",
                )
            "select" ->
                listOf(
                    "Use suggestions",
                    "Выбрать предложенное",
                    "Ұсынысты таңдау",
                    "Сунуштарды тандоо",
                    "Интихоби пешниҳодҳо",
                    "Takliflarni tanlash",
                )
            "clear" ->
                listOf(
                    "Clear selection",
                    "Очистить выбор",
                    "Таңдауды тазалау",
                    "Тандоону тазалоо",
                    "Тоза кардани интихоб",
                    "Tanlovni tozalash",
                )
            "supplier" ->
                listOf(
                    "Supplier",
                    "Поставщик",
                    "Жеткізуші",
                    "Жеткирүүчү",
                    "Таъминкунанда",
                    "Yetkazuvchi",
                )
            "quantity" ->
                listOf(
                    "Order quantity",
                    "Количество к заказу",
                    "Тапсырыс саны",
                    "Тапшырык саны",
                    "Миқдори фармоиш",
                    "Buyurtma miqdori",
                )
            "confirm_order" ->
                listOf(
                    "Send an order for {count} items to {supplier}? The supplier will receive this order.",
                    "Отправить заказ на {count} товаров поставщику «{supplier}»? Поставщик получит этот заказ.",
                    "{count} тауарға тапсырысты «{supplier}» жеткізушісіне жіберу керек пе? Жеткізуші тапсырысты алады.",
                    "{count} товарга тапшырыкты «{supplier}» жеткирүүчүсүнө жөнөтөсүзбү? Жеткирүүчү тапшырыкты алат.",
                    "Фармоиши {count} молро ба «{supplier}» мефиристед? Таъминкунанда фармоишро мегирад.",
                    "{count} mahsulot buyurtmasini «{supplier}» yetkazuvchiga yuborasizmi? Yetkazuvchi bu buyurtmani oladi.",
                )
            "create" ->
                listOf(
                    "Send reviewed order",
                    "Отправить проверенный заказ",
                    "Тексерілген тапсырысты жіберу",
                    "Текшерилген тапшырыкты жөнөтүү",
                    "Фиристодани фармоиши санҷида",
                    "Tekshirilgan buyurtmani yuborish",
                )
            "retry" ->
                listOf(
                    "Retry the same draft",
                    "Повторить тот же черновик",
                    "Сол жобаны қайталау",
                    "Ошол долбоорду кайталоо",
                    "Такрори ҳамон сиёҳнавис",
                    "Shu qoralamani takrorlash",
                )
            "pending" ->
                listOf(
                    "Save not confirmed. This exact draft is kept for retry; its quantities are locked to prevent duplicate orders.",
                    "Сохранение не подтверждено. Этот черновик сохранён для повтора; количества заблокированы, чтобы не создать повторный заказ.",
                    "Сақтау расталмады. Қайталау үшін жоба сақталған; қосарланған тапсырыстан қорғау үшін саны бекітілген.",
                    "Сактоо ырасталган жок. Кайталоо үчүн долбоор сакталды; кош тапшырык болбош үчүн саны бекитилди.",
                    "Сабт тасдиқ нашуд. Сиёҳнавис барои такрор нигоҳ дошта шуд; миқдорҳо қулфанд.",
                    "Saqlash tasdiqlanmadi. Qayta urinish uchun qoralama saqlandi; takror buyurtma bo‘lmasligi uchun miqdorlar qulflangan.",
                )
            "saved" ->
                listOf(
                    "Order sent. Track confirmation and receiving in an item’s supplier-order history.",
                    "Заказ отправлен. Подтверждение и приёмка доступны в истории заказов поставщику у товара.",
                    "Тапсырыс жіберілді. Растау мен қабылдау тауардың тапсырыс тарихында.",
                    "Тапшырык жөнөтүлдү. Ырастоо жана кабыл алуу товардын тапшырык тарыхында.",
                    "Фармоиш фиристода шуд. Тасдиқ ва қабул дар таърихи фармоиши мол.",
                    "Buyurtma yuborildi. Tasdiq va qabul mahsulot buyurtma tarixida.",
                )
            "quantity_error" ->
                listOf(
                    "Enter a valid positive quantity for every selected item. Piece units need whole numbers.",
                    "Укажите допустимое положительное количество для каждого выбранного товара. Для штучных товаров нужны целые числа.",
                    "Әр таңдалған тауарға жарамды оң сан енгізіңіз. Даналық тауарға бүтін сан қажет.",
                    "Ар бир тандалган товарга жарактуу оң сан киргизиңиз. Дааналык товарга бүтүн сан керек.",
                    "Барои ҳар моли интихобшуда миқдори дурусти мусбат диҳед. Барои дона адади бутун лозим.",
                    "Har bir tanlangan mahsulot uchun to‘g‘ri musbat miqdor kiriting. Donali birlik butun son talab qiladi.",
                )
            "selection_error" ->
                listOf(
                    "Choose a supplier and 1–500 items with valid positive quantities. Whole-piece units need whole numbers. Check any highlighted rows.",
                    "Выберите поставщика и 1–500 товаров с допустимым положительным количеством. Для штучных товаров нужны целые числа. Проверьте отмеченные строки.",
                    "Жеткізуші мен 1–500 тауарды оң санмен таңдаңыз. Даналық тауарға бүтін сан қажет.",
                    "Жеткирүүчү жана 1–500 товарды оң сан менен тандаңыз. Дааналык товарга бүтүн сан керек.",
                    "Таъминкунанда ва 1–500 молро бо миқдори мусбат интихоб кунед. Барои дона адади бутун лозим.",
                    "Yetkazuvchi va 1–500 mahsulotni musbat miqdorda tanlang. Donali birlik butun son talab qiladi.",
                )
            "busy" ->
                listOf(
                    "Please wait a minute before refreshing this plan again.",
                    "Подождите минуту перед повторным обновлением плана.",
                    "Жоспарды қайта жаңартуға бір минут күтіңіз.",
                    "Планды кайра жаңыртуудан мурда бир мүнөт күтүңүз.",
                    "Пеш аз навсозии нақша як дақиқа интизор шавед.",
                    "Rejani qayta yangilashdan oldin bir daqiqa kuting.",
                )
            "unavailable" ->
                listOf(
                    "Could not refresh the plan. Your draft is saved; please retry.",
                    "Не удалось обновить план. Черновик сохранён; повторите попытку.",
                    "Жоспар жаңармады. Жоба сақталған; қайталаңыз.",
                    "План жаңырган жок. Долбоор сакталган; кайталаңыз.",
                    "Нақша нав нашуд. Сиёҳнавис сабт шудааст; такрор кунед.",
                    "Reja yangilanmadi. Qoralama saqlangan; qayta urining.",
                )
            "empty" ->
                listOf(
                    "No active stock items in this selection",
                    "В этом выборе нет активных товаров",
                    "Таңдауда белсенді тауар жоқ",
                    "Тандоодо активдүү товар жок",
                    "Дар интихоб моли фаъол нест",
                    "Tanlovda faol mahsulot yo‘q",
                )
            else -> listOf(key, key, key, key, key, key)
        }
    return values[
        when (language.substringBefore('-').lowercase()) {
            "ru" -> 1
            "kk" -> 2
            "ky" -> 3
            "tg" -> 4
            "uz" -> 5
            else -> 0
        }]
}
