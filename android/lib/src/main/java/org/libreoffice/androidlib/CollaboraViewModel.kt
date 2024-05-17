package org.libreoffice.androidlib

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.os.Build
import android.content.ClipboardManager
import android.webkit.JavascriptInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.libreoffice.androidlib.BuildConfig
import org.libreoffice.androidlib.R
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale
import java.nio.charset.Charset
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.File
import android.util.Log
import android.content.ClipData
import android.net.Uri
import android.util.Base64
import android.util.JsonReader
import android.util.JsonWriter
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
/**
 * Вью модель для фрагмента [DocumentViewerFragment].
 *
 * @property applicationContext Контекст приложения.
 *
 * @author Уколов Александр 25.06.2021.
 */
@SuppressLint("StaticFieldLeak")
open class CollaboraViewModel(private val applicationContext: Context) : ViewModel(), CoolMessageHandler {

    private val clipboardManager : ClipboardManager by lazy {
        applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    init {
        System.loadLibrary("androidapp")
    }
    /**
     * Нативный метод для создания LOOLWSD.
     *
     * @param dataDir Путь до директории в памяти устройства.
     * @param cacheDir Путь до директории в кэше устройства.
     * @param apkFile Путь до пакета приложения, включая сам пакет.
     * @param assetManager Менеджер ассетов.
     * @param loadFileURL Исходный uri файла в виде строки для загрузки.
     * @param uiMode Режим для пользовательского интерфейса.
     */
    external fun createCOOLWSD(
        dataDir: String,
        cacheDir: String,
        apkFile: String,
        assetManager: AssetManager,
        loadFileURL: String,
        uiMode: String,
        userName: String
    )

    external fun saveAs(
        fileUri: String,
        format: String,
        options: String
    )

    external fun getClipboardContent(
        aData : LokClipboardData
    ) : Boolean

    external fun setClipboardContent(
        aData : LokClipboardData
    )

    external fun paste(
        mimeType : String,
        data : ByteArray
    )

    /**
     * Передает сообщение из JavaScript в C++ с помощью JNI,
     * а затем в Android и наоборот.
     *
     * @param message Сообщение, которое состоит из двух параметров,
     * разделенных знаком пробела.
     */
    external fun postMobileMessageNative(message: String)

    protected val _stringFileUrlToLoad = Channel<String>(Channel.CONFLATED)
    val stringFileUrlToLoad: Flow<String> = _stringFileUrlToLoad.receiveAsFlow()

    protected val _asyncProcess = MutableStateFlow<AsyncProcessStatus?>(null)
    val asyncProcess: StateFlow<AsyncProcessStatus?> = _asyncProcess.asStateFlow()

    protected val _progressValue = Channel<Int>(Channel.UNLIMITED)
    val progressValue: Flow<Int> = _progressValue.receiveAsFlow()

    protected val _hyperlink = Channel<String>(Channel.CONFLATED)
    val hyperlink: Flow<String> = _hyperlink.receiveAsFlow()

    protected val _fakeWebSocketOnMessageCalled = Channel<String>(Channel.UNLIMITED)
    val fakeWebSocketOnMessageCalled: Flow<String> = _fakeWebSocketOnMessageCalled.receiveAsFlow()

    private var _userName : String = ""
    private var clipData: ClipData? = null

    @JavascriptInterface
    override open fun postMobileMessage(message: String) {
        val messageAndParameter = message.split(" ", ignoreCase = true, limit = 2)
        if (beforeMessageFromWebView(messageAndParameter)) {
            postMobileMessageNative(message)
        }
    }

    @JavascriptInterface
    override open fun isChromeOS(): Boolean {
        return false
    }

    @JavascriptInterface
    override open fun postMobileError(message: String) {
        // Не используется в стандартной Android реализации Collabora.
        // Вызывается из JavaScript, по этому добавили пустую реализацию.
    }

    @JavascriptInterface
    override open fun postMobileDebug(message: String) {
        // Не используется в стандартной Android реализации Collabora.
        // Вызывается из JavaScript, по этому добавили пустую реализацию.
    }

    /**
     * Отправляет сообщение о том, что необходимо завершить работу
     * с открытым документом. Как ни странно, в оригинале это сообщение
     * отправляется в главном потоке. Наверное, жизненно необходимо
     * дождаться окончания процесса, который связан с этим сообщением.
     */
    open fun postMobileMessageNativeBye() {
        postMobileMessageNative(MSG_BYE)
    }

    /**
     * Этот метод вызывается из C++ кода через JNI.
     * Вызов осуществляется не из главного потока.
     *
     * @param message Сообщение с данными.
     */
    open fun callFakeWebsocketOnMessage(message: String) {
        // Uri метода с JSON-параметром для выполнения в JavaScript.
        val javaScriptMethodUri = "javascript:window.TheFakeWebSocket.onmessage({'data':$message});"
        _fakeWebSocketOnMessageCalled.trySend(javaScriptMethodUri)

        // Обновление состояния прогресс бара.
        if (message.startsWith(MSG_PARAM_STATUS_INDICATOR) || message.startsWith(MSG_PARAM_ERROR)) {
            if (message.startsWith(MSG_PARAM_STATUS_INDICATOR_VALUE)) {
                val value = getProgressValue(message)
                _progressValue.trySend(value)
            } else if (message.startsWith(MSG_PARAM_STATUS_INDICATOR_FINISH) || message.startsWith(MSG_PARAM_ERROR)) {
                stopAsyncProcess()
            }
        }
    }

    /**
     * Выполняет подготовительные мероприятия перед загрузкой файла, а затем
     * загружает файл.
     *
     * @param permission указывает, открываем файл для просмотра или редактирования readonly/edit.
     * @param fileToLoad Файл для загрузки.
     */
    open fun prepareAndLoadFile(fileToLoad: File, permission : String, userName : String) {
        _userName = userName
        viewModelScope.launch {
            val stringFileUriToLoad = fileToLoad.toURI().toString()
            val stringFileUrlToLoad = buildFileUrlToLoad(stringFileUriToLoad, permission)
            if (assetsWereExtracted()) {
                afterAssetsWereExtracted(stringFileUriToLoad, stringFileUrlToLoad)
            } else {
                startFirstFileLoading()
                withContext(Dispatchers.IO) {
                    val assetManager = applicationContext.assets
                    val destinationPath = applicationContext.applicationInfo.dataDir
                    copyUnpackAssetsRecursively(assetManager, UNPACK_ASSETS_DIRECTORY, destinationPath)
                }
                writeAssetsExtractedPreference()
                afterAssetsWereExtracted(stringFileUriToLoad, stringFileUrlToLoad)
            }
        }
    }

    /**
     * Получает значение индикатора загрузки из [message] в виде строки
     * и преобразует его в число типа [Int].
     *
     * @param message Сообщение с данными.
     * @return Значение индикатора загрузки.
     */
    protected open fun getProgressValue(message: String): Int {
        val start = MSG_PARAM_STATUS_INDICATOR_VALUE.length
        val end = message.indexOf(MSG_PARAM_DELIMITER, start)

        return try {
            message.substring(start, end).toInt()
        } catch (e: IndexOutOfBoundsException) {
            // TODO: Add exception handling.
            0
        } catch (e: NumberFormatException) {
            // TODO: Add exception handling.
            0
        }
    }

    /**
     * Метод, который выполняется после экспорта ассетов в память устройства.
     *
     * @param stringFileUriToLoad Uri файла в виде строки для загрузки.
     * @param stringFileUrlToLoad Url файла в виде строки для загрузки.
     */
    protected open fun afterAssetsWereExtracted(stringFileUriToLoad: String, stringFileUrlToLoad: String) {
        createLoolwsd(stringFileUriToLoad)
        startDeterminateFileLoading()
        _stringFileUrlToLoad.trySend(stringFileUrlToLoad)
    }

    /**
     * Подготавливает url файла в виде строки для его загрузки.
     *
     * @param stringFileToLoadUri Uri файла в виде строки, который нужно загрузить.
     * @param permission указывает, открываем файл для просмотра или редактирования readonly/edit.
     * @return Url файла в виде строки, который нужно загрузить.
     */
    protected open fun buildFileUrlToLoad(
        stringFileToLoadUri: String,
        permission : String
    ): String =
        StringBuilder()
            .append("file:///android_asset/dist/cool.html")
            .append("?file_path=$stringFileToLoadUri")
            .append("&closebutton=1")
            .append("&lang=${getCurrentLanguageTag()}")
            .append("&permission=$permission")
            .toString()

    /**
     * Обертка для нативного метода [createCOOLWSD].
     *
     * @param stringFileUriToLoad Uri файла в виде строки для загрузки.
     */
    private fun createLoolwsd(stringFileUriToLoad: String) {
        createCOOLWSD(
            dataDir = applicationContext.applicationInfo.dataDir,
            cacheDir = applicationContext.cacheDir.absolutePath,
            apkFile = applicationContext.packageResourcePath,
            assetManager = applicationContext.assets,
            loadFileURL = stringFileUriToLoad,
            uiMode = UI_MODE_CLASSIC,
            userName = _userName
        )
    }

    /**
     * Ищет ассеты и выполняет копирование каждого ассета из [sourcePath] в [destinationPath].
     *
     * @param assetManager Менеджер ассетов.
     * @param sourcePath Путь источника копирования.
     * @param destinationPath Путь назначения копирования.
     */
    protected open fun copyUnpackAssetsRecursively(assetManager: AssetManager, sourcePath: String, destinationPath: String) {
        try {
            assetManager.list(sourcePath)?.forEach { entity ->
                val sourceEntityPath = "$sourcePath/$entity"
                val destinationEntityPath = "$destinationPath/$entity"

                // Определяем является ли сущность файловой системы файлом.
                // Если сущность не содержит вложенные сущности, то это файл.
                if (assetManager.list(sourceEntityPath)?.isEmpty() == true) {
                    File(destinationPath).mkdirs()
                    copyAsset(assetManager, sourceEntityPath, destinationEntityPath)
                } else {
                    copyUnpackAssetsRecursively(assetManager, sourceEntityPath, destinationEntityPath)
                }
            }
        } catch (e: IOException) {
            // TODO: Add exception handling.
        } catch (e: Exception) {
            // TODO: Add exception handling.
        }
    }

    /**
     * Копирует ассет из [sourcePath] в [destinationPath].
     *
     * @param assetManager Менеджер ассетов.
     * @param sourcePath Путь источника копирования.
     * @param destinationPath Путь назначения копирования.
     */
    protected open fun copyAsset(assetManager: AssetManager, sourcePath: String, destinationPath: String) {
        BufferedInputStream(assetManager.open(sourcePath)).use { inputStream ->
            BufferedOutputStream(FileOutputStream(destinationPath)).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    /**
     * Возвращает true, если ассеты были скопированы
     * в память устройства, иначе false.
     */
    protected open fun assetsWereExtracted(): Boolean =
        readAssetsExtractedPreference() == BuildConfig.GIT_COMMIT

    /**
     * Возвращает значение преференса [ASSETS_EXTRACTED_GIT_COMMIT]
     * или null, если преференса не существует.
     */
    protected open fun readAssetsExtractedPreference(): String? =
        getSharedPreferences().getString(ASSETS_EXTRACTED_GIT_COMMIT, null)

    /**
     * Сохраняет значение [BuildConfig.GIT_COMMIT] в преференс
     * с ключем [ASSETS_EXTRACTED_GIT_COMMIT].
     */
    protected open fun writeAssetsExtractedPreference(): Unit =
        getSharedPreferences()
            .edit()
            .putString(ASSETS_EXTRACTED_GIT_COMMIT, BuildConfig.GIT_COMMIT)
            .apply()

    /**
     * Возвращает преференсы по умолчанию.
     */
    protected open fun getSharedPreferences(): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(applicationContext)

    /**
     * Возвращает буквенный код языка активной локали.
     */
    protected open fun getCurrentLanguageTag(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            applicationContext.resources.configuration.locales.get(0).toLanguageTag()
        } else {
            @Suppress("DEPRECATION")
            applicationContext.resources.configuration.locale.toLanguageTag()
        }

    /**
     * Сообщает о том, что началась первая загрузка файла.
     */
    protected open fun startFirstFileLoading() {
        _asyncProcess.value = AsyncProcessStatus(
            inProgress = true,
            statusText = applicationContext.getString(R.string.preparing_for_the_first_start_after_an_update),
            progressType = ProgressType.INDETERMINATE
        )
    }

    /**
     * Сообщает о том, что началась загрузка файла с конечным прогресс баром.
     */
    protected open fun startDeterminateFileLoading() {
        _asyncProcess.value = AsyncProcessStatus(
            inProgress = true,
            statusText = applicationContext.getString(R.string.loading),
            progressType = ProgressType.DETERMINATE
        )
    }

    /**
     * Сообщает о том, что асинхронный процесс завершился.
     */
    protected open fun stopAsyncProcess() {
        _asyncProcess.value = AsyncProcessStatus(
            inProgress = false,
            statusText = "",
            progressType = null
        )
    }

    /**
     * Не содержит обработчиков некоторых сообщений за ненадобностью,
     * а некоторые оставлены для совместимости.
     *
     * @param messageAndParameter Список, первый элемент которого - это сообщение,
     * а второй - это параметр сообщения. Параметр является опциональным, его может не быть
     * для некоторых сообщений.
     * @return Вернет true, если [messageAndParameter] нужно обработать нативным методом,
     * иначе вернет false.
     */
    protected open fun beforeMessageFromWebView(messageAndParameter: List<String>): Boolean {
        when (messageAndParameter[0].uppercase(Locale.getDefault())) {
            MSG_BYE,
            MSG_SLIDESHOW,
            MSG_MOBILEWIZARD -> {
                return false
            }
            MSG_HYPERLINK -> {
                _hyperlink.trySend(messageAndParameter[1])
                return false
            }
//            MSG_UNO -> {
//                when (messageAndParameter[1].uppercase()) {
//                    MSG_PARAM_UNO_CUT,
//                    MSG_PARAM_UNO_COPY -> {
//                        return false
//                    }
//                }
//            }
            MSG_LOADWITHPASSWORD -> {
                startDeterminateFileLoading()
                return true
            }
            MSG_PRINT,
            MSG_SAVE,
            MSG_DOWNLOADAS,
            MSG_DIM_SCREEN,
            MSG_LIGHT_SCREEN,
            HIDEPROGRESSBAR,
            MSG_REQUESTFILECOPY,
            MSG_EDITMODE
            -> {
                return false
            }
        }

        return true
    }

    /// Needs to be executed after the .uno:Copy / Paste has executed
    private val CLIPBOARD_FILE_PATH = "LibreofficeClipboardFile.data"

    fun populateClipboard() {
        val clipboardFile = File(applicationContext.cacheDir, CLIPBOARD_FILE_PATH)
        if (clipboardFile.exists()) clipboardFile.delete()
        val clipboardData = LokClipboardData()
        if (!getClipboardContent(clipboardData)) Log.e(
            "DebugVC50X86RegisterEnums",
            "no clipboard to copy"
        ) else {
            clipboardData.writeToFile(clipboardFile)
            val text: String? = clipboardData.text
            var html: String? = clipboardData.html
            if (html != null) {
                var idx: Int = html!!.indexOf("<meta name=\"generator\" content=\"")
                if (idx < 0) idx =
                    html!!.indexOf("<meta http-equiv=\"content-type\" content=\"text/html;")
                if (idx >= 0) { // inject our magic
                    val newHtml: java.lang.StringBuffer = java.lang.StringBuffer(html!!)
                    newHtml.insert(
                        idx,
                        "<meta name=\"origin\" content=\"" + getClipboardMagic() + "\"/>\n"
                    )
                    html = newHtml.toString()
                }
                if (text == null || text?.length == 0) Log.i(
                    "DebugVC50X86RegisterEnums",
                    "set text to clipoard with: text '$text' and html '$html'"
                )
                clipData = ClipData.newHtmlText(ClipDescription.MIMETYPE_TEXT_HTML, text!!, html)
                clipboardManager.setPrimaryClip(clipData!!)
            }
        }
    }
    private fun getClipboardMagic(): String {
        return "cool-clip-magic-4a22437e49a8-" + java.lang.Long.toString(12312412412)
    }

    /// Do the paste, and return true if we should short-circuit the paste locally (ie. let the core handle that)
    private fun performPaste(): Boolean {
        clipData = clipboardManager.getPrimaryClip()
        if (clipData == null) return false
        val clipDesc: ClipDescription = clipData?.getDescription() ?: return false
        for (i in 0 until clipDesc.getMimeTypeCount()) {
            Log.d(
                "DebugVC50X86RegisterEnums",
                "Pasting mime " + i + ": " + clipDesc.getMimeType(i)
            )
            if (clipDesc.getMimeType(i).equals(ClipDescription.MIMETYPE_TEXT_HTML)) {
                val html: String = clipData!!.getItemAt(i).getHtmlText()
                // Check if the clipboard content was made with the app
                return if (html.contains("cool-clip-magic-4a22437e49a8-")) {
                    // Check if the clipboard content is from the same app instance
                    if (html.contains(getClipboardMagic())) {
                        Log.i(
                            "DebugVC50X86RegisterEnums",
                            "clipboard comes from us - same instance: short circuit it $html"
                        )
                        true
                    } else {
                        Log.i(
                            "DebugVC50X86RegisterEnums",
                            "clipboard comes from us - other instance: paste from clipboard file"
                        )
                        val clipboardFile =
                            File(applicationContext.getCacheDir(), CLIPBOARD_FILE_PATH)
                        var clipboardData: LokClipboardData? = null
                        if (clipboardFile.exists()) clipboardData =
                            LokClipboardData.createFromFile(clipboardFile)
                        if (clipboardData != null) {
                            setClipboardContent(clipboardData)
                            return true
                        } else {
                            // Couldn't get data from the clipboard file, but we can still paste html
                            val htmlByteArray: ByteArray =
                                html.toByteArray(Charset.forName("UTF-8"))
                            paste("text/html", htmlByteArray)
                        }
                        false
                    }
                } else {
                    Log.i("DebugVC50X86RegisterEnums", "foreign html '$html'")
                    val htmlByteArray: ByteArray = html.toByteArray(Charset.forName("UTF-8"))
                    paste("text/html", htmlByteArray)
                    false
                }
            } else if (clipDesc.getMimeType(i).startsWith("image/")) {
                val item: ClipData.Item = clipData!!.getItemAt(i)
                val uri: Uri = item.getUri()
                try {
                    val imageStream: java.io.InputStream = applicationContext.getContentResolver().openInputStream(uri)!!
                    val buffer = ByteArrayOutputStream()
                    var nRead: Int
                    val data = ByteArray(16384)
                    while (imageStream.read(data, 0, data.size).also { nRead = it } != -1) {
                        buffer.write(data, 0, nRead)
                    }
                    paste(clipDesc.getMimeType(i), buffer.toByteArray())
                    return false
                } catch (e: java.lang.Exception) {
                    Log.d("DebugVC50X86RegisterEnums", "Failed to paste image: " + e.message)
                }
            }
        }

        // try the plaintext as the last resort
        for (i in 0 until clipDesc.getMimeTypeCount()) {
            Log.d(
                "DebugVC50X86RegisterEnums",
                "Plain text paste attempt " + i + ": " + clipDesc.getMimeType(i)
            )
            if (clipDesc.getMimeType(i).equals(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                val clipItem: ClipData.Item = clipData!!.getItemAt(i)
                val text: String = clipItem.getText().toString()
                val textByteArray: ByteArray = text.toByteArray(Charset.forName("UTF-8"))
                paste("text/plain;charset=utf-8", textByteArray)
            }
        }
        return false
    }

    protected companion object {
        /**
         * Имя директории с нераспакованными ассетами.
         */
        const val UNPACK_ASSETS_DIRECTORY = "unpack"

        /**
         * Параметр в [SharedPreferences], который определяет,
         * нужно ли копировать ассеты в память устройства.
         */
        const val ASSETS_EXTRACTED_GIT_COMMIT = "ASSETS_EXTRACTED_GIT_COMMIT"

        /**
         * Режим для пользовательского интерфейса для обычных дисплеев.
         */
        const val UI_MODE_CLASSIC = "classic"

        /**
         * Разделитель для параметров, которые находятся в сообщении в методе [callFakeWebsocketOnMessage].
         */
        const val MSG_PARAM_DELIMITER = "'"

        /**
         * Параметр, который может находится в сообщении в методе [callFakeWebsocketOnMessage],
         * и обозначет событие, которое связано с индикатором загрузки.
         */
        const val MSG_PARAM_STATUS_INDICATOR = "${MSG_PARAM_DELIMITER}statusindicator"

        /**
         * Параметр, который может находится в сообщении в методе [callFakeWebsocketOnMessage],
         * и обозначет стартовый индекс значения индикатора загрузки.
         */
        const val MSG_PARAM_STATUS_INDICATOR_VALUE = "${MSG_PARAM_DELIMITER}statusindicatorsetvalue: "

        /**
         * Параметр, который может находится в сообщении в методе [callFakeWebsocketOnMessage],
         * и обозначет завершение процесса загрузки документа.
         */
        const val MSG_PARAM_STATUS_INDICATOR_FINISH = "${MSG_PARAM_DELIMITER}statusindicatorfinish:"

        /**
         * Параметр, который может находится в сообщении в методе [callFakeWebsocketOnMessage],
         * и обозначет ошибку.
         */
        const val MSG_PARAM_ERROR = "${MSG_PARAM_DELIMITER}error:"

        /**
         * Параметр, который может находится в сообщении в методе [callFakeWebsocketOnMessage],
         * и отвечает за вставку значений.
         */
        const val MSG_PARAM_UNO_PASTE = ".UNO:PASTE"

        /**
         * Параметр, который может находится в сообщении в методе [callFakeWebsocketOnMessage],
         * и отвечает за копирование значений. Добавлен в форке
         */
        const val MSG_PARAM_UNO_COPY = ".UNO:COPY"

        /**
         * Параметр, который может находится в сообщении в методе [callFakeWebsocketOnMessage],
         * и отвечает за вырезание значений. Добавлен в форке
         */
        const val MSG_PARAM_UNO_CUT = ".UNO:CUT"

        /**
         * Сообщение для инициации события закрытия документа.
         * При этом в оригинальном коде выполняется операция по
         * сохранению документа в Intent. Нам это не нужно,
         * по этому обрабатывать это сообщение мы не будем,
         * но вызвать его из Android кода нужно обязательно,
         * так как оно обрабатывается еще и нативно.
         */
        const val MSG_BYE = "BYE"

        /**
         * Сообщение для инициации события распечатки документа.
         * Функционал у нас запрещен.
         */
        const val MSG_PRINT = "PRINT"

        /**
         * Сообщение для инициации события запуска слайд-шоу презентации.
         * Функционал был перенесен на Android.
         */
        const val MSG_SLIDESHOW = "SLIDESHOW"

        /**
         * Сообщение для инициации события сохранения документа.
         * Функционал у нас запрещен.
         */
        const val MSG_SAVE = "SAVE"

        /**
         * Сообщение для инициации события экспорта документа.
         * Функционал у нас запрещен.
         */
        const val MSG_DOWNLOADAS = "DOWNLOADAS"

        /**
         * Сообщение для инициации события пробуждения экрана.
         * Функционал не актуален.
         */
        const val MSG_DIM_SCREEN = "DIM_SCREEN"

        /**
         * Сообщение для инициации события пробуждения экрана.
         * Функционал не актуален.
         */
        const val MSG_LIGHT_SCREEN = "LIGHT_SCREEN"
        const val HIDEPROGRESSBAR = "HIDEPROGRESSBAR"

        /**
         * Сообщение для инициации события выделения с помощью волшебной палочки.
         * Функционал у нас запрещен.
         */
        const val MSG_MOBILEWIZARD = "MOBILEWIZARD"

        /**
         * Сообщение для инициации события перехода по гиперссылке из документа.
         */
        const val MSG_HYPERLINK = "HYPERLINK"

        /**
         * Сообщение для идентификации режима работы с документом (можно или нельзя редактировать).
         * Функционал у нас запрещен (по умолчанию Readonly режим).
         */
        const val MSG_EDITMODE = "EDITMODE"

        /**
         * Сообщение для инициации события открытия документа с паролем.
         * Функционал у нас отсутствует.
         */
        const val MSG_LOADWITHPASSWORD = "LOADWITHPASSWORD"

        /**
         * Сообщение для инициации события копирования документа.
         * Функционал у нас запрещен.
         */
        const val MSG_REQUESTFILECOPY = "REQUESTFILECOPY"

        /**
         * Сообщение относится к ядру или Collabora Office или LibreOffice.
         * Отвечает за операции с ClipBoard.
         * Функционал у нас запрещен.
         */
        const val MSG_UNO = "UNO"
    }
}

class LokClipboardData : java.io.Serializable {
    var clipboardEntries: java.util.ArrayList<LokClipboardEntry> =
        java.util.ArrayList<LokClipboardEntry>()
    val text: String?
        get() {
            for (aEntry in clipboardEntries) {
                if (aEntry.mime?.startsWith("text/plain") == true) { // text/plain;charset=utf-8
                    return String(aEntry.data, java.nio.charset.StandardCharsets.UTF_8)
                }
            }
            return null
        }
    val html: String?
        get() {
            for (aEntry in clipboardEntries) {
                if (aEntry.mime?.startsWith("text/html") == true) {
                    return String(aEntry.data, java.nio.charset.StandardCharsets.UTF_8)
                }
            }
            return null
        }

    fun writeToFile(file: File): Boolean {
        try {
            val fileStream = FileOutputStream(file.getAbsoluteFile())
            val oos = ObjectOutputStream(fileStream)
            oos.writeObject(this)
            oos.close()
            fileStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
        return true
    }

    val best: LokClipboardEntry?
        get() = if (!clipboardEntries.isEmpty()) {
            clipboardEntries.get(0)
        } else null

    companion object {
        fun createFromFile(file: File): LokClipboardData? {
            return try {
                val fileStream = FileInputStream(file.getAbsoluteFile())
                val ois = ObjectInputStream(fileStream)
                val data = ois.readObject() as LokClipboardData
                ois.close()
                fileStream.close()
                data
            } catch (e: IOException) {
                e.printStackTrace()
                null
            } catch (e: java.lang.ClassNotFoundException) {
                e.printStackTrace()
                null
            }
        }
    }
}


class LokClipboardEntry : java.io.Serializable {
    var mime: String? = null
    var data: ByteArray = byteArrayOf()
}
