package kz.aita

internal fun stockFileText(key: String, language: String): String {
    val values =
        when (key) {
            "title" ->
                listOf(
                    "Import stock",
                    "Импорт товаров",
                    "Тауарларды импорттау",
                    "Товарларды импорттоо",
                    "Воридоти мол",
                    "Mahsulot importi",
                )
            "confirm_import" ->
                listOf(
                    "Import {count} reviewed items into {store}? Existing items will be preserved.",
                    "Импортировать проверенные товары ({count}) в магазин «{store}»? Существующие товары сохранятся.",
                    "Тексерілген {count} тауарды «{store}» дүкеніне импорттау керек пе? Бар тауарлар сақталады.",
                    "Текшерилген {count} товарды «{store}» дүкөнүнө импорттойсузбу? Бар товарлар сакталат.",
                    "{count} моли санҷидаро ба «{store}» ворид мекунед? Молҳои мавҷуда нигоҳ дошта мешаванд.",
                    "Tekshirilgan {count} mahsulotni «{store}» do‘koniga import qilasizmi? Mavjud mahsulotlar saqlanadi.",
                )
            "file" ->
                listOf(
                    "Choose spreadsheet",
                    "Выбрать таблицу",
                    "Кестені таңдау",
                    "Таблицаны тандоо",
                    "Интихоби ҷадвал",
                    "Jadval tanlash",
                )
            "capture" ->
                listOf(
                    "Read another app",
                    "Считать из другой программы",
                    "Басқа қолданбадан оқу",
                    "Башка колдонмодон окуу",
                    "Хондан аз барномаи дигар",
                    "Boshqa ilovadan o‘qish",
                )
            "help" ->
                listOf(
                    "Choose a CSV, TSV or XLSX stock export. Match columns, review items and import. Files stay on this device; only approved items are saved to your store.",
                    "Выберите выгрузку товаров CSV, TSV или XLSX. Сопоставьте столбцы, проверьте товары и импортируйте. Файл остаётся на устройстве; в магазин сохраняются только проверенные товары.",
                    "CSV, TSV немесе XLSX тауар кестесін таңдаңыз. Бағандарды сәйкестендіріп, тауарларды тексеріп импорттаңыз. Файл құрылғыда қалады; дүкенге тек тексерілген тауарлар сақталады.",
                    "CSV, TSV же XLSX товар файлын тандаңыз. Мамычаларды дал келтирип, товарларды текшерип импорттоңуз. Файл түзмөктө калат; дүкөнгө текшерилген товарлар гана сакталат.",
                    "Файли молҳои CSV, TSV ё XLSX-ро интихоб кунед. Сутунҳоро мувофиқ ва молҳоро санҷида ворид кунед. Файл дар дастгоҳ мемонад; танҳо молҳои санҷида сабт мешаванд.",
                    "CSV, TSV yoki XLSX mahsulot faylini tanlang. Ustunlarni moslang, tekshiring va import qiling. Fayl qurilmada qoladi; faqat tekshirilgan mahsulotlar saqlanadi.",
                )
            "sheet" -> listOf("Worksheet", "Лист", "Парақ", "Барак", "Варақ", "Varaq")
            "header" ->
                listOf(
                    "Header row",
                    "Строка заголовков",
                    "Тақырып жолы",
                    "Баш аттар сабы",
                    "Сатри сарлавҳа",
                    "Sarlavha qatori",
                )
            "mapping" ->
                listOf(
                    "Match columns",
                    "Сопоставить столбцы",
                    "Бағандарды сәйкестендіру",
                    "Мамычаларды дал келтирүү",
                    "Мувофиқсозии сутунҳо",
                    "Ustunlarni moslash",
                )
            "preview" ->
                listOf(
                    "Review these rows",
                    "Проверить эти строки",
                    "Жолдарды тексеру",
                    "Саптарды текшерүү",
                    "Санҷиши сатрҳо",
                    "Qatorlarni tekshirish",
                )
            "default_unit" ->
                listOf(
                    "Unit when the cell is empty",
                    "Единица для пустых ячеек",
                    "Бос ұяшықтардың өлшемі",
                    "Бош уячалардын бирдиги",
                    "Воҳид барои ҳуҷайраи холӣ",
                    "Bo‘sh katak uchun birlik",
                )
            "supply_same" ->
                listOf(
                    "Use sale price where supply price is empty",
                    "При пустой цене поставки использовать цену продажи",
                    "Жеткізу бағасы бос болса, сату бағасын қолдану",
                    "Жеткирүү баасы бош болсо сатуу баасын колдонуу",
                    "Агар нархи харид холӣ бошад, нархи фурӯшро истифода бурдан",
                    "Xarid narxi bo‘sh bo‘lsa, sotuv narxini ishlatish",
                )
            "rows" -> listOf("Rows", "Строки", "Жолдар", "Саптар", "Сатрҳо", "Qatorlar")
            "encoding" ->
                listOf(
                    "Text encoding",
                    "Кодировка текста",
                    "Мәтін кодтауы",
                    "Текст коддоосу",
                    "Рамзгузории матн",
                    "Matn kodlash",
                )
            "delimiter" ->
                listOf(
                    "Column separator",
                    "Разделитель столбцов",
                    "Баған бөлгіші",
                    "Мамыча бөлгүчү",
                    "Ҷудокунандаи сутун",
                    "Ustun ajratgichi",
                )
            "auto" ->
                listOf(
                    "Detect automatically",
                    "Определить автоматически",
                    "Автоматты анықтау",
                    "Автоматтык аныктоо",
                    "Муайянкунии худкор",
                    "Avtomatik aniqlash",
                )
            "file_error" ->
                listOf(
                    "Could not read this file. Try selecting it again.",
                    "Не удалось прочитать файл. Выберите его ещё раз.",
                    "Файл оқылмады. Қайта таңдаңыз.",
                    "Файл окулган жок. Кайра тандаңыз.",
                    "Файл хонда нашуд. Боз интихоб кунед.",
                    "Fayl o‘qilmadi. Qayta tanlang.",
                )
            "file_size",
            "file_limit" ->
                listOf(
                    "Split this export into smaller files: at most 3,000 items, 64 columns and 8 MB per file. Nothing from this file has been imported.",
                    "Разделите выгрузку: не более 3000 товаров, 64 столбцов и 8 МБ на файл. Данные из этого файла ещё не импортированы.",
                    "Файлды бөліңіз: 3000 тауар, 64 баған, 8 МБ дейін. Бұл файлдан ештеңе импортталмады.",
                    "Файлды бөлүңүз: 3000 товар, 64 мамыча, 8 МБ чейин. Бул файлдан эч нерсе импорттолгон жок.",
                    "Файлро тақсим кунед: то 3000 мол, 64 сутун ва 8 МБ. Аз ин файл чизе ворид нашудааст.",
                    "Faylni bo‘ling: 3000 mahsulot, 64 ustun, 8 MB gacha. Bu fayldan hech narsa import qilinmadi.",
                )
            "file_format",
            "file_cell",
            "file_external" ->
                listOf(
                    "This workbook format is not supported. Export its stock sheet as CSV (UTF-8), then select that file. Links and macros are never opened.",
                    "Формат этой книги не поддерживается. Сохраните лист товаров как CSV (UTF-8) и выберите его. Ссылки и макросы не открываются.",
                    "Кітап пішімі қолдау таппайды. Тауар парағын CSV (UTF-8) етіп сақтап таңдаңыз. Сілтемелер мен макростар ашылмайды.",
                    "Китеп форматы колдоого алынбайт. Товар барагын CSV (UTF-8) кылып сактап тандаңыз. Шилтемелер жана макростор ачылбайт.",
                    "Формати китоб дастгирӣ намешавад. Варақи молро CSV (UTF-8) захира ва интихоб кунед. Пайванду макросҳо кушода намешаванд.",
                    "Kitob formati qo‘llanmaydi. Mahsulot varag‘ini CSV (UTF-8) saqlab tanlang. Havola va makroslar ochilmaydi.",
                )
            "file_quotes" ->
                listOf(
                    "The CSV has unfinished or invalid quotes. Export it again from the source app.",
                    "В CSV незакрытые или неверные кавычки. Повторите выгрузку из исходной программы.",
                    "CSV тырнақшалары қате. Бастапқы қолданбадан қайта экспорттаңыз.",
                    "CSV тырмакчалары туура эмес. Баштапкы колдонмодон кайра чыгарыңыз.",
                    "Нохунакҳои CSV нодурустанд. Аз барномаи аслӣ боз содир кунед.",
                    "CSV qo‘shtirnoqlari noto‘g‘ri. Asl ilovadan qayta eksport qiling.",
                )
            "file_encoding" ->
                listOf(
                    "The text encoding could not be read. Try Windows-1251 below or export CSV as UTF-8.",
                    "Не удалось прочитать кодировку. Попробуйте Windows-1251 ниже или выгрузите CSV в UTF-8.",
                    "Кодтау оқылмады. Windows-1251 таңдаңыз немесе CSV UTF-8 қолданыңыз.",
                    "Коддоо окулган жок. Windows-1251 тандаңыз же CSV UTF-8 колдонуңуз.",
                    "Рамзгузорӣ хонда нашуд. Windows-1251 ё CSV UTF-8-ро истифода баред.",
                    "Kodlash o‘qilmadi. Windows-1251 yoki CSV UTF-8 ishlating.",
                )
            "file_empty" ->
                listOf(
                    "No item rows in the selected sheet. Check the header row or choose another sheet.",
                    "В выбранном листе нет строк товаров. Проверьте строку заголовков или выберите другой лист.",
                    "Тауар жолдары жоқ. Тақырып жолын немесе парақты тексеріңіз.",
                    "Товар саптары жок. Баш ат сабын же баракты текшериңиз.",
                    "Сатрҳои мол нестанд. Сарлавҳа ё варақро санҷед.",
                    "Mahsulot qatorlari yo‘q. Sarlavha yoki varaqni tekshiring.",
                )
            "file_mapping" ->
                listOf(
                    "Assign the name column. Each field may be assigned only once.",
                    "Укажите столбец названия. Каждое поле можно назначить только один раз.",
                    "Атау бағанын таңдаңыз. Әр өрісті бір рет тағайындаңыз.",
                    "Аталыш мамычасын тандаңыз. Ар талааны бир жолу дайындаңыз.",
                    "Сутуни номро интихоб кунед. Ҳар майдонро як бор таъин кунед.",
                    "Nom ustunini tanlang. Har maydonni faqat bir marta belgilang.",
                )
            "file_formula" ->
                listOf(
                    "This workbook contains formulas. In the source app, copy and paste values only, or export CSV. AITA will not trust cached formula results.",
                    "В книге есть формулы. В исходной программе скопируйте и вставьте только значения либо выгрузите CSV. Сохранённые результаты формул могут быть устаревшими.",
                    "Кітапта формулалар бар. Тек мәндерді көшіріп қойыңыз немесе CSV экспорттаңыз. Формула нәтижелері ескі болуы мүмкін.",
                    "Китепте формулалар бар. Маанилерди гана көчүрүп чаптаңыз же CSV чыгарыңыз. Формула натыйжалары эски болушу мүмкүн.",
                    "Китоб формула дорад. Танҳо қиматҳоро нусхабардорӣ кунед ё CSV содир кунед. Натиҷаи формула метавонад кӯҳна бошад.",
                    "Kitobda formulalar bor. Faqat qiymatlarni nusxalang yoki CSV eksport qiling. Formula natijasi eskirgan bo‘lishi mumkin.",
                )
            "file_numeric" ->
                listOf(
                    "A number is stored in scientific notation or exceeds Excel precision. Save barcodes as Text and prices as plain decimal values, then export again.",
                    "Число записано в экспоненциальном виде или превышает точность Excel. Сохраните штрихкоды как текст, а цены — обычными десятичными числами и повторите выгрузку.",
                    "Сан дәлдігі қолдау таппайды. Штрихкодтарды мәтін, бағаларды ондық сан етіп экспорттаңыз.",
                    "Сандын тактыгы колдоого алынбайт. Штрихкодду текст, бааны ондук сан кылып чыгарыңыз.",
                    "Дақиқии рақам дастгирӣ намешавад. Штрихкодро матн ва нархро адади даҳӣ содир кунед.",
                    "Son aniqligi qo‘llanmaydi. Shtrix-kodni matn, narxni oddiy o‘nlik son qilib eksport qiling.",
                )
            "file_xlsx_unavailable" ->
                listOf(
                    "XLSX decoding is unavailable here. Export the sheet as CSV (UTF-8); no app update is required for CSV.",
                    "Здесь недоступно чтение XLSX. Сохраните лист как CSV (UTF-8); для CSV обновлять приложение не нужно.",
                    "XLSX оқу қолжетімсіз. CSV (UTF-8) қолданыңыз.",
                    "XLSX окуу жеткиликсиз. CSV (UTF-8) колдонуңуз.",
                    "Хондани XLSX дастнорас аст. CSV (UTF-8) истифода баред.",
                    "XLSX o‘qish mavjud emas. CSV (UTF-8) ishlating.",
                )
            "file_category" ->
                listOf(
                    "Match the source category to an AITA category",
                    "Сопоставьте исходную категорию с категорией AITA",
                    "Бастапқы санатты AITA санатына сәйкестендіріңіз",
                    "Баштапкы категорияны AITA категориясына дал келтириңиз",
                    "Гурӯҳи аслиро ба гурӯҳи AITA мувофиқ кунед",
                    "Asl toifani AITA toifasiga moslang",
                )
            else -> return stockScreenText(key, language)
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
