package org.libreoffice.androidlib

/**
 * Интерфейс для двухстороннего взаимодействия JavaScript и Android.
 * Должен полностью соответствовать всем вызовам, которые присутствуют в JavaScript.
 *
 * @author Уколов Александр 25.06.2021.
 */
internal interface CoolMessageHandler {

    /**
     * Принимает сообщения от JavaScript. Используется вместо связи через WebSocket.
     *
     * @param message Сообщение, которое состоит из двух параметров,
     * разделенных знаком пробела. Второй параметр является опциональным,
     * его может не быть для некоторых сообщений.
     */
    fun postMobileMessage(message: String)

    /**
     * Работает ли приложение под ChromeOs. Если да, то вернется true, иначе false.
     * В нашем случае приложение будет использоваться только на мобильных устройствах.
     * JavaScript библиотека требует, чтобы было возвращено какое-то значение,
     * по этому должно вернуться false.
     */
    fun isChromeOS(): Boolean

    /**
     * Принимает сообщения от JavaScript. Используется вместо связи через WebSocket.
     *
     * @param message Сообщение с информацией.
     */
    fun postMobileError(message: String)

    /**
     * Принимает сообщения от JavaScript. Используется вместо связи через WebSocket.
     *
     * @param message Сообщение с информацией.
     */
    fun postMobileDebug(message: String)
}
