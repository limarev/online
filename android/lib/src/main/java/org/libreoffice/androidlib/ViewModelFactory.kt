package org.libreoffice.androidlib

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Фабрика для создания вью моделей.
 *
 * @param context Любой контекст.
 *
 * @author Уколов Александр 24.06.2021.
 */
internal class ViewModelFactory(context: Context) : ViewModelProvider.Factory {

    private val applicationContext: Context = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            DocumentViewerViewModel::class.java -> DocumentViewerViewModel(applicationContext) as T
            else -> throw IllegalArgumentException()
        }
    }
}
