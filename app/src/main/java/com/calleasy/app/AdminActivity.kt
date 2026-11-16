package com.calleasy.app

import android.Manifest
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.calleasy.app.data.ContactRepository
import com.calleasy.app.data.FamilyContact
import com.calleasy.app.nfc.NfcTagWriter
import com.calleasy.app.ui.AdminScreen
import com.calleasy.app.ui.AdminState
import com.calleasy.app.ui.CallEasyTheme
import com.calleasy.app.ui.NfcWriteStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Panel del familiar/administrador: contactos, fotos, permisos y grabación del sticker NFC.
 * Solo se llega aquí manteniendo pulsada la cabecera de la pantalla principal.
 */
class AdminActivity : ComponentActivity() {

    private lateinit var repository: ContactRepository
    private var nfcAdapter: NfcAdapter? = null
    private val state = mutableStateOf(AdminState())

    /** Contacto al que se le cambiará la foto tras elegirla en la galería. */
    private var photoTargetId: String? = null

    // ---------- Lanzadores de resultados ----------

    private val requestCallPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshStatus() }

    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Con o sin permiso abrimos el selector; sin permiso solo no se importa la foto.
            launchPhonePicker()
        }

    private val pickPhone = registerForActivityResult(PickPhoneContract()) { uri ->
        if (uri != null) importFromAgenda(uri)
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetId = photoTargetId
        photoTargetId = null
        if (uri != null && targetId != null) setGalleryPhoto(targetId, uri)
    }

    // ---------- Ciclo de vida ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        repository = ContactRepository(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        state.value = state.value.copy(contacts = repository.load())
        refreshStatus()

        // El permiso de llamada lo concede el administrador, nunca el adulto mayor.
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            requestCallPermission.launch(Manifest.permission.CALL_PHONE)
        }

        setContent {
            CallEasyTheme {
                AdminScreen(
                    state = state.value,
                    onAddFromAgenda = ::addFromAgenda,
                    onAddManual = { name, phone -> upsert(FamilyContact(name = name, phone = phone)) },
                    onEdit = { contact -> upsert(contact) },
                    onPickPhoto = ::pickPhotoFor,
                    onRemovePhoto = { id -> updatePhoto(id, null) },
                    onDelete = ::delete,
                    onMove = ::move,
                    onRequestCallPermission = { requestCallPermission.launch(Manifest.permission.CALL_PHONE) },
                    onOpenNfcSettings = ::openNfcSettings,
                    onStartNfcWrite = { setNfcStatus(NfcWriteStatus.Waiting) },
                    onDismissNfcWrite = { setNfcStatus(NfcWriteStatus.Idle) },
                    onDone = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        enableNfcDispatch()
    }

    override fun onPause() {
        super.onPause()
        runCatching { nfcAdapter?.disableForegroundDispatch(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return
        if (state.value.nfcStatus !is NfcWriteStatus.Waiting) {
            Toast.makeText(this, "Pulsa \"Grabar sticker NFC\" antes de acercarlo.", Toast.LENGTH_SHORT).show()
            return
        }
        setNfcStatus(NfcWriteStatus.Writing)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { NfcTagWriter.write(tag, packageName) }
            setNfcStatus(
                when (result) {
                    NfcTagWriter.Result.Success -> NfcWriteStatus.Done
                    is NfcTagWriter.Result.Failure -> NfcWriteStatus.Error(result.message)
                }
            )
        }
    }

    // ---------- NFC ----------

    /**
     * Mientras el panel está abierto, todos los tags llegan aquí (y no abren la pantalla principal).
     */
    private fun enableNfcDispatch() {
        val adapter = nfcAdapter ?: return
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getActivity(this, 0, intent, flags)
        runCatching { adapter.enableForegroundDispatch(this, pending, null, null) }
    }

    private fun openNfcSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }

    private fun setNfcStatus(status: NfcWriteStatus) {
        state.value = state.value.copy(nfcStatus = status)
    }

    private fun refreshStatus() {
        state.value = state.value.copy(
            callPermissionGranted = hasPermission(Manifest.permission.CALL_PHONE),
            nfcAvailable = nfcAdapter != null,
            nfcEnabled = nfcAdapter?.isEnabled == true,
        )
    }

    // ---------- Contactos ----------

    private fun saveContacts(list: List<FamilyContact>) {
        repository.save(list)
        state.value = state.value.copy(contacts = list)
    }

    private fun upsert(contact: FamilyContact) {
        val list = state.value.contacts
        val index = list.indexOfFirst { it.id == contact.id }
        if (index >= 0) {
            saveContacts(list.toMutableList().also { it[index] = contact })
        } else if (list.size < ContactRepository.MAX_CONTACTS) {
            saveContacts(list + contact)
        } else {
            toast("Máximo ${ContactRepository.MAX_CONTACTS} familiares.")
        }
    }

    private fun delete(id: String) {
        val contact = state.value.contacts.firstOrNull { it.id == id } ?: return
        releasePhoto(contact.photoUri)
        saveContacts(state.value.contacts.filterNot { it.id == id })
    }

    private fun move(id: String, delta: Int) {
        val list = state.value.contacts.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        saveContacts(list)
    }

    private fun addFromAgenda() {
        if (state.value.contacts.size >= ContactRepository.MAX_CONTACTS) {
            toast("Máximo ${ContactRepository.MAX_CONTACTS} familiares.")
            return
        }
        if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            launchPhonePicker()
        } else {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun launchPhonePicker() {
        try {
            pickPhone.launch(Unit)
        } catch (e: ActivityNotFoundException) {
            toast("No se encontró la agenda de contactos.")
        }
    }

    /** Lee nombre, número y foto del contacto elegido en la agenda nativa. */
    private fun importFromAgenda(phoneUri: Uri) {
        lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) { readAgendaEntry(phoneUri) }
            if (imported == null) toast("No se pudo leer el contacto.") else upsert(imported)
        }
    }

    private fun readAgendaEntry(phoneUri: Uri): FamilyContact? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        )
        val (name, number, contactId) = contentResolver.query(phoneUri, projection, null, null, null)
            ?.use { c ->
                if (!c.moveToFirst()) return null
                Triple(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getLong(2))
            } ?: return null
        if (number.isBlank()) return null

        val contact = FamilyContact(name = name.ifBlank { number }, phone = number)
        val photo = if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            copyAgendaPhoto(contactId, contact.id)
        } else null
        return contact.copy(photoUri = photo)
    }

    /** Copia la foto de la agenda al almacenamiento interno para no depender de permisos después. */
    private fun copyAgendaPhoto(contactId: Long, fileId: String): String? = runCatching {
        val contactUri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_URI, contactId.toString()
        )
        ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, contactUri, true)
            ?.use { input ->
                val file = File(photosDir(), "$fileId.jpg")
                file.outputStream().use { input.copyTo(it) }
                Uri.fromFile(file).toString()
            }
    }.getOrNull()

    // ---------- Fotos ----------

    private fun pickPhotoFor(id: String) {
        photoTargetId = id
        pickImage.launch(arrayOf("image/*"))
    }

    private fun setGalleryPhoto(id: String, uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Algunos proveedores no ofrecen permisos persistentes: la foto podría dejar
            // de verse tras reiniciar. Avisamos, pero la usamos igualmente.
            toast("Aviso: esta galería no permite acceso permanente a la foto.")
        }
        updatePhoto(id, uri.toString())
    }

    private fun updatePhoto(id: String, newUri: String?) {
        val contact = state.value.contacts.firstOrNull { it.id == id } ?: return
        if (contact.photoUri != newUri) releasePhoto(contact.photoUri)
        upsert(contact.copy(photoUri = newUri))
    }

    private fun releasePhoto(photoUri: String?) {
        if (photoUri.isNullOrBlank()) return
        val uri = Uri.parse(photoUri)
        when (uri.scheme) {
            "file" -> uri.path?.let { File(it).takeIf { f -> f.parentFile == photosDir() }?.delete() }
            "content" -> runCatching {
                val stillUsed = state.value.contacts.count { it.photoUri == photoUri } > 1
                if (!stillUsed) {
                    contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
    }

    private fun photosDir(): File = File(filesDir, "photos").apply { mkdirs() }

    // ---------- Utilidades ----------

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    /** Abre la agenda mostrando solo contactos con número de teléfono. */
    private class PickPhoneContract : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit) =
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            intent?.data.takeIf { resultCode == android.app.Activity.RESULT_OK }
    }
}
