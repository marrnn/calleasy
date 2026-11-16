package com.calleasy.app.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

object NfcTagWriter {

    /** Tipo MIME propio: el sistema abre directamente esta app, sin selector. */
    const val MIME_TYPE = "application/vnd.abuelito.llamada"

    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    private fun buildMessage(packageName: String): NdefMessage = NdefMessage(
        arrayOf(
            NdefRecord.createMime(MIME_TYPE, "llamar".toByteArray(Charsets.UTF_8)),
            // Android Application Record: refuerza que se abra esta app concreta.
            NdefRecord.createApplicationRecord(packageName),
        )
    )

    fun write(tag: Tag, packageName: String): Result {
        val message = buildMessage(packageName)
        val size = message.toByteArray().size

        Ndef.get(tag)?.let { ndef ->
            return try {
                ndef.connect()
                when {
                    !ndef.isWritable -> Result.Failure("El sticker está bloqueado (solo lectura).")
                    ndef.maxSize < size -> Result.Failure(
                        "El sticker es muy pequeño (${ndef.maxSize} bytes, se necesitan $size)."
                    )
                    else -> {
                        ndef.writeNdefMessage(message)
                        Result.Success
                    }
                }
            } catch (e: Exception) {
                Result.Failure("No se pudo grabar. Mantén el teléfono quieto sobre el sticker e inténtalo otra vez.")
            } finally {
                runCatching { ndef.close() }
            }
        }

        NdefFormatable.get(tag)?.let { formatable ->
            return try {
                formatable.connect()
                formatable.format(message)
                Result.Success
            } catch (e: Exception) {
                Result.Failure("No se pudo formatear el sticker. Inténtalo otra vez.")
            } finally {
                runCatching { formatable.close() }
            }
        }

        return Result.Failure("Este sticker no es compatible con NDEF. Usa un sticker NTAG213/215/216.")
    }
}
