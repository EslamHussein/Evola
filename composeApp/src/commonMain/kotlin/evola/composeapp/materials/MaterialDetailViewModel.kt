package evola.composeapp.materials

import evola.shared.materials.MaterialsRepository
import pro.respawn.flowmvi.android.StoreViewModel

class MaterialDetailViewModel(materialId: String, repository: MaterialsRepository) :
    StoreViewModel<MaterialDetailState, MaterialDetailIntent, Nothing>(MaterialDetailContainer(materialId, repository))
