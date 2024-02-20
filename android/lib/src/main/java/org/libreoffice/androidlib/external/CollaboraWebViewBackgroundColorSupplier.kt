package org.libreoffice.androidlib.external

import androidx.annotation.AttrRes
import org.libreoffice.androidlib.documentviewer.DocumentViewerFragment

/**
 * Интерфейс, который должна реализовать активити, которая выступает
 * в роли хоста для фрагмента [DocumentViewerFragment].
 */
interface CollaboraWebViewBackgroundColorSupplier {

    /**
     * Должен вернуть аттрибут, который фрагмент конвертирует в цвет
     * текущей темы активити.
     */
    @AttrRes
    fun supplyCollaboraWebViewBackgroundColorAttr(): Int
}
