package com.sasha.alarm.platform

import android.content.Context
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import com.sasha.alarm.core.MelodySource
import java.io.File

/**
 * Мелодия будильника.
 *
 * Выбранный файл **копируется внутрь приложения** и дальше играется оттуда.
 * Причина простая: ссылка на чужой файл переживёт не всё — папку почистят, файл
 * переименуют, доступ к внешнему хранилищу отзовут, — и однажды утром будильник
 * просто промолчит. Своя копия молчать не может.
 */
class MelodyStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** Копирует выбранный файл к себе. Возвращает null, если скопировать не вышло. */
    fun save(uri: Uri): MelodySource.Stored? {
        val displayName = queryDisplayName(uri) ?: DEFAULT_NAME
        val extension = displayName.substringAfterLast('.', "").ifEmpty { "mp3" }
        val fileName = "melody.$extension"

        return try {
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            if (target.length() == 0L) {
                target.delete()
                return null
            }
            Log.i(TAG, "мелодия скопирована: $fileName (${target.length()} байт)")
            MelodySource.Stored(fileName, displayName)
        } catch (e: Exception) {
            Log.e(TAG, "мелодию не удалось скопировать", e)
            null
        }
    }

    /** Что реально отдать проигрывателю. */
    fun resolve(melody: MelodySource): Uri? = when (melody) {
        is MelodySource.SystemAlarm -> systemAlarmUri()
        is MelodySource.Stored -> {
            val file = File(dir, melody.fileName)
            if (file.exists()) {
                file.toUri()
            } else {
                // Файл пропал — молчать нельзя, откатываемся на системный рингтон.
                Log.w(TAG, "своя мелодия пропала, беру системную")
                systemAlarmUri()
            }
        }
    }

    private fun systemAlarmUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            val index = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: -1
            if (cursor != null && index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } catch (e: Exception) {
            Log.w(TAG, "имя файла не прочиталось", e)
            null
        } finally {
            cursor?.close()
        }
    }

    private companion object {
        const val TAG = "MelodyStore"
        const val DIR_NAME = "melody"
        const val DEFAULT_NAME = "melody.mp3"
    }
}
