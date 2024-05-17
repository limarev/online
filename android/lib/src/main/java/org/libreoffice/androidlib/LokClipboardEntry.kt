package org.libreoffice.androidlib

import java.io.Serializable;

class LokClipboardEntry : java.io.Serializable {
    var mime: String? = null
    var data: ByteArray = byteArrayOf()
}
