package evola.server

import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.jetbrains.exposed.sql.deleteAll
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
        // No ExtractionWorker is running in this test, so there's no cache hit yet - but the
        // second upload still shouldn't queue a redundant job for the same content hash.
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
}
