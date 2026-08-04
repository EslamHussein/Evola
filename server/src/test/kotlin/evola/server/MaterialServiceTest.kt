package evola.server

import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MaterialServiceTest {

    @TempDir
    lateinit var tempUploadsDir: Path

    private val database = TestDatabase.database
    private val authService = AuthService(database, jwtSecret = "test-secret")
    private val goalService = GoalService(database)
    private var queuedJobs = 0
    private val materialService by lazy {
        MaterialService(database, tempUploadsDir.toString(), onJobQueued = { queuedJobs++ })
    }

    @BeforeEach
    fun clearTables() {
        queuedJobs = 0
        transaction(database) {
            VocabularyProgressTable.deleteAll()
            VocabularyItemsTable.deleteAll()
            VocabularyExtractionJobsTable.deleteAll()
            LessonsTable.deleteAll()
            MaterialsTable.deleteAll()
            ExtractionJobsTable.deleteAll()
            ExtractionCacheTable.deleteAll()
            GoalsTable.deleteAll()
            RefreshTokensTable.deleteAll()
            UsersTable.deleteAll()
        }
    }

    private suspend fun registerUserWithGoal(email: String = "materialtester@example.com"): Pair<String, String> {
        val registered = authService.register(RegisterRequest("Material Tester", email, "Passw0rd!"))
        val userId = (registered as RegisterOutcome.Created).tokens.user.id
        val goal = goalService.createGoal(userId, CreateGoalRequest("Pass the German B1 exam"))
        val goalId = (goal as CreateGoalOutcome.Created).goal.id
        return userId to goalId
    }

    private fun samplePdfBytes(text: String? = "Der Hund läuft schnell durch den Park und spielt mit dem Ball."): ByteArray {
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            if (text != null) {
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                    stream.newLineAtOffset(50f, 700f)
                    stream.showText(text)
                    stream.endText()
                }
            }
            val out = ByteArrayOutputStream()
            document.save(out)
            return out.toByteArray()
        }
    }

    private fun sampleDocxBytes(text: String = "Die Katze schläft auf dem warmen Sofa im Wohnzimmer."): ByteArray {
        XWPFDocument().use { document ->
            document.createParagraph().createRun().setText(text)
            val out = ByteArrayOutputStream()
            document.write(out)
            return out.toByteArray()
        }
    }

    @Test
    fun `uploading a valid PDF succeeds and queues an extraction job`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val outcome = materialService.uploadMaterial(userId, goalId, "book.pdf", samplePdfBytes())

        assertIs<UploadOutcome.Created>(outcome)
        assertEquals("UPLOADED", outcome.status)
        assertEquals(1, queuedJobs)

        val stored = transaction(database) { MaterialsTable.selectAll().single() }
        assertEquals(MIME_PDF, stored[MaterialsTable.mimeType])
        assertEquals(1, stored[MaterialsTable.pageCount])
    }

    @Test
    fun `uploading a valid DOCX succeeds`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val outcome = materialService.uploadMaterial(userId, goalId, "notes.docx", sampleDocxBytes())

        assertIs<UploadOutcome.Created>(outcome)
        val stored = transaction(database) { MaterialsTable.selectAll().single() }
        assertEquals(MIME_DOCX, stored[MaterialsTable.mimeType])
    }

    @Test
    fun `an unsupported file type is rejected`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val outcome = materialService.uploadMaterial(userId, goalId, "notes.txt", "just plain text".toByteArray())
        assertIs<UploadOutcome.UnsupportedFileType>(outcome)
    }

    @Test
    fun `a file over 25MB is rejected`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val oversized = ByteArray(26 * 1024 * 1024)
        val outcome = materialService.uploadMaterial(userId, goalId, "huge.pdf", oversized)
        assertIs<UploadOutcome.FileTooLarge>(outcome)
    }

    @Test
    fun `a PDF with no extractable text is rejected`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val outcome = materialService.uploadMaterial(userId, goalId, "scanned.pdf", samplePdfBytes(text = null))
        assertIs<UploadOutcome.NoExtractableText>(outcome)
    }

    @Test
    fun `organization_mode 'entire' materializes exactly one lesson synchronously with no extraction job`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val outcome = materialService.uploadMaterial(
            userId, goalId, "book.pdf", samplePdfBytes(),
            organizationMode = "entire",
        )

        assertIs<UploadOutcome.Created>(outcome)
        assertEquals("READY", outcome.status)
        assertEquals(0, queuedJobs)

        val lessons = transaction(database) { LessonsTable.selectAll().toList() }
        assertEquals(1, lessons.size)
        assertEquals("book.pdf", lessons.single()[LessonsTable.title])

        val extractionJobs = transaction(database) { ExtractionJobsTable.selectAll().count() }
        assertEquals(0, extractionJobs)

        val vocabJobs = transaction(database) { VocabularyExtractionJobsTable.selectAll().count() }
        assertEquals(1, vocabJobs)
    }

    @Test
    fun `organization_mode and ai_instructions are persisted on the material row`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        materialService.uploadMaterial(
            userId, goalId, "book.pdf", samplePdfBytes(),
            organizationMode = "auto",
            aiInstructions = "Focus on food vocabulary",
            resourceType = "book",
        )

        val stored = transaction(database) { MaterialsTable.selectAll().single() }
        assertEquals("auto", stored[MaterialsTable.organizationMode])
        assertEquals("Focus on food vocabulary", stored[MaterialsTable.aiInstructions])
        assertEquals("book", stored[MaterialsTable.resourceType])
    }

    @Test
    fun `uploading without organization_mode defaults to auto (regression)`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val outcome = materialService.uploadMaterial(userId, goalId, "book.pdf", samplePdfBytes())

        assertIs<UploadOutcome.Created>(outcome)
        assertEquals("UPLOADED", outcome.status)
        val stored = transaction(database) { MaterialsTable.selectAll().single() }
        assertEquals("auto", stored[MaterialsTable.organizationMode])
    }

    @Test
    fun `uploading pasted text succeeds and queues an extraction job`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val outcome = materialService.uploadTextMaterial(
            userId, goalId, "Pasted text",
            "Der Hund läuft schnell durch den Park und spielt mit dem Ball.",
        )

        assertIs<UploadOutcome.Created>(outcome)
        assertEquals("UPLOADED", outcome.status)
        assertEquals(1, queuedJobs)

        val stored = transaction(database) { MaterialsTable.selectAll().single() }
        assertEquals("text/plain", stored[MaterialsTable.mimeType])
    }

    @Test
    fun `pasted text under the minimum length is rejected`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val outcome = materialService.uploadTextMaterial(userId, goalId, "Pasted text", "too short")
        assertIs<UploadOutcome.NoExtractableText>(outcome)
    }

    @Test
    fun `pasted text over the max length is rejected`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val huge = "a".repeat(200_001)
        val outcome = materialService.uploadTextMaterial(userId, goalId, "Pasted text", huge)
        assertIs<UploadOutcome.FileTooLarge>(outcome)
    }

    @Test
    fun `pasted text deduplicates against an identical file upload for the same user`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val text = "Der Hund läuft schnell durch den Park und spielt mit dem Ball."
        val fileOutcome = materialService.uploadMaterial(userId, goalId, "book.pdf", samplePdfBytes(text))
        assertIs<UploadOutcome.Created>(fileOutcome)

        val textOutcome = materialService.uploadTextMaterial(userId, goalId, "Pasted text", text)
        assertIs<UploadOutcome.DuplicateFile>(textOutcome)
        assertEquals(fileOutcome.materialId, textOutcome.existingMaterialId)
    }

    @Test
    fun `uploading pasted text for a goal the user doesn't own is rejected`() = runTest {
        val (userId, _) = registerUserWithGoal()
        val someoneElsesGoalId = java.util.UUID.randomUUID().toString()
        val outcome = materialService.uploadTextMaterial(
            userId, someoneElsesGoalId, "Pasted text",
            "Der Hund läuft schnell durch den Park und spielt mit dem Ball.",
        )
        assertIs<UploadOutcome.GoalNotFound>(outcome)
    }

    @Test
    fun `uploading a goal the user doesn't own is rejected`() = runTest {
        val (userId, _) = registerUserWithGoal()
        val someoneElsesGoalId = java.util.UUID.randomUUID().toString()
        val outcome = materialService.uploadMaterial(userId, someoneElsesGoalId, "book.pdf", samplePdfBytes())
        assertIs<UploadOutcome.GoalNotFound>(outcome)
    }

    @Test
    fun `re-uploading identical content for the same user is flagged as a duplicate`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val bytes = samplePdfBytes()
        val first = materialService.uploadMaterial(userId, goalId, "book.pdf", bytes)
        assertIs<UploadOutcome.Created>(first)

        val second = materialService.uploadMaterial(userId, goalId, "book-copy.pdf", bytes)
        assertIs<UploadOutcome.DuplicateFile>(second)
        assertEquals(first.materialId, second.existingMaterialId)
    }

    @Test
    fun `two different users can upload the same content without conflict`() = runTest {
        val (userA, goalA) = registerUserWithGoal("usera@example.com")
        val (userB, goalB) = registerUserWithGoal("userb@example.com")
        val bytes = samplePdfBytes()

        val first = materialService.uploadMaterial(userA, goalA, "book.pdf", bytes)
        val second = materialService.uploadMaterial(userB, goalB, "book.pdf", bytes)

        assertIs<UploadOutcome.Created>(first)
        assertIs<UploadOutcome.Created>(second)
        assertNotEquals(first.materialId, second.materialId)
        // No LessonSegmentationWorker is running in this test, so there's no cache hit yet - but
        // the second upload still shouldn't queue a redundant job for the same content hash.
        assertEquals(1, queuedJobs)
        assertEquals("UPLOADED", second.status)
    }

    @Test
    fun `listMaterials only returns the caller's own materials`() = runTest {
        val (userA, goalA) = registerUserWithGoal("usera@example.com")
        val (userB, goalB) = registerUserWithGoal("userb@example.com")
        materialService.uploadMaterial(userA, goalA, "a.pdf", samplePdfBytes("Text for user A's book."))
        materialService.uploadMaterial(userB, goalB, "b.pdf", samplePdfBytes("Text for user B's book."))

        val materials = materialService.listMaterials(userA)
        assertEquals(1, materials.size)
        assertEquals("a.pdf", materials.single().filename)
    }

    @Test
    fun `reprocess requeues a failed material but not one that's not failed`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val created = materialService.uploadMaterial(userId, goalId, "book.pdf", samplePdfBytes())
        assertIs<UploadOutcome.Created>(created)

        // Not failed yet - reprocess should refuse.
        assertTrue(!materialService.reprocess(userId, created.materialId))

        transaction(database) {
            val materialUuid = java.util.UUID.fromString(created.materialId)
            val contentHash = MaterialsTable.selectAll().where { MaterialsTable.id eq materialUuid }.single()[MaterialsTable.contentHash]
            MaterialsTable.update({ MaterialsTable.id eq materialUuid }) { it[status] = "FAILED" }
            ExtractionJobsTable.update({ ExtractionJobsTable.contentHash eq contentHash }) { it[status] = "FAILED" }
        }

        assertTrue(materialService.reprocess(userId, created.materialId))
        assertEquals(2, queuedJobs)
    }

    @Test
    fun `getMaterial computes vocab_count and an averaged vocab_progress per lesson`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val created = materialService.uploadMaterial(
            userId, goalId, "book.pdf", samplePdfBytes(),
            organizationMode = "entire",
        )
        assertIs<UploadOutcome.Created>(created)

        val lessonId = transaction(database) {
            LessonsTable.selectAll().where { LessonsTable.materialId eq java.util.UUID.fromString(created.materialId) }
                .single()[LessonsTable.id]
        }

        // Two items: one "new" (stage 0/3), one "mastered" (stage 3/3) -> average 0.5.
        transaction(database) {
            val newItemId = java.util.UUID.randomUUID()
            VocabularyItemsTable.insert {
                it[id] = newItemId
                it[this.lessonId] = lessonId
                it[term] = "Hund"
                it[meaning] = "dog"
                it[createdAt] = java.time.Instant.now()
            }
            VocabularyProgressTable.insert {
                it[id] = java.util.UUID.randomUUID()
                it[this.userId] = java.util.UUID.fromString(userId)
                it[vocabularyItemId] = newItemId
                it[masteryState] = "new"
                it[correctStreak] = 0
                it[intervalIndex] = 0
                it[nextReviewAt] = java.time.Instant.now()
                it[lastReviewedAt] = null
            }

            val masteredItemId = java.util.UUID.randomUUID()
            VocabularyItemsTable.insert {
                it[id] = masteredItemId
                it[this.lessonId] = lessonId
                it[term] = "Katze"
                it[meaning] = "cat"
                it[createdAt] = java.time.Instant.now()
            }
            VocabularyProgressTable.insert {
                it[id] = java.util.UUID.randomUUID()
                it[this.userId] = java.util.UUID.fromString(userId)
                it[vocabularyItemId] = masteredItemId
                it[masteryState] = "mastered"
                it[correctStreak] = 4
                it[intervalIndex] = 4
                it[nextReviewAt] = java.time.Instant.now()
                it[lastReviewedAt] = java.time.Instant.now()
            }
        }

        val detail = materialService.getMaterial(created.materialId)!!
        val lesson = detail.lessons.single()
        assertEquals(2, lesson.vocabCount)
        assertEquals(0.5f, lesson.vocabProgress)
    }

    @Test
    fun `getMaterial reports 0 vocab_progress for a lesson with no vocabulary yet`() = runTest {
        val (userId, goalId) = registerUserWithGoal()
        val created = materialService.uploadMaterial(
            userId, goalId, "book.pdf", samplePdfBytes(),
            organizationMode = "entire",
        )
        assertIs<UploadOutcome.Created>(created)

        val detail = materialService.getMaterial(created.materialId)!!
        val lesson = detail.lessons.single()
        assertEquals(0, lesson.vocabCount)
        assertEquals(0f, lesson.vocabProgress)
    }
}
