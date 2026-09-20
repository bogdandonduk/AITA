package kz.aita

const val APP_THEME_PURPLE = 2L
const val APP_THEME_BLUE = 3L
const val APP_THEME_BUTTER_YELLOW = 16L
const val APP_THEME_WARM_IVORY = 17L

private data class AppThemeShade(val id: Long, val surface: String, val dark: Boolean, val names: List<String>)

// IDs are persisted locally and on accounts. Never reorder or reuse them for another shade.
private val appThemeShades = listOf(
    AppThemeShade(0L, "ffffffff", false, listOf("Porcelain White", "Фарфоровый белый", "Фарфор ақ", "Фарфор ак", "Сафеди чинӣ", "Chinni oq")),
    AppThemeShade(1L, "ff111111", true, listOf("Obsidian Black", "Обсидиановый чёрный", "Обсидиан қара", "Обсидиан кара", "Сиёҳи обсидианӣ", "Obsidian qora")),
    AppThemeShade(2L, "ff241c36", true, listOf("Midnight Amethyst", "Полуночный аметист", "Түнгі аметист", "Түнкү аметист", "Аметисти нимашабӣ", "Tun ametisti")),
    AppThemeShade(3L, "ff17212b", true, listOf("Midnight Slate Blue", "Полуночный аспидно-синий", "Түнгі тақтатас көк", "Түнкү шифер көк", "Кабуди шиферии нимашабӣ", "Tun shifer ko‘ki")),
    AppThemeShade(4L, "ff210c14", true, listOf("Black Cherry Crimson", "Черешневый багряный", "Қара шие күрең", "Кара алча кочкул кызыл", "Қирмизии гелоси сиёҳ", "Qora olcha qirmizisi")),
    AppThemeShade(5L, "ff2b1017", true, listOf("Oxblood Burgundy", "Бордовый бычьей крови", "Қанық бургунд қызыл", "Кочкул бургунд кызыл", "Бургундии хунин", "To‘q burgund qizili")),
    AppThemeShade(6L, "ff35141d", true, listOf("Dark Garnet Red", "Тёмный гранатовый", "Қою анар қызыл", "Кочкул анар кызыл", "Сурхи анории тира", "To‘q anor qizili")),
    AppThemeShade(7L, "ff17152f", true, listOf("Midnight Indigo", "Полуночный индиго", "Түнгі индиго", "Түнкү индиго", "Индигои нимашабӣ", "Tun indigosi")),
    AppThemeShade(8L, "ff102039", true, listOf("Deep Cobalt Blue", "Глубокий кобальтовый", "Қанық кобальт көк", "Кочкул кобальт көк", "Кабуди кобалтии амиқ", "To‘q kobalt ko‘ki")),
    AppThemeShade(9L, "ff09282b", true, listOf("Abyssal Teal", "Глубоководный бирюзовый", "Тұңғиық көк-жасыл", "Терең көгүш жашыл", "Фирӯзаии қаъри баҳр", "Tubsiz dengiz moviysi")),
    AppThemeShade(10L, "ff0c291f", true, listOf("Deep Emerald Green", "Глубокий изумрудный", "Қанық зүбәржат жасыл", "Кочкул зымырыт жашыл", "Сабзи зумуррадии амиқ", "To‘q zumrad yashili")),
    AppThemeShade(11L, "ff111f17", true, listOf("Black Forest Green", "Чёрно-лесной зелёный", "Қара орман жасыл", "Кара токой жашыл", "Сабзи ҷангали сиёҳ", "Qora o‘rmon yashili")),
    AppThemeShade(12L, "ff25271a", true, listOf("Smoked Olive", "Дымчатый оливковый", "Түтінді зәйтүн", "Түтүндүү зайтун", "Зайтунии дудӣ", "Tutunsimon zaytun")),
    AppThemeShade(13L, "ff2c172a", true, listOf("Dark Aubergine", "Тёмный баклажановый", "Қою баялды", "Кочкул баклажан", "Бодинҷонии тира", "To‘q baqlajon")),
    AppThemeShade(14L, "ff2b1e19", true, listOf("Espresso Brown", "Кофейный эспрессо", "Эспрессо қоңыр", "Эспрессо күрөң", "Қаҳваранги эспрессо", "Espresso jigarrangi")),
    AppThemeShade(15L, "ff24272c", true, listOf("Graphite Charcoal", "Графитовый уголь", "Графит көмір", "Графит көмүр", "Ангиштии графитӣ", "Grafit ko‘mir")),
    AppThemeShade(16L, "fffff2bf", false, listOf("Pale Butter Yellow", "Нежный сливочно-жёлтый", "Ақшыл сары май", "Ачык сары май", "Зарди равғании равшан", "Och sariyog‘ sariq")),
    AppThemeShade(17L, "fff6edda", false, listOf("Warm Ivory", "Тёплая слоновая кость", "Жылы піл сүйегі", "Жылуу пил сөөгү", "Устухонии гарм", "Iliq fil suyagi")),
    AppThemeShade(18L, "ff102b31", true, listOf("Deep Petrol Blue", "Глубокий нефтяной синий", "Қанық мұнай көк", "Кочкул мунай көк", "Кабуди нафтии амиқ", "To‘q neft ko‘ki")),
    AppThemeShade(19L, "ff1d2c35", true, listOf("Stormy Atlantic Blue", "Штормовой атлантический синий", "Дауылды Атлантика көгі", "Бороондуу Атлантика көгү", "Кабуди Атлантикаи тӯфонӣ", "Bo‘ronli Atlantika ko‘ki")),
    AppThemeShade(20L, "ff281b38", true, listOf("Blackberry Velvet", "Ежевичный бархат", "Қаражидек барқыты", "Кара бүлдүркөн баркыты", "Махмали тамашки сиёҳ", "Maymunjon baxmali")),
    AppThemeShade(21L, "ff35172b", true, listOf("Black Plum Wine", "Вино чёрной сливы", "Қара өрік шарабы", "Кара өрүк шарабы", "Шароби олуи сиёҳ", "Qora olxo‘ri sharobi")),
    AppThemeShade(22L, "ff30201f", true, listOf("Smoked Rosewood", "Дымчатое розовое дерево", "Түтінді раушан ағашы", "Түтүндүү роза жыгачы", "Чӯби садбарги дудӣ", "Tutunsimon atirgul yog‘ochi")),
    AppThemeShade(23L, "ff342019", true, listOf("Burnished Copper Brown", "Тёмный медно-коричневый", "Қою мыс қоңыр", "Кочкул жез күрөң", "Қаҳваранги мисии тира", "To‘q mis jigarrangi")),
    AppThemeShade(24L, "ff243024", true, listOf("Ancient Moss Green", "Зелёный древнего мха", "Көне мүк жасылы", "Байыркы мох жашылы", "Сабзи ушнаи қадим", "Qadimiy yo‘sin yashili")),
    AppThemeShade(25L, "ff17312d", true, listOf("Deep Jade Stone", "Глубокий нефритовый", "Қанық нефрит", "Кочкул нефрит", "Нефрити амиқ", "To‘q nefrit")),
    AppThemeShade(26L, "fff1e5e9", false, listOf("Pale Rose Quartz", "Светлый розовый кварц", "Ақшыл раушан кварцы", "Ачык кызгылт кварц", "Кварси гулобии равшан", "Och pushti kvars")),
    AppThemeShade(27L, "ffe6eee7", false, listOf("Soft Sage Mist", "Нежный шалфейный туман", "Нәзік шалфей тұманы", "Назик шалфей туманы", "Тумани нарми мармарак", "Mayin marmarak tumani")),
    AppThemeShade(28L, "ffe8edf4", false, listOf("Frosted Glacier Blue", "Ледниковый светло-голубой", "Ақшыл мұздық көк", "Ачык мөңгү көк", "Кабуди равшани пирях", "Och muzlik ko‘ki")),
    AppThemeShade(29L, "fff0eaf6", false, listOf("Pale Wisteria Silk", "Светлый глициниевый шёлк", "Ақшыл глициния жібегі", "Ачык глициния жибеги", "Абрешими равшани глициния", "Och glitsiniya ipagi"))
)
val SUPPORTED_APP_THEME_IDS: List<Long> = appThemeShades.map { it.id }
fun isDarkAppTheme(theme: Long?): Boolean = appThemeShades.first { it.id == normalizeAppThemePreference(theme) }.dark
fun appDrawableThemeId(theme: Long?): Long = if (isDarkAppTheme(theme)) 1L else 0L
fun appThemeSwatch(theme: Long): String = appThemeShades.first { it.id == normalizeAppThemePreference(theme) }.surface

