package evola.composeapp.feature.learning.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.components.EvolaCard
import evola.shared.feature.learning.domain.GrammarTopic

@Composable
internal fun TopicRow(topic: GrammarTopic, onClick: () -> Unit) {
    EvolaCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(topic.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                topic.masteryState.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.height(EvolaSpacing.xs))
        Text(topic.explanation, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
    }
}
