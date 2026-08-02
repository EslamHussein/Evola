package evola.server

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MaterialServiceTest {

    private val database = TestDatabase.database
    private var queuedJobs = 0
    private val materialService = MaterialService(database, onJobQueued = { queuedJobs++ })

    @BeforeEach
    fun clearTables() {
        queuedJobs = 0
        transaction(database) {
            MaterialsTable.deleteAll()
            ExtractionJobsTable.deleteAll()
            ExtractionCacheTable.deleteAll()
        }
    }

    @Test
    fun `uploading new content queues an extraction job`() = runTest {
        val userId = UUID.randomUUID().toString()
        val response = materialService.uploadMaterial(
            UploadMaterialRequest(userId, "book.txt", "Hello world, this is a test."),
        )

        assertEquals("UPLOADED", response.status)
        assertFalse(response.cacheHit)
        assertEquals(1, queuedJobs)
        assertEquals(1L, transaction(database) { ExtractionJobsTable.selectAll().count() })
    }

    @Test
    fun `uploading the same normalized content twice only queues one job`() = runTest {
        val userId = UUID.randomUUID().toString()
        materialService.uploadMaterial(UploadMaterialRequest(userId, "book.txt", "Hello   world"))
        materialService.uploadMaterial(UploadMaterialRequest(userId, "book-copy.txt", "Hello world"))

        assertEquals(1, queuedJobs)
        assertEquals(1L, transaction(database) { ExtractionJobsTable.selectAll().count() })
        assertEquals(2L, transaction(database) { MaterialsTable.selectAll().count() })
    }

    @Test
    fun `listMaterials returns only that user's materials`() = runTest {
        val userA = UUID.randomUUID().toString()
        val userB = UUID.randomUUID().toString()
        materialService.uploadMaterial(UploadMaterialRequest(userA, "a.txt", "Content A"))
        materialService.uploadMaterial(UploadMaterialRequest(userB, "b.txt", "Content B"))

        val materials = materialService.listMaterials(userA)
        assertEquals(1, materials.size)
        assertEquals("a.txt", materials.single().filename)
    }
}
