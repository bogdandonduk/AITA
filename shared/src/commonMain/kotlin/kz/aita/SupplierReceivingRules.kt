package kz.aita

import kotlin.math.abs

/** Receiving is a cumulative commercial record, independent of the remaining stock in its batches. */
fun SupplierOrderLineDataModel.receivableQuantity(): QuantityDataModel = supplierAcceptedQuantity ?: requestedQuantity

fun SupplierOrderLineDataModel.receivedQuantityTotalValue(): Double {
    val expected = receivableQuantity().total.takeIf { it.isFinite() && it >= 0.0 } ?: return 0.0
    return receivedQuantityTotal?.takeIf { it.isFinite() && it >= 0.0 }
        // Old releases considered any linked batch a completed line. Preserve that boundary rather
        // than using the current (possibly already sold) batch quantity and receiving stock twice.
        ?: if (deliveredBatchIds.isNotEmpty()) expected else 0.0
}

fun SupplierOrderLineDataModel.remainingReceivableQuantity(): Double =
    (receivableQuantity().total - receivedQuantityTotalValue()).takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0

fun supplierOrderQuantityValid(quantity: QuantityDataModel, allowZero: Boolean = false): Boolean =
    quantity.id.isNotBlank() && quantity.total.isFinite() &&
        (quantity.total > 0.0 || (allowZero && quantity.total == 0.0)) &&
        quantity.pricedAmount.isFinite() && quantity.pricedAmount > 0.0 &&
        (!quantity.roundTotal || abs(quantity.total - kotlin.math.round(quantity.total)) < 0.0000001)

fun supplierReceiptQuantityValid(line: SupplierOrderLineDataModel, received: QuantityDataModel): Boolean {
    val target = line.receivableQuantity()
    return supplierOrderQuantityValid(target, allowZero = true) && supplierOrderQuantityValid(received) &&
        received.id == target.id && received.roundTotal == target.roundTotal &&
        abs(received.pricedAmount - target.pricedAmount) < 0.0000001 &&
        line.remainingReceivableQuantity() > 0.0 && received.total <= line.remainingReceivableQuantity() + 0.0000001
}

fun SupplierOrderDataModel.canReceiveSupplierOrder(): Boolean = isActive && status !in setOf(
    SupplierOrderStatusDataModel.Draft, SupplierOrderStatusDataModel.Cancelled, SupplierOrderStatusDataModel.Delivered)

