package com.calleasy.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Familiar que aparece como tarjeta en la pantalla principal. */
data class FamilyContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    /** URI de la foto: file:// (copiada desde la agenda) o content:// (galería, permiso persistente). */
    val photoUri: String? = null,
)

/**
 * Persistencia ligera: la lista se guarda como JSON en SharedPreferences.
 * No necesita base de datos ni internet.
 */
class ContactRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<FamilyContact> {
        val raw = prefs.getString(KEY_CONTACTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                FamilyContact(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    phone = o.optString("phone"),
                    photoUri = o.optString("photoUri").ifBlank { null },
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(contacts: List<FamilyContact>) {
        val array = JSONArray()
        contacts.forEach { c ->
            array.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("phone", c.phone)
                    .put("photoUri", c.photoUri ?: "")
            )
        }
        prefs.edit().putString(KEY_CONTACTS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "calleasy"
        private const val KEY_CONTACTS = "contacts"

        /**
         * Límite del MVP. Para escalar basta con subir este valor:
         * la cuadrícula de la pantalla principal se adapta a cualquier cantidad.
         */
        const val MAX_CONTACTS = 4
    }
}
