package com.sasha.alarm.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.util.Log
import java.net.Inet4Address

/**
 * Чем одна сеть отличается от другой.
 *
 * ⚠️ **Нужно потому, что сетей три, а компьютер один** (владелец, 02.09.2026: дом, дом
 * родителей, работа). Один вписанный адрес годился бы ровно для одной из них: дома
 * компьютер `192.168.0.26`, в другой сети у него будет другой адрес, и вписанный руками
 * начал бы врать молча. Поэтому телефон не спрашивает адрес, а **запоминает** его —
 * отдельно для каждой сети, в которой компьютер однажды отозвался.
 *
 * ⚠️ **Ключ строится по шлюзу и размеру подсети, а не по имени сети.** Имя (SSID) с
 * Android 10 отдают только за разрешение на местоположение, а просить доступ к
 * геопозиции ради того, чтобы вспомнить адрес ноутбука, — цена не по товару. Шлюз и
 * маска видны без единого разрешения и меняются вместе с сетью ровно так же.
 *
 * ⚠️ **Совпадение ключей возможно и не страшно.** Две квартиры на `192.168.0.1/24` дадут
 * один ключ, и телефон постучится по чужому запомненному адресу. Ответа не будет, попытка
 * оборвётся за секунду по таймауту, и дальше пойдёт обычный широковещательный поиск —
 * то есть худшее, что даёт совпадение, это одна лишняя секунда.
 */
object NetworkKey {

    /** Ключ текущей сети. `null` — сети нет либо она не даёт о себе ничего внятного. */
    fun current(context: Context): String? = try {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager?.activeNetwork
        val properties = network?.let { manager.getLinkProperties(it) }
        properties?.let(::keyOf)
    } catch (e: Exception) {
        // Сеть не опросилась — значит запомнить нечего и вспомнить нечего. Поиск в сети
        // от этого не ломается, он и был основным путём.
        Log.w(TAG, "сеть не опозналась", e)
        null
    }

    private fun keyOf(properties: LinkProperties): String? {
        val gateway = properties.routes
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway
            ?.hostAddress
        val prefix = properties.linkAddresses
            .firstOrNull { it.address is Inet4Address }
            ?.prefixLength
        if (gateway == null || prefix == null) return null
        return "$gateway/$prefix"
    }

    private const val TAG = "NetworkKey"
}
