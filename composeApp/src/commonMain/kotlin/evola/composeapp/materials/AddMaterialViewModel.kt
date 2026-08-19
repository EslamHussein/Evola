package evola.composeapp.materials

import pro.respawn.flowmvi.android.StoreViewModel

class AddMaterialViewModel :
    StoreViewModel<AddMaterialState, AddMaterialIntent, Nothing>(AddMaterialContainer())
