# Llamar Familia (CallEasy)

App Android (Kotlin + Jetpack Compose) para adultos mayores: se acerca el teléfono a un sticker NFC,
la app se abre sola (incluso sobre la pantalla de bloqueo) y muestra de 1 a 4 fotos grandes de
familiares. Un toque sobre la foto inicia la llamada.

## Compilar

```
./gradlew assembleDebug        # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # instala en el teléfono conectado por USB
```

Requisitos: JDK 17+, Android SDK 36. minSdk 26 (Android 8.0).

## Puesta en marcha (lo hace el familiar)

1. Instalar la app y abrirla. Con la lista vacía aparece el botón **CONFIGURAR**.
2. Conceder el permiso de **llamadas** (necesario para llamar sin pasar por el marcador).
3. Agregar familiares desde la agenda o escribiendo nombre y número; tocar la foto para cambiarla
   por una de la galería. Ordenar con ▲ ▼.
4. **Grabar sticker NFC**: pulsar el botón y apoyar el teléfono sobre el sticker (NTAG213/215/216).
5. En los ajustes del teléfono: quitar PIN/patrón/huella (bloqueo "Deslizar" o "Ninguno") y
   activar NFC.

Para volver a la configuración: **mantener pulsado el título "¿A QUIÉN DESEAS LLAMAR?" 3 segundos**.

## Cómo funciona

| Requisito | Implementación |
|---|---|
| Abrir solo con el sticker | `NDEF_DISCOVERED` con MIME `application/vnd.abuelito.llamada` + Android Application Record (`MainActivity`, `AndroidManifest.xml`) |
| Grabador NFC integrado | `enableForegroundDispatch` + `Ndef` / `NdefFormatable` (`AdminActivity`, `nfc/NfcTagWriter.kt`) |
| Sobre la pantalla de bloqueo | `setShowWhenLocked(true)`, `setTurnScreenOn(true)`; al llamar se pide `requestDismissKeyguard` |
| Llamada directa | `ACTION_CALL` con `CALL_PHONE` (si falta el permiso, respaldo a `ACTION_DIAL`) |
| Contactos y fotos | Agenda vía `ContactsContract` (la foto se copia al almacenamiento interno); galería vía `OpenDocument` + `takePersistableUriPermission`; carga con Coil 3 |
| Voz | `TextToSpeech` en español, velocidad 0.8, al abrir y en cada nueva lectura del sticker |
| Persistencia | JSON en `SharedPreferences` (`data/ContactRepository.kt`) |
| Distribución | 1 = pantalla completa · 2 = dos filas · 3 = uno arriba y dos abajo · 4 = 2×2 (`layoutRows` en `ui/MainScreen.kt`) |

Para admitir más de 4 contactos basta con subir `ContactRepository.MAX_CONTACTS`; la cuadrícula
continúa en filas de dos.

## Limitaciones conocidas

- Android solo lee NFC con la pantalla **encendida**: el usuario debe pulsar el botón de encendido
  antes de acercar el teléfono al sticker.
- Con PIN, patrón o biometría la app aparece sobre el bloqueo, pero la llamada puede pedir desbloquear.
- Algunos fabricantes no leen NFC sobre la pantalla de bloqueo ni siquiera sin PIN; conviene probarlo
  en el teléfono real.
