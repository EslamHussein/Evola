package evola.composeapp.wizard

import evola.shared.materials.MaterialsRepository
import pro.respawn.flowmvi.android.StoreViewModel

class ProcessingViewModel(materialId: String, repository: MaterialsRepository) :
    StoreViewModel<ProcessingState, ProcessingIntent, Nothing>(ProcessingContainer(materialId, repository))
