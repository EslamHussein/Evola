package evola.learningresources.infrastructure

import evola.core.kernel.LearningResourceId
import evola.learningresources.application.ResourceFileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/** Local disk storage — no S3/object-storage dependency, matches the lean-MVP infra decision. */
class LocalDiskResourceFileStorage(private val rootDir: String) : ResourceFileStorage {
    override suspend fun save(resourceId: LearningResourceId, fileName: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val dir = Path.of(rootDir)
            Files.createDirectories(dir)
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val path = dir.resolve("${resourceId.value}-$safeName")
            Files.write(path, bytes)
            path.toString()
        }
}
