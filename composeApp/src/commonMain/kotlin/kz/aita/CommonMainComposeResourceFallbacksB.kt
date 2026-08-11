// THIS IS CommonMainCompose.kt split slice: ResourceFallbacksB
@file:OptIn(ExperimentalTime::class, ExperimentalFoundationApi::class)
package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import io.kamel.core.config.*
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.kamel.image.config.imageBitmapDecoder
import io.kamel.image.config.svgDecoder
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import kz.aita.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.text.equals
import kotlin.time.ExperimentalTime

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart0() {
    put(0L, mapOf("main" to "AITA", "en" to "AITA", "ru" to "AITA", "kk" to "AITA"))
    put(1L, mapOf("main" to "Log In", "en" to "Log In", "ru" to "Войти", "kk" to "Кіру"))
    put(2L, mapOf("main" to "Phone number", "en" to "Phone number", "ru" to "Номер телефона", "kk" to "Телефон нөмірі"))
    put(3L, mapOf("main" to "Enter phone number", "en" to "Enter phone number", "ru" to "Введите номер телефона", "kk" to "Телефон нөміріңізді енгізіңіз"))
    put(4L, mapOf("main" to "Email", "en" to "Email", "ru" to "Email", "kk" to "Email"))
    put(5L, mapOf("main" to "Enter email address", "en" to "Enter email address", "ru" to "Введите адрес email", "kk" to "Электрондық пошта мекенжайыңызды енгізіңіз"))
    put(6L, mapOf("main" to "Password", "en" to "Password", "ru" to "Пароль", "kk" to "Құпия сөз"))
    put(7L, mapOf("main" to "Enter password", "en" to "Enter password", "ru" to "Введите пароль", "kk" to "Құпия сөзді енгізіңіз"))
    put(8L, mapOf("main" to "Cancel", "en" to "Cancel", "ru" to "Отменить", "kk" to "Болдырмау"))
    put(9L, mapOf("main" to "Clear", "en" to "Clear", "ru" to "Очистить", "kk" to "Тазалау"))
    put(10L, mapOf("main" to "Authentication failed", "en" to "Authentication failed", "ru" to "Аутентификация не удалась", "kk" to "Аутентификация сәтсіз аяқталды"))
    put(11L, mapOf("main" to "Incorrect phone number length", "en" to "Incorrect phone number length", "ru" to "Неправильная длина номера телефона", "kk" to "Телефон нөмірі дұрыс емес"))
    put(12L, mapOf("main" to "Incorrect email address format", "en" to "Incorrect email address format", "ru" to "Неправильный формат email адреса", "kk" to "Қате электрондық пошта мекенжайы пішімі"))
    put(13L, mapOf("main" to "Password must be 8 or more symbols long and contain at least one digit and one special symbol", "en" to "Password must be 8 or more symbols long and contain at least one digit and one special symbol", "ru" to "Пароль должен быть длиной 8 или более символов и содержать хотя бы одну цифру и один специальный символ", "kk" to "Құпия сөз ұзындығы 8 немесе одан да көп таңбадан тұруы және кемінде бір сан мен бір арнайы таңбадан тұруы керек"))
    put(14L, mapOf("main" to "Repeat password", "en" to "Repeat password", "ru" to "Повторите пароль", "kk" to "Құпия сөзді қайталаңыз"))
    put(15L, mapOf("main" to "Passwords must match", "en" to "Passwords must match", "ru" to "Пароли должны совпадать", "kk" to "Құпия сөздер бірдей болуы тиіс"))
    put(16L, mapOf("main" to "First name", "en" to "First name", "ru" to "Имя", "kk" to "Аты"))
    put(17L, mapOf("main" to "Last name", "en" to "Last name", "ru" to "Фамилия", "kk" to "Фамилиясы"))
    put(18L, mapOf("main" to "Enter first name", "en" to "Enter first name", "ru" to "Введите имя", "kk" to "Атын енгізіңіз"))
    put(19L, mapOf("main" to "Enter last name", "en" to "Enter last name", "ru" to "Введите фамилию", "kk" to "Фамилиясын енгізіңіз"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart1() {
    put(20L, mapOf("main" to "User with this phone number is already registered", "en" to "User with this phone number is already registered", "ru" to "Пользователь с этим номером телефона уже зарегистрирован", "kk" to "Бұл телефон нөмірі бар пайдаланушы әлдеқашан тіркелген"))
    put(21L, mapOf("main" to "User with this email address is already registered", "en" to "User with this email address is already registered", "ru" to "Пользователь с этим email адресом уже зарегистрирован", "kk" to "Бұл электрондық пошта мекенжайы бар пайдаланушы әлдеқашан тіркелген"))
    put(22L, mapOf("main" to "Sign Up", "en" to "Sign Up", "ru" to "Зарегистрироваться", "kk" to "Тіркелу"))
    put(23L, mapOf("main" to "Confirm", "en" to "Confirm", "ru" to "Подтвердить", "kk" to "Растау"))
    put(24L, mapOf("main" to "Sale", "en" to "Sale", "ru" to "Продажа", "kk" to "Cату"))
    put(25L, mapOf("main" to "Return", "en" to "Return", "ru" to "Возврат", "kk" to "Қайту"))
    put(26L, mapOf("main" to "Supply", "en" to "Supply", "ru" to "Поставка", "kk" to "Жеткізу"))
    put(27L, mapOf("main" to "Stock", "en" to "Stock", "ru" to "Склад", "kk" to "Қор"))
    put(28L, mapOf("main" to "Menu", "en" to "Menu", "ru" to "Меню", "kk" to "Мәзір"))
    put(29L, mapOf("main" to "Back", "en" to "Back", "ru" to "Назад", "kk" to "Артқа"))
    put(30L, mapOf("main" to "Add goods item", "en" to "Add goods item", "ru" to "Добавить товар", "kk" to "Өнімді қосыңыз"))
    put(31L, mapOf("main" to "Edit goods item", "en" to "Edit goods item", "ru" to "Редактировать товар", "kk" to "Өнімді өңдеу"))
    put(32L, mapOf("main" to "User account", "en" to "User account", "ru" to "Аккаунт пользователя", "kk" to "Пайдаланушы тіркелгісі"))
    put(33L, mapOf("main" to "Goods categories", "en" to "Goods categories", "ru" to "Категории товаров", "kk" to "Өнім санаттары"))
    put(34L, mapOf("main" to "Add goods category", "en" to "Add goods category", "ru" to "Добавить категорию товаров", "kk" to "Өнім санатын қосыңыз"))
    put(35L, mapOf("main" to "Edit goods category", "en" to "Edit goods category", "ru" to "Редактировать категорию товаров", "kk" to "Өнім санатын өңдеу"))
    put(36L, mapOf("main" to "Stores", "en" to "Stores", "ru" to "Магазины", "kk" to "Дүкендер"))
    put(37L, mapOf("main" to "Add store", "en" to "Add store", "ru" to "Добавить магазин", "kk" to "Дүкен қосу"))
    put(38L, mapOf("main" to "Edit store", "en" to "Edit store", "ru" to "Редактировать магазин", "kk" to "Дүкенді өңдеу"))
    put(39L, mapOf("main" to "Subscription", "en" to "Subscription", "ru" to "Подписка", "kk" to "Жазылым"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart2() {
    put(40L, mapOf("main" to "Subscription plans", "en" to "Subscription plans", "ru" to "Планы подписки", "kk" to "Жазылым жоспарлары"))
    put(41L, mapOf("main" to "Transaction history", "en" to "Transaction history", "ru" to "История транзакций", "kk" to "Транзакция тарихы"))
    put(42L, mapOf("main" to "Receipt", "en" to "Receipt", "ru" to "Чек", "kk" to "Түбіртек"))
    put(43L, mapOf("main" to "Analytics", "en" to "Analytics", "ru" to "Аналитика", "kk" to "Аналитика"))
    put(44L, mapOf("main" to "Workers", "en" to "Workers", "ru" to "Сотрудники", "kk" to "Қызметкерлер"))
    put(45L, mapOf("main" to "Add worker", "en" to "Add worker", "ru" to "Добавить сотрудника", "kk" to "Қызметкерді қосыңыз"))
    put(46L, mapOf("main" to "Edit worker", "en" to "Edit worker", "ru" to "Редактировать сотрудника", "kk" to "Қызметкерді өңдеу"))
    put(47L, mapOf("main" to "Suppliers", "en" to "Suppliers", "ru" to "Поставщики", "kk" to "Жабдықтаушылар"))
    put(48L, mapOf("main" to "Add supplier", "en" to "Add supplier", "ru" to "Добавить поставщика", "kk" to "Жеткізуші қосыңыз"))
    put(49L, mapOf("main" to "Edit supplier", "en" to "Edit supplier", "ru" to "Редактировать поставщика", "kk" to "Жеткізушіні өңдеу"))
    put(50L, mapOf("main" to "Debtors", "en" to "Debtors", "ru" to "Должники", "kk" to "Борышкерлер"))
    put(51L, mapOf("main" to "Close debt", "en" to "Close debt", "ru" to "Погасить долг", "kk" to "Қарызды өтеу"))
    put(52L, mapOf("main" to "Devices", "en" to "Devices", "ru" to "Устройства", "kk" to "Құрылғылар"))
    put(53L, mapOf("main" to "App language", "en" to "App language", "ru" to "Язык приложения", "kk" to "Қолданба тілі"))
    put(54L, mapOf("main" to "App theme", "en" to "App theme", "ru" to "Тема приложения", "kk" to "Қолданба тақырыбы"))
    put(55L, mapOf("main" to "Select", "en" to "Select", "ru" to "Выбрать", "kk" to "Таңдау"))
    put(56L, mapOf("main" to "User with this phone number and email address is already registered", "en" to "User with this phone number and email address is already registered", "ru" to "Пользователь с этим номером телефона и email адресом уже зарегистрирован", "kk" to "Осы телефон нөмірі мен электрондық пошта мекенжайы бар пайдаланушы әлдеқашан тіркелген"))
    put(57L, mapOf("main" to "First name cannot be empty or just whitespaces", "en" to "First name cannot be empty or just whitespaces", "ru" to "Имя не может быть пустым или только пробелами", "kk" to "Атау бос болмауы немесе тек бос орындардан тұруы мүмкін емес"))
    put(58L, mapOf("main" to "Last name cannot be empty or just whitespaces", "en" to "Last name cannot be empty or just whitespaces", "ru" to "Фамилия не может быть пустой или только пробелами", "kk" to "Тегі бос болмауы немесе тек бос орындар болуы мүмкін емес"))
    put(59L, mapOf("main" to "System language", "en" to "System language", "ru" to "Системный язык", "kk" to "Жүйе тілі"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart3() {
    put(60L, mapOf("main" to "Bluetooth permission required", "en" to "Bluetooth permission required", "ru" to "Необходимо разрешение на Bluetooth", "kk" to "Bluetooth рұқсаты қажет"))
    put(61L, mapOf("main" to "To find and connect Bluetooth barcode scanners and receipt printers", "en" to "To find and connect Bluetooth barcode scanners and receipt printers", "ru" to "Для поиска и подключения Bluetooth-сканеров штрих-кодов и принтеров чеков", "kk" to "Bluetooth штрих-код сканерлері мен түбіртек принтерлерін табу және қосу үшін"))
    put(62L, mapOf("main" to "AITA uses Bluetooth to find and connect barcode scanners and receipt printers. You can allow it in app settings.", "en" to "AITA uses Bluetooth to find and connect barcode scanners and receipt printers. You can allow it in app settings.", "ru" to "AITA использует Bluetooth, чтобы находить и подключать сканеры штрих-кодов и принтеры чеков. Разрешение можно включить в настройках приложения.", "kk" to "AITA Bluetooth арқылы штрих-код сканерлері мен түбіртек принтерлерін тауып, қосады. Рұқсатты қолданба баптауларында қосуға болады."))
    put(63L, mapOf("main" to "Bluetooth disabled", "en" to "Bluetooth disabled", "ru" to "Bluetooth выключен", "kk" to "Bluetooth өшірілген"))
    put(64L, mapOf("main" to "Enable for search and connection to Bluetooth barcode scanners and receipt printers", "en" to "Enable for search and connection to Bluetooth barcode scanners and receipt printers", "ru" to "Включите для поиска и соединения с Bluetooth сканерами штрих-кодов и принтеров чеков", "kk" to "Bluetooth штрих-код сканерлері мен түбіртек принтерлерін іздеуді және оларға қосылуды қосыңыз"))
    put(65L, mapOf("main" to "Search by any data", "en" to "Search by any data", "ru" to "Ищите по любым данным", "kk" to "Кез келген деректер бойынша іздеу"))
    put(66L, mapOf("main" to "List empty", "en" to "List empty", "ru" to "Список пуст", "kk" to "Тізім бос"))
    put(67L, mapOf("main" to "No matches", "en" to "No matches", "ru" to "Нет совпадений", "kk" to "Сәйкестік жоқ"))
    put(68L, mapOf("main" to "Name", "en" to "Name", "ru" to "Название", "kk" to "Аты"))
    put(69L, mapOf("main" to "Barcode", "en" to "Barcode", "ru" to "Штрих-код", "kk" to "Штрих-код"))
    put(70L, mapOf("main" to "Supply price", "en" to "Supply price", "ru" to "Цена поставки", "kk" to "Жеткізу бағасы"))
    put(71L, mapOf("main" to "Sale price", "en" to "Sale price", "ru" to "Цена продажи", "kk" to "Сату бағасы"))
    put(72L, mapOf("main" to "Return price", "en" to "Return price", "ru" to "Цена возврата", "kk" to "Қайтару бағасы"))
    put(73L, mapOf("main" to "Category", "en" to "Category", "ru" to "Категория", "kk" to "Санат"))
    put(74L, mapOf("main" to "Supplier", "en" to "Supplier", "ru" to "Поставщик", "kk" to "Жеткізуші"))
    put(75L, mapOf("main" to "Enter barcode", "en" to "Enter barcode", "ru" to "Введите штрих-код", "kk" to "Штрих-кодты енгізіңіз"))
    put(76L, mapOf("main" to "Enter name", "en" to "Enter name", "ru" to "Введите название", "kk" to "Атын енгізіңіз"))
    put(77L, mapOf("main" to "Enter supply price", "en" to "Enter supply price", "ru" to "Введите цену поставки", "kk" to "Жеткізу бағасын енгізіңіз"))
    put(78L, mapOf("main" to "Enter sale price", "en" to "Enter sale price", "ru" to "Введите цену продажи", "kk" to "Сату бағасын енгізіңіз"))
    put(79L, mapOf("main" to "Enter return price", "en" to "Enter return price", "ru" to "Введите цену возврата", "kk" to "Қайтару бағасын енгізіңіз"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart4() {
    put(80L, mapOf("main" to "Select category", "en" to "Select category", "ru" to "Выберите категорию", "kk" to "Санатты таңдаңыз"))
    put(81L, mapOf("main" to "Select supplier", "en" to "Select supplier", "ru" to "Выберите поставщика", "kk" to "Жеткізушіні таңдаңыз"))
    put(82L, mapOf("main" to "Edit", "en" to "Edit", "ru" to "Редактировать", "kk" to "Өңдеу"))
    put(83L, mapOf("main" to "Change password", "en" to "Change password", "ru" to "Сменить пароль", "kk" to "Құпия сөзді өзгерту"))
    put(84L, mapOf("main" to "New password", "en" to "New password", "ru" to "Новый пароль", "kk" to "Жаңа құпия сөз"))
    put(85L, mapOf("main" to "Enter new password", "en" to "Enter new password", "ru" to "Введите новый пароль", "kk" to "Жаңа құпия сөзді енгізіңіз"))
    put(86L, mapOf("main" to "Repeat new password", "en" to "Repeat new password", "ru" to "Повторите новый пароль", "kk" to "Жаңа құпия сөзді қайталаңыз"))
    put(87L, mapOf("main" to "Confirmation password", "en" to "Confirmation password", "ru" to "Пароль для подтверждения", "kk" to "Растау үшін пароль"))
    put(88L, mapOf("main" to "Required to edit account", "en" to "Required to edit account", "ru" to "Необходим для редактирования аккаунта", "kk" to "Есептік жазбаны өңдеу үшін қажет"))
    put(89L, mapOf("main" to "Account successfully updated", "en" to "Account successfully updated", "ru" to "Аккаунт успешно обновлен", "kk" to "Есептік жазба сәтті жаңартылды"))
    put(90L, mapOf("main" to "Logging out", "en" to "Logging out", "ru" to "Выполняется выход из аккаунта", "kk" to "Шығу орындалуда"))
    put(91L, mapOf("main" to "Cloud sign-in expired. Sign in again to sync. Your local data stays available.", "en" to "Cloud sign-in expired. Sign in again to sync. Your local data stays available.", "ru" to "Срок облачного входа истёк. Войдите снова для синхронизации. Локальные данные останутся доступны.", "kk" to "Бұлттық кіру мерзімі аяқталды. Синхрондау үшін қайта кіріңіз. Жергілікті деректер қолжетімді болып қалады."))
    put(92L, mapOf("main" to "Alias", "en" to "Alias", "ru" to "Дополнительное название", "kk" to "Қосымша атау"))
    put(93L, mapOf("main" to "Description", "en" to "Description", "ru" to "Описание", "kk" to "Сипаттама"))
    put(94L, mapOf("main" to "Enter alias", "en" to "Enter alias", "ru" to "Введите дополнительное название", "kk" to "Қосымша атау енгізіңіз"))
    put(95L, mapOf("main" to "Enter description", "en" to "Enter description", "ru" to "Введите описание", "kk" to "Сипаттама енгізіңіз"))
    put(96L, mapOf("main" to "Optional", "en" to "Optional", "ru" to "Необязательно", "kk" to "Міндетті емес"))
    put(97L, mapOf("main" to "Logging in", "en" to "Logging in", "ru" to "Выполняется вход в аккаунт", "kk" to "Сіз өзіңіздің есептік жазбаңызға кіріп жатырсыз"))
    put(98L, mapOf("main" to "Signing up", "en" to "Signing up", "ru" to "Выполняется регистрация", "kk" to "Тіркелу жүріп жатыр"))
    put(99L, mapOf("main" to "Company form", "en" to "Company form", "ru" to "Форма компании", "kk" to "Компания формасы"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart5() {
    put(100L, mapOf("main" to "Measurement unit", "en" to "Measurement unit", "ru" to "Единица измерения", "kk" to "Өлшем бірлігі"))
    put(101L, mapOf("main" to "No active store", "en" to "No active store", "ru" to "Нет активного магазина", "kk" to "Белсенді дүкен жоқ"))
    put(102L, mapOf("main" to "Select in menu", "en" to "Select in menu", "ru" to "Выбрать в меню", "kk" to "Мәзірден таңдаңыз"))
    put(103L, mapOf("main" to "Supply data", "en" to "Supply data", "ru" to "Данные о поставке", "kk" to "Жеткізу мәліметтері"))
    put(104L, mapOf("main" to "Sale data", "en" to "Sale data", "ru" to "Данные о продаже", "kk" to "Сату деректері"))
    put(105L, mapOf("main" to "Return data", "en" to "Return data", "ru" to "Данные о возврате", "kk" to "Мәліметтерді қайтару"))
    put(106L, mapOf("main" to "Add supply data", "en" to "Add supply data", "ru" to "Добавить данные о поставке", "kk" to "Жеткізу мәліметтерін қосыңыз"))
    put(107L, mapOf("main" to "Add sale data", "en" to "Add sale data", "ru" to "Добавить данные о продаже", "kk" to "Сатылым мәліметтерін қосыңыз"))
    put(108L, mapOf("main" to "Add return data", "en" to "Add return data", "ru" to "Добавить данные о возврате", "kk" to "Қайтару мәліметтерін қосыңыз"))
    put(109L, mapOf("main" to "Add barcode", "en" to "Add barcode", "ru" to "Добавить штрих-код", "kk" to "Штрих-код қосу"))
    put(110L, mapOf("main" to "Add name", "en" to "Add name", "ru" to "Добавить название", "kk" to "Атау қосу"))
    put(111L, mapOf("main" to "Payment", "en" to "Payment", "ru" to "Оплата", "kk" to "Төлем"))
    put(112L, mapOf("main" to "All", "en" to "All", "ru" to "Все", "kk" to "Барлығы"))
    put(113L, mapOf("main" to "Quick", "en" to "Quick", "ru" to "Быстрые", "kk" to "Жылдам"))
    put(114L, mapOf("main" to "Categories", "en" to "Categories", "ru" to "Категории", "kk" to "Санаттар"))
    put(115L, mapOf("main" to "Main", "en" to "Main", "ru" to "Основной", "kk" to "Негізгі"))
    put(116L, mapOf("main" to "Add translation", "en" to "Add translation", "ru" to "Добавить перевод", "kk" to "Аударма қосу"))
    put(117L, mapOf("main" to "Set active", "en" to "Set active", "ru" to "Сделать активным", "kk" to "Белсенді ету"))
    put(118L, mapOf("main" to "Out of stock", "en" to "Out of stock", "ru" to "Нет в наличии", "kk" to "Қоймада жоқ"))
    put(119L, mapOf("main" to "Delete", "en" to "Delete", "ru" to "Удалить", "kk" to "Жою"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart6() {
    put(120L, mapOf("main" to "Cash", "en" to "Cash", "ru" to "Наличные", "kk" to "Қолма-қол ақша"))
    put(121L, mapOf("main" to "Cashless", "en" to "Cashless", "ru" to "Безналичные", "kk" to "Ақшасыз"))
    put(122L, mapOf("main" to "Mixed", "en" to "Mixed", "ru" to "Смешанная", "kk" to "Аралас"))
    put(123L, mapOf("main" to "Add", "en" to "Add", "ru" to "Добавить", "kk" to "Қосу"))
    put(124L, mapOf("main" to "Subtract", "en" to "Subtract", "ru" to "Вычесть", "kk" to "Алып тастау"))
    put(125L, mapOf("main" to "Current batch data", "en" to "Current batch data", "ru" to "Данные о текущих партиях", "kk" to "Ағымдағы кеш мәліметтері"))
    put(126L, mapOf("main" to "Enter quantity", "en" to "Enter quantity", "ru" to "Введите количество", "kk" to "Санын енгізіңіз"))
    put(127L, mapOf("main" to "Add quantity data", "en" to "Add quantity data", "ru" to "Добавить данные о количестве", "kk" to "Сандық деректерді қосу"))
    put(128L, mapOf("main" to "Shelf batch", "en" to "Shelf batch", "ru" to "Партия на полке", "kk" to "Сөредегі кеш"))
    put(129L, mapOf("main" to "Active store", "en" to "Active store", "ru" to "Активный магазин", "kk" to "Белсенді дүкен"))
    put(130L, mapOf("main" to "Make inactive", "en" to "Make inactive", "ru" to "Сделать неактивным", "kk" to "Белсенді емес ету"))
    put(131L, mapOf("main" to "Cart empty", "en" to "Cart empty", "ru" to "Корзина пуста", "kk" to "Себет бос"))
    put(132L, mapOf("main" to "Complete", "en" to "Complete", "ru" to "Завершить", "kk" to "Аяқтау"))
    put(133L, mapOf("main" to "No active workshift", "en" to "No active workshift", "ru" to "Активной рабочей смены нет", "kk" to "Белсенді жұмыс ауысымы жоқ"))
    put(134L, mapOf("main" to "Cart", "en" to "Cart", "ru" to "Корзина", "kk" to "Себет"))
    put(135L, mapOf("main" to "App mode", "en" to "App mode", "ru" to "Режим приложения", "kk" to "Қолданба режимі"))
    put(136L, mapOf("main" to "Finances", "en" to "Finances", "ru" to "Финансы", "kk" to "Қаржы"))
    put(137L, mapOf("main" to "Items", "en" to "Items", "ru" to "Наименования", "kk" to "Заттар"))
    put(138L, mapOf("main" to "Batches", "en" to "Batches", "ru" to "Партии", "kk" to "Кештер"))
    put(139L, mapOf("main" to "Standard prices by suppliers", "en" to "Standard prices by suppliers", "ru" to "Стандартные цены по поставщикам", "kk" to "Жеткізушілердің стандартты бағалары"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart7() {
    put(140L, mapOf("main" to "Editable for individual batches", "en" to "Editable for individual batches", "ru" to "Можно изменить для индивидуальных партий", "kk" to "Жеке тараптар үшін өзгертуге болады"))
    put(141L, mapOf("main" to "Batches data", "en" to "Batches data", "ru" to "Данные о партиях", "kk" to "Партия деректері"))
    put(142L, mapOf("main" to "Receipt number", "en" to "Receipt number", "ru" to "Номер чека", "kk" to "Чек нөмірі"))
    put(143L, mapOf("main" to "Transaction ID", "en" to "Transaction ID", "ru" to "ID транзакции", "kk" to "Транзакция ID-і"))
    put(144L, mapOf("main" to "Date", "en" to "Date", "ru" to "Дата", "kk" to "Күні"))
    put(145L, mapOf("main" to "Cashier", "en" to "Cashier", "ru" to "Кассир", "kk" to "Кассир"))
    put(146L, mapOf("main" to "Store", "en" to "Store", "ru" to "Магазин", "kk" to "Дүкен"))
    put(147L, mapOf("main" to "Address", "en" to "Address", "ru" to "Адрес", "kk" to "Мекенжай"))
    put(148L, mapOf("main" to "Phone", "en" to "Phone", "ru" to "Телефон", "kk" to "Телефон"))
    put(149L, mapOf("main" to "Total", "en" to "Total", "ru" to "Итого", "kk" to "Барлығы"))
    put(150L, mapOf("main" to "Paid", "en" to "Paid", "ru" to "Оплачено", "kk" to "Төленді"))
    put(151L, mapOf("main" to "Debt", "en" to "Debt", "ru" to "Долг", "kk" to "Қарыз"))
    put(152L, mapOf("main" to "Debtor", "en" to "Debtor", "ru" to "Должник", "kk" to "Қарыз алушы"))
    put(153L, mapOf("main" to "Debtor phone", "en" to "Debtor phone", "ru" to "Телефон должника", "kk" to "Қарыз алушының телефоны"))
    put(154L, mapOf("main" to "Change", "en" to "Change", "ru" to "Сдача", "kk" to "Қайтарым"))
    put(155L, mapOf("main" to "VAT / НДС / ҚҚС", "en" to "VAT / НДС / ҚҚС", "ru" to "НДС / VAT / ҚҚС", "kk" to "ҚҚС / НДС / VAT"))
    put(156L, mapOf("main" to "Not specified", "en" to "Not specified", "ru" to "Не указано", "kk" to "Көрсетілмеген"))
    put(157L, mapOf("main" to "Fiscal status", "en" to "Fiscal status", "ru" to "Фискальный статус", "kk" to "Фискалдық мәртебе"))
    put(158L, mapOf("main" to "Non-fiscal software receipt", "en" to "Non-fiscal software receipt", "ru" to "Нефискальный программный чек", "kk" to "Фискалдық емес бағдарламалық чек"))
    put(159L, mapOf("main" to "Thank you for your purchase!", "en" to "Thank you for your purchase!", "ru" to "Спасибо за покупку!", "kk" to "Сатып алғаныңызға рақмет!"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart8() {
    put(160L, mapOf("main" to "No items", "en" to "No items", "ru" to "Нет товаров", "kk" to "Тауарлар жоқ"))
    put(161L, mapOf("main" to "PDF", "en" to "PDF", "ru" to "PDF", "kk" to "PDF"))
    put(162L, mapOf("main" to "Share", "en" to "Share", "ru" to "Поделиться", "kk" to "Бөлісу"))
    put(163L, mapOf("main" to "WhatsApp", "en" to "WhatsApp", "ru" to "WhatsApp", "kk" to "WhatsApp"))
    put(164L, mapOf("main" to "Print", "en" to "Print", "ru" to "Печать", "kk" to "Басып шығару"))
    put(165L, mapOf("main" to "Quit", "en" to "Quit", "ru" to "Выйти", "kk" to "Шығу"))
    put(166L, mapOf("main" to "Receipt PDF saved", "en" to "Receipt PDF saved", "ru" to "PDF-чек сохранён", "kk" to "PDF чек сақталды"))
    put(167L, mapOf("main" to "Receipt shared", "en" to "Receipt shared", "ru" to "Чек отправлен", "kk" to "Чек жіберілді"))
    put(168L, mapOf("main" to "Receipt sent to WhatsApp", "en" to "Receipt sent to WhatsApp", "ru" to "Чек отправлен в WhatsApp", "kk" to "Чек WhatsApp арқылы жіберілді"))
    put(169L, mapOf("main" to "Receipt sent to printer", "en" to "Receipt sent to printer", "ru" to "Чек отправлен на принтер", "kk" to "Чек принтерге жіберілді"))
    put(170L, mapOf("main" to "Receipt action failed", "en" to "Receipt action failed", "ru" to "Не удалось выполнить действие с чеком", "kk" to "Чек әрекетін орындау мүмкін болмады"))
    put(171L, mapOf("main" to "Goods receipt", "en" to "Goods receipt", "ru" to "Товарный чек", "kk" to "Тауар чегі"))
    put(172L, mapOf("main" to "Sale", "en" to "Sale", "ru" to "Продажа", "kk" to "Сату"))
    put(173L, mapOf("main" to "Return", "en" to "Return", "ru" to "Возврат", "kk" to "Қайтару"))
    put(174L, mapOf("main" to "Acceptance", "en" to "Acceptance", "ru" to "Приёмка", "kk" to "Қабылдау"))
    put(175L, mapOf("main" to "Draft", "en" to "Draft", "ru" to "Черновик", "kk" to "Жоба"))
    put(176L, mapOf("main" to "No name", "en" to "No name", "ru" to "Без названия", "kk" to "Атаусыз"))
    put(177L, mapOf("main" to "Notifications", "en" to "Notifications", "ru" to "Уведомления", "kk" to "Хабарламалар"))
    put(178L, mapOf("main" to "Unread", "en" to "Unread", "ru" to "Непрочитанные", "kk" to "Оқылмаған"))
    put(179L, mapOf("main" to "Positive", "en" to "Positive", "ru" to "Положительные", "kk" to "Жағымды"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart9() {
    put(180L, mapOf("main" to "Negative", "en" to "Negative", "ru" to "Отрицательные", "kk" to "Жағымсыз"))
    put(181L, mapOf("main" to "Neutral", "en" to "Neutral", "ru" to "Нейтральные", "kk" to "Бейтарап"))
    put(182L, mapOf("main" to "NEW", "en" to "NEW", "ru" to "НОВОЕ", "kk" to "ЖАҢА"))
    put(183L, mapOf("main" to "more notifications queued", "en" to "more notifications queued", "ru" to "уведомлений в очереди", "kk" to "хабарлама кезекте"))
    put(184L, mapOf("main" to "Subcategory", "en" to "Subcategory", "ru" to "Подкатегория", "kk" to "Ішкі санат"))
    put(185L, mapOf("main" to "Select subcategory", "en" to "Select subcategory", "ru" to "Выберите подкатегорию", "kk" to "Ішкі санатты таңдаңыз"))
    put(186L, mapOf("main" to "No categories loaded yet. Restart server after category migrations or refresh categories.", "en" to "No categories loaded yet. Restart server after category migrations or refresh categories.", "ru" to "Категории ещё не загружены. Перезапустите сервер после миграций категорий или обновите категории.", "kk" to "Санаттар әлі жүктелмеді. Санат миграцияларынан кейін серверді қайта іске қосыңыз немесе санаттарды жаңартыңыз."))
    put(187L, mapOf("main" to "Refresh categories", "en" to "Refresh categories", "ru" to "Обновить категории", "kk" to "Санаттарды жаңарту"))
    put(188L, mapOf("main" to "Select unit", "en" to "Select unit", "ru" to "Выберите единицу", "kk" to "Өлшем бірлігін таңдаңыз"))
    put(189L, mapOf("main" to "Select category", "en" to "Select category", "ru" to "Выберите категорию", "kk" to "Санатты таңдаңыз"))
    put(190L, mapOf("main" to "read", "en" to "read", "ru" to "прочитано", "kk" to "оқылды"))
    put(191L, mapOf("main" to "Source", "en" to "Source", "ru" to "Источник", "kk" to "Дереккөз"))
    put(192L, mapOf("main" to "Created", "en" to "Created", "ru" to "Создано", "kk" to "Жасалды"))
    put(193L, mapOf("main" to "No notifications match this search", "en" to "No notifications match this search", "ru" to "По этому поиску уведомлений нет", "kk" to "Бұл іздеу бойынша хабарламалар жоқ"))
    put(194L, mapOf("main" to "Add batch", "en" to "Add batch", "ru" to "Добавить партию", "kk" to "Партия қосу"))
    put(195L, mapOf("main" to "Edit batch", "en" to "Edit batch", "ru" to "Редактировать партию", "kk" to "Партияны өңдеу"))
    put(196L, mapOf("main" to "Save the goods item first, then you can add batches.", "en" to "Save the goods item first, then you can add batches.", "ru" to "Сначала сохраните товар, затем можно будет добавить партии.", "kk" to "Алдымен тауарды сақтаңыз, содан кейін партияларды қосуға болады."))
    put(197L, mapOf("main" to "Shelf order", "en" to "Shelf order", "ru" to "Порядок на полке", "kk" to "Сөредегі рет"))
    put(198L, mapOf("main" to "The first batch is the active shelf batch. Long-press and drag a batch up or down to change shelf order.", "en" to "The first batch is the active shelf batch. Long-press and drag a batch up or down to change shelf order.", "ru" to "Первая партия считается активной на полке. Зажмите и перетащите партию вверх или вниз, чтобы изменить порядок.", "kk" to "Бірінші партия сөредегі белсенді партия болып саналады. Ретін өзгерту үшін партияны басып тұрып жоғары немесе төмен сүйреңіз."))
    put(199L, mapOf("main" to "No batches yet", "en" to "No batches yet", "ru" to "Партий пока нет", "kk" to "Әзірге партия жоқ"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart10() {
    put(200L, mapOf("main" to "Status", "en" to "Status", "ru" to "Статус", "kk" to "Күйі"))
    put(201L, mapOf("main" to "Notes", "en" to "Notes", "ru" to "Заметки", "kk" to "Ескертпелер"))
    put(202L, mapOf("main" to "Expiration", "en" to "Expiration", "ru" to "Срок годности", "kk" to "Жарамдылық мерзімі"))
    put(203L, mapOf("main" to "Supplier prices", "en" to "Supplier prices", "ru" to "Цены поставщиков", "kk" to "Жеткізуші бағалары"))
    put(204L, mapOf("main" to "Confirm action", "en" to "Confirm action", "ru" to "Подтвердите действие", "kk" to "Әрекетті растаңыз"))
    put(205L, mapOf("main" to "This action may change or delete important data.", "en" to "This action may change or delete important data.", "ru" to "Это действие может изменить или удалить важные данные.", "kk" to "Бұл әрекет маңызды деректерді өзгертуі немесе өшіруі мүмкін."))
    put(206L, mapOf("main" to "Weight", "en" to "Weight", "ru" to "Вес", "kk" to "Салмақ"))
    put(207L, mapOf("main" to "Enter weight", "en" to "Enter weight", "ru" to "Введите вес", "kk" to "Салмақты енгізіңіз"))
    put(208L, mapOf("main" to "Set weight", "en" to "Set weight", "ru" to "Установить вес", "kk" to "Салмақты орнату"))
    put(209L, mapOf("main" to "Security", "en" to "Security", "ru" to "Безопасность", "kk" to "Қауіпсіздік"))
    put(210L, mapOf("main" to "Active sessions", "en" to "Active sessions", "ru" to "Активные сеансы", "kk" to "Белсенді сеанстар"))
    put(211L, mapOf("main" to "Current device", "en" to "Current device", "ru" to "Текущее устройство", "kk" to "Ағымдағы құрылғы"))
    put(212L, mapOf("main" to "Revoke session", "en" to "Revoke session", "ru" to "Завершить сеанс", "kk" to "Сеансты тоқтату"))
    put(213L, mapOf("main" to "Revoke other sessions", "en" to "Revoke other sessions", "ru" to "Завершить другие сеансы", "kk" to "Басқа сеанстарды тоқтату"))
    put(214L, mapOf("main" to "Cannot reach server. Keeping you signed in offline.", "en" to "Cannot reach server. Keeping you signed in offline.", "ru" to "Сервер недоступен. Вы остаётесь в аккаунте офлайн.", "kk" to "Сервер қолжетімсіз. Сіз офлайн режимде аккаунтта қаласыз."))
    put(215L, mapOf("main" to "Cannot reach server. Security sessions will refresh when connection returns.", "en" to "Cannot reach server. Security sessions will refresh when connection returns.", "ru" to "Сервер недоступен. Сеансы безопасности обновятся после восстановления соединения.", "kk" to "Сервер қолжетімсіз. Қауіпсіздік сеанстары байланыс қалпына келгенде жаңартылады."))
    put(216L, mapOf("main" to "Session revoked", "en" to "Session revoked", "ru" to "Сеанс завершён", "kk" to "Сеанс тоқтатылды"))
    put(217L, mapOf("main" to "Other sessions revoked", "en" to "Other sessions revoked", "ru" to "Другие сеансы завершены", "kk" to "Басқа сеанстар тоқтатылды"))
    put(218L, mapOf("main" to "Active sessions loaded", "en" to "Active sessions loaded", "ru" to "Активные сеансы загружены", "kk" to "Белсенді сеанстар жүктелді"))
    put(219L, mapOf("main" to "Login is already in progress", "en" to "Login is already in progress", "ru" to "Вход уже выполняется", "kk" to "Кіру қазірдің өзінде орындалып жатыр"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart11() {
    put(220L, mapOf("main" to "Logged out locally", "en" to "Logged out locally", "ru" to "Вы вышли локально", "kk" to "Сіз жергілікті түрде шықтыңыз"))
    put(221L, mapOf("main" to "Logged out locally; server session cleanup failed", "en" to "Logged out locally; server session cleanup failed", "ru" to "Вы вышли локально; не удалось завершить сеанс на сервере", "kk" to "Сіз жергілікті түрде шықтыңыз; сервердегі сеансты аяқтау мүмкін болмады"))
    put(222L, mapOf("main" to "Login failed: empty token response", "en" to "Login failed: empty token response", "ru" to "Не удалось войти: сервер не вернул токены", "kk" to "Кіру орындалмады: сервер токендерді қайтармады"))
    put(223L, mapOf("main" to "Cannot reach server", "en" to "Cannot reach server", "ru" to "Сервер недоступен", "kk" to "Сервер қолжетімсіз"))
    put(224L, mapOf("main" to "Completing transaction", "en" to "Completing transaction", "ru" to "Завершение операции", "kk" to "Операция аяқталуда"))
    put(225L, mapOf("main" to "Server response could not be read", "en" to "Server response could not be read", "ru" to "Не удалось прочитать ответ сервера", "kk" to "Сервер жауабын оқу мүмкін болмады"))
    put(226L, mapOf("main" to "Account security", "en" to "Account security", "ru" to "Безопасность аккаунта", "kk" to "Аккаунт қауіпсіздігі"))
    put(227L, mapOf("main" to "Review where your account is signed in. Revoke sessions you do not recognize.", "en" to "Review where your account is signed in. Revoke sessions you do not recognize.", "ru" to "Проверьте, где выполнен вход в аккаунт. Завершите незнакомые сеансы.", "kk" to "Аккаунтқа қай жерде кірілгенін тексеріңіз. Танымайтын сеанстарды тоқтатыңыз."))
    put(228L, mapOf("main" to "This device", "en" to "This device", "ru" to "Это устройство", "kk" to "Осы құрылғы"))
    put(229L, mapOf("main" to "Current session", "en" to "Current session", "ru" to "Текущий сеанс", "kk" to "Ағымдағы сеанс"))
    put(230L, mapOf("main" to "Unknown device", "en" to "Unknown device", "ru" to "Неизвестное устройство", "kk" to "Белгісіз құрылғы"))
    put(231L, mapOf("main" to "Active session", "en" to "Active session", "ru" to "Активный сеанс", "kk" to "Белсенді сеанс"))
    put(232L, mapOf("main" to "Current", "en" to "Current", "ru" to "Текущий", "kk" to "Ағымдағы"))
    put(233L, mapOf("main" to "Signed in", "en" to "Signed in", "ru" to "Вход выполнен", "kk" to "Кірілген"))
    put(234L, mapOf("main" to "Expires", "en" to "Expires", "ru" to "Истекает", "kk" to "Аяқталады"))
    put(235L, mapOf("main" to "Language", "en" to "Language", "ru" to "Язык", "kk" to "Тіл"))
    put(236L, mapOf("main" to "User agent", "en" to "User agent", "ru" to "User agent", "kk" to "User agent"))
    put(237L, mapOf("main" to "Refresh", "en" to "Refresh", "ru" to "Обновить", "kk" to "Жаңарту"))
    put(238L, mapOf("main" to "Revoke others", "en" to "Revoke others", "ru" to "Завершить другие", "kk" to "Басқаларын тоқтату"))
    put(239L, mapOf("main" to "No active sessions loaded yet", "en" to "No active sessions loaded yet", "ru" to "Активные сеансы ещё не загружены", "kk" to "Белсенді сеанстар әлі жүктелмеді"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart12() {
    put(240L, mapOf("main" to "Session id is required", "en" to "Session id is required", "ru" to "Нужен id сеанса", "kk" to "Сеанс id қажет"))
    put(241L, mapOf("main" to "Use logout to revoke the current session", "en" to "Use logout to revoke the current session", "ru" to "Чтобы завершить текущий сеанс, выйдите из аккаунта", "kk" to "Ағымдағы сеансты тоқтату үшін аккаунттан шығыңыз"))
    put(242L, mapOf("main" to "Server", "en" to "Server", "ru" to "Сервер", "kk" to "Сервер"))
    put(243L, mapOf("main" to "Sale method", "en" to "Sale method", "ru" to "Способ продажи", "kk" to "Сату тәсілі"))
    put(244L, mapOf("main" to "Retail", "en" to "Retail", "ru" to "Розница", "kk" to "Бөлшек"))
    put(245L, mapOf("main" to "Wholesale", "en" to "Wholesale", "ru" to "Оптом", "kk" to "Көтерме"))
    put(246L, mapOf("main" to "Wholesale price", "en" to "Wholesale price", "ru" to "Оптовая цена", "kk" to "Көтерме баға"))
    put(247L, mapOf("main" to "Wholesale price override", "en" to "Wholesale price override", "ru" to "Оптовая цена партии", "kk" to "Партияның көтерме бағасы"))
    put(248L, mapOf("main" to "Wholesale minimum quantity", "en" to "Wholesale minimum quantity", "ru" to "Минимум для опта", "kk" to "Көтерме үшін минимум"))
    put(249L, mapOf("main" to "Enter wholesale minimum quantity", "en" to "Enter wholesale minimum quantity", "ru" to "Введите минимум для опта", "kk" to "Көтерме минимумын енгізіңіз"))
    put(250L, mapOf("main" to "Wholesale from", "en" to "Wholesale from", "ru" to "Оптом от", "kk" to "Көтерме"))
    put(251L, mapOf("main" to "Not enough for wholesale", "en" to "Not enough for wholesale", "ru" to "Недостаточно для опта", "kk" to "Көтерме үшін жеткіліксіз"))
    put(252L, mapOf("main" to "Info", "en" to "Info", "ru" to "Информация", "kk" to "Ақпарат"))
    put(253L, mapOf("main" to "Generic prices", "en" to "Generic prices", "ru" to "Базовые цены", "kk" to "Негізгі бағалар"))
    put(254L, mapOf("main" to "Orders", "en" to "Orders", "ru" to "Заказы", "kk" to "Тапсырыстар"))
    put(255L, mapOf("main" to "Cash register", "en" to "Cash register", "ru" to "Касса", "kk" to "Касса"))
    put(256L, mapOf("main" to "Cash registers", "en" to "Cash registers", "ru" to "Кассы", "kk" to "Кассалар"))
    put(257L, mapOf("main" to "History", "en" to "History", "ru" to "История", "kk" to "Тарих"))
    put(258L, mapOf("main" to "No sales in this period", "en" to "No sales in this period", "ru" to "За этот период продаж нет", "kk" to "Бұл кезеңде сатылым жоқ"))
    put(259L, mapOf("main" to "No cash register extractions in this period", "en" to "No cash register extractions in this period", "ru" to "За этот период изъятий из кассы нет", "kk" to "Бұл кезеңде кассадан алу жоқ"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart13() {
    put(260L, mapOf("main" to "Today", "en" to "Today", "ru" to "Сегодня", "kk" to "Бүгін"))
    put(261L, mapOf("main" to "7 days", "en" to "7 days", "ru" to "7 дней", "kk" to "7 күн"))
    put(262L, mapOf("main" to "30 days", "en" to "30 days", "ru" to "30 дней", "kk" to "30 күн"))
    put(263L, mapOf("main" to "In cart", "en" to "In cart", "ru" to "В корзине", "kk" to "Себетте"))
    put(264L, mapOf("main" to "Available", "en" to "Available", "ru" to "Доступно", "kk" to "Қолжетімді"))
    put(265L, mapOf("main" to "Active batch", "en" to "Active batch", "ru" to "Активная партия", "kk" to "Белсенді партия"))
    put(266L, mapOf("main" to "Note", "en" to "Note", "ru" to "Заметка", "kk" to "Ескертпе"))
    put(267L, mapOf("main" to "Batch prices", "en" to "Batch prices", "ru" to "Цены партии", "kk" to "Партия бағалары"))
    put(268L, mapOf("main" to "Currency", "en" to "Currency", "ru" to "Валюта", "kk" to "Валюта"))
    put(269L, mapOf("main" to "Select currency", "en" to "Select currency", "ru" to "Выберите валюту", "kk" to "Валютаны таңдаңыз"))
    put(270L, mapOf("main" to "Unit", "en" to "Unit", "ru" to "Единица", "kk" to "Бірлік"))
    put(271L, mapOf("main" to "Quantity", "en" to "Quantity", "ru" to "Количество", "kk" to "Саны"))
    put(272L, mapOf("main" to "Save the goods item first, then supplier orders can be attached to it.", "en" to "Save the goods item first, then supplier orders can be attached to it.", "ru" to "Сначала сохраните товар, затем к нему можно будет прикреплять заказы поставщикам.", "kk" to "Алдымен тауарды сақтаңыз, содан кейін оған жеткізуші тапсырыстарын байланыстыруға болады."))
    put(273L, mapOf("main" to "Supplier orders are ready as a tab destination. The order creation and receiving form is the next safe layer to connect.", "en" to "Supplier orders are ready as a tab destination. The order creation and receiving form is the next safe layer to connect.", "ru" to "Вкладка заказов поставщикам уже готова. Следующий безопасный слой — форма создания и приёмки заказа.", "kk" to "Жеткізуші тапсырыстары қойындысы дайын. Келесі қауіпсіз қабат — тапсырыс жасау және қабылдау формасы."))
    put(274L, mapOf("main" to "Set the minimum amount required for the wholesale sale method. Leave wholesale price empty to disable it.", "en" to "Set the minimum amount required for the wholesale sale method. Leave wholesale price empty to disable it.", "ru" to "Укажите минимальное количество для продажи оптом. Оставьте оптовую цену пустой, чтобы отключить опт.", "kk" to "Көтерме сату үшін ең аз санды көрсетіңіз. Көтерме бағаны өшіру үшін оны бос қалдырыңыз."))
    put(275L, mapOf("main" to "No returns in this period", "en" to "No returns in this period", "ru" to "За этот период возвратов нет", "kk" to "Бұл кезеңде қайтарым жоқ"))
    put(276L, mapOf("main" to "No supply transactions in this period", "en" to "No supply transactions in this period", "ru" to "За этот период поставок нет", "kk" to "Бұл кезеңде жеткізу операциялары жоқ"))
    put(277L, mapOf("main" to "Current amount", "en" to "Current amount", "ru" to "Текущая сумма", "kk" to "Ағымдағы сома"))
    put(278L, mapOf("main" to "Extracted", "en" to "Extracted", "ru" to "Изъято", "kk" to "Алынды"))
    put(279L, mapOf("main" to "Extractions", "en" to "Extractions", "ru" to "Изъятия", "kk" to "Алу операциялары"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart14() {
    put(280L, mapOf("main" to "Active items", "en" to "Active items", "ru" to "Активные товары", "kk" to "Белсенді тауарлар"))
    put(281L, mapOf("main" to "Inactive items", "en" to "Inactive items", "ru" to "Неактивные товары", "kk" to "Белсенді емес тауарлар"))
    put(282L, mapOf("main" to "All stock items", "en" to "All stock items", "ru" to "Все товары склада", "kk" to "Қоймадағы барлық тауарлар"))
    put(283L, mapOf("main" to "Quick-sale items", "en" to "Quick-sale items", "ru" to "Быстрые товары", "kk" to "Жылдам сатылатын тауарлар"))
    put(284L, mapOf("main" to "Active batches", "en" to "Active batches", "ru" to "Активные партии", "kk" to "Белсенді партиялар"))
    put(285L, mapOf("main" to "Inactive batches", "en" to "Inactive batches", "ru" to "Неактивные партии", "kk" to "Белсенді емес партиялар"))
    put(286L, mapOf("main" to "Select debtor", "en" to "Select debtor", "ru" to "Выберите должника", "kk" to "Борышкерді таңдаңыз"))
    put(287L, mapOf("main" to "Or add debtor", "en" to "Or add debtor", "ru" to "Или добавьте должника", "kk" to "Немесе борышкер қосыңыз"))
    put(288L, mapOf("main" to "Debtor form", "en" to "Debtor form", "ru" to "Форма должника", "kk" to "Борышкер түрі"))
    put(289L, mapOf("main" to "Individual", "en" to "Individual", "ru" to "Физическое лицо", "kk" to "Жеке тұлға"))
    put(290L, mapOf("main" to "Company", "en" to "Company", "ru" to "Компания", "kk" to "Компания"))
    put(291L, mapOf("main" to "Company name", "en" to "Company name", "ru" to "Название компании", "kk" to "Компания атауы"))
    put(292L, mapOf("main" to "Company ID / BIN", "en" to "Company ID / BIN", "ru" to "БИН компании", "kk" to "Компания БСН"))
    put(293L, mapOf("main" to "ID number", "en" to "ID number", "ru" to "Номер удостоверения", "kk" to "ЖСН/құжат нөмірі"))
    put(294L, mapOf("main" to "ID number optional", "en" to "ID number optional", "ru" to "Номер удостоверения необязательно", "kk" to "Құжат нөмірі міндетті емес"))
    put(295L, mapOf("main" to "Debt final repay date optional", "en" to "Debt final repay date optional", "ru" to "Крайняя дата возврата долга необязательно", "kk" to "Қарызды өтеудің соңғы күні міндетті емес"))
    put(296L, mapOf("main" to "Connected module", "en" to "Connected module", "ru" to "Подключённый модуль", "kk" to "Қосылған модуль"))
    put(297L, mapOf("main" to "Supplier analytics", "en" to "Supplier analytics", "ru" to "Аналитика поставщиков", "kk" to "Жеткізуші аналитикасы"))
    put(298L, mapOf("main" to "Use accepted goods grouped by supplier here", "en" to "Use accepted goods grouped by supplier here", "ru" to "Здесь можно использовать принятые товары, сгруппированные по поставщику", "kk" to "Мұнда жеткізуші бойынша топталған қабылданған тауарларды қолдануға болады"))
    put(299L, mapOf("main" to "Useful metric", "en" to "Useful metric", "ru" to "Полезная метрика", "kk" to "Пайдалы көрсеткіш"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart15() {
    put(300L, mapOf("main" to "Acceptance total", "en" to "Acceptance total", "ru" to "Итого приёмки", "kk" to "Қабылдау жиыны"))
    put(301L, mapOf("main" to "Worker analytics", "en" to "Worker analytics", "ru" to "Аналитика сотрудников", "kk" to "Қызметкер аналитикасы"))
    put(302L, mapOf("main" to "Use workshifts, sales per worker, and salary here", "en" to "Use workshifts, sales per worker, and salary here", "ru" to "Здесь можно использовать смены, продажи по сотрудникам и зарплату", "kk" to "Мұнда ауысымдарды, қызметкер бойынша сатылымды және жалақыны қолдануға болады"))
    put(303L, mapOf("main" to "Revenue / worker", "en" to "Revenue / worker", "ru" to "Выручка / сотрудник", "kk" to "Түсім / қызметкер"))
    put(304L, mapOf("main" to "Good for cashier performance later", "en" to "Good for cashier performance later", "ru" to "Позже удобно для оценки кассиров", "kk" to "Кейін кассир жұмысына баға беруге ыңғайлы"))
    put(305L, mapOf("main" to "Use predicted expiration", "en" to "Use predicted expiration", "ru" to "Использовать рассчитанный срок годности", "kk" to "Есептелген жарамдылық мерзімін қолдану"))
    put(306L, mapOf("main" to "Manufactured date", "en" to "Manufactured date", "ru" to "Дата производства", "kk" to "Өндірілген күні"))
    put(307L, mapOf("main" to "Add note translation", "en" to "Add note translation", "ru" to "Добавить перевод заметки", "kk" to "Ескертпе аудармасын қосу"))
    put(308L, mapOf("main" to "Debt payment receipt", "en" to "Debt payment receipt", "ru" to "Чек оплаты долга", "kk" to "Қарыз төлемінің чегі"))
    put(309L, mapOf("main" to "Type", "en" to "Type", "ru" to "Тип", "kk" to "Түрі"))
    put(310L, mapOf("main" to "Full", "en" to "Full", "ru" to "Полностью", "kk" to "Толық"))
    put(311L, mapOf("main" to "Partial", "en" to "Partial", "ru" to "Частично", "kk" to "Ішінара"))
    put(312L, mapOf("main" to "Debt before", "en" to "Debt before", "ru" to "Долг до оплаты", "kk" to "Төлемге дейінгі қарыз"))
    put(313L, mapOf("main" to "Debt remaining", "en" to "Debt remaining", "ru" to "Остаток долга", "kk" to "Қалған қарыз"))
    put(314L, mapOf("main" to "Final due date", "en" to "Final due date", "ru" to "Конечная дата оплаты", "kk" to "Соңғы төлем күні"))
    put(315L, mapOf("main" to "Original cart content", "en" to "Original cart content", "ru" to "Исходный состав корзины", "kk" to "Бастапқы себет құрамы"))
    put(316L, mapOf("main" to "Transaction", "en" to "Transaction", "ru" to "Транзакция", "kk" to "Транзакция"))
    put(317L, mapOf("main" to "Planned next payments", "en" to "Planned next payments", "ru" to "Следующие плановые платежи", "kk" to "Келесі жоспарлы төлемдер"))
    put(318L, mapOf("main" to "No date", "en" to "No date", "ru" to "Без даты", "kk" to "Күні жоқ"))
    put(319L, mapOf("main" to "Close", "en" to "Close", "ru" to "Закрыть", "kk" to "Жабу"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart16() {
    put(320L, mapOf("main" to "Due", "en" to "Due", "ru" to "Срок", "kk" to "Мерзімі"))
    put(321L, mapOf("main" to "Final date", "en" to "Final date", "ru" to "Конечная дата", "kk" to "Соңғы күн"))
    put(322L, mapOf("main" to "Interest", "en" to "Interest", "ru" to "Процент", "kk" to "Пайыз"))
    put(323L, mapOf("main" to "Debt with interest", "en" to "Debt with interest", "ru" to "Долг с процентом", "kk" to "Пайызбен қарыз"))
    put(324L, mapOf("main" to "Plan", "en" to "Plan", "ru" to "План", "kk" to "Жоспар"))
    put(325L, mapOf("main" to "Paid records", "en" to "Paid records", "ru" to "Записи оплат", "kk" to "Төлем жазбалары"))
    put(326L, mapOf("main" to "Edit / pay debt", "en" to "Edit / pay debt", "ru" to "Изменить / оплатить долг", "kk" to "Қарызды өзгерту / төлеу"))
    put(327L, mapOf("main" to "per", "en" to "per", "ru" to "за", "kk" to "әр"))
    put(328L, mapOf("main" to "est.", "en" to "est.", "ru" to "примерно", "kk" to "шамамен"))
    put(329L, mapOf("main" to "open payments", "en" to "open payments", "ru" to "открытых платежей", "kk" to "ашық төлем"))
    put(330L, mapOf("main" to "Remaining", "en" to "Remaining", "ru" to "Осталось", "kk" to "Қалды"))
    put(331L, mapOf("main" to "Or add debtor for this transaction", "en" to "Or add debtor for this transaction", "ru" to "Или добавьте должника для этой транзакции", "kk" to "Немесе осы транзакцияға борышкер қосыңыз"))
    put(332L, mapOf("main" to "Generic expiration period", "en" to "Generic expiration period", "ru" to "Базовый срок годности", "kk" to "Негізгі жарамдылық мерзімі"))
    put(333L, mapOf("main" to "Save the goods item first, then supplier prices will appear.", "en" to "Save the goods item first, then supplier prices will appear.", "ru" to "Сначала сохраните товар, затем появятся цены поставщиков.", "kk" to "Алдымен тауарды сақтаңыз, содан кейін жеткізуші бағалары пайда болады."))
    put(334L, mapOf("main" to "Add / update supplier price", "en" to "Add / update supplier price", "ru" to "Добавить / обновить цену поставщика", "kk" to "Жеткізуші бағасын қосу / жаңарту"))
    put(335L, mapOf("main" to "No suppliers yet. Add suppliers first, then connect them to this goods item.", "en" to "No suppliers yet. Add suppliers first, then connect them to this goods item.", "ru" to "Поставщиков пока нет. Сначала добавьте поставщиков, затем свяжите их с этим товаром.", "kk" to "Әзірге жеткізушілер жоқ. Алдымен жеткізушілерді қосып, кейін оларды осы тауармен байланыстырыңыз."))
    put(336L, mapOf("main" to "Supplier goods name / article", "en" to "Supplier goods name / article", "ru" to "Название / артикул у поставщика", "kk" to "Жеткізушідегі атау / артикул"))
    put(337L, mapOf("main" to "Min order", "en" to "Min order", "ru" to "Минимальный заказ", "kk" to "Ең аз тапсырыс"))
    put(338L, mapOf("main" to "Package qty", "en" to "Package qty", "ru" to "Количество в упаковке", "kk" to "Қаптамадағы саны"))
    put(339L, mapOf("main" to "Save supplier price", "en" to "Save supplier price", "ru" to "Сохранить цену поставщика", "kk" to "Жеткізуші бағасын сақтау"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart17() {
    put(340L, mapOf("main" to "Prices", "en" to "Prices", "ru" to "Цены", "kk" to "Бағалар"))
    put(341L, mapOf("main" to "Batch data", "en" to "Batch data", "ru" to "Данные партии", "kk" to "Партия деректері"))
    put(342L, mapOf("main" to "Delivered", "en" to "Delivered", "ru" to "Поставлено", "kk" to "Жеткізілді"))
    put(343L, mapOf("main" to "Made", "en" to "Made", "ru" to "Изготовлено", "kk" to "Жасалған"))
    put(344L, mapOf("main" to "Shelf position", "en" to "Shelf position", "ru" to "Место на полке", "kk" to "Сөредегі орны"))
    put(345L, mapOf("main" to "Priority", "en" to "Priority", "ru" to "Приоритет", "kk" to "Басымдық"))
    put(346L, mapOf("main" to "Discounts", "en" to "Discounts", "ru" to "Скидки", "kk" to "Жеңілдіктер"))
    put(347L, mapOf("main" to "Save debtor", "en" to "Save debtor", "ru" to "Сохранить должника", "kk" to "Борышкерді сақтау"))
    put(348L, mapOf("main" to "Add planned payment", "en" to "Add planned payment", "ru" to "Добавить плановый платёж", "kk" to "Жоспарлы төлем қосу"))
    put(349L, mapOf("main" to "No payment records yet", "en" to "No payment records yet", "ru" to "Записей оплат пока нет", "kk" to "Әзірге төлем жазбалары жоқ"))
    put(350L, mapOf("main" to "Pay partial", "en" to "Pay partial", "ru" to "Оплатить частично", "kk" to "Ішінара төлеу"))
    put(351L, mapOf("main" to "Pay full", "en" to "Pay full", "ru" to "Оплатить полностью", "kk" to "Толық төлеу"))
    put(352L, mapOf("main" to "If everything is correct the user will be invited", "en" to "If everything is correct the user will be invited", "ru" to "Если всё верно, пользователь получит приглашение", "kk" to "Бәрі дұрыс болса, пайдаланушы шақырылады"))
    put(353L, mapOf("main" to "Goods item was not found", "en" to "Goods item was not found", "ru" to "Товар не найден", "kk" to "Тауар табылмады"))
    put(354L, mapOf("main" to "Pay", "en" to "Pay", "ru" to "Оплата", "kk" to "Төлеу"))
    put(355L, mapOf("main" to "Edit debt", "en" to "Edit debt", "ru" to "Изменить долг", "kk" to "Қарызды өзгерту"))
    put(356L, mapOf("main" to "Payment plan", "en" to "Payment plan", "ru" to "План платежей", "kk" to "Төлем жоспары"))
    put(357L, mapOf("main" to "Cash + cashless", "en" to "Cash + cashless", "ru" to "Наличные + безнал", "kk" to "Қолма-қол + қолма-қолсыз"))
    put(358L, mapOf("main" to "Transactions", "en" to "Transactions", "ru" to "Транзакции", "kk" to "Транзакциялар"))
    put(359L, mapOf("main" to "Average transaction", "en" to "Average transaction", "ru" to "Средняя транзакция", "kk" to "Орташа транзакция"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart18() {
    put(360L, mapOf("main" to "Old Android version reused supplier screen from analytics", "en" to "Old Android version reused supplier screen from analytics", "ru" to "Старая Android-версия использовала экран поставщиков из аналитики", "kk" to "Ескі Android нұсқасы аналитикадағы жеткізушілер экранын қолданған"))
    put(361L, mapOf("main" to "Shelf", "en" to "Shelf", "ru" to "Полка", "kk" to "Сөре"))
    put(362L, mapOf("main" to "Debt amount", "en" to "Debt amount", "ru" to "Сумма долга", "kk" to "Қарыз сомасы"))
    put(363L, mapOf("main" to "Period", "en" to "Period", "ru" to "Период", "kk" to "Кезең"))
    put(364L, mapOf("main" to "Planned amount", "en" to "Planned amount", "ru" to "Плановая сумма", "kk" to "Жоспарланған сома"))
    put(365L, mapOf("main" to "Goods item", "en" to "Goods item", "ru" to "Товар", "kk" to "Тауар"))
    put(366L, mapOf("main" to "Debt due", "en" to "Debt due", "ru" to "Срок долга", "kk" to "Қарыз мерзімі"))
    put(367L, mapOf("main" to "Day", "en" to "Day", "ru" to "День", "kk" to "Күн"))
    put(368L, mapOf("main" to "Week", "en" to "Week", "ru" to "Неделя", "kk" to "Апта"))
    put(369L, mapOf("main" to "Month", "en" to "Month", "ru" to "Месяц", "kk" to "Ай"))
    put(370L, mapOf("main" to "Year", "en" to "Year", "ru" to "Год", "kk" to "Жыл"))
    put(371L, mapOf("main" to "Remaining after open planned payments", "en" to "Remaining after open planned payments", "ru" to "Остаток после открытых плановых платежей", "kk" to "Ашық жоспарлы төлемдерден кейінгі қалдық"))
    put(372L, mapOf("main" to "Planned payment date", "en" to "Planned payment date", "ru" to "Дата планового платежа", "kk" to "Жоспарлы төлем күні"))
    put(373L, mapOf("main" to "Plan note", "en" to "Plan note", "ru" to "Заметка к плану", "kk" to "Жоспар ескертпесі"))
    put(374L, mapOf("main" to "Planned", "en" to "Planned", "ru" to "Плановый", "kk" to "Жоспарланған"))
    put(375L, mapOf("main" to "Percent", "en" to "Percent", "ru" to "Процент", "kk" to "Пайыз"))
    put(376L, mapOf("main" to "Original debt transactions", "en" to "Original debt transactions", "ru" to "Исходные долговые транзакции", "kk" to "Бастапқы қарыз транзакциялары"))
    put(377L, mapOf("main" to "Payment history", "en" to "Payment history", "ru" to "История оплат", "kk" to "Төлем тарихы"))
    put(378L, mapOf("main" to "Before / after", "en" to "Before / after", "ru" to "До / после", "kk" to "Дейін / кейін"))
    put(379L, mapOf("main" to "Interest estimate", "en" to "Interest estimate", "ru" to "Оценка процентов", "kk" to "Пайыз болжамы"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart19() {
    put(380L, mapOf("main" to "Payable now", "en" to "Payable now", "ru" to "К оплате сейчас", "kk" to "Қазір төленеді"))
    put(381L, mapOf("main" to "Delete debtor?", "en" to "Delete debtor?", "ru" to "Удалить должника?", "kk" to "Борышкерді жою керек пе?"))
    put(382L, mapOf("main" to "Search transactions", "en" to "Search transactions", "ru" to "Поиск транзакций", "kk" to "Транзакцияларды іздеу"))
    put(383L, mapOf("main" to "All transaction types", "en" to "All transaction types", "ru" to "Все типы транзакций", "kk" to "Барлық транзакция түрлері"))
    put(384L, mapOf("main" to "Start date", "en" to "Start date", "ru" to "Дата начала", "kk" to "Басталу күні"))
    put(385L, mapOf("main" to "End date", "en" to "End date", "ru" to "Дата окончания", "kk" to "Аяқталу күні"))
    put(386L, mapOf("main" to "Custom period", "en" to "Custom period", "ru" to "Произвольный период", "kk" to "Таңдалған кезең"))
    put(387L, mapOf("main" to "This month", "en" to "This month", "ru" to "Этот месяц", "kk" to "Осы ай"))
    put(388L, mapOf("main" to "This year", "en" to "This year", "ru" to "Этот год", "kk" to "Осы жыл"))
    put(389L, mapOf("main" to "Apply period", "en" to "Apply period", "ru" to "Применить период", "kk" to "Кезеңді қолдану"))
    put(390L, mapOf("main" to "Clear period", "en" to "Clear period", "ru" to "Очистить период", "kk" to "Кезеңді тазалау"))
    put(391L, mapOf("main" to "No transactions in this period", "en" to "No transactions in this period", "ru" to "За этот период транзакций нет", "kk" to "Бұл кезеңде транзакциялар жоқ"))
    put(392L, mapOf("main" to "Items", "en" to "Items", "ru" to "Товары", "kk" to "Тауарлар"))
    put(393L, mapOf("main" to "Payment details", "en" to "Payment details", "ru" to "Детали оплаты", "kk" to "Төлем мәліметтері"))
    put(394L, mapOf("main" to "Open receipt", "en" to "Open receipt", "ru" to "Открыть чек", "kk" to "Чекті ашу"))
    put(395L, mapOf("main" to "Period", "en" to "Period", "ru" to "Период", "kk" to "Кезең"))
    put(396L, mapOf("main" to "From", "en" to "From", "ru" to "С", "kk" to "Бастап"))
    put(397L, mapOf("main" to "To", "en" to "To", "ru" to "По", "kk" to "Дейін"))
    put(398L, mapOf("main" to "Newest first", "en" to "Newest first", "ru" to "Сначала новые", "kk" to "Алдымен жаңалары"))
    put(399L, mapOf("main" to "Oldest first", "en" to "Oldest first", "ru" to "Сначала старые", "kk" to "Алдымен ескілері"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart20() {
    put(400L, mapOf("main" to "Highest total", "en" to "Highest total", "ru" to "Сначала крупные суммы", "kk" to "Алдымен жоғары сома"))
    put(401L, mapOf("main" to "Lowest total", "en" to "Lowest total", "ru" to "Сначала малые суммы", "kk" to "Алдымен төмен сома"))
    put(402L, mapOf("main" to "Calendar", "en" to "Calendar", "ru" to "Календарь", "kk" to "Күнтізбе"))
    put(403L, mapOf("main" to "Pick start date", "en" to "Pick start date", "ru" to "Выберите дату начала", "kk" to "Басталу күнін таңдаңыз"))
    put(404L, mapOf("main" to "Pick end date", "en" to "Pick end date", "ru" to "Выберите дату окончания", "kk" to "Аяқталу күнін таңдаңыз"))
    put(405L, mapOf("main" to "Tap a day to set the date", "en" to "Tap a day to set the date", "ru" to "Нажмите на день, чтобы выбрать дату", "kk" to "Күнді таңдау үшін басыңыз"))
    put(406L, mapOf("main" to "Transaction details", "en" to "Transaction details", "ru" to "Детали транзакции", "kk" to "Транзакция мәліметтері"))
    put(407L, mapOf("main" to "Line items", "en" to "Line items", "ru" to "Позиции чека", "kk" to "Чек позициялары"))
    put(408L, mapOf("main" to "Receipt operations", "en" to "Receipt operations", "ru" to "Операции с чеком", "kk" to "Чек әрекеттері"))
    put(409L, mapOf("main" to "Refresh history", "en" to "Refresh history", "ru" to "Обновить историю", "kk" to "Тарихты жаңарту"))
    put(410L, mapOf("main" to "Sort", "en" to "Sort", "ru" to "Сортировка", "kk" to "Сұрыптау"))
    put(411L, mapOf("main" to "Mon", "en" to "Mon", "ru" to "Пн", "kk" to "Дс"))
    put(412L, mapOf("main" to "Tue", "en" to "Tue", "ru" to "Вт", "kk" to "Сс"))
    put(413L, mapOf("main" to "Wed", "en" to "Wed", "ru" to "Ср", "kk" to "Ср"))
    put(414L, mapOf("main" to "Thu", "en" to "Thu", "ru" to "Чт", "kk" to "Бс"))
    put(415L, mapOf("main" to "Fri", "en" to "Fri", "ru" to "Пт", "kk" to "Жм"))
    put(416L, mapOf("main" to "Sat", "en" to "Sat", "ru" to "Сб", "kk" to "Сн"))
    put(417L, mapOf("main" to "Sun", "en" to "Sun", "ru" to "Вс", "kk" to "Жс"))
    put(418L, mapOf("main" to "Receipt preview", "en" to "Receipt preview", "ru" to "Предпросмотр чека", "kk" to "Чекті алдын ала қарау"))
    put(419L, mapOf("main" to "Transaction type", "en" to "Transaction type", "ru" to "Тип транзакции", "kk" to "Транзакция түрі"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart21() {
    put(420L, mapOf("main" to "Paid by cash", "en" to "Paid by cash", "ru" to "Оплачено наличными", "kk" to "Қолма-қол төленді"))
    put(421L, mapOf("main" to "Paid cashless", "en" to "Paid cashless", "ru" to "Оплачено безналично", "kk" to "Қолма-қолсыз төленді"))
    put(422L, mapOf("main" to "Debt part", "en" to "Debt part", "ru" to "Часть в долг", "kk" to "Қарыз бөлігі"))
    put(423L, mapOf("main" to "Cash / card / debt", "en" to "Cash / card / debt", "ru" to "Наличные / безнал / долг", "kk" to "Қолма-қол / карта / қарыз"))
    put(424L, mapOf("main" to "No matching transactions", "en" to "No matching transactions", "ru" to "Подходящих транзакций нет", "kk" to "Сәйкес транзакциялар жоқ"))
    put(425L, mapOf("main" to "All period", "en" to "All period", "ru" to "Весь период", "kk" to "Барлық кезең"))
    put(426L, mapOf("main" to "Transaction total", "en" to "Transaction total", "ru" to "Сумма транзакции", "kk" to "Транзакция сомасы"))
    put(427L, mapOf("main" to "First page", "en" to "First page", "ru" to "В начало", "kk" to "Басына"))
    put(428L, mapOf("main" to "Last page", "en" to "Last page", "ru" to "В конец", "kk" to "Соңына"))
    put(429L, mapOf("main" to "Active workers", "en" to "Active workers", "ru" to "Активные сотрудники", "kk" to "Белсенді қызметкерлер"))
    put(430L, mapOf("main" to "Employees connected to this store", "en" to "Employees connected to this store", "ru" to "Сотрудники, привязанные к этому магазину", "kk" to "Осы дүкенге қосылған қызметкерлер"))
    put(431L, mapOf("main" to "Admins", "en" to "Admins", "ru" to "Администраторы", "kk" to "Әкімшілер"))
    put(432L, mapOf("main" to "Standard workers", "en" to "Standard workers", "ru" to "Стандартные сотрудники", "kk" to "Стандартты қызметкерлер"))
    put(433L, mapOf("main" to "Cash physically expected in the drawer", "en" to "Cash physically expected in the drawer", "ru" to "Наличные, которые должны быть в кассе", "kk" to "Кассада болуы тиіс қолма-қол ақша"))
    put(434L, mapOf("main" to "Cash from sales", "en" to "Cash from sales", "ru" to "Наличные от продаж", "kk" to "Сатылымнан түскен қолма-қол ақша"))
    put(435L, mapOf("main" to "Cash paid for returns", "en" to "Cash paid for returns", "ru" to "Наличные, выданные по возвратам", "kk" to "Қайтарымдарға берілген қолма-қол ақша"))
    put(436L, mapOf("main" to "Cash events", "en" to "Cash events", "ru" to "Операции с наличными", "kk" to "Қолма-қол ақша оқиғалары"))
    put(437L, mapOf("main" to "Extract cash", "en" to "Extract cash", "ru" to "Изъять наличные", "kk" to "Қолма-қол ақшаны алу"))
    put(438L, mapOf("main" to "You do not have permission to extract cash from this register", "en" to "You do not have permission to extract cash from this register", "ru" to "У вас нет прав изымать наличные из этой кассы", "kk" to "Бұл кассадан қолма-қол ақша алуға рұқсатыңыз жоқ"))
    put(439L, mapOf("main" to "Amount to extract", "en" to "Amount to extract", "ru" to "Сумма изъятия", "kk" to "Алынатын сома"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart22() {
    put(440L, mapOf("main" to "Extract", "en" to "Extract", "ru" to "Изъять", "kk" to "Алу"))
    put(441L, mapOf("main" to "Extraction note", "en" to "Extraction note", "ru" to "Заметка к изъятию", "kk" to "Алу туралы ескерту"))
    put(442L, mapOf("main" to "No cash register events in this period", "en" to "No cash register events in this period", "ru" to "Нет операций с кассой за этот период", "kk" to "Бұл кезеңде касса оқиғалары жоқ"))
    put(443L, mapOf("main" to "Balance", "en" to "Balance", "ru" to "Баланс", "kk" to "Баланс"))
    put(444L, mapOf("main" to "Sale cash received", "en" to "Sale cash received", "ru" to "Наличные от продажи", "kk" to "Сатылымнан қолма-қол ақша түсті"))
    put(445L, mapOf("main" to "Return cash paid out", "en" to "Return cash paid out", "ru" to "Наличные выданы по возврату", "kk" to "Қайтарым бойынша қолма-қол ақша берілді"))
    put(446L, mapOf("main" to "Cash extraction", "en" to "Cash extraction", "ru" to "Изъятие наличных", "kk" to "Қолма-қол ақшаны алу"))
    put(447L, mapOf("main" to "Cash register adjustment", "en" to "Cash register adjustment", "ru" to "Корректировка кассы", "kk" to "Кассаны түзету"))
    put(448L, mapOf("main" to "Admin", "en" to "Admin", "ru" to "Администратор", "kk" to "Әкімші"))
    put(449L, mapOf("main" to "Owner", "en" to "Owner", "ru" to "Владелец", "kk" to "Иесі"))
    put(450L, mapOf("main" to "Standard", "en" to "Standard", "ru" to "Стандартный", "kk" to "Стандартты"))
    put(451L, mapOf("main" to "Sale transaction", "en" to "Sale transaction", "ru" to "Продажа", "kk" to "Сату операциясы"))
    put(452L, mapOf("main" to "Return transaction", "en" to "Return transaction", "ru" to "Возврат", "kk" to "Қайтару операциясы"))
    put(453L, mapOf("main" to "Supply transaction", "en" to "Supply transaction", "ru" to "Поставка / приёмка", "kk" to "Жеткізу / қабылдау операциясы"))
    put(454L, mapOf("main" to "View stock", "en" to "View stock", "ru" to "Просмотр склада", "kk" to "Қойманы көру"))
    put(455L, mapOf("main" to "Edit stock", "en" to "Edit stock", "ru" to "Редактирование склада", "kk" to "Қойманы өңдеу"))
    put(456L, mapOf("main" to "View transaction history", "en" to "View transaction history", "ru" to "Просмотр истории транзакций", "kk" to "Транзакция тарихын көру"))
    put(457L, mapOf("main" to "View analytics", "en" to "View analytics", "ru" to "Просмотр аналитики", "kk" to "Аналитиканы көру"))
    put(458L, mapOf("main" to "View cash register", "en" to "View cash register", "ru" to "Просмотр кассы", "kk" to "Кассаны көру"))
    put(459L, mapOf("main" to "Extract cash", "en" to "Extract cash", "ru" to "Изъятие наличных", "kk" to "Қолма-қол ақша алу"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart23() {
    put(460L, mapOf("main" to "View workers", "en" to "View workers", "ru" to "Просмотр сотрудников", "kk" to "Қызметкерлерді көру"))
    put(461L, mapOf("main" to "Manage workers", "en" to "Manage workers", "ru" to "Управление сотрудниками", "kk" to "Қызметкерлерді басқару"))
    put(462L, mapOf("main" to "Manage store", "en" to "Manage store", "ru" to "Управление магазином", "kk" to "Дүкенді басқару"))
    put(463L, mapOf("main" to "Cashier/basic worker", "en" to "Cashier/basic worker", "ru" to "Кассир / базовый сотрудник", "kk" to "Кассир / негізгі қызметкер"))
    put(464L, mapOf("main" to "Can manage most store operations", "en" to "Can manage most store operations", "ru" to "Может управлять большинством операций магазина", "kk" to "Дүкен операцияларының көбін басқара алады"))
    put(465L, mapOf("main" to "Requested", "en" to "Requested", "ru" to "Запрошено", "kk" to "Сұралды"))
    put(466L, mapOf("main" to "Role", "en" to "Role", "ru" to "Роль", "kk" to "Рөл"))
    put(467L, mapOf("main" to "Allowed actions", "en" to "Allowed actions", "ru" to "Разрешённые действия", "kk" to "Рұқсат етілген әрекеттер"))
    put(468L, mapOf("main" to "Accept", "en" to "Accept", "ru" to "Принять", "kk" to "Қабылдау"))
    put(469L, mapOf("main" to "Decline", "en" to "Decline", "ru" to "Отклонить", "kk" to "Қабылдамау"))
    put(470L, mapOf("main" to "Accepted", "en" to "Accepted", "ru" to "Принят", "kk" to "Қабылданды"))
    put(471L, mapOf("main" to "Save permissions", "en" to "Save permissions", "ru" to "Сохранить права", "kk" to "Рұқсаттарды сақтау"))
    put(472L, mapOf("main" to "My work", "en" to "My work", "ru" to "Моя работа", "kk" to "Менің жұмысым"))
    put(473L, mapOf("main" to "Store workers", "en" to "Store workers", "ru" to "Сотрудники магазина", "kk" to "Дүкен қызметкерлері"))
    put(474L, mapOf("main" to "Requests", "en" to "Requests", "ru" to "Заявки", "kk" to "Өтінімдер"))
    put(475L, mapOf("main" to "Request employment in a store", "en" to "Request employment in a store", "ru" to "Подать заявку на работу в магазине", "kk" to "Дүкенге жұмысқа өтінім беру"))
    put(476L, mapOf("main" to "Enter store public ID", "en" to "Enter store public ID", "ru" to "Введите публичный ID магазина", "kk" to "Дүкеннің жария ID-ін енгізіңіз"))
    put(477L, mapOf("main" to "Send request", "en" to "Send request", "ru" to "Отправить заявку", "kk" to "Өтінім жіберу"))
    put(478L, mapOf("main" to "Managed stores", "en" to "Managed stores", "ru" to "Управляемые магазины", "kk" to "Басқарылатын дүкендер"))
    put(479L, mapOf("main" to "You are not employed in other stores yet", "en" to "You are not employed in other stores yet", "ru" to "Вы пока не работаете в других магазинах", "kk" to "Сіз әзірге басқа дүкендерде жұмыс істемейсіз"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart24() {
    put(480L, mapOf("main" to "My employment requests", "en" to "My employment requests", "ru" to "Мои заявки на работу", "kk" to "Менің жұмысқа өтінімдерім"))
    put(481L, mapOf("main" to "No employment requests yet", "en" to "No employment requests yet", "ru" to "Заявок на работу пока нет", "kk" to "Әзірге жұмысқа өтінімдер жоқ"))
    put(482L, mapOf("main" to "Status", "en" to "Status", "ru" to "Статус", "kk" to "Күй"))
    put(483L, mapOf("main" to "You do not have permission to view workers in this store", "en" to "You do not have permission to view workers in this store", "ru" to "У вас нет прав просматривать сотрудников этого магазина", "kk" to "Бұл дүкеннің қызметкерлерін көруге рұқсатыңыз жоқ"))
    put(484L, mapOf("main" to "No workers in this store yet", "en" to "No workers in this store yet", "ru" to "В этом магазине пока нет сотрудников", "kk" to "Бұл дүкенде әзірге қызметкерлер жоқ"))
    put(485L, mapOf("main" to "Incoming employment requests", "en" to "Incoming employment requests", "ru" to "Входящие заявки на работу", "kk" to "Кіріс жұмыс өтінімдері"))
    put(486L, mapOf("main" to "Only store owners and worker managers can accept employment requests", "en" to "Only store owners and worker managers can accept employment requests", "ru" to "Только владелец магазина и управляющие сотрудниками могут принимать заявки", "kk" to "Жұмыс өтінімдерін тек дүкен иелері мен қызметкер менеджерлері қабылдай алады"))
    put(487L, mapOf("main" to "No incoming employment requests", "en" to "No incoming employment requests", "ru" to "Нет входящих заявок на работу", "kk" to "Кіріс жұмыс өтінімдері жоқ"))
    put(488L, mapOf("main" to "Pending", "en" to "Pending", "ru" to "Ожидает", "kk" to "Күтуде"))
    put(489L, mapOf("main" to "My stores", "en" to "My stores", "ru" to "Мои магазины", "kk" to "Менің дүкендерім"))
    put(490L, mapOf("main" to "Owned by me", "en" to "Owned by me", "ru" to "Принадлежат мне", "kk" to "Маған тиесілі"))
    put(491L, mapOf("main" to "Managed by me", "en" to "Managed by me", "ru" to "Я администрирую", "kk" to "Мен басқарамын"))
    put(492L, mapOf("main" to "Declined", "en" to "Declined", "ru" to "Отклонена", "kk" to "Қабылданбады"))
    put(493L, mapOf("main" to "Public IDs", "en" to "Public IDs", "ru" to "Публичные ID", "kk" to "Жария ID-лер"))
    put(494L, mapOf("main" to "Public IDs are short safe codes for invitations and employment requests", "en" to "Public IDs are short safe codes for invitations and employment requests", "ru" to "Публичные ID — короткие безопасные коды для приглашений и заявок на работу", "kk" to "Жария ID — шақырулар мен жұмыс сұрауларына арналған қысқа қауіпсіз кодтар"))
    put(495L, mapOf("main" to "Store owner invite", "en" to "Store owner invite", "ru" to "Приглашение от владельца магазина", "kk" to "Дүкен иесінің шақыруы"))
    put(496L, mapOf("main" to "User invite ID", "en" to "User invite ID", "ru" to "ID пользователя для приглашения", "kk" to "Шақыруға арналған пайдаланушы ID-і"))
    put(497L, mapOf("main" to "Invitation accepted", "en" to "Invitation accepted", "ru" to "Приглашение принято", "kk" to "Шақыру қабылданды"))
    put(498L, mapOf("main" to "Invitation declined", "en" to "Invitation declined", "ru" to "Приглашение отклонено", "kk" to "Шақыру қабылданбады"))
    put(499L, mapOf("main" to "Worker invited", "en" to "Worker invited", "ru" to "Сотрудник приглашён", "kk" to "Қызметкер шақырылды"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart25() {
    put(500L, mapOf("main" to "Worker already invited", "en" to "Worker already invited", "ru" to "Сотрудник уже приглашён", "kk" to "Қызметкер бұрын шақырылған"))
    put(501L, mapOf("main" to "Copied to clipboard", "en" to "Copied to clipboard", "ru" to "Скопировано в буфер обмена", "kk" to "Алмасу буферіне көшірілді"))
    put(502L, mapOf("main" to "Public store ID", "en" to "Public store ID", "ru" to "Публичный ID магазина", "kk" to "Дүкеннің жария ID-і"))
    put(503L, mapOf("main" to "Copy", "en" to "Copy", "ru" to "Копировать", "kk" to "Көшіру"))
    put(504L, mapOf("main" to "Your public worker ID", "en" to "Your public worker ID", "ru" to "Ваш публичный ID сотрудника", "kk" to "Сіздің жария қызметкер ID-іңіз"))
    put(505L, mapOf("main" to "Invite worker", "en" to "Invite worker", "ru" to "Пригласить сотрудника", "kk" to "Қызметкерді шақыру"))
    put(506L, mapOf("main" to "Enter user public ID", "en" to "Enter user public ID", "ru" to "Введите публичный ID пользователя", "kk" to "Пайдаланушының жария ID-ін енгізіңіз"))
    put(507L, mapOf("main" to "Send invite", "en" to "Send invite", "ru" to "Отправить приглашение", "kk" to "Шақыру жіберу"))
    put(508L, mapOf("main" to "Invited", "en" to "Invited", "ru" to "Приглашён", "kk" to "Шақырылды"))
    put(509L, mapOf("main" to "Worker invitations", "en" to "Worker invitations", "ru" to "Приглашения сотрудников", "kk" to "Қызметкер шақырулары"))
    put(510L, mapOf("main" to "Accept invite", "en" to "Accept invite", "ru" to "Принять приглашение", "kk" to "Шақыруды қабылдау"))
    put(511L, mapOf("main" to "Decline invite", "en" to "Decline invite", "ru" to "Отклонить приглашение", "kk" to "Шақырудан бас тарту"))
    put(512L, mapOf("main" to "Sort by", "en" to "Sort by", "ru" to "Сортировать по", "kk" to "Бойынша сұрыптау"))
    put(513L, mapOf("main" to "Quantity", "en" to "Quantity", "ru" to "Количество", "kk" to "Саны"))
    put(514L, mapOf("main" to "Time added", "en" to "Time added", "ru" to "Время добавления", "kk" to "Қосылған уақыты"))
    put(515L, mapOf("main" to "Ascending", "en" to "Ascending", "ru" to "По возрастанию", "kk" to "Өсу ретімен"))
    put(516L, mapOf("main" to "Descending", "en" to "Descending", "ru" to "По убыванию", "kk" to "Кему ретімен"))
    put(517L, mapOf("main" to "No company form specified", "en" to "No company form specified", "ru" to "Форма компании не указана", "kk" to "Компания түрі көрсетілмеген"))
    put(518L, mapOf("main" to "Use this ID when a store owner invites you as a worker", "en" to "Use this ID when a store owner invites you as a worker", "ru" to "Используйте этот ID, когда владелец магазина приглашает вас как сотрудника", "kk" to "Дүкен иесі сізді қызметкер ретінде шақырғанда осы ID-ді пайдаланыңыз"))
    put(519L, mapOf("main" to "Use this ID when requesting employment", "en" to "Use this ID when requesting employment", "ru" to "Используйте этот ID для запроса трудоустройства", "kk" to "Жұмысқа сұрау жіберу үшін осы ID-ді пайдаланыңыз"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart26() {
    put(520L, mapOf("main" to "Address", "en" to "Address", "ru" to "Адрес", "kk" to "Мекенжай"))
    put(521L, mapOf("main" to "Enter address", "en" to "Enter address", "ru" to "Введите адрес", "kk" to "Мекенжайды енгізіңіз"))
    put(522L, mapOf("main" to "Address is required", "en" to "Address is required", "ru" to "Адрес обязателен", "kk" to "Мекенжай қажет"))
    put(523L, mapOf("main" to "Legal ID", "en" to "Legal ID", "ru" to "Юридический ID", "kk" to "Заңды ID"))
    put(524L, mapOf("main" to "Enter legal ID", "en" to "Enter legal ID", "ru" to "Введите юридический ID", "kk" to "Заңды ID енгізіңіз"))
    put(525L, mapOf("main" to "Legal ID format is invalid", "en" to "Legal ID format is invalid", "ru" to "Неверный формат юридического ID", "kk" to "Заңды ID пішімі қате"))
    put(526L, mapOf("main" to "Legal ID is selected by the store country", "en" to "Legal ID is selected by the store country", "ru" to "Тип юридического ID выбирается по стране магазина", "kk" to "Заңды ID түрі дүкен елі бойынша таңдалады"))
    put(527L, mapOf("main" to "Store name is required", "en" to "Store name is required", "ru" to "Название магазина обязательно", "kk" to "Дүкен атауы қажет"))
    put(528L, mapOf("main" to "Branch name is required", "en" to "Branch name is required", "ru" to "Название филиала обязательно", "kk" to "Филиал атауы қажет"))
    put(529L, mapOf("main" to "Parent store can be active as the main warehouse.", "en" to "Parent store can be active as the main warehouse.", "ru" to "Головной магазин можно сделать активным как главный склад.", "kk" to "Негізгі дүкенді басты қойма ретінде белсенді етуге болады."))
    put(530L, mapOf("main" to "Branch", "en" to "Branch", "ru" to "Филиал", "kk" to "Филиал"))
    put(531L, mapOf("main" to "Branch name", "en" to "Branch name", "ru" to "Название филиала", "kk" to "Филиал атауы"))
    put(532L, mapOf("main" to "Branches", "en" to "Branches", "ru" to "Филиалы", "kk" to "Филиалдар"))
    put(533L, mapOf("main" to "Add branch", "en" to "Add branch", "ru" to "Добавить филиал", "kk" to "Филиал қосу"))
    put(534L, mapOf("main" to "Edit branch", "en" to "Edit branch", "ru" to "Редактировать филиал", "kk" to "Филиалды өңдеу"))
    put(535L, mapOf("main" to "Parent store", "en" to "Parent store", "ru" to "Родительский магазин", "kk" to "Негізгі дүкен"))
    put(536L, mapOf("main" to "Enter branch name", "en" to "Enter branch name", "ru" to "Введите название филиала", "kk" to "Филиал атауын енгізіңіз"))
    put(537L, mapOf("main" to "Add branch name translation", "en" to "Add branch name translation", "ru" to "Добавить перевод названия филиала", "kk" to "Филиал атауының аудармасын қосу"))
    put(538L, mapOf("main" to "Delete store", "en" to "Delete store", "ru" to "Удалить магазин", "kk" to "Дүкенді жою"))
    put(539L, mapOf("main" to "Delete branch", "en" to "Delete branch", "ru" to "Удалить филиал", "kk" to "Филиалды жою"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart27() {
    put(540L, mapOf("main" to "Store address", "en" to "Store address", "ru" to "Адрес магазина", "kk" to "Дүкен мекенжайы"))
    put(541L, mapOf("main" to "Branch address", "en" to "Branch address", "ru" to "Адрес филиала", "kk" to "Филиал мекенжайы"))
    put(542L, mapOf("main" to "Parent store is active as the main warehouse", "en" to "Parent store is active as the main warehouse", "ru" to "Головной магазин активен как главный склад", "kk" to "Негізгі дүкен басты қойма ретінде белсенді"))
    put(543L, mapOf("main" to "Business identification number", "en" to "Business identification number", "ru" to "Бизнес-идентификационный номер", "kk" to "Бизнес сәйкестендіру нөмірі"))
    put(544L, mapOf("main" to "Taxpayer identification number", "en" to "Taxpayer identification number", "ru" to "Идентификационный номер налогоплательщика", "kk" to "Салық төлеушінің сәйкестендіру нөмірі"))
    put(545L, mapOf("main" to "Store branch", "en" to "Store branch", "ru" to "Филиал магазина", "kk" to "Дүкен филиалы"))
    put(546L, mapOf("main" to "Branch stock", "en" to "Branch stock", "ru" to "Остатки по филиалам", "kk" to "Филиалдардағы қор"))
    put(547L, mapOf("main" to "Across branches", "en" to "Across branches", "ru" to "По филиалам", "kk" to "Филиалдар бойынша"))
    put(548L, mapOf("main" to "Current location", "en" to "Current location", "ru" to "Текущая точка", "kk" to "Ағымдағы орын"))
    put(549L, mapOf("main" to "Move", "en" to "Move", "ru" to "Переместить", "kk" to "Жылжыту"))
    put(550L, mapOf("main" to "Move batch", "en" to "Move batch", "ru" to "Переместить партию", "kk" to "Партияны жылжыту"))
    put(551L, mapOf("main" to "Import", "en" to "Import", "ru" to "Импортировать", "kk" to "Импорттау"))
    put(552L, mapOf("main" to "Export", "en" to "Export", "ru" to "Экспортировать", "kk" to "Экспорттау"))
    put(553L, mapOf("main" to "Destination", "en" to "Destination", "ru" to "Назначение", "kk" to "Баратын орын"))
    put(554L, mapOf("main" to "Source", "en" to "Source", "ru" to "Источник", "kk" to "Бастапқы орын"))
    put(555L, mapOf("main" to "Quantity to move", "en" to "Quantity to move", "ru" to "Количество для перемещения", "kk" to "Жылжытылатын сан"))
    put(556L, mapOf("main" to "Move here", "en" to "Move here", "ru" to "Переместить сюда", "kk" to "Осында жылжыту"))
    put(557L, mapOf("main" to "Available in this store entity", "en" to "Available in this store entity", "ru" to "Доступно в этой структуре магазина", "kk" to "Осы дүкен құрылымында бар"))
    put(558L, mapOf("main" to "No other locations", "en" to "No other locations", "ru" to "Других точек нет", "kk" to "Басқа орындар жоқ"))
    put(559L, mapOf("main" to "Movement history", "en" to "Movement history", "ru" to "История перемещений", "kk" to "Жылжыту тарихы"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart28() {
    put(560L, mapOf("main" to "Moved by", "en" to "Moved by", "ru" to "Переместил", "kk" to "Жылжытқан"))
    put(561L, mapOf("main" to "Refresh availability", "en" to "Refresh availability", "ru" to "Обновить остатки", "kk" to "Қорды жаңарту"))
    put(562L, mapOf("main" to "From", "en" to "From", "ru" to "Из", "kk" to "Қайдан"))
    put(563L, mapOf("main" to "To", "en" to "To", "ru" to "В", "kk" to "Қайда"))
    put(564L, mapOf("main" to "Stock moved", "en" to "Stock moved", "ru" to "Склад перемещён", "kk" to "Қор жылжытылды"))
    put(565L, mapOf("main" to "This item is not created in that location yet. It will be copied automatically.", "en" to "This item is not created in that location yet. It will be copied automatically.", "ru" to "В этой точке товар ещё не создан. Он будет скопирован автоматически.", "kk" to "Бұл жерде тауар әлі жасалмаған. Ол автоматты түрде көшіріледі."))
    put(566L, mapOf("main" to "Import from this location", "en" to "Import from this location", "ru" to "Импортировать из этой точки", "kk" to "Осы орыннан импорттау"))
    put(567L, mapOf("main" to "Export to another location", "en" to "Export to another location", "ru" to "Экспортировать в другую точку", "kk" to "Басқа орынға экспорттау"))
    put(568L, mapOf("main" to "Parent warehouse", "en" to "Parent warehouse", "ru" to "Головной склад", "kk" to "Негізгі қойма"))
    put(569L, mapOf("main" to "Branch warehouse", "en" to "Branch warehouse", "ru" to "Склад филиала", "kk" to "Филиал қоймасы"))
    put(570L, mapOf("main" to "Quantity after move", "en" to "Quantity after move", "ru" to "Количество после перемещения", "kk" to "Жылжытудан кейінгі сан"))
    put(571L, mapOf("main" to "No movable batches here", "en" to "No movable batches here", "ru" to "Здесь нет партий для перемещения", "kk" to "Мұнда жылжытатын партия жоқ"))
    put(572L, mapOf("main" to "Loaded cached data", "en" to "Loaded cached data", "ru" to "Загружены сохранённые данные", "kk" to "Сақталған деректер жүктелді"))
    put(573L, mapOf("main" to "Live updates connected", "en" to "Live updates connected", "ru" to "Онлайн-обновления подключены", "kk" to "Нақты уақыттағы жаңартулар қосылды"))
    put(574L, mapOf("main" to "Live updates disconnected. Using cached data while reconnecting.", "en" to "Live updates disconnected. Using cached data while reconnecting.", "ru" to "Онлайн-обновления отключены. Пока идёт переподключение, используются сохранённые данные.", "kk" to "Нақты уақыттағы жаңартулар ажыратылды. Қайта қосылғанша сақталған деректер қолданылады."))
    put(575L, mapOf("main" to "Refreshing changed data", "en" to "Refreshing changed data", "ru" to "Обновление изменённых данных", "kk" to "Өзгерген деректер жаңартылуда"))
    put(576L, mapOf("main" to "Offline cache", "en" to "Offline cache", "ru" to "Офлайн-кэш", "kk" to "Офлайн кэш"))
    put(577L, mapOf("main" to "Previous", "en" to "Previous", "ru" to "Назад", "kk" to "Артқа"))
    put(578L, mapOf("main" to "Page", "en" to "Page", "ru" to "Страница", "kk" to "Бет"))
    put(579L, mapOf("main" to "Next", "en" to "Next", "ru" to "Далее", "kk" to "Келесі"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart29() {
    put(580L, mapOf("main" to "Top up balance", "en" to "Top up balance", "ru" to "Пополнить баланс", "kk" to "Балансты толтыру"))
    put(581L, mapOf("main" to "Amount", "en" to "Amount", "ru" to "Сумма", "kk" to "Сома"))
    put(582L, mapOf("main" to "Kaspi call is prepared but disabled until merchant API credentials are connected.", "en" to "Kaspi call is prepared but disabled until merchant API credentials are connected.", "ru" to "Вызов Kaspi подготовлен, но отключён до подключения данных мерчанта.", "kk" to "Kaspi шақыруы дайын, бірақ мерчант деректері қосылғанша өшірулі."))
    put(583L, mapOf("main" to "Create invoice", "en" to "Create invoice", "ru" to "Создать счёт", "kk" to "Шот жасау"))
    put(584L, mapOf("main" to "Payment invoices", "en" to "Payment invoices", "ru" to "Счета на оплату", "kk" to "Төлем шоттары"))
    put(585L, mapOf("main" to "Balance history", "en" to "Balance history", "ru" to "История баланса", "kk" to "Баланс тарихы"))
    put(586L, mapOf("main" to "No balance operations yet", "en" to "No balance operations yet", "ru" to "Операций по балансу пока нет", "kk" to "Баланс операциялары әлі жоқ"))
    put(587L, mapOf("main" to "Current subscription", "en" to "Current subscription", "ru" to "Текущая подписка", "kk" to "Ағымдағы жазылым"))
    put(588L, mapOf("main" to "Balance", "en" to "Balance", "ru" to "Баланс", "kk" to "Баланс"))
    put(589L, mapOf("main" to "Subscription charges", "en" to "Subscription charges", "ru" to "Списания подписки", "kk" to "Жазылым төлемдері"))
    put(590L, mapOf("main" to "Top-up", "en" to "Top-up", "ru" to "Пополнение", "kk" to "Толтыру"))
    put(591L, mapOf("main" to "Subscription charge", "en" to "Subscription charge", "ru" to "Списание подписки", "kk" to "Жазылым төлемі"))
    put(592L, mapOf("main" to "AITA balance", "en" to "AITA balance", "ru" to "Баланс AITA", "kk" to "AITA балансы"))
    put(593L, mapOf("main" to "1 AITA unit equals 1 unit of your national currency", "en" to "1 AITA unit equals 1 unit of your national currency", "ru" to "1 единица AITA равна 1 единице вашей национальной валюты", "kk" to "1 AITA бірлігі ұлттық валютаңыздың 1 бірлігіне тең"))
    put(594L, mapOf("main" to "Confirm test payment", "en" to "Confirm test payment", "ru" to "Подтвердить тестовый платёж", "kk" to "Тест төлемін растау"))
    put(595L, mapOf("main" to "Branches", "en" to "Branches", "ru" to "Филиалы", "kk" to "Филиалдар"))
    put(596L, mapOf("main" to "Stock items", "en" to "Stock items", "ru" to "Товары на складе", "kk" to "Қойма тауарлары"))
    put(597L, mapOf("main" to "Activate plan", "en" to "Activate plan", "ru" to "Активировать план", "kk" to "Жоспарды қосу"))
    put(598L, mapOf("main" to "Finance dashboard loaded", "en" to "Finance dashboard loaded", "ru" to "Финансы загружены", "kk" to "Қаржы жүктелді"))
    put(599L, mapOf("main" to "Top-up invoice created", "en" to "Top-up invoice created", "ru" to "Счёт на пополнение создан", "kk" to "Толтыру шоты жасалды"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart30() {
    put(600L, mapOf("main" to "Balance topped up", "en" to "Balance topped up", "ru" to "Баланс пополнен", "kk" to "Баланс толтырылды"))
    put(601L, mapOf("main" to "Subscription updated", "en" to "Subscription updated", "ru" to "Подписка обновлена", "kk" to "Жазылым жаңартылды"))
    put(602L, mapOf("main" to "Subscription plans loaded", "en" to "Subscription plans loaded", "ru" to "Планы подписки загружены", "kk" to "Жазылым жоспарлары жүктелді"))
    put(603L, mapOf("main" to "Store subscription loaded", "en" to "Store subscription loaded", "ru" to "Подписка магазина загружена", "kk" to "Дүкен жазылымы жүктелді"))
    put(604L, mapOf("main" to "Cannot update subscription. Check balance and permissions.", "en" to "Cannot update subscription. Check balance and permissions.", "ru" to "Не удалось обновить подписку. Проверьте баланс и права.", "kk" to "Жазылымды жаңарту мүмкін болмады. Баланс пен құқықтарды тексеріңіз."))
    put(605L, mapOf("main" to "Paging", "en" to "Paging", "ru" to "Постранично", "kk" to "Беттер бойынша"))
    put(606L, mapOf("main" to "AITA KZT", "en" to "AITA KZT", "ru" to "AITA KZT", "kk" to "AITA KZT"))
    put(607L, mapOf("main" to "Manual development top-up", "en" to "Manual development top-up", "ru" to "Тестовое ручное пополнение", "kk" to "Қолмен тест толтыру"))
    put(608L, mapOf("main" to "Kaspi invoice", "en" to "Kaspi invoice", "ru" to "Счёт Kaspi", "kk" to "Kaspi шоты"))
    put(609L, mapOf("main" to "Conditions", "en" to "Conditions", "ru" to "Условия", "kk" to "Шарттар"))
    put(610L, mapOf("main" to "Add condition", "en" to "Add condition", "ru" to "Добавить условие", "kk" to "Шарт қосу"))
    put(611L, mapOf("main" to "Enter condition", "en" to "Enter condition", "ru" to "Введите условие", "kk" to "Шартты енгізіңіз"))
    put(612L, mapOf("main" to "Check all conditions", "en" to "Check all conditions", "ru" to "Отметьте все условия", "kk" to "Барлық шарттарды белгілеңіз"))
    put(613L, mapOf("main" to "Item in acceptable condition", "en" to "Item in acceptable condition", "ru" to "Товар в приемлемом состоянии", "kk" to "Тауар қабылдауға жарамды күйде"))
    put(614L, mapOf("main" to "Cart total", "en" to "Cart total", "ru" to "Итого в корзине", "kk" to "Себет қорытындысы"))
    put(615L, mapOf("main" to "items", "en" to "items", "ru" to "товаров", "kk" to "тауар"))
    put(616L, mapOf("main" to "Open system devices", "en" to "Open system devices", "ru" to "Открыть устройства системы", "kk" to "Жүйе құрылғыларын ашу"))
    put(617L, mapOf("main" to "Open Bluetooth/devices settings", "en" to "Open Bluetooth/devices settings", "ru" to "Открыть настройки Bluetooth/устройств", "kk" to "Bluetooth/құрылғылар баптауларын ашу"))
    put(618L, mapOf("main" to "My", "en" to "My", "ru" to "Мои", "kk" to "Менің"))
    put(619L, mapOf("main" to "Generic", "en" to "Generic", "ru" to "Общие", "kk" to "Жалпы"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart31() {
    put(620L, mapOf("main" to "Supplier phone number", "en" to "Supplier phone number", "ru" to "Телефон поставщика", "kk" to "Жеткізуші телефоны"))
    put(621L, mapOf("main" to "Supplier email", "en" to "Supplier email", "ru" to "Email поставщика", "kk" to "Жеткізуші email"))
    put(622L, mapOf("main" to "Enter supplier name", "en" to "Enter supplier name", "ru" to "Введите название поставщика", "kk" to "Жеткізуші атауын енгізіңіз"))
    put(623L, mapOf("main" to "Supplier added", "en" to "Supplier added", "ru" to "Поставщик добавлен", "kk" to "Жеткізуші қосылды"))
    put(624L, mapOf("main" to "Supplier updated", "en" to "Supplier updated", "ru" to "Поставщик обновлён", "kk" to "Жеткізуші жаңартылды"))
    put(625L, mapOf("main" to "Supplier deleted", "en" to "Supplier deleted", "ru" to "Поставщик удалён", "kk" to "Жеткізуші жойылды"))
    put(626L, mapOf("main" to "No suppliers yet", "en" to "No suppliers yet", "ru" to "Поставщиков пока нет", "kk" to "Әзірге жеткізушілер жоқ"))
    put(627L, mapOf("main" to "Add my supplier", "en" to "Add my supplier", "ru" to "Добавить моего поставщика", "kk" to "Менің жеткізушімді қосу"))
    put(628L, mapOf("main" to "Generic suppliers", "en" to "Generic suppliers", "ru" to "Общие поставщики", "kk" to "Жалпы жеткізушілер"))
    put(629L, mapOf("main" to "My suppliers", "en" to "My suppliers", "ru" to "Мои поставщики", "kk" to "Менің жеткізушілерім"))
    put(630L, mapOf("main" to "Supplier data", "en" to "Supplier data", "ru" to "Данные поставщика", "kk" to "Жеткізуші деректері"))
    put(631L, mapOf("main" to "Save supplier", "en" to "Save supplier", "ru" to "Сохранить поставщика", "kk" to "Жеткізушіні сақтау"))
    put(632L, mapOf("main" to "This supplier is generic and cannot be edited here", "en" to "This supplier is generic and cannot be edited here", "ru" to "Это общий поставщик, здесь его нельзя редактировать", "kk" to "Бұл жалпы жеткізуші, оны мұнда өңдеуге болмайды"))
    put(633L, mapOf("main" to "Device settings opened", "en" to "Device settings opened", "ru" to "Настройки устройств открыты", "kk" to "Құрылғы баптаулары ашылды"))
    put(634L, mapOf("main" to "Could not open device settings", "en" to "Could not open device settings", "ru" to "Не удалось открыть настройки устройств", "kk" to "Құрылғы баптауларын ашу мүмкін болмады"))
    put(635L, mapOf("main" to "Add supplier here", "en" to "Add supplier here", "ru" to "Добавить поставщика здесь", "kk" to "Жеткізушіні осында қосу"))
    put(636L, mapOf("main" to "Quick add item", "en" to "Quick add item", "ru" to "Быстро добавить товар", "kk" to "Тауарды жылдам қосу"))
    put(637L, mapOf("main" to "Save item and add to cart", "en" to "Save item and add to cart", "ru" to "Сохранить товар и добавить в корзину", "kk" to "Тауарды сақтап, себетке қосу"))
    put(638L, mapOf("main" to "No supplier selected", "en" to "No supplier selected", "ru" to "Поставщик не выбран", "kk" to "Жеткізуші таңдалмаған"))
    put(639L, mapOf("main" to "Supplier for supply", "en" to "Supplier for supply", "ru" to "Поставщик для поставки", "kk" to "Жеткізуге арналған жеткізуші"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart32() {
    put(640L, mapOf("main" to "Select supplier for supply", "en" to "Select supplier for supply", "ru" to "Выберите поставщика для поставки", "kk" to "Жеткізу үшін жеткізушіні таңдаңыз"))
    put(641L, mapOf("main" to "Supplier selected", "en" to "Supplier selected", "ru" to "Поставщик выбран", "kk" to "Жеткізуші таңдалды"))
    put(642L, mapOf("main" to "New barcode was not found. Add item now.", "en" to "New barcode was not found. Add item now.", "ru" to "Новый штрих-код не найден. Добавьте товар сейчас.", "kk" to "Жаңа штрих-код табылмады. Тауарды қазір қосыңыз."))
    put(643L, mapOf("main" to "Start workshift", "en" to "Start workshift", "ru" to "Начать смену", "kk" to "Ауысымды бастау"))
    put(644L, mapOf("main" to "Workshift required", "en" to "Workshift required", "ru" to "Требуется смена", "kk" to "Ауысым қажет"))
    put(645L, mapOf("main" to "Start a workshift before taking payments or changing cash. You can still browse the store and prepare work without starting a shift.", "en" to "Start a workshift before taking payments or changing cash. You can still browse the store and prepare work without starting a shift.", "ru" to "Начните смену перед приёмом оплат или изменением кассы. Просматривать магазин и готовить работу можно и без начала смены.", "kk" to "Төлем қабылдау немесе кассаны өзгерту алдында ауысымды бастаңыз. Дүкенді қарап, жұмысты дайындауды ауысымсыз да жасауға болады."))
    put(646L, mapOf("main" to "Worker ID / phone / email", "en" to "Worker ID / phone / email", "ru" to "ID сотрудника / телефон / email", "kk" to "Қызметкер ID / телефон / email"))
    put(647L, mapOf("main" to "Enter worker ID or account phone", "en" to "Enter worker ID or account phone", "ru" to "Введите ID сотрудника или телефон аккаунта", "kk" to "Қызметкер ID немесе аккаунт телефонын енгізіңіз"))
    put(648L, mapOf("main" to "Workshift password", "en" to "Workshift password", "ru" to "Пароль смены", "kk" to "Ауысым құпия сөзі"))
    put(649L, mapOf("main" to "Enter workshift password", "en" to "Enter workshift password", "ru" to "Введите пароль смены", "kk" to "Ауысым құпия сөзін енгізіңіз"))
    put(650L, mapOf("main" to "End workshift", "en" to "End workshift", "ru" to "Завершить смену", "kk" to "Ауысымды аяқтау"))
    put(651L, mapOf("main" to "Invites", "en" to "Invites", "ru" to "Приглашения", "kk" to "Шақырулар"))
    put(652L, mapOf("main" to "Incoming invites from stores", "en" to "Incoming invites from stores", "ru" to "Входящие приглашения от магазинов", "kk" to "Дүкендерден келген шақырулар"))
    put(653L, mapOf("main" to "No worker invites yet", "en" to "No worker invites yet", "ru" to "Приглашений пока нет", "kk" to "Әзірше шақырулар жоқ"))
    put(654L, mapOf("main" to "Workshift password", "en" to "Workshift password", "ru" to "Пароль смены", "kk" to "Ауысым құпия сөзі"))
    put(655L, mapOf("main" to "Set or replace your password for starting workshifts", "en" to "Set or replace your password for starting workshifts", "ru" to "Задайте или замените ваш пароль для начала смен", "kk" to "Ауысым бастауға арналған құпия сөзіңізді қойыңыз немесе ауыстырыңыз"))
    put(656L, mapOf("main" to "Store or branch public ID", "en" to "Store or branch public ID", "ru" to "Публичный ID магазина или филиала", "kk" to "Дүкен немесе филиалдың жария ID"))
    put(657L, mapOf("main" to "Enter store or branch public ID", "en" to "Enter store or branch public ID", "ru" to "Введите публичный ID магазина или филиала", "kk" to "Дүкен немесе филиалдың жария ID енгізіңіз"))
    put(658L, mapOf("main" to "Requests to this store or branch", "en" to "Requests to this store or branch", "ru" to "Заявки в этот магазин или филиал", "kk" to "Осы дүкенге немесе филиалға өтініштер"))
    put(659L, mapOf("main" to "Tap a menu item to open it. Long names wrap naturally.", "en" to "Tap a menu item to open it. Long names wrap naturally.", "ru" to "Нажмите пункт меню, чтобы открыть. Длинные названия переносятся автоматически.", "kk" to "Ашу үшін мәзір тармағын басыңыз. Ұзын атаулар автоматты түрде тасымалданады."))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart33() {
    put(660L, mapOf("main" to "This password is private to this store job only", "en" to "This password is private to this store job only", "ru" to "Этот пароль личный и относится только к работе в этом магазине", "kk" to "Бұл құпия сөз жеке және тек осы дүкендегі жұмысқа арналған"))
    put(661L, mapOf("main" to "Active workshift", "en" to "Active workshift", "ru" to "Активная смена", "kk" to "Белсенді ауысым"))
    put(662L, mapOf("main" to "Operation logs", "en" to "Operation logs", "ru" to "Журнал операций", "kk" to "Операциялар журналы"))
    put(663L, mapOf("main" to "Search logs", "en" to "Search logs", "ru" to "Поиск в журнале", "kk" to "Журналдан іздеу"))
    put(664L, mapOf("main" to "Current place", "en" to "Current place", "ru" to "Текущая точка", "kk" to "Ағымдағы орын"))
    put(665L, mapOf("main" to "You do not have permission for this action", "en" to "You do not have permission for this action", "ru" to "У вас нет прав для этого действия", "kk" to "Бұл әрекетке рұқсатыңыз жоқ"))
    put(666L, mapOf("main" to "Parent and branches", "en" to "Parent and branches", "ru" to "Головной и филиалы", "kk" to "Негізгі және филиалдар"))
    put(667L, mapOf("main" to "No operation logs yet", "en" to "No operation logs yet", "ru" to "Журнал операций пока пуст", "kk" to "Операциялар журналы әзірге бос"))
    put(668L, mapOf("main" to "By", "en" to "By", "ru" to "Кто", "kk" to "Кім"))
    put(669L, mapOf("main" to "Place", "en" to "Place", "ru" to "Место", "kk" to "Орын"))
    put(670L, mapOf("main" to "Worker permissions are applied", "en" to "Worker permissions are applied", "ru" to "Права сотрудника применены", "kk" to "Қызметкер рұқсаттары қолданылды"))
    put(671L, mapOf("main" to "Default", "en" to "Default", "ru" to "По умолчанию", "kk" to "Әдепкі"))
    put(672L, mapOf("main" to "Overview", "en" to "Overview", "ru" to "Обзор", "kk" to "Шолу"))
    put(673L, mapOf("main" to "Gross profit estimate", "en" to "Gross profit estimate", "ru" to "Оценка валовой прибыли", "kk" to "Жалпы пайда болжамы"))
    put(674L, mapOf("main" to "Margin", "en" to "Margin", "ru" to "Маржа", "kk" to "Маржа"))
    put(675L, mapOf("main" to "Payment mix", "en" to "Payment mix", "ru" to "Структура оплаты", "kk" to "Төлем құрылымы"))
    put(676L, mapOf("main" to "Debt amount", "en" to "Debt amount", "ru" to "Сумма долга", "kk" to "Қарыз сомасы"))
    put(677L, mapOf("main" to "Cashless share", "en" to "Cashless share", "ru" to "Доля безнала", "kk" to "Қолма-қолсыз үлесі"))
    put(678L, mapOf("main" to "Best hour", "en" to "Best hour", "ru" to "Лучший час", "kk" to "Ең жақсы сағат"))
    put(679L, mapOf("main" to "Best day", "en" to "Best day", "ru" to "Лучший день", "kk" to "Ең жақсы күн"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart34() {
    put(680L, mapOf("main" to "Top items by revenue", "en" to "Top items by revenue", "ru" to "Товары по выручке", "kk" to "Түсім бойынша тауарлар"))
    put(681L, mapOf("main" to "Top items by quantity", "en" to "Top items by quantity", "ru" to "Товары по количеству", "kk" to "Саны бойынша тауарлар"))
    put(682L, mapOf("main" to "Slow-moving inventory", "en" to "Slow-moving inventory", "ru" to "Медленно продающиеся остатки", "kk" to "Баяу өтетін қор"))
    put(683L, mapOf("main" to "Inventory value at sale price", "en" to "Inventory value at sale price", "ru" to "Стоимость остатков по цене продажи", "kk" to "Сату бағасымен қор құны"))
    put(684L, mapOf("main" to "Inventory value at supply cost", "en" to "Inventory value at supply cost", "ru" to "Себестоимость остатков", "kk" to "Қордың жеткізу құны"))
    put(685L, mapOf("main" to "Low stock items", "en" to "Low stock items", "ru" to "Товары с низким остатком", "kk" to "Қоры аз тауарлар"))
    put(686L, mapOf("main" to "Out of stock items", "en" to "Out of stock items", "ru" to "Товары без остатка", "kk" to "Қоры біткен тауарлар"))
    put(687L, mapOf("main" to "Expired batches", "en" to "Expired batches", "ru" to "Просроченные партии", "kk" to "Мерзімі өткен партиялар"))
    put(688L, mapOf("main" to "Expiring soon", "en" to "Expiring soon", "ru" to "Скоро истекает срок", "kk" to "Жақында мерзімі бітеді"))
    put(689L, mapOf("main" to "Sell-through estimate", "en" to "Sell-through estimate", "ru" to "Оценка доли проданного", "kk" to "Сатылған үлес бағасы"))
    put(690L, mapOf("main" to "Estimated from current/latest supply prices", "en" to "Estimated from current/latest supply prices", "ru" to "Рассчитано по текущим/последним закупочным ценам", "kk" to "Ағымдағы/соңғы жеткізу бағалары бойынша есептелді"))
    put(691L, mapOf("main" to "Sales by day", "en" to "Sales by day", "ru" to "Продажи по дням", "kk" to "Күндер бойынша сатылым"))
    put(692L, mapOf("main" to "Sales by hour", "en" to "Sales by hour", "ru" to "Продажи по часам", "kk" to "Сағаттар бойынша сатылым"))
    put(693L, mapOf("main" to "Revenue", "en" to "Revenue", "ru" to "Выручка", "kk" to "Түсім"))
    put(694L, mapOf("main" to "Gross sales", "en" to "Gross sales", "ru" to "Валовые продажи", "kk" to "Жалпы сатылым"))
    put(695L, mapOf("main" to "Returns amount", "en" to "Returns amount", "ru" to "Сумма возвратов", "kk" to "Қайтарым сомасы"))
    put(696L, mapOf("main" to "Average sale", "en" to "Average sale", "ru" to "Средняя продажа", "kk" to "Орташа сатылым"))
    put(697L, mapOf("main" to "Net revenue", "en" to "Net revenue", "ru" to "Чистая выручка", "kk" to "Таза түсім"))
    put(698L, mapOf("main" to "Stock health", "en" to "Stock health", "ru" to "Здоровье склада", "kk" to "Қор жағдайы"))
    put(699L, mapOf("main" to "Inventory risk", "en" to "Inventory risk", "ru" to "Риск по запасам", "kk" to "Қор тәуекелі"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart35() {
    put(700L, mapOf("main" to "Items per sale", "en" to "Items per sale", "ru" to "Товаров в продаже", "kk" to "Бір сатылымдағы тауар"))
    put(701L, mapOf("main" to "Profit estimate", "en" to "Profit estimate", "ru" to "Оценка прибыли", "kk" to "Пайда болжамы"))
    put(702L, mapOf("main" to "Performance", "en" to "Performance", "ru" to "Эффективность", "kk" to "Нәтижелілік"))
    put(703L, mapOf("main" to "Top performers", "en" to "Top performers", "ru" to "Лучшие показатели", "kk" to "Үздік көрсеткіштер"))
    put(704L, mapOf("main" to "No analytics data yet", "en" to "No analytics data yet", "ru" to "Данных аналитики пока нет", "kk" to "Әзірге аналитика деректері жоқ"))
    put(705L, mapOf("main" to "Sold quantity", "en" to "Sold quantity", "ru" to "Проданное количество", "kk" to "Сатылған саны"))
    put(706L, mapOf("main" to "Transactions", "en" to "Transactions", "ru" to "Транзакции", "kk" to "Транзакциялар"))
    put(707L, mapOf("main" to "Accepted goods value", "en" to "Accepted goods value", "ru" to "Стоимость приёмки", "kk" to "Қабылданған тауар құны"))
    put(708L, mapOf("main" to "Average acceptance", "en" to "Average acceptance", "ru" to "Средняя приёмка", "kk" to "Орташа қабылдау"))
    put(709L, mapOf("main" to "Suppliers used", "en" to "Suppliers used", "ru" to "Использованные поставщики", "kk" to "Қолданылған жеткізушілер"))
    put(710L, mapOf("main" to "Average items", "en" to "Average items", "ru" to "Среднее количество товаров", "kk" to "Орташа тауар саны"))
    put(711L, mapOf("main" to "Analytics loaded", "en" to "Analytics loaded", "ru" to "Аналитика загружена", "kk" to "Аналитика жүктелді"))
    put(712L, mapOf("main" to "Inventory quantity", "en" to "Inventory quantity", "ru" to "Количество на складе", "kk" to "Қоймадағы саны"))
    put(713L, mapOf("main" to "Most popular", "en" to "Most popular", "ru" to "Популярные", "kk" to "Танымал"))
    put(714L, mapOf("main" to "Recently used", "en" to "Recently used", "ru" to "Недавние", "kk" to "Жақында"))
    put(715L, mapOf("main" to "Restock", "en" to "Restock", "ru" to "Пополнить", "kk" to "Толықтыру"))
    put(716L, mapOf("main" to "Low stock", "en" to "Low stock", "ru" to "Мало остатка", "kk" to "Қор аз"))
    put(717L, mapOf("main" to "Expiring", "en" to "Expiring", "ru" to "Срок истекает", "kk" to "Мерзімі бітеді"))
    put(718L, mapOf("main" to "Slow movers", "en" to "Slow movers", "ru" to "Медленные", "kk" to "Баяу өтетін"))
    put(719L, mapOf("main" to "Log out?", "en" to "Log out?", "ru" to "Выйти из аккаунта?", "kk" to "Аккаунттан шығу керек пе?"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart36() {
    put(720L, mapOf("main" to "You will leave this account on this device.", "en" to "You will leave this account on this device.", "ru" to "Вы выйдете из аккаунта на этом устройстве.", "kk" to "Осы құрылғыда аккаунттан шығасыз."))
    put(721L, mapOf("main" to "Recently sold", "en" to "Recently sold", "ru" to "Недавно проданные", "kk" to "Жақында сатылған"))
    put(722L, mapOf("main" to "Hide paid debts", "en" to "Hide paid debts", "ru" to "Скрыть оплаченные долги", "kk" to "Төленген қарыздарды жасыру"))
    put(723L, mapOf("main" to "Show paid debts", "en" to "Show paid debts", "ru" to "Показать оплаченные долги", "kk" to "Төленген қарыздарды көрсету"))
    put(724L, mapOf("main" to "Store mode", "en" to "Store mode", "ru" to "Режим магазина", "kk" to "Дүкен режимі"))
    put(725L, mapOf("main" to "Buyer mode", "en" to "Buyer mode", "ru" to "Режим покупателя", "kk" to "Сатып алушы режимі"))
    put(726L, mapOf("main" to "Supplier mode", "en" to "Supplier mode", "ru" to "Режим поставщика", "kk" to "Жеткізуші режимі"))
    put(727L, mapOf("main" to "Producer mode", "en" to "Producer mode", "ru" to "Режим производителя", "kk" to "Өндіруші режимі"))
    put(728L, mapOf("main" to "Local branch network enabled", "en" to "Local branch network enabled", "ru" to "Локальная сеть филиала включена", "kk" to "Филиалдың жергілікті желісі қосылды"))
    put(729L, mapOf("main" to "Local branch network disabled", "en" to "Local branch network disabled", "ru" to "Локальная сеть филиала выключена", "kk" to "Филиалдың жергілікті желісі өшірілді"))
    put(730L, mapOf("main" to "This device is the local server", "en" to "This device is the local server", "ru" to "Это устройство — локальный сервер", "kk" to "Бұл құрылғы жергілікті сервер"))
    put(731L, mapOf("main" to "Joined local server", "en" to "Joined local server", "ru" to "Подключено к локальному серверу", "kk" to "Жергілікті серверге қосылды"))
    put(732L, mapOf("main" to "Local branch network", "en" to "Local branch network", "ru" to "Локальная сеть филиала", "kk" to "Филиалдың жергілікті желісі"))
    put(733L, mapOf("main" to "Queued locally for cloud sync", "en" to "Queued locally for cloud sync", "ru" to "Сохранено локально для синхронизации", "kk" to "Бұлтпен синхрондау үшін жергілікті сақталды"))
    put(734L, mapOf("main" to "Local branch state updated", "en" to "Local branch state updated", "ru" to "Локальное состояние филиала обновлено", "kk" to "Филиалдың жергілікті күйі жаңартылды"))
    put(735L, mapOf("main" to "Use this only inside one physical branch on the same Wi-Fi/LAN. One device becomes a local server; other devices send operations to it while cloud internet is unstable.", "en" to "Use this only inside one physical branch on the same Wi-Fi/LAN. One device becomes a local server; other devices send operations to it while cloud internet is unstable.", "ru" to "Используйте только внутри одного физического филиала в одной Wi‑Fi/LAN сети. Одно устройство становится локальным сервером; остальные отправляют ему операции, пока интернет до облака нестабилен.", "kk" to "Мұны бір физикалық филиал ішінде, бір Wi‑Fi/LAN желісінде ғана қолданыңыз. Бір құрылғы жергілікті сервер болады; қалғандары бұлт интернеті тұрақсыз кезде операцияларды соған жібереді."))
    put(736L, mapOf("main" to "Become local server", "en" to "Become local server", "ru" to "Стать локальным сервером", "kk" to "Жергілікті сервер болу"))
    put(737L, mapOf("main" to "Scan devices", "en" to "Scan devices", "ru" to "Найти устройства", "kk" to "Құрылғыларды іздеу"))
    put(738L, mapOf("main" to "Sync queued operations", "en" to "Sync queued operations", "ru" to "Синхронизировать очередь", "kk" to "Кезекті синхрондау"))
    put(739L, mapOf("main" to "Clear synced", "en" to "Clear synced", "ru" to "Очистить отправленные", "kk" to "Жіберілгендерді тазалау"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart37() {
    put(740L, mapOf("main" to "Detected devices", "en" to "Detected devices", "ru" to "Найденные устройства", "kk" to "Табылған құрылғылар"))
    put(741L, mapOf("main" to "Queued operations", "en" to "Queued operations", "ru" to "Операции в очереди", "kk" to "Кезектегі операциялар"))
    put(742L, mapOf("main" to "Cloud connection", "en" to "Cloud connection", "ru" to "Связь с облаком", "kk" to "Бұлтпен байланыс"))
    put(743L, mapOf("main" to "Local role", "en" to "Local role", "ru" to "Локальная роль", "kk" to "Жергілікті рөл"))
    put(744L, mapOf("main" to "Join", "en" to "Join", "ru" to "Подключиться", "kk" to "Қосылу"))
    put(745L, mapOf("main" to "No devices found yet", "en" to "No devices found yet", "ru" to "Устройства пока не найдены", "kk" to "Әзірге құрылғылар табылмады"))
    put(746L, mapOf("main" to "Pending", "en" to "Pending", "ru" to "Ожидает", "kk" to "Күтуде"))
    put(747L, mapOf("main" to "Synced", "en" to "Synced", "ru" to "Отправлено", "kk" to "Синхрондалды"))
    put(748L, mapOf("main" to "Failed", "en" to "Failed", "ru" to "Ошибка", "kk" to "Қате"))
    put(749L, mapOf("main" to "Enable local branch network", "en" to "Enable local branch network", "ru" to "Включить локальную сеть филиала", "kk" to "Филиалдың жергілікті желісін қосу"))
    put(750L, mapOf("main" to "Disable local branch network", "en" to "Disable local branch network", "ru" to "Выключить локальную сеть филиала", "kk" to "Филиалдың жергілікті желісін өшіру"))
    put(751L, mapOf("main" to "Select one physical branch first. Local branch network cannot run on a parent warehouse with several branches.", "en" to "Select one physical branch first. Local branch network cannot run on a parent warehouse with several branches.", "ru" to "Сначала выберите один физический филиал. Локальная сеть филиала не работает на головном складе с несколькими филиалами.", "kk" to "Алдымен бір нақты филиалды таңдаңыз. Филиалдың жергілікті желісі бірнеше филиалы бар негізгі қоймада жұмыс істемейді."))
    put(756L, mapOf("main" to "Stock", "en" to "Stock", "ru" to "Склад", "kk" to "Қор"))
    put(800L, mapOf("main" to "Remembered price exists", "en" to "Remembered price exists", "ru" to "Сохранённая цена уже есть", "kk" to "Сақталған баға бар"))
    put(801L, mapOf("main" to "New supplier price", "en" to "New supplier price", "ru" to "Новая цена поставщика", "kk" to "Жеткізушінің жаңа бағасы"))
    put(802L, mapOf("main" to "Remember default supply prices for this goods item per supplier. These prices can prefill new batches later.", "en" to "Remember default supply prices for this goods item per supplier. These prices can prefill new batches later.", "ru" to "Сохраняйте стандартные закупочные цены этого товара по каждому поставщику. Потом они смогут автоматически заполнять новые партии.", "kk" to "Әр жеткізуші бойынша осы тауардың әдепкі сатып алу бағаларын сақтаңыз. Кейін олар жаңа партияларды автоматты толтыра алады."))
    put(803L, mapOf("main" to "Remembered supplier prices", "en" to "Remembered supplier prices", "ru" to "Сохранённые цены поставщиков", "kk" to "Сақталған жеткізуші бағалары"))
    put(804L, mapOf("main" to "No supplier prices yet. Add one above or add a batch with supplier price.", "en" to "No supplier prices yet. Add one above or add a batch with supplier price.", "ru" to "Цен поставщиков пока нет. Добавьте цену выше или создайте партию с ценой поставщика.", "kk" to "Жеткізуші бағалары әзірге жоқ. Жоғарыдан қосыңыз немесе жеткізуші бағасы бар партия қосыңыз."))
    put(805L, mapOf("main" to "No supplier prices match this search.", "en" to "No supplier prices match this search.", "ru" to "По этому поиску цен поставщиков нет.", "kk" to "Бұл іздеу бойынша жеткізуші бағалары жоқ."))
    put(806L, mapOf("main" to "Goods name", "en" to "Goods name", "ru" to "Название товара", "kk" to "Тауар атауы"))
    put(807L, mapOf("main" to "Last used", "en" to "Last used", "ru" to "Последнее использование", "kk" to "Соңғы қолдану"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart38() {
    put(808L, mapOf("main" to "Updated", "en" to "Updated", "ru" to "Обновлено", "kk" to "Жаңартылды"))
    put(809L, mapOf("main" to "Enable interest", "en" to "Enable interest", "ru" to "Включить проценты", "kk" to "Пайызды қосу"))
    put(810L, mapOf("main" to "Disable interest", "en" to "Disable interest", "ru" to "Выключить проценты", "kk" to "Пайызды өшіру"))
    put(811L, mapOf("main" to "Rate %", "en" to "Rate %", "ru" to "Ставка %", "kk" to "Мөлшерлеме %"))
    put(812L, mapOf("main" to "active", "en" to "active", "ru" to "активная", "kk" to "белсенді"))
    put(813L, mapOf("main" to "Support", "en" to "Support", "ru" to "Поддержка", "kk" to "Қолдау"))
    put(814L, mapOf("main" to "FAQ", "en" to "FAQ", "ru" to "FAQ", "kk" to "FAQ"))
    put(815L, mapOf("main" to "Support chat", "en" to "Support chat", "ru" to "Чат с поддержкой", "kk" to "Қолдау чаты"))
    put(816L, mapOf("main" to "Search FAQ", "en" to "Search FAQ", "ru" to "Поиск по FAQ", "kk" to "FAQ бойынша іздеу"))
    put(817L, mapOf("main" to "Ask support", "en" to "Ask support", "ru" to "Написать в поддержку", "kk" to "Қолдауға жазу"))
    put(818L, mapOf("main" to "Choose topic", "en" to "Choose topic", "ru" to "Выберите тему", "kk" to "Тақырыпты таңдаңыз"))
    put(819L, mapOf("main" to "General", "en" to "General", "ru" to "Общее", "kk" to "Жалпы"))
    put(820L, mapOf("main" to "Billing", "en" to "Billing", "ru" to "Оплата и подписка", "kk" to "Төлем және жазылым"))
    put(821L, mapOf("main" to "Technical", "en" to "Technical", "ru" to "Техническое", "kk" to "Техникалық"))
    put(822L, mapOf("main" to "Store operations", "en" to "Store operations", "ru" to "Работа магазина", "kk" to "Дүкен жұмысы"))
    put(823L, mapOf("main" to "Account and security", "en" to "Account and security", "ru" to "Аккаунт и безопасность", "kk" to "Аккаунт және қауіпсіздік"))
    put(824L, mapOf("main" to "Type your message", "en" to "Type your message", "ru" to "Введите сообщение", "kk" to "Хабарламаңызды енгізіңіз"))
    put(825L, mapOf("main" to "Send", "en" to "Send", "ru" to "Отправить", "kk" to "Жіберу"))
    put(826L, mapOf("main" to "New question", "en" to "New question", "ru" to "Новый вопрос", "kk" to "Жаңа сұрақ"))
    put(827L, mapOf("main" to "Close request", "en" to "Close request", "ru" to "Закрыть обращение", "kk" to "Өтінішті жабу"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart39() {
    put(828L, mapOf("main" to "Reopen request", "en" to "Reopen request", "ru" to "Открыть снова", "kk" to "Қайта ашу"))
    put(829L, mapOf("main" to "No support messages yet", "en" to "No support messages yet", "ru" to "Сообщений поддержки пока нет", "kk" to "Қолдау хабарламалары әзір жоқ"))
    put(830L, mapOf("main" to "We usually answer inside this chat. Describe what happened and add IDs, barcode, store or screenshots if useful.", "en" to "We usually answer inside this chat. Describe what happened and add IDs, barcode, store or screenshots if useful.", "ru" to "Мы отвечаем прямо в этом чате. Опишите, что произошло, и добавьте ID, штрих-код, магазин или скриншоты, если это поможет.", "kk" to "Біз осы чатта жауап береміз. Не болғанын сипаттап, қажет болса ID, штрих-код, дүкен немесе скриншот қосыңыз."))
    put(831L, mapOf("main" to "Open", "en" to "Open", "ru" to "Открыто", "kk" to "Ашық"))
    put(832L, mapOf("main" to "Closed", "en" to "Closed", "ru" to "Закрыто", "kk" to "Жабық"))
    put(833L, mapOf("main" to "Waiting for support", "en" to "Waiting for support", "ru" to "Ожидает поддержки", "kk" to "Қолдауды күтуде"))
    put(834L, mapOf("main" to "You", "en" to "You", "ru" to "Вы", "kk" to "Сіз"))
    put(835L, mapOf("main" to "Support team", "en" to "Support team", "ru" to "Команда поддержки", "kk" to "Қолдау тобы"))
    put(836L, mapOf("main" to "Conversation", "en" to "Conversation", "ru" to "Переписка", "kk" to "Хат алмасу"))
    put(837L, mapOf("main" to "This request is closed. Reopen it to send a new message.", "en" to "This request is closed. Reopen it to send a new message.", "ru" to "Это обращение закрыто. Откройте его снова, чтобы отправить новое сообщение.", "kk" to "Бұл өтініш жабық. Жаңа хабарлама жіберу үшін оны қайта ашыңыз."))
    put(838L, mapOf("main" to "Select a conversation", "en" to "Select a conversation", "ru" to "Выберите переписку", "kk" to "Хат алмасуды таңдаңыз"))
    put(839L, mapOf("main" to "No support requests yet", "en" to "No support requests yet", "ru" to "Обращений в поддержку пока нет", "kk" to "Қолдау өтініштері әзір жоқ"))
    put(840L, mapOf("main" to "Support request", "en" to "Support request", "ru" to "Обращение в поддержку", "kk" to "Қолдау өтініші"))
    put(841L, mapOf("main" to "Nothing found in FAQ", "en" to "Nothing found in FAQ", "ru" to "В FAQ ничего не найдено", "kk" to "FAQ ішінде ештеңе табылмады"))
    put(842L, mapOf("main" to "Last update", "en" to "Last update", "ru" to "Последнее обновление", "kk" to "Соңғы жаңарту"))
    put(850L, mapOf("main" to "What is AITA for?", "en" to "What is AITA for?", "ru" to "Для чего нужен AITA?", "kk" to "AITA не үшін қажет?"))
    put(851L, mapOf("main" to "AITA is a store operating system: it helps you run sales, returns, supplies, stock, workers, receipts, analytics, debts, subscriptions and devices from one account.", "en" to "AITA is a store operating system: it helps you run sales, returns, supplies, stock, workers, receipts, analytics, debts, subscriptions and devices from one account.", "ru" to "AITA — это операционная система для магазина: продажи, возвраты, поставки, склад, сотрудники, чеки, аналитика, долги, подписки и устройства в одном аккаунте.", "kk" to "AITA — дүкенге арналған операциялық жүйе: сату, қайтару, жеткізу, қойма, қызметкерлер, түбіртектер, аналитика, қарыздар, жазылымдар және құрылғылар бір аккаунтта."))
    put(852L, mapOf("main" to "How do I create a store and branches?", "en" to "How do I create a store and branches?", "ru" to "Как создать магазин и филиалы?", "kk" to "Дүкен мен филиалдарды қалай құруға болады?"))
    put(853L, mapOf("main" to "Open Menu → Stores, add the main store first, then add branches under it. The main store can work like a root warehouse, while branches can sell from their own stock.", "en" to "Open Menu → Stores, add the main store first, then add branches under it. The main store can work like a root warehouse, while branches can sell from their own stock.", "ru" to "Откройте Меню → Магазины, сначала добавьте главный магазин, затем добавьте к нему филиалы. Главный магазин может быть корневым складом, а филиалы могут продавать со своего остатка.", "kk" to "Мәзір → Дүкендер бөлімін ашып, алдымен негізгі дүкенді, кейін оның филиалдарын қосыңыз. Негізгі дүкен орталық қойма сияқты, ал филиалдар өз қалдығынан сата алады."))
    put(854L, mapOf("main" to "What does active store mean?", "en" to "What does active store mean?", "ru" to "Что означает активный магазин?", "kk" to "Белсенді дүкен нені білдіреді?"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart40() {
    put(855L, mapOf("main" to "The active store is the store or branch the app is currently working with. Sales, stock, workers, debtors, cash register and analytics are loaded for that selected store.", "en" to "The active store is the store or branch the app is currently working with. Sales, stock, workers, debtors, cash register and analytics are loaded for that selected store.", "ru" to "Активный магазин — это магазин или филиал, с которым приложение работает сейчас. Продажи, склад, сотрудники, должники, касса и аналитика загружаются именно для него.", "kk" to "Белсенді дүкен — қолданба қазір жұмыс істеп тұрған дүкен немесе филиал. Сату, қойма, қызметкерлер, борышкерлер, касса және аналитика сол дүкен үшін жүктеледі."))
    put(856L, mapOf("main" to "How do workers and permissions work?", "en" to "How do workers and permissions work?", "ru" to "Как работают сотрудники и права доступа?", "kk" to "Қызметкерлер мен рұқсаттар қалай жұмыс істейді?"))
    put(857L, mapOf("main" to "Owners can invite workers or accept employment requests. Each worker can have permissions for stock, sales, returns, supplies, workers, analytics, logs and cash operations.", "en" to "Owners can invite workers or accept employment requests. Each worker can have permissions for stock, sales, returns, supplies, workers, analytics, logs and cash operations.", "ru" to "Владелец может приглашать сотрудников или принимать заявки на работу. Каждому сотруднику можно дать права на склад, продажи, возвраты, поставки, сотрудников, аналитику, журнал и кассовые операции.", "kk" to "Иесі қызметкерлерді шақыра алады немесе жұмыс өтініштерін қабылдай алады. Әр қызметкерге қойма, сату, қайтару, жеткізу, қызметкерлер, аналитика, журнал және касса операциялары бойынша рұқсат беріледі."))
    put(858L, mapOf("main" to "Why do workers need a workshift?", "en" to "Why do workers need a workshift?", "ru" to "Зачем сотрудникам нужна рабочая смена?", "kk" to "Қызметкерлерге жұмыс ауысымы не үшін керек?"))
    put(859L, mapOf("main" to "A workshift is like a signed work session. It links transactions and cash actions to the exact worker and time, which makes reports, logs and responsibility clearer.", "en" to "A workshift is like a signed work session. It links transactions and cash actions to the exact worker and time, which makes reports, logs and responsibility clearer.", "ru" to "Рабочая смена — как подписанная рабочая сессия. Она связывает транзакции и кассовые действия с конкретным сотрудником и временем, поэтому отчёты, журнал и ответственность понятнее.", "kk" to "Жұмыс ауысымы — қол қойылған жұмыс сессиясы сияқты. Ол транзакциялар мен касса әрекеттерін нақты қызметкермен және уақытпен байланыстырады, сондықтан есеп, журнал және жауапкершілік анық болады."))
    put(860L, mapOf("main" to "How do sales work?", "en" to "How do sales work?", "ru" to "Как работают продажи?", "kk" to "Сату қалай жұмыс істейді?"))
    put(861L, mapOf("main" to "In Sale, choose goods from stock or scan a barcode, set quantity, choose payment type, complete the payment and print/share the receipt. Stock is reduced automatically.", "en" to "In Sale, choose goods from stock or scan a barcode, set quantity, choose payment type, complete the payment and print/share the receipt. Stock is reduced automatically.", "ru" to "В продаже выберите товар со склада или сканируйте штрих-код, укажите количество, выберите тип оплаты, завершите оплату и распечатайте/поделитесь чеком. Остаток уменьшится автоматически.", "kk" to "Сатуда қоймадан тауар таңдаңыз немесе штрих-код сканерлеңіз, санын қойыңыз, төлем түрін таңдаңыз, төлемді аяқтап, түбіртекті басып шығарыңыз/бөлісіңіз. Қалдық автоматты азаяды."))
    put(862L, mapOf("main" to "How do returns work?", "en" to "How do returns work?", "ru" to "Как работают возвраты?", "kk" to "Қайтару қалай жұмыс істейді?"))
    put(863L, mapOf("main" to "In Return, choose returned goods and payment method. Stock is increased and the cash register is corrected according to how the refund was paid.", "en" to "In Return, choose returned goods and payment method. Stock is increased and the cash register is corrected according to how the refund was paid.", "ru" to "В возврате выберите возвращаемые товары и способ оплаты. Остаток увеличится, а касса скорректируется в зависимости от способа возврата денег.", "kk" to "Қайтаруда қайтарылатын тауарды және төлем әдісін таңдаңыз. Қалдық көбейеді, ал касса ақша қалай қайтарылғанына қарай түзетіледі."))
    put(864L, mapOf("main" to "How do supply and acceptance transactions work?", "en" to "How do supply and acceptance transactions work?", "ru" to "Как работают поставки и приёмка?", "kk" to "Жеткізу және қабылдау қалай жұмыс істейді?"))
    put(865L, mapOf("main" to "Supply/acceptance adds goods to stock. It is useful when receiving goods from suppliers, correcting warehouse quantity, or creating batches with delivery data.", "en" to "Supply/acceptance adds goods to stock. It is useful when receiving goods from suppliers, correcting warehouse quantity, or creating batches with delivery data.", "ru" to "Поставка/приёмка добавляет товар на склад. Это нужно при получении товара от поставщика, корректировке количества или создании партий с данными поставки.", "kk" to "Жеткізу/қабылдау тауарды қоймаға қосады. Бұл жеткізушіден тауар қабылдағанда, санды түзеткенде немесе жеткізу деректері бар партия жасағанда пайдалы."))
    put(866L, mapOf("main" to "What are stock batches?", "en" to "What are stock batches?", "ru" to "Что такое партии товара?", "kk" to "Тауар партиялары деген не?"))
    put(867L, mapOf("main" to "A batch is a separate delivery of the same goods item. Batches store quantity, supplier, supply price, sale price, dates, shelf priority, status and expiration information.", "en" to "A batch is a separate delivery of the same goods item. Batches store quantity, supplier, supply price, sale price, dates, shelf priority, status and expiration information.", "ru" to "Партия — отдельная поставка одного и того же товара. В ней хранятся количество, поставщик, закупочная и продажная цена, даты, приоритет полки, статус и срок годности.", "kk" to "Партия — бір тауардың бөлек жеткізілімі. Онда сан, жеткізуші, сатып алу және сату бағасы, күндер, сөре басымдығы, статус және жарамдылық деректері сақталады."))
    put(868L, mapOf("main" to "What is a shelf batch?", "en" to "What is a shelf batch?", "ru" to "Что такое партия на полке?", "kk" to "Сөредегі партия деген не?"))
    put(869L, mapOf("main" to "A shelf batch is the batch currently used for sales. It lets you sell from the right delivery first, especially when expiration dates and supplier prices matter.", "en" to "A shelf batch is the batch currently used for sales. It lets you sell from the right delivery first, especially when expiration dates and supplier prices matter.", "ru" to "Партия на полке — партия, из которой сейчас идут продажи. Это помогает продавать из правильной поставки, особенно если важны сроки годности и цены поставщика.", "kk" to "Сөредегі партия — қазір сатылып жатқан партия. Бұл дұрыс жеткізілімнен сатуға көмектеседі, әсіресе жарамдылық мерзімі мен жеткізуші бағасы маңызды болса."))
    put(870L, mapOf("main" to "How do suppliers and supplier prices work?", "en" to "How do suppliers and supplier prices work?", "ru" to "Как работают поставщики и цены поставщиков?", "kk" to "Жеткізушілер мен жеткізуші бағалары қалай жұмыс істейді?"))
    put(871L, mapOf("main" to "Suppliers can be attached to goods. Remembered supplier prices help prefill future batches, so repeated deliveries become faster and less error-prone.", "en" to "Suppliers can be attached to goods. Remembered supplier prices help prefill future batches, so repeated deliveries become faster and less error-prone.", "ru" to "Поставщиков можно привязать к товарам. Сохранённые цены поставщиков помогают автоматически заполнять будущие партии, поэтому повторные поставки быстрее и с меньшим числом ошибок.", "kk" to "Жеткізушілерді тауарларға байланыстыруға болады. Сақталған жеткізуші бағалары болашақ партияларды алдын ала толтырып, қайталанатын жеткізілімді жылдам әрі қателігі аз етеді."))
    put(872L, mapOf("main" to "How do supplier orders work?", "en" to "How do supplier orders work?", "ru" to "Как работают заказы поставщикам?", "kk" to "Жеткізуші тапсырыстары қалай жұмыс істейді?"))
    put(873L, mapOf("main" to "Supplier orders are used to plan what you expect to receive from a supplier. When received, they can become stock batches and update warehouse quantities.", "en" to "Supplier orders are used to plan what you expect to receive from a supplier. When received, they can become stock batches and update warehouse quantities.", "ru" to "Заказы поставщикам нужны для планирования ожидаемой поставки. После приёмки они могут стать партиями товара и обновить складские остатки.", "kk" to "Жеткізуші тапсырыстары күтілетін жеткізілімді жоспарлау үшін керек. Қабылданғаннан кейін олар тауар партиясына айналып, қойма қалдығын жаңарта алады."))
    put(874L, mapOf("main" to "How do receipts, PDF, sharing, WhatsApp and printing work?", "en" to "How do receipts, PDF, sharing, WhatsApp and printing work?", "ru" to "Как работают чеки, PDF, отправка, WhatsApp и печать?", "kk" to "Түбіртек, PDF, бөлісу, WhatsApp және басып шығару қалай жұмыс істейді?"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart41() {
    put(875L, mapOf("main" to "After a transaction, receipt actions can save PDF, open the platform share sheet, send through WhatsApp when available, or send the receipt to a printer/device bridge.", "en" to "After a transaction, receipt actions can save PDF, open the platform share sheet, send through WhatsApp when available, or send the receipt to a printer/device bridge.", "ru" to "После транзакции действия с чеком могут сохранить PDF, открыть системное меню отправки, отправить через WhatsApp при наличии или передать чек в мост принтера/устройства.", "kk" to "Транзакциядан кейін түбіртек әрекеттері PDF сақтайды, жүйелік бөлісу мәзірін ашады, бар болса WhatsApp арқылы жібереді немесе принтер/құрылғы көпіріне береді."))
    put(876L, mapOf("main" to "How does the cash register work?", "en" to "How does the cash register work?", "ru" to "Как работает касса?", "kk" to "Касса қалай жұмыс істейді?"))
    put(877L, mapOf("main" to "The cash register tracks expected cash in the drawer. Cash sales increase it, cash returns decrease it, and authorized users can register cash extractions.", "en" to "The cash register tracks expected cash in the drawer. Cash sales increase it, cash returns decrease it, and authorized users can register cash extractions.", "ru" to "Касса отслеживает ожидаемую наличность в ящике. Продажи наличными увеличивают сумму, возвраты наличными уменьшают, а пользователи с правами могут фиксировать изъятие денег.", "kk" to "Касса жәшіктегі күтілетін қолма-қол ақшаны бақылайды. Қолма-қол сату көбейтеді, қолма-қол қайтару азайтады, ал рұқсаты бар пайдаланушы ақшаны алуын тіркей алады."))
    put(878L, mapOf("main" to "How do debtors and partial payments work?", "en" to "How do debtors and partial payments work?", "ru" to "Как работают должники и частичные оплаты?", "kk" to "Борышкерлер мен ішінара төлемдер қалай жұмыс істейді?"))
    put(879L, mapOf("main" to "Debtors store customers or companies that owe money. You can track debt amount, due date, interest, payment history and partial repayments.", "en" to "Debtors store customers or companies that owe money. You can track debt amount, due date, interest, payment history and partial repayments.", "ru" to "Должники — клиенты или компании, которые должны деньги. Можно отслеживать сумму долга, срок, проценты, историю оплат и частичные платежи.", "kk" to "Борышкерлер — қарызы бар клиенттер немесе компаниялар. Қарыз сомасын, мерзімін, пайызын, төлем тарихын және ішінара төлемдерді бақылауға болады."))
    put(880L, mapOf("main" to "What does Analytics show?", "en" to "What does Analytics show?", "ru" to "Что показывает аналитика?", "kk" to "Аналитика нені көрсетеді?"))
    put(881L, mapOf("main" to "Analytics summarizes revenue, returns, supply cost, estimated profit, payment mix, debt, top items, sales by time, stock risk, expiring batches, supplier activity and worker performance.", "en" to "Analytics summarizes revenue, returns, supply cost, estimated profit, payment mix, debt, top items, sales by time, stock risk, expiring batches, supplier activity and worker performance.", "ru" to "Аналитика показывает выручку, возвраты, себестоимость поставок, примерную прибыль, структуру оплат, долги, топ товаров, продажи по времени, риски склада, истекающие партии, поставщиков и эффективность сотрудников.", "kk" to "Аналитика түсім, қайтару, жеткізу құны, болжамды пайда, төлем құрылымы, қарыз, үздік тауарлар, уақыт бойынша сату, қойма тәуекелі, мерзімі бітетін партиялар, жеткізушілер және қызметкерлер тиімділігін көрсетеді."))
    put(882L, mapOf("main" to "How do finances and subscriptions work?", "en" to "How do finances and subscriptions work?", "ru" to "Как работают финансы и подписки?", "kk" to "Қаржы мен жазылымдар қалай жұмыс істейді?"))
    put(883L, mapOf("main" to "Finances show wallet balance, top-ups, ledger entries and subscription charges. Store subscriptions unlock plan-based functionality and renewal state.", "en" to "Finances show wallet balance, top-ups, ledger entries and subscription charges. Store subscriptions unlock plan-based functionality and renewal state.", "ru" to "Финансы показывают баланс кошелька, пополнения, записи баланса и списания подписки. Подписка магазина включает функциональность по тарифу и хранит состояние продления.", "kk" to "Қаржы әмиян балансын, толықтыруды, журнал жазбаларын және жазылым төлемдерін көрсетеді. Дүкен жазылымы тариф функцияларын және ұзарту күйін басқарады."))
    put(884L, mapOf("main" to "What happens if the server or internet is unavailable?", "en" to "What happens if the server or internet is unavailable?", "ru" to "Что будет, если сервер или интернет недоступны?", "kk" to "Сервер немесе интернет қолжетімсіз болса не болады?"))
    put(885L, mapOf("main" to "The app keeps cached data where possible and shows connection notifications. When the server returns, realtime sync refreshes stores, stock, transactions, workers, finance and messages.", "en" to "The app keeps cached data where possible and shows connection notifications. When the server returns, realtime sync refreshes stores, stock, transactions, workers, finance and messages.", "ru" to "Приложение использует кэшированные данные, где это возможно, и показывает уведомления о соединении. Когда сервер возвращается, realtime-синхронизация обновляет магазины, склад, транзакции, сотрудников, финансы и сообщения.", "kk" to "Қолданба мүмкін жерде кэштелген деректерді қолданады және байланыс туралы хабарлама көрсетеді. Сервер қайта қосылғанда realtime синхрондау дүкендерді, қойманы, транзакцияларды, қызметкерлерді, қаржыны және хабарламаларды жаңартады."))
    put(886L, mapOf("main" to "How does realtime sync work?", "en" to "How does realtime sync work?", "ru" to "Как работает realtime-синхронизация?", "kk" to "Realtime синхрондау қалай жұмыс істейді?"))
    put(887L, mapOf("main" to "The app keeps a WebSocket connection to the server. After changes, connected clients refresh affected data, so cashier, owner and branch devices stay closer to the same state.", "en" to "The app keeps a WebSocket connection to the server. After changes, connected clients refresh affected data, so cashier, owner and branch devices stay closer to the same state.", "ru" to "Приложение держит WebSocket-соединение с сервером. После изменений подключённые клиенты обновляют затронутые данные, поэтому кассир, владелец и филиалы остаются ближе к одному состоянию.", "kk" to "Қолданба сервермен WebSocket байланысын ұстайды. Өзгерістерден кейін қосылған клиенттер тиісті деректерді жаңартады, сондықтан кассир, иесі және филиал құрылғылары бір күйге жақын болады."))
    put(888L, mapOf("main" to "How do scanners, printers and devices work?", "en" to "How do scanners, printers and devices work?", "ru" to "Как работают сканеры, принтеры и устройства?", "kk" to "Сканерлер, принтерлер және құрылғылар қалай жұмыс істейді?"))
    put(889L, mapOf("main" to "Device screens and platform bridges handle barcode scanners, receipt printers, Bluetooth/system settings and platform-specific saving, sharing and printing.", "en" to "Device screens and platform bridges handle barcode scanners, receipt printers, Bluetooth/system settings and platform-specific saving, sharing and printing.", "ru" to "Экраны устройств и платформенные мосты работают со сканерами штрих-кодов, принтерами чеков, Bluetooth/системными настройками, сохранением, отправкой и печатью на каждой платформе.", "kk" to "Құрылғы экрандары мен платформалық көпірлер штрих-код сканерлері, түбіртек принтерлері, Bluetooth/жүйелік баптаулар, сақтау, бөлісу және басып шығарумен жұмыс істейді."))
    put(890L, mapOf("main" to "What is local branch network?", "en" to "What is local branch network?", "ru" to "Что такое локальная сеть филиалов?", "kk" to "Жергілікті филиал желісі деген не?"))
    put(891L, mapOf("main" to "Local branch network is meant for branch devices that can exchange queued local operations and snapshots when direct server availability is limited. It should still reconcile with the server as authority.", "en" to "Local branch network is meant for branch devices that can exchange queued local operations and snapshots when direct server availability is limited. It should still reconcile with the server as authority.", "ru" to "Локальная сеть филиалов нужна для устройств филиала, которые могут обмениваться локальными операциями и снимками, когда прямой доступ к серверу ограничен. Но финальным источником истины остаётся сервер.", "kk" to "Жергілікті филиал желісі серверге тікелей қолжетімділік шектеулі кезде жергілікті операциялар мен көшірмелерді алмасатын филиал құрылғылары үшін керек. Бірақ соңғы беделді дереккөз сервер болып қалады."))
    put(892L, mapOf("main" to "How do language and theme preferences work?", "en" to "How do language and theme preferences work?", "ru" to "Как работают язык и тема приложения?", "kk" to "Қолданба тілі мен тақырыбы қалай жұмыс істейді?"))
    put(893L, mapOf("main" to "Language and theme are saved locally and can also be synced with the user account. Login-screen choices can intentionally override saved account preferences.", "en" to "Language and theme are saved locally and can also be synced with the user account. Login-screen choices can intentionally override saved account preferences.", "ru" to "Язык и тема сохраняются локально и могут синхронизироваться с аккаунтом. Выбор на экране входа может намеренно переопределить сохранённые настройки аккаунта.", "kk" to "Тіл мен тақырып жергілікті сақталады және аккаунтпен синхрондала алады. Кіру экранындағы таңдау сақталған аккаунт баптауларын әдейі ауыстыра алады."))
    put(894L, mapOf("main" to "How is account security handled?", "en" to "How is account security handled?", "ru" to "Как устроена безопасность аккаунта?", "kk" to "Аккаунт қауіпсіздігі қалай қамтамасыз етіледі?"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart42() {
    put(895L, mapOf("main" to "Security sessions show devices signed into the account. You can revoke unfamiliar sessions, and tokens are refreshed securely by platform storage.", "en" to "Security sessions show devices signed into the account. You can revoke unfamiliar sessions, and tokens are refreshed securely by platform storage.", "ru" to "Сеансы безопасности показывают устройства, вошедшие в аккаунт. Незнакомые сеансы можно завершить, а токены безопасно обновляются через хранилище платформы.", "kk" to "Қауіпсіздік сеанстары аккаунтқа кірген құрылғыларды көрсетеді. Бейтаныс сеанстарды тоқтатуға болады, ал токендер платформа сақтауымен қауіпсіз жаңартылады."))
    put(896L, mapOf("main" to "What should I do if stock or transaction data looks wrong?", "en" to "What should I do if stock or transaction data looks wrong?", "ru" to "Что делать, если склад или транзакции выглядят неверно?", "kk" to "Қойма немесе транзакция деректері қате көрінсе не істеу керек?"))
    put(897L, mapOf("main" to "Refresh data, check active store/branch, check operation logs, review batches and transaction history, then contact support with store, time, item barcode and screenshots if needed.", "en" to "Refresh data, check active store/branch, check operation logs, review batches and transaction history, then contact support with store, time, item barcode and screenshots if needed.", "ru" to "Обновите данные, проверьте активный магазин/филиал, журнал операций, партии и историю транзакций. Затем напишите в поддержку, указав магазин, время, штрих-код товара и скриншоты при необходимости.", "kk" to "Деректерді жаңартыңыз, белсенді дүкен/филиалды, операциялар журналын, партияларды және транзакция тарихын тексеріңіз. Кейін қолдауға дүкенді, уақытты, тауар штрих-кодын және қажет болса скриншоттарды жіберіңіз."))
    put(898L, mapOf("main" to "How are goods categories and global goods used?", "en" to "How are goods categories and global goods used?", "ru" to "Как используются категории и глобальные товары?", "kk" to "Санаттар мен глобал тауарлар қалай қолданылады?"))
    put(899L, mapOf("main" to "Generic categories and goods templates help start faster. Store-specific goods can still have their own names, barcodes, prices, units, conditions and supplier data.", "en" to "Generic categories and goods templates help start faster. Store-specific goods can still have their own names, barcodes, prices, units, conditions and supplier data.", "ru" to "Общие категории и шаблоны товаров помогают быстрее начать работу. Товары конкретного магазина всё равно могут иметь свои названия, штрих-коды, цены, единицы, условия и данные поставщиков.", "kk" to "Жалпы санаттар мен тауар үлгілері тез бастауға көмектеседі. Нақты дүкен тауарларының өз атауы, штрих-коды, бағасы, бірлігі, жағдайы және жеткізуші деректері болуы мүмкін."))
    put(900L, mapOf("main" to "How will manufacturers, suppliers, stores and buyers connect?", "en" to "How will manufacturers, suppliers, stores and buyers connect?", "ru" to "Как будут связаны производители, поставщики, магазины и покупатели?", "kk" to "Өндірушілер, жеткізушілер, дүкендер және сатып алушылар қалай байланысады?"))
    put(901L, mapOf("main" to "The project is being built as a chain: manufacturer/supplier data can feed stores, stores manage branches and stock, and future buyer flows can show goods and orders outside the current account.", "en" to "The project is being built as a chain: manufacturer/supplier data can feed stores, stores manage branches and stock, and future buyer flows can show goods and orders outside the current account.", "ru" to "Проект строится как цепочка: данные производителя/поставщика могут идти в магазины, магазины управляют филиалами и складом, а будущие покупательские сценарии смогут показывать товары и заказы за пределами текущего аккаунта.", "kk" to "Жоба тізбек ретінде құрылуда: өндіруші/жеткізуші деректері дүкендерге түседі, дүкендер филиалдар мен қойманы басқарады, ал болашақ сатып алушы сценарийлері тауарлар мен тапсырыстарды аккаунттан тыс көрсете алады."))
    put(902L, mapOf("main" to "How should I prepare before using AITA in a real store day?", "en" to "How should I prepare before using AITA in a real store day?", "ru" to "Как подготовиться перед реальным рабочим днём в AITA?", "kk" to "AITA-ны нақты жұмыс күнінде қолданар алдында қалай дайындалу керек?"))
    put(903L, mapOf("main" to "Create the store and branches, add workers and permissions, add goods and batches, test scanner/printer, make a few test transactions, check receipts and confirm analytics/cash register behavior.", "en" to "Create the store and branches, add workers and permissions, add goods and batches, test scanner/printer, make a few test transactions, check receipts and confirm analytics/cash register behavior.", "ru" to "Создайте магазин и филиалы, добавьте сотрудников и права, товары и партии, проверьте сканер/принтер, сделайте несколько тестовых транзакций, проверьте чеки, аналитику и кассу.", "kk" to "Дүкен мен филиалдарды құрып, қызметкерлер мен рұқсаттарды, тауарлар мен партияларды қосыңыз, сканер/принтерді тексеріңіз, бірнеше сынақ транзакция жасап, түбіртек, аналитика және касса жұмысын растаңыз."))
    put(904L, mapOf("main" to "What should I include when contacting support?", "en" to "What should I include when contacting support?", "ru" to "Что указать при обращении в поддержку?", "kk" to "Қолдауға жазғанда не көрсету керек?"))
    put(905L, mapOf("main" to "Send what you were doing, store/branch, approximate time, device/platform, barcode or transaction id, screenshots, and whether the issue repeats after refresh or reconnect.", "en" to "Send what you were doing, store/branch, approximate time, device/platform, barcode or transaction id, screenshots, and whether the issue repeats after refresh or reconnect.", "ru" to "Напишите, что вы делали, магазин/филиал, примерное время, устройство/платформу, штрих-код или ID транзакции, скриншоты и повторяется ли проблема после обновления или переподключения.", "kk" to "Не істегеніңізді, дүкен/филиалды, шамамен уақытты, құрылғы/платформаны, штрих-код немесе транзакция ID, скриншоттарды және жаңартудан/қайта қосылудан кейін қайталана ма — соны жіберіңіз."))
    put(906L, mapOf("main" to "Working…", "en" to "Working…", "ru" to "Выполняется…", "kk" to "Орындалуда…"))
    put(907L, mapOf("main" to "Sending…", "en" to "Sending…", "ru" to "Отправка…", "kk" to "Жіберілуде…"))
    put(908L, mapOf("main" to "Starting workshift…", "en" to "Starting workshift…", "ru" to "Смена запускается…", "kk" to "Ауысым басталуда…"))
    put(909L, mapOf("main" to "Syncing saved notifications…", "en" to "Syncing saved notifications…", "ru" to "Сохранённые уведомления синхронизируются…", "kk" to "Сақталған хабарламалар синхрондалуда…"))
    put(910L, mapOf("main" to "Interface scale", "en" to "Interface scale", "ru" to "Масштаб интерфейса", "kk" to "Интерфейс масштабы"))
    put(911L, mapOf("main" to "Default", "en" to "Default", "ru" to "Обычный", "kk" to "Әдеттегі"))
    put(912L, mapOf("main" to "Big", "en" to "Big", "ru" to "Крупный", "kk" to "Үлкен"))
    put(913L, mapOf("main" to "Server is not connected. Working from saved local data.", "en" to "Server is not connected. Working from saved local data.", "ru" to "Сервер не подключён. Работа продолжается с сохранёнными локальными данными.", "kk" to "Сервер қосылмаған. Сақталған жергілікті деректермен жұмыс жалғасуда."))
    put(914L, mapOf("main" to "Server is not connected. Branch local network mode is active.", "en" to "Server is not connected. Branch local network mode is active.", "ru" to "Сервер не подключён. Активен режим локальной сети филиала.", "kk" to "Сервер қосылмаған. Филиалдың жергілікті желі режимі қосулы."))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart43() {
    put(915L, mapOf("main" to "Current comfortable size", "en" to "Current comfortable size", "ru" to "Текущий удобный размер", "kk" to "Қазіргі ыңғайлы өлшем"))
    put(916L, mapOf("main" to "Larger text and controls for easier reading", "en" to "Larger text and controls for easier reading", "ru" to "Увеличенные текст и элементы управления для более лёгкого чтения", "kk" to "Оқуға жеңіл болу үшін мәтін мен басқару элементтері үлкейтіледі"))
    put(917L, mapOf("main" to "inactive", "en" to "inactive", "ru" to "неактивна", "kk" to "белсенді емес"))
    put(918L, mapOf("main" to "past due", "en" to "past due", "ru" to "просрочена", "kk" to "мерзімі өтті"))
    put(919L, mapOf("main" to "cancelled", "en" to "cancelled", "ru" to "отменена", "kk" to "бас тартылды"))
    put(920L, mapOf("main" to "Promos", "en" to "Promos", "ru" to "Промо", "kk" to "Промо"))
    put(921L, mapOf("main" to "Add promo", "en" to "Add promo", "ru" to "Добавить промо", "kk" to "Промо қосу"))
    put(922L, mapOf("main" to "No promos yet", "en" to "No promos yet", "ru" to "Промо пока нет", "kk" to "Әзірге промо жоқ"))
    put(923L, mapOf("main" to "Promo title", "en" to "Promo title", "ru" to "Название промо", "kk" to "Промо атауы"))
    put(924L, mapOf("main" to "Discount", "en" to "Discount", "ru" to "Скидка", "kk" to "Жеңілдік"))
    put(925L, mapOf("main" to "Special price", "en" to "Special price", "ru" to "Спеццена", "kk" to "Арнайы баға"))
    put(926L, mapOf("main" to "Restriction", "en" to "Restriction", "ru" to "Ограничение", "kk" to "Шектеу"))
    put(927L, mapOf("main" to "Percent", "en" to "Percent", "ru" to "Процент", "kk" to "Пайыз"))
    put(928L, mapOf("main" to "Fixed amount", "en" to "Fixed amount", "ru" to "Фиксированная сумма", "kk" to "Бекітілген сома"))
    put(929L, mapOf("main" to "New price", "en" to "New price", "ru" to "Новая цена", "kk" to "Жаңа баға"))
    put(930L, mapOf("main" to "Value", "en" to "Value", "ru" to "Значение", "kk" to "Мән"))
    put(931L, mapOf("main" to "Minimum quantity", "en" to "Minimum quantity", "ru" to "Минимальное количество", "kk" to "Ең аз саны"))
    put(932L, mapOf("main" to "Starts", "en" to "Starts", "ru" to "Начало", "kk" to "Басталуы"))
    put(933L, mapOf("main" to "Ends", "en" to "Ends", "ru" to "Окончание", "kk" to "Аяқталуы"))
    put(934L, mapOf("main" to "Apply to all batches of this supplier", "en" to "Apply to all batches of this supplier", "ru" to "Применить ко всем партиям этого поставщика", "kk" to "Осы жеткізушінің барлық партиясына қолдану"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart44() {
    put(935L, mapOf("main" to "Active promo", "en" to "Active promo", "ru" to "Активное промо", "kk" to "Белсенді промо"))
    put(936L, mapOf("main" to "Promo period", "en" to "Promo period", "ru" to "Промо-период", "kk" to "Промо-кезең"))
    put(937L, mapOf("main" to "Restriction not satisfied", "en" to "Restriction not satisfied", "ru" to "Ограничение не выполнено", "kk" to "Шектеу орындалмады"))
    put(938L, mapOf("main" to "All transaction types", "en" to "All transaction types", "ru" to "Все типы транзакций", "kk" to "Барлық транзакция түрлері"))
    put(939L, mapOf("main" to "Promo price", "en" to "Promo price", "ru" to "Промо-цена", "kk" to "Промо баға"))
    put(950L, mapOf("main" to "Clear period", "en" to "Clear period", "ru" to "Очистить период", "kk" to "Кезеңді тазалау"))
    put(951L, mapOf("main" to "Add promo note translation", "en" to "Add promo note translation", "ru" to "Добавить перевод заметки промо", "kk" to "Промо ескертпесінің аудармасын қосу"))
    put(952L, mapOf("main" to "Add batch note translation", "en" to "Add batch note translation", "ru" to "Добавить перевод заметки партии", "kk" to "Партия ескертпесінің аудармасын қосу"))
    put(953L, mapOf("main" to "Supplier order", "en" to "Supplier order", "ru" to "Заказ поставщику", "kk" to "Жеткізушіге тапсырыс"))
    put(954L, mapOf("main" to "Create supplier order", "en" to "Create supplier order", "ru" to "Создать заказ поставщику", "kk" to "Жеткізушіге тапсырыс жасау"))
    put(955L, mapOf("main" to "Send to supplier", "en" to "Send to supplier", "ru" to "Отправить поставщику", "kk" to "Жеткізушіге жіберу"))
    put(956L, mapOf("main" to "Desired delivery", "en" to "Desired delivery", "ru" to "Желаемая доставка", "kk" to "Қалаулы жеткізу"))
    put(957L, mapOf("main" to "Desired expiration", "en" to "Desired expiration", "ru" to "Желаемый срок годности", "kk" to "Қалаулы жарамдылық мерзімі"))
    put(958L, mapOf("main" to "Expected supply price", "en" to "Expected supply price", "ru" to "Ожидаемая цена поставки", "kk" to "Күтілетін жеткізу бағасы"))
    put(959L, mapOf("main" to "Receive order", "en" to "Receive order", "ru" to "Принять заказ", "kk" to "Тапсырысты қабылдау"))
    put(960L, mapOf("main" to "Ordered quantity", "en" to "Ordered quantity", "ru" to "Заказанное количество", "kk" to "Тапсырыс саны"))
    put(961L, mapOf("main" to "Supplier response ready", "en" to "Supplier response ready", "ru" to "Ответ поставщика готов", "kk" to "Жеткізуші жауабы дайын"))
    put(962L, mapOf("main" to "No supplier orders yet", "en" to "No supplier orders yet", "ru" to "Заказов поставщику пока нет", "kk" to "Әзірге жеткізушіге тапсырыс жоқ"))
    put(963L, mapOf("main" to "Store and supplier order bridge", "en" to "Store and supplier order bridge", "ru" to "Связка заказов магазина и поставщика", "kk" to "Дүкен мен жеткізуші тапсырыстарының байланысы"))
    put(964L, mapOf("main" to "This order keeps store-side data ready for the future supplier app: supplier, quantities, expected price, delivery dates, notes and receiving batches.", "en" to "This order keeps store-side data ready for the future supplier app: supplier, quantities, expected price, delivery dates, notes and receiving batches.", "ru" to "Заказ хранит данные магазина для будущего приложения поставщика: поставщик, количество, ожидаемая цена, даты доставки, заметки и приёмка партиями.", "kk" to "Тапсырыс болашақ жеткізуші қолданбасы үшін дүкен деректерін сақтайды: жеткізуші, сан, күтілетін баға, жеткізу күндері, ескертпелер және партиямен қабылдау."))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart45() {
    put(965L, mapOf("main" to "Cancel order", "en" to "Cancel order", "ru" to "Отменить заказ", "kk" to "Тапсырыстан бас тарту"))
    put(966L, mapOf("main" to "Draft", "en" to "Draft", "ru" to "Черновик", "kk" to "Жоба"))
    put(967L, mapOf("main" to "Sent", "en" to "Sent", "ru" to "Отправлен", "kk" to "Жіберілді"))
    put(968L, mapOf("main" to "Seen by supplier", "en" to "Seen by supplier", "ru" to "Просмотрен поставщиком", "kk" to "Жеткізуші көрді"))
    put(969L, mapOf("main" to "Confirmed", "en" to "Confirmed", "ru" to "Подтверждён", "kk" to "Расталды"))
    put(970L, mapOf("main" to "Packed", "en" to "Packed", "ru" to "Собран", "kk" to "Жиналды"))
    put(971L, mapOf("main" to "In delivery", "en" to "In delivery", "ru" to "В доставке", "kk" to "Жеткізілуде"))
    put(972L, mapOf("main" to "Partially delivered", "en" to "Partially delivered", "ru" to "Частично доставлен", "kk" to "Ішінара жеткізілді"))
    put(973L, mapOf("main" to "Delivered", "en" to "Delivered", "ru" to "Доставлен", "kk" to "Жеткізілді"))
    put(974L, mapOf("main" to "Issue reported", "en" to "Issue reported", "ru" to "Есть проблема", "kk" to "Мәселе тіркелді"))
    put(975L, mapOf("main" to "Cancelled", "en" to "Cancelled", "ru" to "Отменён", "kk" to "Бас тартылды"))
    put(979L, mapOf("main" to "Receive full quantity", "en" to "Receive full quantity", "ru" to "Принять всё количество", "kk" to "Барлық санды қабылдау"))
    put(980L, mapOf("main" to "Add order note translation", "en" to "Add order note translation", "ru" to "Добавить перевод заметки заказа", "kk" to "Тапсырыс ескертпесінің аудармасын қосу"))
    put(981L, mapOf("main" to "Select date", "en" to "Select date", "ru" to "Выберите дату", "kk" to "Күнді таңдаңыз"))
    put(982L, mapOf("main" to "Camera barcode scanner", "en" to "Camera barcode scanner", "ru" to "Сканер штрих-кодов камерой", "kk" to "Камерамен штрих-код сканері"))
    put(983L, mapOf("main" to "Camera permission lets this scanner read item and transaction barcodes from the camera preview. AITA uses the camera only after you tap the camera scanner button.", "en" to "Camera permission lets this scanner read item and transaction barcodes from the camera preview. AITA uses the camera only after you tap the camera scanner button.", "ru" to "Разрешение на камеру нужно, чтобы сканер считывал штрих-коды товаров и транзакций с предпросмотра камеры. AITA включает камеру только после нажатия кнопки сканера камерой.", "kk" to "Камераға рұқсат сканерге камера көрінісінен тауар және транзакция штрих-кодтарын оқуға керек. AITA камераны тек камера сканері түймесін басқаннан кейін қолданады."))
    put(984L, mapOf("main" to "Camera access was denied", "en" to "Camera access was denied", "ru" to "Доступ к камере запрещён", "kk" to "Камераға рұқсат берілмеді"))
    put(985L, mapOf("main" to "Camera access is blocked. Open app settings, allow camera access for AITA, then return and tap the camera scanner again.", "en" to "Camera access is blocked. Open app settings, allow camera access for AITA, then return and tap the camera scanner again.", "ru" to "Доступ к камере заблокирован. Откройте настройки приложения, разрешите камеру для AITA, затем вернитесь и снова нажмите сканер камерой.", "kk" to "Камераға рұқсат бұғатталған. Қолданба баптауларын ашып, AITA үшін камераға рұқсат беріңіз, содан кейін қайтып келіп камера сканерін қайта басыңыз."))
    put(986L, mapOf("main" to "Aim the camera at a barcode. The same barcode is ignored for 3 seconds to avoid accidental repeats.", "en" to "Aim the camera at a barcode. The same barcode is ignored for 3 seconds to avoid accidental repeats.", "ru" to "Наводите камеру на штрих-код. Один и тот же штрих-код игнорируется 3 секунды, чтобы не добавлять его случайно несколько раз.", "kk" to "Камераны штрих-кодқа бағыттаңыз. Қайталанбауы үшін бірдей штрих-код 3 секунд еленбейді."))
    put(987L, mapOf("main" to "Switch camera", "en" to "Switch camera", "ru" to "Сменить камеру", "kk" to "Камераны ауыстыру"))
}

internal fun MutableMap<Long, Map<String, String>>.putBundledLocalizedStringFallbacksPart46() {
    put(988L, mapOf("main" to "Torch", "en" to "Torch", "ru" to "Фонарик", "kk" to "Шам"))
    put(989L, mapOf("main" to "Close scanner", "en" to "Close scanner", "ru" to "Закрыть сканер", "kk" to "Сканерді жабу"))
    put(990L, mapOf("main" to "Adding", "en" to "Adding", "ru" to "Добавление", "kk" to "Қосу"))
    put(991L, mapOf("main" to "Editing", "en" to "Editing", "ru" to "Редактирование", "kk" to "Өңдеу"))
    put(992L, mapOf("main" to "Deleting", "en" to "Deleting", "ru" to "Удаление", "kk" to "Жою"))
    put(993L, mapOf("main" to "Completion", "en" to "Completion", "ru" to "Завершение", "kk" to "Аяқтау"))
    put(994L, mapOf("main" to "Cash extraction", "en" to "Cash extraction", "ru" to "Изъятие наличных", "kk" to "Қолма-қол ақшаны алу"))
    put(995L, mapOf("main" to "Workshift start", "en" to "Workshift start", "ru" to "Начало смены", "kk" to "Ауысымды бастау"))
    put(996L, mapOf("main" to "Workshift end", "en" to "Workshift end", "ru" to "Завершение смены", "kk" to "Ауысымды аяқтау"))
    put(997L, mapOf("main" to "Acceptance", "en" to "Acceptance", "ru" to "Принятие", "kk" to "Қабылдау"))
    put(998L, mapOf("main" to "Decline", "en" to "Decline", "ru" to "Отклонение", "kk" to "Бас тарту"))
    put(999L, mapOf("main" to "Invitation", "en" to "Invitation", "ru" to "Приглашение", "kk" to "Шақыру"))
    put(1000L, mapOf("main" to "Movement", "en" to "Movement", "ru" to "Перемещение", "kk" to "Жылжыту"))
    put(1001L, mapOf("main" to "Handheld barcode scanner", "en" to "Handheld barcode scanner", "ru" to "Ручной сканер штрих-кодов", "kk" to "Қол штрих-код сканері"))
    put(2300L, mapOf("main" to "Verified address", "en" to "Verified address", "ru" to "Проверенный адрес", "kk" to "Тексерілген мекенжай"))
    put(2301L, mapOf("main" to "Address map preview", "en" to "Address map preview", "ru" to "Адрес на карте", "kk" to "Мекенжайдың картадағы көрінісі"))
    put(2302L, mapOf("main" to "Open on map", "en" to "Open on map", "ru" to "Открыть на карте", "kk" to "Картадан ашу"))
    put(2303L, mapOf("main" to "Map preview is temporarily unavailable. The verified coordinates are still saved.", "en" to "Map preview is temporarily unavailable. The verified coordinates are still saved.", "ru" to "Предпросмотр карты временно недоступен. Проверенные координаты всё равно будут сохранены.", "kk" to "Картаны алдын ала көру уақытша қолжетімсіз. Тексерілген координаттар бәрібір сақталады."))
    put(2304L, mapOf("main" to "Start typing and select a verified address", "en" to "Start typing and select a verified address", "ru" to "Начните вводить и выберите проверенный адрес", "kk" to "Теруді бастап, тексерілген мекенжайды таңдаңыз"))
    put(2305L, mapOf("main" to "Select an address from suggestions", "en" to "Select an address from suggestions", "ru" to "Выберите адрес из подсказок", "kk" to "Мекенжайды ұсыныстардан таңдаңыз"))
    put(2306L, mapOf("main" to "Could not load address suggestions", "en" to "Could not load address suggestions", "ru" to "Не удалось загрузить подсказки адресов", "kk" to "Мекенжай ұсыныстарын жүктеу мүмкін болмады"))
    put(2307L, mapOf("main" to "No matching addresses found", "en" to "No matching addresses found", "ru" to "Подходящие адреса не найдены", "kk" to "Сәйкес мекенжайлар табылмады"))
    put(2308L, mapOf("main" to "Could not verify this address. Select another suggestion or try again.", "en" to "Could not verify this address. Select another suggestion or try again.", "ru" to "Не удалось проверить этот адрес. Выберите другую подсказку или повторите попытку.", "kk" to "Бұл мекенжайды тексеру мүмкін болмады. Басқа ұсынысты таңдаңыз немесе қайталап көріңіз."))
    put(2309L, mapOf("main" to "Opening maps is unavailable on this device", "en" to "Opening maps is unavailable on this device", "ru" to "Открытие карты недоступно на этом устройстве", "kk" to "Бұл құрылғыда картаны ашу қолжетімсіз"))
    put(2310L, mapOf("main" to "Choose one of the verified address suggestions before saving", "en" to "Choose one of the verified address suggestions before saving", "ru" to "Перед сохранением выберите один из проверенных вариантов адреса", "kk" to "Сақтамас бұрын тексерілген мекенжай нұсқаларының бірін таңдаңыз"))
    put(2311L, mapOf("main" to "The country changed. Select the address again.", "en" to "The country changed. Select the address again.", "ru" to "Страна изменилась. Выберите адрес заново.", "kk" to "Ел өзгерді. Мекенжайды қайта таңдаңыз."))
}

fun AppConfiguration.localizedStringResource(
    id: Long,
    fallback: String
): String {
    val language = stateValues.appLanguage

    return stateValues.strings.extractString(id, language)
        ?: bundledLocalizedStringFallbacks[id]?.get(language)
        ?: stateValues.strings.extractString(id, "main")
        ?: bundledLocalizedStringFallbacks[id]?.get("main")
        ?: stateValues.strings.extractString(id, "en")
        ?: bundledLocalizedStringFallbacks[id]?.get("en")
        ?: fallback
}

suspend fun loadResourceDimensions(): List<StylizedDimensionGroupDataModel> {
    return runCatching {
        decodeBundledResourcePayload<List<StylizedDimensionGroupDataModel>>(
            Res.readBytes("files/assets/values/dimensions.json").decodeToString()
        )
    }.getOrDefault(emptyList())
}

suspend fun loadResourceColors(): List<StylizedColorGroupDataModel> {
    return runCatching {
        decodeBundledResourcePayload<List<StylizedColorGroupDataModel>>(
            Res.readBytes("files/assets/values/colors.json").decodeToString()
        )
    }.getOrDefault(emptyList())
}


suspend fun loadResourceDrawablePaths(): List<StylizedDrawablePathsGroupDataModel> {
    return runCatching {
        decodeBundledResourcePayload<List<StylizedDrawablePathsGroupDataModel>>(
            Res.readBytes("files/assets/drawable/drawables.json").decodeToString()
        )
    }.getOrDefault(emptyList())
}

internal fun Color.softAppBackgroundColor(): Color {
    return when {
        red > 0.97f && green > 0.97f && blue > 0.97f -> Color(0xFFF4F5F7)
        red < 0.04f && green < 0.04f && blue < 0.04f -> Color(0xFF121316)
        else -> this
    }
}

internal fun <T> List<T>.clientPaged(page: Int, pageSize: Int): List<T> {
    if (isEmpty()) return emptyList()
    val safePageSize = pageSize.coerceAtLeast(1)
    val safePage = page.coerceAtLeast(0)
    val from = (safePage * safePageSize).coerceAtMost(size)
    val to = (from + safePageSize).coerceAtMost(size)
    return subList(from, to)
}

internal fun Int.totalClientPages(pageSize: Int): Int {
    val safePageSize = pageSize.coerceAtLeast(1)
    return if (this <= 0) 0 else ((this - 1) / safePageSize) + 1
}