fun supplierProcurementProblemMessage(kind: String): List<LocalizedStringDataModel> {
    val values = when (kind) {
        "conflict" -> listOf("This supplier operation was already submitted with different data. Refresh and review the order before retrying.", "Эта операция поставщика уже отправлена с другими данными. Обновите и проверьте заказ перед повтором.", "Бұл жеткізуші әрекеті басқа деректермен жіберілген. Қайталаудан бұрын тапсырысты жаңартып тексеріңіз.", "Бул жеткирүүчү операциясы башка маалымат менен жөнөтүлгөн. Кайталанардан мурун тапшырыкты жаңыртып текшериңиз.", "Ин амали таъминкунанда бо маълумоти дигар фиристода шудааст. Пеш аз такрор фармоишро нав карда санҷед.", "Bu yetkazib beruvchi amali boshqa ma’lumotlar bilan yuborilgan. Qayta urinishdan oldin buyurtmani yangilab tekshiring.")
        "pending" -> listOf("A previous supplier request is still awaiting confirmation. Retry that saved request before changing its details.", "Предыдущий запрос поставщику ещё не подтверждён. Повторите сохранённый запрос, прежде чем менять его данные.", "Алдыңғы жеткізуші сұрауы әлі расталмады. Деректерін өзгертпес бұрын сақталған сұрауды қайталаңыз.", "Мурунку жеткирүүчү суроосу ырастала элек. Маалыматты өзгөртүүдөн мурда сакталган суроону кайталаңыз.", "Дархости қаблии таъминкунанда ҳанӯз тасдиқ нашудааст. Пеш аз тағйир додан дархости сабтшударо такрор кунед.", "Oldingi yetkazib beruvchi so‘rovi hali tasdiqlanmagan. Ma’lumotlarni o‘zgartirishdan oldin saqlangan so‘rovni qayta yuboring.")
        "storage" -> listOf("Could not safely save this supplier request on this device. Free storage and retry; nothing new was sent.", "Не удалось надёжно сохранить запрос поставщику на устройстве. Освободите место и повторите; новый запрос не отправлен.", "Жеткізуші сұрауын құрылғыға сенімді сақтау мүмкін болмады. Орын босатып қайталаңыз; жаңа сұрау жіберілмеді.", "Жеткирүүчү суроосун түзмөккө сактоо мүмкүн болбоду. Орун бошотуп кайталаңыз; жаңы суроо жөнөтүлгөн жок.", "Дархости таъминкунанда дар дастгоҳ сабт нашуд. Ҷойро холӣ карда такрор кунед; дархости нав фиристода нашуд.", "Yetkazib beruvchi so‘rovi qurilmada saqlanmadi. Joy bo‘shatib qayta urinib ko‘ring; yangi so‘rov yuborilmadi.")
        "closed" -> listOf("This order can no longer be changed or received. Refresh its status.", "Этот заказ больше нельзя изменить или принять. Обновите его статус.", "Бұл тапсырысты енді өзгерту не қабылдау мүмкін емес. Күйін жаңартыңыз.", "Бул тапшырыкты эми өзгөртүү же кабыл алуу мүмкүн эмес. Абалын жаңыртыңыз.", "Ин фармоиш дигар тағйир ё қабул карда намешавад. Ҳолаташро нав кунед.", "Bu buyurtmani endi o‘zgartirib yoki qabul qilib bo‘lmaydi. Holatini yangilang.")
        else -> listOf("Check every order line: valid store item, unit, positive remaining quantity and supply price are required. Nothing was partially saved.", "Проверьте все позиции: нужны товар этого магазина, единица, допустимое положительное количество и цена поставки. Частичных изменений нет.", "Барлық жолды тексеріңіз: осы дүкеннің тауары, өлшем бірлігі, рұқсат етілген оң саны және жеткізу бағасы қажет. Ішінара өзгеріс сақталмады.", "Бардык саптарды текшериңиз: ушул дүкөндүн товары, бирдиги, уруксат берилген оң саны жана жеткирүү баасы керек. Жарым-жартылай өзгөртүү сакталган жок.", "Ҳамаи сатрҳоро санҷед: моли ҳамин мағоза, воҳид, миқдори мусбати иҷозатшуда ва нархи таъминот лозиманд. Тағйироти қисман сабт нашуд.", "Barcha qatorlarni tekshiring: shu do‘kon tovari, birlik, ruxsat etilgan musbat miqdor va ta’minot narxi kerak. Qisman o‘zgarishlar saqlanmadi.")
    }
    return listOf("main", "en", "ru", "kk", "ky", "tg", "uz").mapIndexed { index, language ->
        LocalizedStringDataModel(language, values[if (index <= 1) 0 else index - 1])
    }
}

internal fun SupplierOrderWithLinesDataModel.acknowledgesSupplierCreate(request: SupplierOrderWithLinesDataModel): Boolean =
    order.id == request.order.id && order.storeId == request.order.storeId && order.supplierId == request.order.supplierId &&
        order.userId == request.order.userId && lines.isNotEmpty() && lines.all { it.orderId == order.id } &&
        lines.map { it.goodsItemId }.toSet().containsAll(request.lines.map { it.goodsItemId })

internal fun SupplierOrderWithLinesDataModel.acknowledgesSupplierReceipt(request: ReceiveSupplierOrderRequestDataModel): Boolean =
    order.id == request.orderId && request.receivedLines.all { received ->
        lines.singleOrNull { it.id == received.orderLineId && it.orderId == request.orderId }?.let { line ->
            (line.substituteGoodsItemId?.takeIf(String::isNotBlank) ?: line.goodsItemId) == received.goodsItemId &&
                line.receivedQuantityTotalValue() + 0.0000001 >= received.receivedQuantity.total &&
                line.deliveredBatchIds.isNotEmpty() && line.deliveredBatchIds.all(::supplierRequestIdentityValid)
        } == true
    }
