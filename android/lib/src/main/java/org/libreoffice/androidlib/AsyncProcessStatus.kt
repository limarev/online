package org.libreoffice.androidlib

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Класс для инкапсуляции данных о статусе асинхронного процесса.
 *
 * @property inProgress Работает ли сайчас процесс или нет.
 * @property statusText Текст статуса процесса.
 * @property progressType Тип прогресс бара (опционально).
 *
 * @author Уколов Александр 28.06.2021.
 */
@Parcelize
data class AsyncProcessStatus(
    val inProgress: Boolean,
    val statusText: String,
    val progressType: ProgressType?,
) : Parcelable
