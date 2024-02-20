package org.libreoffice.androidlib

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.libreoffice.androidlib.R
import org.libreoffice.androidlib.databinding.FragmentDocumentViewerBinding
import java.io.File

/**
 * Фрагмент для открытия документа.
 *
 * @author Уколов Александр 25.06.2021.
 */
open class DocumentViewerFragment : Fragment(R.layout.fragment_document_viewer) {

    private val binding: FragmentDocumentViewerBinding by viewBinding(FragmentDocumentViewerBinding::bind)

    private val viewModel: DocumentViewerViewModel by viewModels(
        factoryProducer = {
            ViewModelFactory(requireContext())
        }
    )

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // TODO: Нужно подумать, как переписать эту часть.
            AsyncProcessStatusFragment
                .newInstance(
                    AsyncProcessStatus(
                        true,
                        getString(R.string.exiting),
                        null
                    )
                )
                .showNow(childFragmentManager, AsyncProcessStatusFragment.TAG)

            // TODO: Блокирующий вызов postMobileMessageNativeBye на главном потоке не лучшее решение,
            //  но в оригинальной колабре было так же. Остается только показать диалог прежде, чем поток
            //  заблокируется на длительное время. Для этого делаем post.
            binding.webView.post {
                viewModel.postMobileMessageNativeBye()
                isEnabled = false
                requireActivity().onBackPressed()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)

        setHasOptionsMenu(true)
        initWebView()
        observeViewModel()

        if (savedInstanceState == null) {
            val fileToLoad = requireNotNull(requireArguments().getSerializable(ARG_KEY_FILE)) as File
            viewModel.prepareAndLoadFile(fileToLoad, "readonly")
        }
    }

    /**
     * Инициализирует [WebView], включает возможность использования JavaScript и
     * связывает JavaScript и Android код для двухстороннего обмена данными.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        val activity = requireActivity()
        if (activity !is CollaboraWebViewBackgroundColorSupplier) {
            throw IllegalArgumentException("Parent activity must implements CollaboraWebViewBackgroundColorSupplier")
        }

        val backgroundColorAttr = activity.supplyCollaboraWebViewBackgroundColorAttr()
        val outputValue = TypedValue().apply {
            requireActivity().theme.resolveAttribute(backgroundColorAttr, this, true)
        }

        binding.webView.setBackgroundColor(outputValue.data)
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.addJavascriptInterface(viewModel, JAVASCRIPT_INTERFACE_NAME)
        binding.webView.settings.domStorageEnabled = true
    }

    /**
     * Инициализирует подписки [viewModel].
     */
    private fun observeViewModel() {
        viewModel.stringFileUrlToLoad.observe(this) { stringFileUrlToLoad: String ->
            binding.webView.loadUrl(stringFileUrlToLoad)
        }

        viewModel.asyncProcess.observe(this) { asyncProcessStatus: AsyncProcessStatus? ->
            if (asyncProcessStatus != null) {
                val fragment = findAsyncProcessStatusFragment()
                if (asyncProcessStatus.inProgress) {
                    if (fragment == null) {
                        AsyncProcessStatusFragment
                            .newInstance(asyncProcessStatus)
                            .show(childFragmentManager, AsyncProcessStatusFragment.TAG)
                    } else {
                        fragment.updateStaticViewData(asyncProcessStatus)
                    }
                } else {
                    fragment?.dismiss()
                }
            }
        }

        viewModel.progressValue.observe(this) { progressValue: Int ->
            findAsyncProcessStatusFragment()?.updateDeterminateProgressBarValue(progressValue)
        }

        viewModel.hyperlink.observe(this) { hyperlink: String ->
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(hyperlink)
            startActivity(intent)
        }

        viewModel.fakeWebSocketOnMessageCalled.observe(this) { javaScriptMethodUri: String ->
            binding.webView.post {
                if (view != null) {
                    binding.webView.loadUrl(javaScriptMethodUri)
                }
            }
        }
    }

    /**
     * Ищет в дочернем менеджере фрагментов фрагмент [AsyncProcessStatusFragment].
     *
     * @return Если фрагмент был найден, то вернется его экземпляр, если нет то null.
     */
    private fun findAsyncProcessStatusFragment(): AsyncProcessStatusFragment? {
        val fragmentTag = AsyncProcessStatusFragment.TAG
        return childFragmentManager.findFragmentByTag(fragmentTag) as? AsyncProcessStatusFragment
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.postMobileMessageNativeBye()
    }

    companion object {
        /**
         * Название интерфейса для обмена данными с JavaScript.
         * Должно быть именно таким, потому что этот идентификатор
         * используется JavaScript для вызова методов
         * интерфейса [LoolMessageHandler],
         */
        private const val JAVASCRIPT_INTERFACE_NAME = "COOLMessageHandler"

        /**
         * Инициализируем библиотеку с JNI.
         * Без инициализации нативные методы не будут найдены.
         */
        init {
            System.loadLibrary("androidapp")
        }

        /**
         * Создает экземпляр фрагмента.
         *
         * @param file Файл для открытия со схемой [ContentResolver.SCHEME_FILE].
         * [file] необходимо создавать с помощью [File.createTempFile].
         * @return Экземпляр фрагмента.
         */
        fun newInstance(file: File): DocumentViewerFragment =
            DocumentViewerFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_KEY_FILE, file)
                }
            }
    }
}
