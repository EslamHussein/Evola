package evola.composeapp.wizard

import evola.composeapp.materials.StagedResource
import evola.shared.materials.MaterialsRepository
import pro.respawn.flowmvi.android.StoreViewModel

class AiWizardViewModel(goalId: String, staged: StagedResource, repository: MaterialsRepository) :
    StoreViewModel<WizardState, WizardIntent, Nothing>(AiWizardContainer(goalId, staged, repository))
