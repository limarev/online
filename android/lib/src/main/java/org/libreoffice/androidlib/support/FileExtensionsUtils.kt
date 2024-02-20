package org.libreoffice.androidlib.support

import android.net.Uri

/**
 * Утилитный класс для работы с расширениями файлов.
 *
 * @author Уколов Александр 06.07.2021.
 */
object FileExtensionsUtils {

    // Расширения файлов для Microsoft PowerPoint.
    internal const val PPT = "ppt"
    internal const val PPTX = "pptx"

    // Расширения файлов для Microsoft Word.
    internal const val DOC = "doc"
    internal const val DOCX = "docx"

    // Расширения файлов для Microsoft Excel.
    internal const val XLS = "xls"
    internal const val XLSX = "xlsx"

    // Расширения файлов изображений.
    internal const val SVG = "svg"

    /**
     * Возвращает список расширений файлов без точки, с которыми умеет
     * работать библиотека.
     *
     * @author Уколов Александр 06.07.2021.
     */
    fun getAvailableFileExtensions(): List<String> =
        listOf(
            PPT,
            PPTX,
            DOC,
            DOCX,
            XLS,
            XLSX
        )
}
