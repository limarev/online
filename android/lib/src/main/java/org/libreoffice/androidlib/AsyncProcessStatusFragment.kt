package org.libreoffice.androidlib

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.libreoffice.androidlib.R
import org.libreoffice.androidlib.data.AsyncProcessStatus
import org.libreoffice.androidlib.data.ProgressType

/**
 * Диалог для отображения состояния асинхронного процесса.
 *
 * @author Уколов Александр 07.07.2021.
 */
internal class AsyncProcessStatusFragment : DialogFragment() {

    private lateinit var statusTextView: TextView
    private lateinit var indeterminateProgressBar: ProgressBar
    private lateinit var determinateProgressBar: ProgressBar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val layoutInflater = LayoutInflater.from(context)
        val view = layoutInflater.inflate(R.layout.fragment_async_process_status, null, false)

        isCancelable = false
        statusTextView = view.findViewById(R.id.status_text_view)
        indeterminateProgressBar = view.findViewById(R.id.indeterminate_progress_bar)
        determinateProgressBar = view.findViewById(R.id.determinate_progress_bar)

        val asyncProcessStatus: AsyncProcessStatus =
            requireNotNull(requireArguments().getParcelable(ARG_KEY_ASYNC_PROCESS_STATUS))
        updateStaticViewData(asyncProcessStatus)

        return MaterialAlertDialogBuilder(context)
            .setCancelable(false)
            .setView(view)
            .create()
    }

    /**
     * Обновляет статические данные представлений диалога.
     *
     * @param asyncProcessStatus Данные о статусе асинхронного процесса.
     */
    fun updateStaticViewData(asyncProcessStatus: AsyncProcessStatus) {
        statusTextView.text = asyncProcessStatus.statusText
        when (asyncProcessStatus.progressType) {
            ProgressType.INDETERMINATE -> showIndeterminateProgressBar()
            ProgressType.DETERMINATE -> showDeterminateProgressBar()
            else -> {}
        }
    }

    /**
     * Обновляет значение прогресс бара [determinateProgressBar].
     *
     * @param progressValue Новое значение прогресса.
     */
    fun updateDeterminateProgressBarValue(progressValue: Int) {
        determinateProgressBar.progress = progressValue
    }

    /**
     * Показывает бесконечный прогресс бар, а конечный скрывает.
     */
    private fun showIndeterminateProgressBar() {
        determinateProgressBar.visibility = View.GONE
        indeterminateProgressBar.visibility = View.VISIBLE
    }

    /**
     * Показывает конечный прогресс бар, а бесконечный скрывает.
     */
    private fun showDeterminateProgressBar() {
        indeterminateProgressBar.visibility = View.GONE
        determinateProgressBar.visibility = View.VISIBLE
    }

    companion object {
        /**
         * Ключ значения, которое представляет из себя данные о статусе асинхронного процесса.
         */
        private const val ARG_KEY_ASYNC_PROCESS_STATUS = "ASYNC_PROCESS_STATUS"

        /**
         * Тэг фрагмента.
         */
        val TAG = AsyncProcessStatusFragment::class.simpleName

        /**
         * Создает экземпляр фрагмента.
         *
         * @param asyncProcessStatus Данные о статусе асинхронного процесса.
         * @return Экземпляр фрагмента.
         */
        fun newInstance(asyncProcessStatus: AsyncProcessStatus): AsyncProcessStatusFragment =
            AsyncProcessStatusFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_KEY_ASYNC_PROCESS_STATUS, asyncProcessStatus)
                }
            }
    }
}
