package me.huanjue.cloudmail.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** 手动 DI 的 ViewModel 工厂 */
class VmFactory<T : ViewModel>(private val creator: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <M : ViewModel> create(modelClass: Class<M>): M = creator() as M
}
