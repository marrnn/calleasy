package com.calleasy.app

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.calleasy.app.data.ContactRepository
import com.calleasy.app.data.FamilyContact
import com.calleasy.app.ui.MainScreen
import com.calleasy.app.ui.MainScreenTheme
import java.util.Locale

/**
 * Pantalla del adulto mayor. Se abre al acercar el sticker NFC (NDEF_DISCOVERED con
 * MIME application/vnd.abuelito.llamada), incluso sobre la pantalla de bloqueo.
 */
class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var repository: ContactRepository
    private val contacts = mutableStateOf<List<FamilyContact>>(emptyList())

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speakWhenReady = true
    private var lastCallAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hideSystemBars()

        repository = ContactRepository(this)
        contacts.value = repository.load()
        tts = TextToSpeech(this, this)

        setContent {
            MainScreenTheme {
                MainScreen(
                    contacts = contacts.value,
                    onCall = ::placeCall,
                    onOpenAdmin = ::openAdmin,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Cada vez que se vuelve a acercar el sticker repetimos la instrucción.
        speakInstruction()
    }

    override fun onResume() {
        super.onResume()
        contacts.value = repository.load()
        hideSystemBars()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    // ---------- Pantalla de bloqueo / pantalla completa ----------

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** Quita el bloqueo (sin PIN) y luego ejecuta [then]. Si no se puede, ejecuta igualmente. */
    private fun dismissKeyguardThen(then: () -> Unit) {
        val keyguard = getSystemService<KeyguardManager>()
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            then(); return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = then()
            override fun onDismissError() = then()
            override fun onDismissCancelled() = then()
        })
    }

    // ---------- Acciones ----------

    private fun placeCall(contact: FamilyContact) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCallAt < 3_000) return // evita llamadas dobles por toques repetidos
        lastCallAt = now

        tts?.stop()
        val uri = Uri.fromParts("tel", contact.phone, null)
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val telecom = getSystemService<TelecomManager>()
        val secureLock = getSystemService<KeyguardManager>()?.isKeyguardSecure == true

        if (hasPermission && telecom != null) {
            // placeCall entrega la llamada directo al sistema telefónico (equivale a
            // ACTION_CALL) sin pasar por el selector de apps: apps como Zoom o WhatsApp
            // registran ACTION_CALL y harían aparecer "Completar la acción utilizando".
            val call = {
                try {
                    telecom.placeCall(uri, Bundle())
                } catch (e: SecurityException) {
                    startDialer(uri, telecom)
                }
            }
            // Con PIN/patrón no pedimos desbloquear: la pantalla de llamada se muestra
            // sobre el bloqueo. Sin PIN, se quita el bloqueo para que al colgar quede normal.
            if (secureLock) call() else dismissKeyguardThen(call)
        } else {
            // Respaldo si el administrador aún no concedió el permiso de llamada.
            dismissKeyguardThen { startDialer(uri, telecom) }
        }
    }

    /** Abre el marcador predeterminado (sin selector) con el número escrito. */
    private fun startDialer(uri: Uri, telecom: TelecomManager?) {
        val intent = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        telecom?.defaultDialerPackage?.let { intent.setPackage(it) }
        runCatching { startActivity(intent) }
            .onFailure { runCatching { startActivity(intent.setPackage(null)) } }
    }

    private fun openAdmin() {
        tts?.stop()
        dismissKeyguardThen {
            startActivity(Intent(this, AdminActivity::class.java))
        }
    }

    // ---------- Voz ----------

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) return

        val candidates = listOfNotNull(
            Locale.getDefault().takeIf { it.language == "es" },
            Locale.forLanguageTag("es-419"),
            Locale.forLanguageTag("es-US"),
            Locale.forLanguageTag("es-ES"),
            Locale.forLanguageTag("es"),
        )
        ttsReady = candidates.any { engine.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE }
        engine.setSpeechRate(0.8f) // claro y pausado
        engine.setPitch(1.0f)

        if (ttsReady && speakWhenReady) speakInstruction()
    }

    private fun speakInstruction() {
        if (!ttsReady) {
            speakWhenReady = true
            return
        }
        speakWhenReady = false
        val text = if (contacts.value.isEmpty()) {
            "Todavía no hay familiares configurados. Pide ayuda a un familiar."
        } else {
            "¿A quién deseas llamar? Toca su foto."
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "instruccion")
    }
}
