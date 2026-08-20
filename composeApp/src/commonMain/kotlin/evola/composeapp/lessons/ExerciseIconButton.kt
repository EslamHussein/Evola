package evola.composeapp.lessons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
internal fun ExerciseIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = EvolaColors.Surface,
        border = BorderStroke(1.dp, EvolaColors.Border),
    ) {
        Icon(icon, contentDescription = description, tint = EvolaColors.Text2, modifier = Modifier.padding(EvolaSpacing.md).size(20.dp))
    }
}

@Preview
@Composable
private fun ExerciseIconButtonPreview() {
    EvolaTheme {
        ExerciseIconButton(icon = Icons.Filled.Keyboard, description = "Type answer", onClick = {})
    }
}
