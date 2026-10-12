# Listening Notes

App Android que **transcribe audios en inglés de forma 100 % offline** (whisper.cpp en el propio móvil) y ayuda a
**estudiarlos frase a frase**: reproducir una frase en bucle, bajar la velocidad sin cambiar el tono, ocultar el texto
(modo *listening*), marcar favoritas, etc. La interfaz y los comentarios del código están en español.

## Diseño

Sistema visual «banca sobria, azul marino» (claro/oscuro/sistema): tokens y componentes base en
`ui/theme/Theme.kt` y `ui/common/Componentes.kt` (`BotonPrincipal`, `TarjetaPanel`, `CabeceraHero`, `FilaPestanas`…).
Usa siempre `colorIngreso()`, `colorGasto()` y `colorAviso()` para los colores semánticos.

## Funcionalidades

- **Entrada de audio**: compartir desde WhatsApp u otras apps (`ACTION_SEND` / `ACTION_VIEW` con `audio/*`) o botón
  «Elegir archivo» (selector del sistema, sin permisos de almacenamiento). El audio se copia al almacenamiento interno.
- **Decodificación** con `MediaExtractor` + `MediaCodec` (m4a, opus, ogg, mp3, wav…) → mono → 16 kHz float
  (remuestreo lineal propio, en streaming).
- **Transcripción** con whisper.cpp (JNI/NDK, solo `arm64-v8a`), progreso en vivo, botón cancelar, pantalla siempre
  encendida y servicio en primer plano con notificación si el audio dura más de 2 minutos.
- **Resultado**: frases con marca `[mm:ss]`, edición con pulsación larga, copiar todo, compartir/guardar como `.txt`.
- **Biblioteca** (Room): búsqueda en títulos y frases, renombrar y borrar.
- **Modo estudio**: reproductor con barra de progreso, tocar una frase la reproduce de `inicioMs` a `finMs`,
  «Repetir frase», velocidad 0.75x/1x/1.25x, «Ocultar texto» (difuminado + ojo por frase), favoritas (estrella) y
  filtro «Solo favoritas», frase activa resaltada con scroll automático.
- **Frases cortas**: las transcripciones nuevas se dividen por puntuación (ajustable en Ajustes); los tiempos se
  reparten según la longitud de cada frase.
- **Vocabulario**: pulsación larga en una frase → toca una palabra para guardarla con su traducción/nota y la frase de
  origen. El diccionario y el traductor se abren en el navegador (la app sigue sin pedir permiso de Internet para ello).
- **Shadowing**: tras cada frase se pausa 1 s / 2 s / 4 s / la duración de la frase para repetirla en voz alta y pasa a la siguiente.
- **Ajustes**: modelo (`base.en` / `small.en`, descarga bajo demanda), hilos y tema claro/oscuro/sistema.
- El modelo (`ggml-base.en.bin`, ~150 MB) **no va en el APK**: se descarga en el primer arranque a
  `filesDir/models/` con barra de progreso, reanudación, comprobación de tamaño y botón de reintentar.

## Requisitos

- **Android Studio** Ladybug (2024.2) o posterior (JDK 17 incluido).
- En *SDK Manager*: **Android SDK Platform 35**, **NDK (Side by side) 27.0.12077973** y **CMake 3.22.1**.
  (Si usas otra versión del NDK, cambia `ndkVersion` en `app/build.gradle.kts`.)
- **git** (whisper.cpp es un submódulo).
- Un dispositivo (o emulador) **arm64-v8a** con Android 8.0 (API 26) o superior. Un móvil real es muy recomendable:
  en emuladores x86 la librería nativa no se compila (solo se genera `arm64-v8a`).

## Cómo compilar

```bash
git clone <url-del-repo> listening-notes
cd listening-notes
git submodule update --init --recursive   # descarga whisper.cpp (fijado en v1.9.5)

./gradlew assembleDebug                   # APK en app/build/outputs/apk/debug/
./gradlew installDebug                    # instala en el móvil conectado por USB
./gradlew testDebugUnitTest               # pruebas unitarias
```

O abre la carpeta en Android Studio, deja que sincronice Gradle y pulsa **Run**.
Si hace falta, crea `local.properties` con `sdk.dir=/ruta/al/Android/Sdk`.

La primera compilación tarda unos minutos porque compila whisper.cpp/ggml con `-O3` (también en *debug*: sin
optimizar, la transcripción sería inutilizable).

## Cómo cambiar de modelo

- **Desde la app**: Ajustes → *Modelo de transcripción* → elige `base.en` o `small.en`. Si no está descargado, empieza
  a descargarse (con reanudación). `small.en` es más preciso, pero más lento y necesita ~1 GB de RAM libre.
