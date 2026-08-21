package kz.aita.server.payments

import java.io.BufferedReader
import java.io.InputStreamReader
import kz.aita.payments.management.PaymentIntegrationSecretPatchRequest
import kz.aita.payments.management.PaymentManagedEnvironment
import kz.aita.payments.management.PaymentManagedProvider
import kz.aita.payments.management.validationError

/**
 * Headless, OS-admin credential importer. Secret values are read from the controlling terminal or
 * standard input and are never accepted as command-line arguments.
 */
object PaymentCredentialImportCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = ImportOptions.parse(args)
        val allowedKeys = configuredKeys(options.provider)
        require(allowedKeys.isNotEmpty()) {
            "No credential keys are enabled for ${options.provider}. Configure the server allowlist first."
        }
        val invalidPromptKeys = options.promptSecretKeys - allowedKeys
        val invalidClearKeys = options.clearSecretKeys - allowedKeys
        require(invalidPromptKeys.isEmpty() && invalidClearKeys.isEmpty()) {
            "Requested credential key is not enabled for this provider"
        }

        val secrets = linkedMapOf<String, String>()
        try {
            options.promptSecretKeys.forEach { key ->
                secrets[key] = readSecret("Enter $key for ${options.provider.name}: ")
                    .also { require(it.isNotBlank()) { "$key cannot be blank" } }
            }
            val repository = PaymentIntegrationManagementRepository(PaymentManagedSecretCipher.fromEnvironment())
            PaymentManagementDatabase.connection().use { connection ->
                connection.autoCommit = false
                try {
                    val existing = repository.find(connection, options.storeId, options.provider, options.environment)
                    val request = PaymentIntegrationSecretPatchRequest(
                        storeId = options.storeId,
                        environment = options.environment,
                        expectedRevision = options.expectedRevision ?: existing?.revision,
                        displayName = options.displayName ?: existing?.displayName,
                        enabled = options.enabled,
                        settings = if (options.settings.isEmpty()) existing?.settings.orEmpty() else options.settings,
                        secrets = secrets,
                        clearSecretKeys = options.clearSecretKeys,
                    )
                    request.validationError()?.let { error(it) }
                    val saved = repository.upsert(
                        connection = connection,
                        actorId = System.getenv("AITA_PAYMENT_IMPORT_ACTOR")?.take(256) ?: "local-os-admin",
                        provider = options.provider,
                        request = request,
                        allowedSecretKeys = allowedKeys,
                    )
                    connection.commit()
                    println("Payment integration saved safely.")
                    println("Store: ${saved.storeId}")
                    println("Provider: ${saved.provider}")
                    println("Environment: ${saved.environment}")
                    println("Revision: ${saved.revision}")
                    println("Configured secret fields: ${saved.configuredSecretKeys.sorted().joinToString().ifEmpty { "none" }}")
                    println("Secret values were not printed or returned.")
                } catch (failure: Throwable) {
                    connection.rollback()
                    throw failure
                }
            }
        } finally {
            secrets.replaceAll { _, value -> "\u0000".repeat(value.length) }
            secrets.clear()
        }
    }

    private fun readSecret(prompt: String): String {
        System.console()?.let { console ->
            val chars = console.readPassword("%s", prompt) ?: error("Secret input was cancelled")
            return try { chars.concatToString() } finally { chars.fill('\u0000') }
        }
        System.err.print(prompt)
        System.err.flush()
        return BufferedReader(InputStreamReader(System.`in`)).readLine()
            ?: error("Secret input ended unexpectedly")
    }

    private fun configuredKeys(provider: PaymentManagedProvider): Set<String> {
        val variable = when (provider) {
            PaymentManagedProvider.WEBKASSA -> "AITA_WEBKASSA_ALLOWED_CREDENTIAL_KEYS"
            PaymentManagedProvider.KASPI_PAY -> "AITA_KASPI_ALLOWED_CREDENTIAL_KEYS"
        }
        return System.getenv(variable).orEmpty()
            .split(',', ';')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
    }
}

private data class ImportOptions(
    val storeId: String,
    val provider: PaymentManagedProvider,
    val environment: PaymentManagedEnvironment,
    val displayName: String?,
    val enabled: Boolean,
    val expectedRevision: Long?,
    val settings: Map<String, String>,
    val promptSecretKeys: Set<String>,
    val clearSecretKeys: Set<String>,
) {
    companion object {
        fun parse(args: Array<String>): ImportOptions {
            var storeId: String? = null
            var provider: PaymentManagedProvider? = null
            var environment = PaymentManagedEnvironment.PRODUCTION
            var displayName: String? = null
            var enabled = true
            var expectedRevision: Long? = null
            val settings = linkedMapOf<String, String>()
            val promptKeys = linkedSetOf<String>()
            val clearKeys = linkedSetOf<String>()

            var index = 0
            fun value(flag: String): String {
                require(index + 1 < args.size) { "$flag requires a value" }
                index += 1
                return args[index]
            }
            while (index < args.size) {
                when (val flag = args[index]) {
                    "--store-id" -> storeId = value(flag).trim()
                    "--provider" -> provider = PaymentManagedProvider.valueOf(value(flag).trim().replace('-', '_').uppercase())
                    "--environment" -> environment = PaymentManagedEnvironment.valueOf(value(flag).trim().uppercase())
                    "--display-name" -> displayName = value(flag).trim().takeIf(String::isNotEmpty)
                    "--enabled" -> enabled = value(flag).toBooleanStrict()
                    "--expected-revision" -> expectedRevision = value(flag).toLong()
                    "--setting" -> {
                        val pair = value(flag).split('=', limit = 2)
                        require(pair.size == 2 && pair[0].isNotBlank()) { "--setting requires key=value" }
                        settings[pair[0].trim()] = pair[1].trim()
                    }
                    "--secret-key" -> promptKeys += value(flag).trim()
                    "--clear-secret" -> clearKeys += value(flag).trim()
                    "--help", "-h" -> usageAndExit()
                    else -> error("Unknown option $flag. Secret values must never be supplied as arguments.")
                }
                index += 1
            }
            require(!storeId.isNullOrBlank()) { "--store-id is required" }
            require(provider != null) { "--provider is required" }
            require(promptKeys.intersect(clearKeys).isEmpty()) { "A secret cannot be set and cleared together" }
            return ImportOptions(
                storeId = storeId,
                provider = provider,
                environment = environment,
                displayName = displayName,
                enabled = enabled,
                expectedRevision = expectedRevision,
                settings = settings,
                promptSecretKeys = promptKeys,
                clearSecretKeys = clearKeys,
            )
        }

        private fun usageAndExit(): Nothing {
            println(
                """
                Usage: PaymentCredentialImportCli
                  --store-id <uuid>
                  --provider <webkassa|kaspi_pay>
                  [--environment <test|production>]
                  [--display-name <non-secret label>]
                  [--setting key=value]
                  [--secret-key <allowed key; value is prompted securely>]
                  [--clear-secret <allowed key>]
                  [--expected-revision <number>]

                Never put a token, password, or private key directly in command-line arguments.
                """.trimIndent()
            )
            kotlin.system.exitProcess(0)
        }
    }
}
