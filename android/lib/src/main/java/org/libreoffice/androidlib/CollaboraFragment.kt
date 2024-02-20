package org.libreoffice.androidlib

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import org.libreoffice.androidlib.R
import org.libreoffice.androidlib.documentviewer.DocumentViewerFragment
import org.libreoffice.androidlib.support.ARG_KEY_FILE
import java.io.File

/**
 * Фрагмент, менеджер фрагментов которого будет управлять фрагментами
 * внутри этого фрагмента.
 *
 * Активити, которая будет выступать в роли хоста для этого фрагмента,
 * должна содержать параметр в манифесте "android:changeConfigs" с флагами:
 * - orientation;
 * - screenSize;
 * - screenLayout;
 * - keyboardHidden.
 *
 * При этом класс активити не должен содержать колбэк для обработки смены
 * этих конфигураций вручную.
 *
 * @author Уколов Александр 30.06.2021.
 */
class CollaboraFragment : Fragment(R.layout.fragment_collabora) {

    /**
     * Колбэк для обработки [AppCompatActivity.onBackPressed].
     *
     * Сначала будет вызван метод [AppCompatActivity.onBackPressed].
     * Далее каскадно в обратном порядке будут вызваны зарегистрированные методы
     * [OnBackPressedCallback.handleOnBackPressed].
     *
     * До тех пор пока нам необходимо обрабатывать колбэк во фрагменте,
     * мы не выставляем [OnBackPressedCallback.isEnabled] в false.
     * После того, как этот флаг будет выставлен в false, фрагмент перестанет
     * обрабатывать событие.
     *
     * В родительской активити необходимо переопределить
     * [Activity.onBackPressed] и добавить проверку на поле
     * [OnBackPressedDispatcher.hasEnabledCallbacks].
     */
    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStack()
            } else {
                isEnabled = false
                requireActivity().onBackPressed()
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            replaceWithDocumentViewerFragment()
        }
    }

    /**
     * Заменяет пустой контейнер на контейнер с фрагментом [DocumentViewerFragment].
     */
    private fun replaceWithDocumentViewerFragment() {
        val file = requireNotNull(requireArguments().getSerializable(ARG_KEY_FILE)) as File
        childFragmentManager.commit {
            disallowAddToBackStack()
            replace(R.id.container, DocumentViewerFragment.newInstance(file))
        }
    }

    companion object {
        /**
         * Создает экземпляр фрагмента.
         *
         * @param file Файл для открытия со схемой [ContentResolver.SCHEME_FILE].
         * [file] необходимо создавать с помощью [File.createTempFile].
         * @return Экземпляр фрагмента.
         */
        fun newInstance(file: File): CollaboraFragment =
            CollaboraFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_KEY_FILE, file)
                }
            }
    }
}
