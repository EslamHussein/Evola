package evola.composeapp.feature.home.vm

import evola.shared.feature.materials.domain.Material

data class ProcessingStatusState(val processingMaterials: List<Material> = emptyList())
