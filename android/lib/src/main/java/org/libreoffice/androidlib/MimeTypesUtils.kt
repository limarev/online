package org.libreoffice.androidlib

/**
 * Утилитный класс для работы с Mime типами.
 *
 * @author Уколов Александр 08.07.2021.
 */
object MimeTypesUtils {

    /**
     * Возвращает список Mime типов, с которыми может работать библиотека.
     *
     * @author Уколов Александр 08.07.2021.
     */
    fun getAvailableMimeTypes(): List<String> =
        listOf(
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.graphics",
            "application/vnd.oasis.opendocument.presentation",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.text-flat-xml",
            "application/vnd.oasis.opendocument.graphics-flat-xml",
            "application/vnd.oasis.opendocument.presentation-flat-xml",
            "application/vnd.oasis.opendocument.spreadsheet-flat-xml",
            "application/vnd.oasis.opendocument.text-template",
            "application/vnd.oasis.opendocument.spreadsheet-template",
            "application/vnd.oasis.opendocument.graphics-template",
            "application/vnd.oasis.opendocument.presentation-template",
            "application/rtf",
            "text/rtf",
            "application/msword",
            "application/vnd.ms-powerpoint",
            "application/vnd.ms-excel",
            "application/vnd.visio",
            "application/vnd.visio.xml",
            "application/x-mspublisher",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
            "application/vnd.openxmlformats-officedocument.presentationml.template",
            "text/csv",
            "text/comma-separated-values",
            "application/vnd.ms-works",
            "application/vnd.apple.keynote",
            "application/x-abiword",
            "application/x-pagemaker",
            "image/x-emf",
            "image/x-svm",
            "image/x-wmf",
            "image/svg+xml"
        )
}
