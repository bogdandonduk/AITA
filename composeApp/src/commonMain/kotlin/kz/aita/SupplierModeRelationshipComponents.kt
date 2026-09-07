package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp

/**
 * Transitional explanation shown while Store-local supplier contacts are being separated from
 * actual Supplier business identities. It intentionally performs no network work.
 */
@Composable
internal fun SupplierRelationshipBoundaryNotice(
    modifier: Modifier = Modifier,
) {
    val language = Locale.current.language.lowercase()
    val copy = when {
        language.startsWith("ru") -> Triple(
            "Профиль поставщика — это компания",
            "Связь магазина с поставщиком хранится отдельно. Добавление контакта магазина не должно создавать или дублировать компанию поставщика.",
            "Связи с магазинами",
        )
        language.startsWith("kk") -> Triple(
            "Жеткізуші профилі — бұл бизнес",
            "Дүкен мен жеткізуші арасындағы байланыс бөлек сақталады. Дүкен контактісін қосу жеткізуші бизнесін жасамауы немесе қайталамауы керек.",
            "Дүкен байланыстары",
        )
        else -> Triple(
            "A Supplier profile is a business identity",
            "A Store connection is a separate relationship. Adding a Store contact must not create or duplicate a Supplier business.",
            "Store connections",
        )
    }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = copy.first,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = copy.second,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = copy.third,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