/** Explicit remote variants still win; older servers inherit coherent light/dark text and icons. */
fun tintedAppThemeColor(id: Long, theme: Long): String? {
    val shade = appThemeShades.first { it.id == normalizeAppThemePreference(theme) }
    if (shade.id < 2L) return null
    return when (id) {
        0L -> if (shade.dark) null else "ff785500"
        1L -> shade.surface
        2L -> if (shade.dark) "ffffffff" else "ff211e18"
        4L -> when (shade.id) {
            APP_THEME_PURPLE -> "b3ded4ee"
            APP_THEME_BLUE -> "b3d6e3ef"
            else -> if (shade.dark) "b3ffffff" else "b3211e18"
        }
        7L -> if (shade.dark) "ccffffff" else "cc211e18"
        else -> null
    }
}

fun availableAppThemes(configured: List<AppThemeDataModel>): List<AppThemeDataModel> = appThemeShades.map { shade ->
    val names = listOf("en", "ru", "kk", "ky", "tg", "uz").zip(shade.names).map { (language, name) ->
        LocalizedStringDataModel(language, name)
    }
    // Bundled shade names supersede old generic server labels. Preserve extra supplied locales.
    AppThemeDataModel(shade.id, names + configured.firstOrNull { it.id == shade.id }?.name.orEmpty()
        .filter { supplied -> names.none { it.language == supplied.language } })
}
