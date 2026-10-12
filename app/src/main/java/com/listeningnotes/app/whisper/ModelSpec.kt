package com.listeningnotes.app.whisper

import java.io.File

/**
 * Modelo de whisper.cpp descargable.
 * Para cambiar el modelo por defecto basta con modificar [DEFAULT_MODEL_ID]
 * (p. ej. a "small.en"); el nombre del fichero y la URL se derivan del id.
 */
const val DEFAULT_MODEL_ID = "base.en"

private const val MODELS_BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

data class ModelSpec(
    val id: String,
    val label: String,
    /** Tamaño aproximado esperado, para validar la descarga y mostrarlo al usuario. */
    val approxBytes: Long,
) {
    val fileName: String get() = "ggml-$id.bin"
    val url: String get() = "$MODELS_BASE_URL/$fileName"

    fun file(modelsDir: File): File = File(modelsDir, fileName)

    /** Un fichero se considera completo si su tamaño está a ±3 % del esperado. */
    fun hasPlausibleSize(bytes: Long): Boolean =
        bytes in (approxBytes * 97 / 100)..(approxBytes * 103 / 100)
}

object Models {
    val BASE_EN = ModelSpec("base.en", "base.en (rápido, ~150 MB)", 147_964_211L)
    val SMALL_EN = ModelSpec("small.en", "small.en (más preciso, ~490 MB)", 487_614_201L)

    val all: List<ModelSpec> = listOf(BASE_EN, SMALL_EN)

    fun byId(id: String): ModelSpec = all.firstOrNull { it.id == id } ?: byId(DEFAULT_MODEL_ID)
}
