package kz.aita

import kotlinx.serialization.Serializable
import io.ktor.http.HttpMethod

/** A parent assignment is copied; a branch assignment is moved. History keeps the old membership. */
@Serializable
data class WorkerPlacementCommand(
    val operationId: String,
    val membershipId: String,
    val sourceStoreId: String,
    val destinationStoreId: String,
)

fun workerWorkplaces(worker: StoreWorkerDataModel, stores: List<StoreDataModel>): List<StoreDataModel> {
    val all = stores.flattenStoresWithBranches().distinctBy { it.id }
    val source = all.firstOrNull { it.id == worker.storeId } ?: return emptyList()
    return all.filter { it.id == source.id || (source.parentStoreId == null && it.parentStoreId == source.id) }
}

/** Prefer an explicit branch membership so shift-password selection is deterministic on every target. */
fun preferredWorkerMembership(memberships: List<StoreWorkerDataModel>, user: String, store: String, parent: String?) =
    memberships.filter { it.isActive && it.userId == user && (it.storeId == store || it.storeId == parent) }
        .sortedWith(compareBy<StoreWorkerDataModel> { it.storeId != store }.thenBy { it.id }).firstOrNull()

suspend fun placeWorker(command: WorkerPlacementCommand, generation: Long): DataState<StoreWorkerDataModel> {
    val response = networkRequest<StoreWorkerDataModel, WorkerPlacementCommand>(
        method = HttpMethod.Post,
        endpointUrl = globalAppConfigurationState.payloadValue.getStoreWorkersPath.first.removeSuffix("/store/get") + "/placement",
        body = command, expectedSessionGeneration = generation)
    if (!authenticatedSessionGenerationIsCurrent(generation)) return DataState.Empty()
    if (response.negative || response.payload == null) return DataState.Empty(response.message)
    getMyWorkerMemberships()
    activeStoreIdState.value?.let { getStoreWorkers(it) }
    getStores()
    getUser(forceLogOut = false, refreshRelatedData = false)
    return DataState.Success(response.payload, response.message)
}

