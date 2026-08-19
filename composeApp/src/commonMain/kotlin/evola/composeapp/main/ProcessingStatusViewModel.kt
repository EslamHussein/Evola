package evola.composeapp.main

import evola.shared.materials.MaterialsRepository
import pro.respawn.flowmvi.android.StoreViewModel

class ProcessingStatusViewModel(materialsRepository: MaterialsRepository) :
    StoreViewModel<ProcessingStatusState, ProcessingStatusIntent, Nothing>(ProcessingStatusContainer(materialsRepository))
