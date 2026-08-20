package evola.composeapp.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import evola.composeapp.backup.rememberBackupFileLoader
import evola.composeapp.backup.rememberBackupFileSaver
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_profile_app_section_title
import evola.composeapp.generated.resources.main_profile_backup_restore_failed_snackbar
import evola.composeapp.generated.resources.main_profile_backup_restored_snackbar
import evola.composeapp.generated.resources.main_profile_backup_saved_snackbar
import evola.composeapp.generated.resources.main_profile_create_backup_subtitle
import evola.composeapp.generated.resources.main_profile_create_backup_title
import evola.composeapp.generated.resources.main_profile_restore_backup_subtitle
import evola.composeapp.generated.resources.main_profile_restore_backup_title
import evola.composeapp.generated.resources.main_profile_settings_row_subtitle
import evola.composeapp.generated.resources.main_profile_settings_row_title
import evola.composeapp.generated.resources.main_profile_share_progress_subtitle
import evola.composeapp.generated.resources.main_profile_share_progress_title
import evola.composeapp.generated.resources.main_profile_share_streak_getting_started
import evola.composeapp.generated.resources.main_profile_share_streak_multi
import evola.composeapp.generated.resources.main_profile_share_streak_single
import evola.composeapp.generated.resources.main_profile_share_text_no_progress
import evola.composeapp.generated.resources.main_profile_share_text_with_progress
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.core.designsystem.components.IconTile
import evola.shared.core.common.ApiResult
import evola.shared.feature.onboarding.domain.Goal
import evola.shared.feature.onboarding.domain.GoalProgress
import evola.shared.feature.onboarding.domain.VocabularyBreakdown
import evola.shared.language.NativeLanguage
import evola.shared.local.BackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Settings entry point + local backup/restore - a JSON file export/import
 * ([BackupRepository]) rather than any cloud sync, matching this app's no-
 * account, fully-local design. */
@Composable
internal fun AppSection(
    onOpenSettings: () -> Unit,
    backupRepository: BackupRepository,
    latestProgress: GoalProgress?,
    goal: Goal,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
) {
    val backupSavedMessage = stringResource(Res.string.main_profile_backup_saved_snackbar)
    val backupRestoredMessage = stringResource(Res.string.main_profile_backup_restored_snackbar)
    val backupRestoreFailedMessage = stringResource(Res.string.main_profile_backup_restore_failed_snackbar)
    val saveBackup = rememberBackupFileSaver(
        content = { backupRepository.export() },
        onSaved = { saved -> if (saved) coroutineScope.launch { snackbarHostState.showSnackbar(backupSavedMessage) } },
    )
    val loadBackup = rememberBackupFileLoader { json ->
        when (backupRepository.import(json)) {
            is ApiResult.Success -> coroutineScope.launch { snackbarHostState.showSnackbar(backupRestoredMessage) }
            is ApiResult.Failure -> coroutineScope.launch { snackbarHostState.showSnackbar(backupRestoreFailedMessage) }
        }
    }
    val shareText = evola.composeapp.core.common.rememberShareText()

    // Reword's "Share progress" summary text - built from whatever [latestProgress] is available at
    // share time; a null/never-loaded progress still produces a sensible (if less detailed) message
    // rather than blocking the share action on a fetch. Resolved here (composable context) since
    // stringResource can't be called from the onClick lambda below.
    val progressShareText = if (latestProgress == null) {
        stringResource(Res.string.main_profile_share_text_no_progress, goal.goalText)
    } else {
        val streakPart = when {
            latestProgress.streakDays > 1 -> stringResource(Res.string.main_profile_share_streak_multi, latestProgress.streakDays)
            latestProgress.streakDays == 1 -> stringResource(Res.string.main_profile_share_streak_single)
            else -> stringResource(Res.string.main_profile_share_streak_getting_started)
        }
        stringResource(Res.string.main_profile_share_text_with_progress, latestProgress.vocabulary.mastered, streakPart, goal.goalText)
    }

    Text(stringResource(Res.string.main_profile_app_section_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(EvolaSpacing.sm))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            AppRow(Icons.Filled.Settings, stringResource(Res.string.main_profile_settings_row_title), stringResource(Res.string.main_profile_settings_row_subtitle), onClick = onOpenSettings)
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            AppRow(
                Icons.AutoMirrored.Filled.TrendingUp,
                stringResource(Res.string.main_profile_share_progress_title),
                stringResource(Res.string.main_profile_share_progress_subtitle),
                onClick = { shareText(progressShareText) },
            )
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            AppRow(Icons.Filled.CloudUpload, stringResource(Res.string.main_profile_create_backup_title), stringResource(Res.string.main_profile_create_backup_subtitle), onClick = saveBackup)
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            AppRow(Icons.Filled.CloudDownload, stringResource(Res.string.main_profile_restore_backup_title), stringResource(Res.string.main_profile_restore_backup_subtitle), onClick = loadBackup)
        }
    }
}

/** Shared icon-tile row layout used by [AppSection] and Profile's danger-zone row. */
@Composable
internal fun AppRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(EvolaSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, locked = false)
        Spacer(Modifier.width(EvolaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = EvolaColors.Text3)
    }
}

private object FakeAppSectionBackupRepository : BackupRepository {
    override fun export() = ""
    override fun import(json: String): ApiResult<Unit> = ApiResult.Success(Unit)
}

private val fakeAppSectionGoal = Goal(
    id = "g1", goalText = "Learn German", title = "German basics",
    nativeLanguage = NativeLanguage.ARABIC, isActive = true, createdAt = "2026-01-01",
)

@Preview
@Composable
private fun AppSectionPreview() {
    EvolaTheme {
        Column {
            AppSection(
                onOpenSettings = {},
                backupRepository = FakeAppSectionBackupRepository,
                latestProgress = GoalProgress(
                    overallPct = 0.42f, currentLessonId = "l1", streakDays = 5, todayCompleted = false,
                    vocabulary = VocabularyBreakdown(notStarted = 12, inProgress = 8, mastered = 20, struggling = 3),
                ),
                goal = fakeAppSectionGoal,
                snackbarHostState = remember { SnackbarHostState() },
                coroutineScope = rememberCoroutineScope(),
            )
        }
    }
}
