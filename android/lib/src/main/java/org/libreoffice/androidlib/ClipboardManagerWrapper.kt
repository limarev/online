package org.libreoffice.androidlib

import android.content.ClipData
import android.content.ClipboardManager
import java.lang.Exception

class ClipboardManagerWrapper(
    private val clipboardManager : ClipboardManager
){
    private var cash : ClipData? = null

    fun setClipData(clipData : ClipData){
        cash = clipData
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText(
                clipboardExternallMessage,
                clipboardExternallMessage
            )
        )
    }

    fun getClipData(): ClipData?{
        val clipData = clipboardManager.primaryClip

        for (i in 0..(clipData?.itemCount ?: 0)){
            try {
                val text = clipData?.getItemAt(i)?.text

                // если скопирована заглушка и кеш не пуст возвращаем кеш
                if (text == clipboardExternallMessage)
                    return cash ?: clipData
            } catch (e : Exception){

            }
        }
        return clipData
    }

    companion object {
        private val clipboardExternallMessage = "Копирование данных запрещено"
    }
}