fun workerPlacementMessage(key: String): List<LocalizedStringDataModel> {
    val t = when (key) {
        "all_stores" -> listOf("All workplaces", "Все места работы", "Барлық жұмыс орындары", "Бардык иштөө жерлери", "Ҳамаи ҷойҳои кор", "Barcha ish joylari")
        "workplace" -> listOf("Workplace", "Место работы", "Жұмыс орны", "Иштөө жери", "Ҷои кор", "Ish joyi")
        "choose" -> listOf("Choose a branch", "Выбрать филиал", "Филиалды таңдау", "Филиалды тандоо", "Интихоби филиал", "Filialni tanlash")
        "saved" -> listOf("Your workplace choice is saved on this device and in your account.", "Выбор места работы сохраняется на устройстве и в аккаунте.", "Жұмыс орны құрылғыда және аккаунтта сақталады.", "Иштөө жери түзмөктө жана аккаунтта сакталат.", "Ҷои кор дар дастгоҳ ва ҳисоб сабт мешавад.", "Ish joyi qurilmada va hisobda saqlanadi.")
        "assign" -> listOf("Assign to a branch", "Назначить в филиал", "Филиалға тағайындау", "Филиалга дайындоо", "Таъин ба филиал", "Filialga tayinlash")
        "move" -> listOf("Transfer to a branch", "Перевести в филиал", "Филиалға ауыстыру", "Филиалга которуу", "Гузаронидан ба филиал", "Filialga o‘tkazish")
        "parent_help" -> listOf("The parent employment stays active, with access to its branches. A separate assignment keeps this branch's shift history.", "Работа в головном магазине и доступ к его филиалам сохраняются. Отдельное назначение ведёт историю смен этого филиала.", "Бас дүкендегі жұмыс пен филиалдарға кіру сақталады. Жеке тағайындау филиал ауысымдарын есепке алады.", "Башкы дүкөндөгү иш жана филиалдарга кирүү сакталат. Өзүнчө дайындоо филиалдын сменаларын эсепке алат.", "Кор дар мағозаи асосӣ ва дастрасӣ ба филиалҳо нигоҳ дошта мешавад. Таъиноти ҷудо таърихи навбатҳои филиалро нигоҳ медорад.", "Bosh do‘kondagi ish va filiallarga kirish saqlanadi. Alohida tayinlash filial smenalari tarixini yuritadi.")
        "move_help" -> listOf("The previous branch assignment ends. Completed shifts remain in its history; role and permissions carry over.", "Назначение в прежнем филиале завершится. История смен сохранится; должность и права переносятся.", "Бұрынғы филиалдағы тағайындау аяқталады. Ауысым тарихы сақталып, лауазым мен құқықтар көшеді.", "Мурунку филиалдагы дайындоо бүтөт. Смена тарыхы сакталат; кызмат жана укуктар которулат.", "Таъинот дар филиали пешина анҷом меёбад. Таърихи навбатҳо мемонад; вазифа ва ҳуқуқҳо мегузаранд.", "Oldingi filialdagi tayinlov tugaydi. Smenalar tarixi saqlanadi; lavozim va huquqlar o‘tadi.")
        "done" -> listOf("Worker assignment saved", "Назначение сотрудника сохранено", "Қызметкер тағайындауы сақталды", "Кызматкердин дайындоосу сакталды", "Таъиноти корманд сабт шуд", "Xodim tayinlovi saqlandi")
        "denied" -> listOf("You need worker-management permission in both stores. Permissions cannot be expanded by a transfer.", "Нужны права управления сотрудниками в обоих магазинах. Перевод не может расширять полномочия.", "Екі дүкенде де қызметкерлерді басқару құқығы қажет. Ауыстыру құқықтарды кеңейтпейді.", "Эки дүкөндө тең кызматкерлерди башкаруу укугу керек. Которуу укуктарды кеңейтпейт.", "Дар ҳар ду мағоза ҳуқуқи идораи кормандон лозим аст. Гузаронидан ҳуқуқҳоро зиёд намекунад.", "Ikkala do‘konda ham xodimlarni boshqarish huquqi kerak. O‘tkazish huquqlarni kengaytirmaydi.")
        "shift" -> listOf("End the worker's current shift before transferring them.", "Завершите текущую смену сотрудника перед переводом.", "Ауыстырмас бұрын қызметкердің ауысымын аяқтаңыз.", "Которуудан мурда кызматкердин сменасын бүтүрүңүз.", "Пеш аз гузаронидан навбати кормандро анҷом диҳед.", "O‘tkazishdan oldin xodimning joriy smenasini yakunlang.")
        "duplicate" -> listOf("This worker already has an active assignment in that branch.", "У сотрудника уже есть действующее назначение в этом филиале.", "Қызметкер осы филиалға тағайындалған.", "Кызматкер бул филиалга дайындалган.", "Корманд аллакай дар ин филиал таъинот дорад.", "Xodim bu filialga allaqachon tayinlangan.")
        "changed" -> listOf("The employment changed. Refresh the worker list and try again.", "Условия работы изменились. Обновите список сотрудников и повторите.", "Жұмыс жағдайы өзгерді. Қызметкерлер тізімін жаңартып қайталаңыз.", "Иштин шарттары өзгөрдү. Кызматкерлер тизмесин жаңыртып кайталаңыз.", "Шартҳои кор тағйир ёфт. Рӯйхати кормандонро нав карда такрор кунед.", "Ish shartlari o‘zgardi. Xodimlar ro‘yxatini yangilab qayta urining.")
        "pending" -> listOf("Resolve the pending employment or removal request first.", "Сначала завершите ожидающий запрос о найме или увольнении.", "Алдымен жұмысқа алу не босату сұрауын аяқтаңыз.", "Адегенде жумушка алуу же бошотуу сурамын бүтүрүңүз.", "Аввал дархости интизори қабул ё озодкуниро анҷом диҳед.", "Avval kutilayotgan ishga olish yoki bo‘shatish so‘rovini yakunlang.")
        "invalid" -> listOf("Choose a different branch of the same parent store.", "Выберите другой филиал того же головного магазина.", "Сол бас дүкеннің басқа филиалын таңдаңыз.", "Ошол башкы дүкөндүн башка филиалын тандаңыз.", "Филиали дигари ҳамин мағозаи асосиро интихоб кунед.", "Shu bosh do‘konning boshqa filialini tanlang.")
        else -> error("Unknown worker placement message: $key")
    }
    return listOf(LocalizedStringDataModel("main", t[0])) + listOf("en", "ru", "kk", "ky", "tg", "uz").mapIndexed { i, l -> LocalizedStringDataModel(l, t[i]) }
}
