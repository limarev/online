package org.libreoffice.androidlib.extensions

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Подключает наблюдаеля за flow.
 * Подключить наблюдателя можно только в [Fragment.onViewCreated].
 *
 * @author Уколов Александр 28.06.2021.
 */
internal inline fun <T> Flow<T>.observe(fragment: Fragment, noinline action: suspend (value: T) -> Unit) {
    val flow = this
    val lifecycleOwner = fragment.viewLifecycleOwner

    lifecycleOwner.lifecycleScope.launch {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect(action)
        }
    }
}
