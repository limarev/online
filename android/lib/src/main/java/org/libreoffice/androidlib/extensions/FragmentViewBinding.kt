package org.libreoffice.androidlib.extensions

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Расширение для фрагмента для упрощенного доступа к делегату,
 * адаптированное для использования в обфусцированном коде (без применения рефлексии).
 *
 * @author Уколов Александр 19.10.2021.
 */
internal fun <T : ViewBinding> Fragment.viewBinding(bindMethod: (rootView: View) -> T): FragmentViewBindingDelegate<T> =
    FragmentViewBindingDelegate(this, bindMethod)

/**
 * Делегат для инициализации биндинга для фрагмента,
 * адаптированный для использования в обфусцированном коде (без применения рефлексии).
 *
 * @property fragment Фрагмент, для которого надо создать биндинг.
 * @property bindMethod Метод для биндинга.
 *
 * @author Уколов Александр 19.10.2021.
 */
internal class FragmentViewBindingDelegate<T : ViewBinding>(
    private val fragment: Fragment,
    private val bindMethod: (rootView: View) -> T
) : ReadOnlyProperty<Fragment, T> {

    private var binding: T? = null

    init {
        fragment.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                fragment.viewLifecycleOwnerLiveData.observe(fragment) { viewLifecycleOwner ->
                    viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            binding = null
                        }
                    })
                }
            }
        })
    }

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        binding?.let { viewBinding ->
            return viewBinding
        }

        val lifecycle = fragment.viewLifecycleOwner.lifecycle
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
            error("Cannot access view bindings. View lifecycle is ${lifecycle.currentState}!")
        }

        return bindMethod.invoke(thisRef.requireView()).also { viewBinding ->
            binding = viewBinding
        }
    }
}
