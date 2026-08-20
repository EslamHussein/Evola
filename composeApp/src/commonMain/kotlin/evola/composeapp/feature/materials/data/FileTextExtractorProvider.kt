package evola.composeapp.feature.materials.data

import androidx.compose.runtime.Composable
import evola.shared.core.common.FileTextExtractor

/** Provides the platform [FileTextExtractor] (Android: PdfBox-Android + ZIP-based DOCX; iOS: PDFKit).
 * Created once and threaded into the local materials repository via the composition root. */
@Composable
expect fun rememberFileTextExtractor(): FileTextExtractor
