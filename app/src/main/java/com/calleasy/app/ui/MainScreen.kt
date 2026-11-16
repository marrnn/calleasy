package com.calleasy.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.calleasy.app.R
import com.calleasy.app.data.FamilyContact
import kotlinx.coroutines.withTimeoutOrNull

private const val ADMIN_HOLD_MS = 3_000L

@Composable
fun MainScreen(
    contacts: List<FamilyContact>,
    onCall: (FamilyContact) -> Unit,
    onOpenAdmin: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .safeDrawingPadding()
            .padding(12.dp),
    ) {
        Header(onLongHold = onOpenAdmin)
        Spacer(Modifier.height(12.dp))
        if (contacts.isEmpty()) {
            EmptyState(onOpenAdmin)
        } else {
            ContactGrid(contacts, onCall)
        }
    }
}

/** Cabecera. Mantenerla pulsada 3 segundos abre el panel de administración. */
@Composable
private fun Header(onLongHold: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val callback by rememberUpdatedState(onLongHold)
    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val released = withTimeoutOrNull(ADMIN_HOLD_MS) { waitForUpOrCancellation() }
                    if (released == null) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        callback()
                    }
                }
            }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "¿A QUIÉN DESEAS LLAMAR?",
            color = Palette.OnDark,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Distribución adaptable:
 * 1 → pantalla completa · 2 → dos filas · 3 → uno arriba y dos abajo · 4 → cuadrícula 2×2.
 * Con más contactos (versiones futuras) sigue en filas de dos.
 */
internal fun layoutRows(count: Int): List<List<Int>> = when {
    count <= 0 -> emptyList()
    count <= 2 -> (0 until count).map { listOf(it) }
    count == 3 -> listOf(listOf(0), listOf(1, 2))
    else -> (0 until count).chunked(2)
}

@Composable
private fun ColumnScope.ContactGrid(contacts: List<FamilyContact>, onCall: (FamilyContact) -> Unit) {
    val rows = layoutRows(contacts.size)
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEach { row ->
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { index ->
                    ContactCard(
                        contact = contacts[index],
                        wide = row.size == 1,
                        onClick = { onCall(contacts[index]) },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactCard(
    contact: FamilyContact,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val name = contact.name.uppercase()

    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        interactionSource = interaction,
        shape = RoundedCornerShape(28.dp),
        color = Palette.Card,
        modifier = modifier.semantics {
            contentDescription = "Llamar a $name"
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            // Foto + nombre
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val nameHeight: Dp = if (wide) 72.dp else 64.dp
                val photoSize = min(maxWidth * 0.9f, maxHeight - nameHeight - 8.dp)
                    .coerceAtLeast(48.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ContactPhoto(contact.photoUri, size = photoSize, borderWidth = if (wide) 8.dp else 5.dp)
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        text = name,
                        maxLines = 2,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 18.sp,
                            maxFontSize = if (wide) 48.sp else 34.sp,
                        ),
                        style = TextStyle(
                            color = Palette.CardText,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.fillMaxWidth().height(nameHeight),
                    )
                }
            }
            // Barra verde "LLAMAR"
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(if (wide) 88.dp else 68.dp)
                    .background(if (pressed) Palette.GreenPressed else Palette.Green),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_phone),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Palette.OnDark),
                    modifier = Modifier.size(if (wide) 44.dp else 32.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "LLAMAR",
                    color = Palette.OnDark,
                    fontSize = if (wide) 36.sp else 26.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

/** Solo se ve antes de la primera configuración (no hay nada que el usuario final pueda romper). */
@Composable
private fun ColumnScope.EmptyState(onOpenAdmin: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "TODAVÍA NO HAY FAMILIARES",
            color = Palette.OnDark,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onOpenAdmin,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Green, contentColor = Palette.OnDark),
            modifier = Modifier.height(72.dp),
        ) {
            Text("CONFIGURAR", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}
