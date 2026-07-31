package evola.integrations.aigateway

data class AnalyzeResourceRequest(
    val text: String,
    val fileNameHint: String?,
)

data class ResourceAnalysisResult(
    val language: String,
    val cefrLevel: String,
    val topics: List<String>,
    val summary: String,
    val modelUsed: String,
)

data class GenerateLearningContentRequest(
    val text: String,
    val goal: String,
    val expectedLevel: String,
    val topics: List<String>,
)

data class GeneratedLearningContent(
    val content: String,
    val modelUsed: String,
)
