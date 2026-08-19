package evola.composeapp.materials

import evola.shared.materials.MaterialsRepository
import pro.respawn.flowmvi.android.StoreViewModel

class MaterialsListViewModel(materialsRepository: MaterialsRepository) :
    StoreViewModel<MaterialsListState, MaterialsListIntent, Nothing>(MaterialsListContainer(materialsRepository))
