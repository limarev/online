package org.libreoffice.androidlib

import android.util.Base64
import android.util.JsonReader
import android.util.JsonWriter
import java.io.Serializable
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.HashMap
import java.util.List

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