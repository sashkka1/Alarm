package com.sasha.alarm.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

/**
 * Чтение NFC-меток.
 *
 * Reader mode, а не диспетчер интентов: пока экран открыт, метки читает только мы,
 * и система не пытается открыть под них чужое приложение. Из метки берётся один
 * лишь идентификатор — содержимое не читается вовсе, поэтому годится любая метка,
 * хоть пустая из коробки (`FLAG_READER_SKIP_NDEF_CHECK`).
 *
 * ⚠️ Reader mode живёт на **активити**, а не на сервисе: включать его можно только
 * пока активити на переднем плане, и обязательно гасить в `onPause`.
 */
class NfcReader(private val activity: Activity) {

    private val adapter: NfcAdapter? =
        activity.getSystemService(NfcManager::class.java)?.defaultAdapter

    private val main = Handler(Looper.getMainLooper())

    /** Есть ли в телефоне NFC вообще. */
    val supported: Boolean get() = adapter != null

    /** Включён ли NFC в системе прямо сейчас. */
    val enabled: Boolean get() = adapter?.isEnabled == true

    /**
     * Начать читать. [onTag] зовётся на главном потоке с идентификатором метки в hex.
     *
     * Callback от системы приходит на чужом потоке — отсюда переброс на главный.
     */
    fun start(onTag: (String) -> Unit) {
        val adapter = adapter ?: return
        try {
            adapter.enableReaderMode(
                activity,
                { tag ->
                    val id = tag?.id?.toHex().orEmpty()
                    if (id.isNotEmpty()) main.post { onTag(id) }
                },
                FLAGS,
                null,
            )
        } catch (e: Exception) {
            // Тревога важнее считывателя: не включился — экран всё равно висит,
            // а из него уводит только дедлайн бэкапа.
            Log.e(TAG, "reader mode не включился", e)
        }
    }

    fun stop() {
        try {
            adapter?.disableReaderMode(activity)
        } catch (e: Exception) {
            Log.w(TAG, "reader mode не выключился", e)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02X".format(byte) }

    companion object {
        private const val TAG = "NfcReader"

        private const val FLAGS = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

        /** Системный экран, где NFC включают. */
        fun openSettings(context: Context) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: Exception) {
                Log.w(TAG, "системный экран NFC не открылся", e)
            }
        }
    }
}