- **Por defecto en el código**: cambia la constante `DEFAULT_MODEL_ID` en
  `app/src/main/java/com/listeningnotes/app/whisper/ModelSpec.kt` (p. ej. a `"small.en"`). El nombre del fichero
  (`ggml-<id>.bin`) y la URL de Hugging Face se derivan de ese id. Para añadir otro modelo, añádelo a `Models.all`
  con su tamaño aproximado (se usa para validar la descarga, con ±3 % de margen).

## Rendimiento (NEON / fp16)

Por defecto ggml se compila para `armv8-a` (NEON, compatible con **cualquier** arm64). Para ir más rápido en móviles
recientes hay dos opciones en `gradle.properties` (o con `-P` en la línea de comandos):

| Propiedad | Efecto |
|---|---|
| `whisperArmArch=armv8.2-a+fp16+dotprod` | Compila con fp16 y *dot product* fijos. Más rápido, pero **falla (SIGILL) en CPUs sin esas extensiones** (p. ej. Cortex-A53). |
| `whisperCpuVariants=true` | ggml compila varias variantes del backend de CPU y **elige la mejor en tiempo de ejecución** (detección automática de fp16/dotprod/i8mm). Es lo ideal, pero es la ruta menos probada: ver nota. |

> **Nota**: la opción `whisperCpuVariants=true` empaqueta varias `.so` extra (`libggml-cpu-*.so`) que ggml carga desde
> `nativeLibraryDir` (por eso `useLegacyPackaging = true`). Si tras activarla la app muestra «No se encontró ningún
> backend de CPU de ggml», vuelve a `false`. En Ajustes → *Ver información del motor* puedes comprobar qué
> extensiones (`NEON`, `FP16_VA`, `DOTPROD`…) usa realmente whisper.cpp.

## Estructura

```
app/src/main/
├── cpp/                  CMakeLists.txt, whisper_jni.cpp y el submódulo whisper.cpp
└── java/com/listeningnotes/app/
    ├── whisper/          WhisperNative (JNI), WhisperEngine, ModelSpec/Downloader/Manager
    ├── audio/            AudioDecoder, Resampler (mono + 16 kHz), AudioImporter, StudyPlayer (Media3)
    ├── data/             room/ (entidades, DAO, base de datos), repository/ (biblioteca y ajustes)
    ├── transcription/    TranscriptionManager (importar→decodificar→transcribir→guardar) y servicio en primer plano
    ├── export/           SegmentExporter (segmentos → texto .txt)
    ├── ui/               library/, transcribe/, study/, settings/, theme/, common/ + navegación
    └── ListeningNotesApp.kt   contenedor de dependencias manual (sin Hilt)
```

## Permisos

Solo `INTERNET` (únicamente para descargar el modelo), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` y
`POST_NOTIFICATIONS` (notificación de progreso en audios largos; si la rechazas todo sigue funcionando). No se
piden permisos de almacenamiento.

## Límites conocidos

- Audios de hasta **60 minutos** (el PCM a 16 kHz en `float` ocupa ~230 MB en RAM).
- Solo inglés (`base.en`/`small.en`, `language = "en"`).
- Whisper trabaja en ventanas de 30 s: los cortes de frase son los que decide el modelo y pueden no coincidir con
  puntos y aparte.

## Problemas típicos

| Síntoma | Causa / solución |
|---|---|
| `Falta el submódulo whisper.cpp` al sincronizar | Ejecuta `git submodule update --init --recursive`. |
| `NDK not configured` / `No version of NDK matched` | Instala la versión indicada en *SDK Manager → SDK Tools → NDK (Side by side)* o ajusta `ndkVersion`. |
| `CMake '3.22.1' was not found` | Instala *CMake 3.22.1* desde *SDK Manager → SDK Tools*. |
| No arranca en el emulador | Solo se compila `arm64-v8a`: usa un móvil real o un emulador con imagen ARM64. |
| La descarga del modelo falla o se corta | Pulsa **Reintentar**: se reanuda desde el fichero `.part`. Comprueba la conexión (Hugging Face debe ser accesible). |
| «Tamaño del modelo descargado no es correcto» | Se borra el parcial automáticamente; reintenta. Si sigues con `small.en`, comprueba que hay espacio libre (~0,5 GB). |
| «No se pudo cargar el modelo (memoria insuficiente)» | Usa `base.en` o cierra otras apps. |
| La transcripción es muy lenta | Sube los hilos en Ajustes (máx. núcleos), usa `base.en`, y prueba `whisperArmArch`/`whisperCpuVariants` (ver *Rendimiento*). El móvil puede reducir la frecuencia por calor. |
| «Este dispositivo no puede decodificar el formato…» | El códec del audio no está disponible en tu Android; conviértelo a m4a/mp3/wav. |
| La notificación no aparece | Android 13+: concede el permiso de notificaciones en los ajustes de la app. |
