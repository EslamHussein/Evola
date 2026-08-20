package evola.composeapp.main

import evola.shared.feature.materials.domain.Material

data class ProcessingStatusState(val processingMaterials: List<Material> = emptyList())
