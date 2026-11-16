package com.calleasy.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calleasy.app.data.ContactRepository
import com.calleasy.app.data.FamilyContact

sealed interface NfcWriteStatus {
    data object Idle : NfcWriteStatus
    data object Waiting : NfcWriteStatus
    data object Writing : NfcWriteStatus
    data object Done : NfcWriteStatus
    data class Error(val message: String) : NfcWriteStatus
}

data class AdminState(
    val contacts: List<FamilyContact> = emptyList(),
    val callPermissionGranted: Boolean = false,
    val nfcAvailable: Boolean = false,
    val nfcEnabled: Boolean = false,
    val nfcStatus: NfcWriteStatus = NfcWriteStatus.Idle,
)

@Composable
fun AdminScreen(
    state: AdminState,
    onAddFromAgenda: () -> Unit,
    onAddManual: (name: String, phone: String) -> Unit,
    onEdit: (FamilyContact) -> Unit,
    onPickPhoto: (id: String) -> Unit,
    onRemovePhoto: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onMove: (id: String, delta: Int) -> Unit,
    onRequestCallPermission: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onStartNfcWrite: () -> Unit,
    onDismissNfcWrite: () -> Unit,
    onDone: () -> Unit,
) {
    var editing by remember { mutableStateOf<FamilyContact?>(null) }
    var addingManual by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<FamilyContact?>(null) }
    val canAdd = state.contacts.size < ContactRepository.MAX_CONTACTS

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Configuración",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onDone) { Text("Listo") }
            }

            // ----- Estado del teléfono -----
            Section("Preparación del teléfono") {
                StatusRow(
                    ok = state.callPermissionGranted,
                    okText = "Permiso de llamada directa concedido",
                    badText = "Falta el permiso de llamada (solo abrirá el marcador)",
                    action = if (!state.callPermissionGranted) "Conceder" to onRequestCallPermission else null,
                )
                StatusRow(
                    ok = state.nfcAvailable && state.nfcEnabled,
                    okText = "NFC activado",
                    badText = if (!state.nfcAvailable) "Este teléfono no tiene NFC" else "NFC desactivado",
                    action = if (state.nfcAvailable && !state.nfcEnabled) "Activar" to onOpenNfcSettings else null,
                )
                Text(
                    "Para que la app aparezca sobre la pantalla de bloqueo, el teléfono no debe tener PIN, " +
                        "patrón ni huella. La pantalla debe estar encendida para leer el sticker.",
                    fontSize = 13.sp,
                    color = Color(0xFF5F6B76),
                )
            }

            // ----- Contactos -----
            Section("Familiares (${state.contacts.size}/${ContactRepository.MAX_CONTACTS})") {
                if (state.contacts.isEmpty()) {
                    Text("Agrega entre 1 y ${ContactRepository.MAX_CONTACTS} familiares.")
                }
                state.contacts.forEachIndexed { index, contact ->
                    ContactRow(
                        contact = contact,
                        isFirst = index == 0,
                        isLast = index == state.contacts.lastIndex,
                        onEdit = { editing = contact },
                        onPickPhoto = { onPickPhoto(contact.id) },
                        onMoveUp = { onMove(contact.id, -1) },
                        onMoveDown = { onMove(contact.id, 1) },
                        onDelete = { confirmDelete = contact },
                    )
                }
                if (canAdd) {
                    Button(onClick = onAddFromAgenda, modifier = Modifier.fillMaxWidth()) {
                        Text("Agregar desde la agenda")
                    }
                    OutlinedButton(onClick = { addingManual = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Agregar escribiendo nombre y número")
                    }
                }
            }

            // ----- Sticker NFC -----
            Section("Sticker NFC") {
                Text(
                    "Graba el sticker una sola vez. Después, al acercar el teléfono, se abrirá " +
                        "esta app directamente.",
                    fontSize = 14.sp,
                )
                Button(
                    onClick = onStartNfcWrite,
                    enabled = state.nfcAvailable && state.nfcEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Grabar sticker NFC") }
            }
        }
    }

    // ----- Diálogos -----

    if (addingManual) {
        ContactEditDialog(
            initial = null,
            onDismiss = { addingManual = false },
            onSave = { name, phone, _ -> onAddManual(name, phone); addingManual = false },
        )
    }

    editing?.let { contact ->
        ContactEditDialog(
            initial = contact,
            onDismiss = { editing = null },
            onSave = { name, phone, removePhoto ->
                if (removePhoto) onRemovePhoto(contact.id)
                onEdit(contact.copy(name = name, phone = phone, photoUri = if (removePhoto) null else contact.photoUri))
                editing = null
            },
        )
    }

    confirmDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("¿Eliminar a ${contact.name}?") },
            confirmButton = {
                TextButton(onClick = { onDelete(contact.id); confirmDelete = null }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }

    if (state.nfcStatus != NfcWriteStatus.Idle) {
        NfcWriteDialog(state.nfcStatus, onDismiss = onDismissNfcWrite, onRetry = onStartNfcWrite)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun StatusRow(ok: Boolean, okText: String, badText: String, action: Pair<String, () -> Unit>?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ok) "✓" else "!",
            color = if (ok) Palette.Green else Palette.Danger,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            modifier = Modifier.width(28.dp),
        )
        Text(if (ok) okText else badText, modifier = Modifier.weight(1f))
        action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
    }
}

