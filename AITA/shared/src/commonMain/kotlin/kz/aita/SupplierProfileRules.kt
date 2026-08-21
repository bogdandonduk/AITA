package kz.aita

private const val SUPPLIER_PROFILE_MAX_NAME_TRANSLATIONS = 8
private const val SUPPLIER_PROFILE_MAX_CONTACTS_PER_KIND = 8
private const val SUPPLIER_PROFILE_MAX_NAME_LENGTH = 160
private const val SUPPLIER_PROFILE_MAX_PHONE_LENGTH = 48
private const val SUPPLIER_PROFILE_MAX_EMAIL_LENGTH = 254

enum class SupplierProfileValidationIssue {
    MissingName,
    InvalidEmail,
    TooManyNames,
    TooManyPhones,
    TooManyEmails,
    NameTooLong,
    PhoneTooLong,
    EmailTooLong
}

enum class SupplierProfileReadinessIssue {
    MissingContact,
    DuplicatePhone,
    DuplicateEmail,
    InvalidEmail
}

fun supplierProfilePhoneComparisonKey(value: String?): String? {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return null
    val digits = raw.filter { it.isDigit() }
    return digits.takeIf { it.isNotBlank() }
        ?: raw.lowercase().replace(" ", "").takeIf { it.isNotBlank() }
}

fun normalizeSupplierProfileEmail(value: String?): String? =
    value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

fun supplierProfileEmailLooksValid(value: String?): Boolean {
    val email = normalizeSupplierProfileEmail(value) ?: return false
    if (email.length > SUPPLIER_PROFILE_MAX_EMAIL_LENGTH || email.any { it.isWhitespace() }) return false
    val at = email.indexOf('@')
    if (at <= 0 || at != email.lastIndexOf('@') || at >= email.lastIndex) return false
    val local = email.substring(0, at)
    val domain = email.substring(at + 1)
    if (local.isBlank() || domain.length < 3 || domain.startsWith('.') || domain.endsWith('.')) return false
    val dot = domain.lastIndexOf('.')
    return dot > 0 && dot < domain.lastIndex && domain.substring(dot + 1).length >= 2
}

fun SupplierDataModel.normalizedSupplierProfileFields(): SupplierDataModel {
    val normalizedNames = name
        .map { it.copy(language = it.language.trim().ifBlank { "main" }, value = it.value.trim()) }
        .filter { it.value.isNotBlank() }
        .distinctBy { it.language.lowercase() }

    val normalizedPhones = buildList {
        val seen = mutableSetOf<String>()
        phoneNumbers.orEmpty().forEach { original ->
            val clean = original.trim()
            val key = supplierProfilePhoneComparisonKey(clean) ?: return@forEach
            if (seen.add(key)) add(clean)
        }
    }

    val normalizedEmails = buildList {
        val seen = mutableSetOf<String>()
        emails.orEmpty().forEach { original ->
            val clean = normalizeSupplierProfileEmail(original) ?: return@forEach
            if (seen.add(clean)) add(clean)
        }
    }

    return copy(
        name = normalizedNames,
        phoneNumbers = normalizedPhones,
        emails = normalizedEmails,
        categoryIds = categoryIds.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        typeIds = typeIds?.map { it.trim() }?.filter { it.isNotBlank() }?.distinct()
    )
}

fun SupplierDataModel.supplierProfileValidationIssues(): Set<SupplierProfileValidationIssue> {
    val rawNames = name.filter { it.value.isNotBlank() }
    val rawPhones = phoneNumbers.orEmpty().filter { it.isNotBlank() }
    val rawEmails = emails.orEmpty().filter { it.isNotBlank() }
    return buildSet {
        if (rawNames.isEmpty()) add(SupplierProfileValidationIssue.MissingName)
        if (rawNames.size > SUPPLIER_PROFILE_MAX_NAME_TRANSLATIONS) add(SupplierProfileValidationIssue.TooManyNames)
        if (rawPhones.size > SUPPLIER_PROFILE_MAX_CONTACTS_PER_KIND) add(SupplierProfileValidationIssue.TooManyPhones)
        if (rawEmails.size > SUPPLIER_PROFILE_MAX_CONTACTS_PER_KIND) add(SupplierProfileValidationIssue.TooManyEmails)
        if (rawNames.any { it.value.trim().length > SUPPLIER_PROFILE_MAX_NAME_LENGTH }) add(SupplierProfileValidationIssue.NameTooLong)
        if (rawPhones.any { it.trim().length > SUPPLIER_PROFILE_MAX_PHONE_LENGTH }) add(SupplierProfileValidationIssue.PhoneTooLong)
        if (rawEmails.any { it.trim().length > SUPPLIER_PROFILE_MAX_EMAIL_LENGTH }) add(SupplierProfileValidationIssue.EmailTooLong)
        if (rawEmails.any { !supplierProfileEmailLooksValid(it) }) add(SupplierProfileValidationIssue.InvalidEmail)
    }
}

fun SupplierDataModel.supplierProfileReadinessIssues(): Set<SupplierProfileReadinessIssue> {
    val rawPhones = phoneNumbers.orEmpty().map { it.trim() }.filter { it.isNotBlank() }
    val rawEmails = emails.orEmpty().map { it.trim() }.filter { it.isNotBlank() }
    val phoneKeys = rawPhones.mapNotNull(::supplierProfilePhoneComparisonKey)
    val emailKeys = rawEmails.mapNotNull(::normalizeSupplierProfileEmail)
    return buildSet {
        if (rawPhones.isEmpty() && rawEmails.isEmpty()) add(SupplierProfileReadinessIssue.MissingContact)
        if (phoneKeys.size != phoneKeys.distinct().size) add(SupplierProfileReadinessIssue.DuplicatePhone)
        if (emailKeys.size != emailKeys.distinct().size) add(SupplierProfileReadinessIssue.DuplicateEmail)
        if (rawEmails.any { !supplierProfileEmailLooksValid(it) }) add(SupplierProfileReadinessIssue.InvalidEmail)
    }
}

fun mergeSupplierProfilePrimaryPhone(
    existing: List<String>?,
    primary: String?
): List<String> {
    val tail = existing.orEmpty().drop(1)
    return buildList {
        primary?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(tail)
    }.distinctBy { supplierProfilePhoneComparisonKey(it) ?: it.trim().lowercase() }
}

fun mergeSupplierProfilePrimaryEmail(
    existing: List<String>?,
    primary: String?
): List<String> {
    val tail = existing.orEmpty().drop(1)
    return buildList {
        normalizeSupplierProfileEmail(primary)?.let(::add)
        addAll(tail.mapNotNull(::normalizeSupplierProfileEmail))
    }.distinct()
}

fun mergeSupplierProfilePrimaryName(
    existing: List<LocalizedStringDataModel>,
    primaryName: String
): List<LocalizedStringDataModel> {
    val cleanName = primaryName.trim()
    val existingMainIndex = existing.indexOfFirst { it.language.equals("main", ignoreCase = true) }
    val next = existing.toMutableList()
    if (existingMainIndex >= 0) {
        next[existingMainIndex] = next[existingMainIndex].copy(language = "main", value = cleanName)
    } else {
        next.add(0, LocalizedStringDataModel("main", cleanName))
    }
    return next
        .map { it.copy(language = it.language.trim().ifBlank { "main" }, value = it.value.trim()) }
        .filter { it.value.isNotBlank() }
        .distinctBy { it.language.lowercase() }
}
