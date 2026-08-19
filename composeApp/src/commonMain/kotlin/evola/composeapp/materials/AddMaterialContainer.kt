package evola.composeapp.materials

import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.reduce

class AddMaterialContainer : Container<AddMaterialState, AddMaterialIntent, Nothing> {
    override val store = store(initial = AddMaterialState()) {
        configure { name = "AddMaterialStore" }
        reduce { intent ->
            when (intent) {
                is AddMaterialIntent.SelectType -> updateState { copy(selectedType = intent.type) }
            }
        }
    }
}
