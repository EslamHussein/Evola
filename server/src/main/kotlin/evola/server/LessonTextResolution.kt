package evola.server

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

/** Resolves a lesson's own text slice - shared by the vocabulary and grammar extraction workers,
 * since both need the same lesson-text-resolution business rule. "entire" mode materials store
 * their (single lesson's) full text directly on the material row rather than the shared,
 * content-hash-keyed extraction_jobs table - see MaterialService/Tables.kt. */
internal fun resolveLessonText(lessonRow: ResultRow, materialRow: ResultRow): String {
    val fullText = materialRow[MaterialsTable.contentText]
        ?: ExtractionJobsTable.selectAll()
            .where { ExtractionJobsTable.contentHash eq materialRow[MaterialsTable.contentHash] }
            .singleOrNull()?.get(ExtractionJobsTable.contentText) ?: ""

    return sliceLessonText(fullText, lessonRow[LessonsTable.sourceTextRef])
}

private fun sliceLessonText(fullText: String, sourceTextRef: String?): String {
    if (sourceTextRef == null) return fullText
    val parts = sourceTextRef.split(":")
    val start = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, fullText.length) ?: 0
    val end = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(start, fullText.length) ?: fullText.length
    return fullText.substring(start, end)
}
