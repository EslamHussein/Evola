package evola.composeapp.feature.learning.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import evola.composeapp.core.common.ChaseLoadingIndicator
import evola.composeapp.core.designsystem.EvolaSpacing

@Composable
internal fun ProgressMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ChaseLoadingIndicator()
            Spacer(Modifier.height(EvolaSpacing.lg))
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
