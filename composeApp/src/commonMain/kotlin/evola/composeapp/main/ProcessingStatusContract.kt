package evola.composeapp.main

import evola.shared.materials.Material
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

data class ProcessingStatusState(val processingMaterials: List<Material> = emptyList()) : MVIState

sealed interface ProcessingStatusIntent : MVIIntent