@Composable
private fun ContactRow(
    contact: FamilyContact,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onPickPhoto: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ContactPhoto(
                contact.photoUri,
                size = 64.dp,
                borderWidth = 3.dp,
                modifier = Modifier.clickable(onClick = onPickPhoto),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(contact.phone, color = Color(0xFF5F6B76))
            }
            Column {
                TextButton(onClick = onMoveUp, enabled = !isFirst) { Text("▲") }
                TextButton(onClick = onMoveDown, enabled = !isLast) { Text("▼") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onPickPhoto) { Text("Cambiar foto") }
            TextButton(onClick = onEdit) { Text("Editar") }
            TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ContactEditDialog(
    initial: FamilyContact?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, removePhoto: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var removePhoto by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && phone.count { it.isDigit() } >= 3

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo familiar" else "Editar familiar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (ej. Hija María)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { v -> phone = v.filter { it.isDigit() || it in "+ -()" } },
                    label = { Text("Número de teléfono") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                if (initial?.photoUri != null) {
                    TextButton(onClick = { removePhoto = !removePhoto }) {
                        Text(if (removePhoto) "Se quitará la foto (deshacer)" else "Quitar foto")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), phone.trim(), removePhoto) }, enabled = valid) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun NfcWriteDialog(status: NfcWriteStatus, onDismiss: () -> Unit, onRetry: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (status != NfcWriteStatus.Writing) onDismiss() },
        title = {
            Text(
                when (status) {
                    NfcWriteStatus.Done -> "¡Sticker listo!"
                    is NfcWriteStatus.Error -> "No se pudo grabar"
                    else -> "Acerca el sticker"
                }
            )
        },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status == NfcWriteStatus.Waiting || status == NfcWriteStatus.Writing) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    when (status) {
                        NfcWriteStatus.Waiting ->
                            "Apoya la parte trasera del teléfono sobre el sticker y no lo muevas."
                        NfcWriteStatus.Writing -> "Grabando… no muevas el teléfono."
                        NfcWriteStatus.Done ->
                            "Pega el sticker en un lugar visible. Al acercar el teléfono se abrirá la pantalla de llamadas."
                        is NfcWriteStatus.Error -> status.message
                        NfcWriteStatus.Idle -> ""
                    }
                )
            }
        },
        confirmButton = {
            when (status) {
                is NfcWriteStatus.Error -> TextButton(onClick = onRetry) { Text("Reintentar") }
                NfcWriteStatus.Done -> TextButton(onClick = onDismiss) { Text("Aceptar") }
                else -> {}
            }
        },
        dismissButton = {
            if (status != NfcWriteStatus.Done && status != NfcWriteStatus.Writing) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
