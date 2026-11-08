package com.marcuspaulo.tarefas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Mantém a tela nas rotações/navegação e descarta seus dados ao trocar de conta. */
class WorkspaceViewModel : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private var workspace: String? = null

    fun select(key: String?) {
        if (workspace == key) return
        viewModelStore.clear()
        workspace = key
    }

    override fun onCleared() {
        viewModelStore.clear()
    }
}
