package kz.aita.server.payments

import java.net.URI
import java.sql.Connection
import java.sql.DriverManager

internal object PaymentManagementDatabase {
    private fun env(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        System.getenv(name)?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun jdbcUrl(raw: String): String {
        if (raw.startsWith("jdbc:")) return raw
        val uri = URI(raw)
        require(uri.scheme == "postgres" || uri.scheme == "postgresql") { "Unsupported database URL scheme" }
        val port = if (uri.port > 0) uri.port else 5432
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "jdbc:postgresql://${uri.host}:$port${uri.rawPath}$query"
    }

    fun connection(): Connection {
        val url = env("AITA_DB_URL", "AITA_DATABASE_URL", "JDBC_DATABASE_URL", "DATABASE_URL", "DB_URL")
            ?: error("Payment management requires a configured PostgreSQL JDBC URL")
        val user = env("AITA_DB_USER", "AITA_DATABASE_USER", "JDBC_DATABASE_USER", "DB_USER")
        val password = env("AITA_DB_PASSWORD", "AITA_DATABASE_PASSWORD", "JDBC_DATABASE_PASSWORD", "DB_PASSWORD")
        Class.forName("org.postgresql.Driver")
        return if (user == null) DriverManager.getConnection(jdbcUrl(url))
        else DriverManager.getConnection(jdbcUrl(url), user, password.orEmpty())
    }
}
