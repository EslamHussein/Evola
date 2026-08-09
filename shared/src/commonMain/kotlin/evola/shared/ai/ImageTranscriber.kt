package evola.shared.ai

import evola.shared.core.ApiResult

private const val TRANSCRIBE_SYSTEM =
    "You transcribe all readable text from an image exactly as written, preserving line breaks " +
        "and structure. Output ONLY the transcribed text - no commentary, no markdown, no " +
        "descriptions of non-text visual elements. If the image contains no readable text, output " +
        "nothing."

/** On-device image-to-text for the Add Resource "Image" material type (photographed pages,
 * screenshots, handwritten notes) - reuses Claude's vision input rather than a native OCR
 * library, so the same downstream pipeline (segmentation, vocab/grammar extraction) that already
 * runs on PDF/DOCX/pasted text can run unchanged on the transcribed result. */
class ImageTranscriber(private val client: AnthropicClient) {
    suspend fun transcribe(imageBytes: ByteArray, mimeType: String): ApiResult<String> =
        client.completeWithImage(
            AnthropicModels.SMALL, 4000, TRANSCRIBE_SYSTEM,
            "Transcribe the text in this image.", imageBytes, mimeType,
        )
}
