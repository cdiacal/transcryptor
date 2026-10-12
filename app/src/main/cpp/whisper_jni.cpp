// Puente JNI entre Kotlin (clase WhisperNative) y whisper.cpp.
// API de whisper.cpp usada (verificada en include/whisper.h del submódulo):
//   whisper_context_default_params, whisper_init_from_file_with_params, whisper_free,
//   whisper_full_default_params, whisper_full, whisper_full_n_segments,
//   whisper_full_get_segment_text / _t0 / _t1, whisper_print_system_info.
#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cstring>
#include <string>

#include "whisper.h"
#include "ggml-backend.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

JavaVM *g_vm = nullptr;

// Estado asociado a cada contexto (el puntero viaja a Kotlin como jlong).
struct Handle {
    whisper_context *ctx = nullptr;
    std::atomic<bool> cancel{false};
};

// Datos para el callback de progreso (viven en la pila de transcribe()).
struct ProgressData {
    jobject listener = nullptr;
    jmethodID method = nullptr;
    int last = -1;
};

Handle *toHandle(jlong ptr) { return reinterpret_cast<Handle *>(ptr); }

void progressCallback(whisper_context *, whisper_state *, int progress, void *user) {
    auto *data = static_cast<ProgressData *>(user);
    if (data == nullptr || data->listener == nullptr || progress == data->last) return;
    data->last = progress;
    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
#ifdef __ANDROID__
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
#else  // jni.h del JDK de escritorio (solo para comprobar sintaxis en el host)
        if (g_vm->AttachCurrentThread(reinterpret_cast<void **>(&env), nullptr) != JNI_OK) return;
#endif
        attached = true;
    }
    env->CallVoidMethod(data->listener, data->method, static_cast<jint>(progress));
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (attached) g_vm->DetachCurrentThread();
}

// whisper/ggml consultan esto con frecuencia; devolver true aborta la inferencia.
bool abortCallback(void *user) {
    return static_cast<Handle *>(user)->cancel.load(std::memory_order_relaxed);
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

// Carga los backends dinámicos de ggml (solo en modo LN_CPU_VARIANTS). Devuelve nº de dispositivos.
JNIEXPORT jint JNICALL
Java_com_listeningnotes_app_whisper_WhisperNative_initBackends(JNIEnv *env, jobject, jstring dir) {
#ifdef LN_BACKEND_DL
    const char *path = env->GetStringUTFChars(dir, nullptr);
    ggml_backend_load_all_from_path(path);
    env->ReleaseStringUTFChars(dir, path);
    return static_cast<jint>(ggml_backend_dev_count());
#else
    (void) env; (void) dir;
    return 1;
#endif
}

JNIEXPORT jlong JNICALL
Java_com_listeningnotes_app_whisper_WhisperNative_initContext(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;  // solo CPU
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (ctx == nullptr) {
        LOGE("No se pudo cargar el modelo");
        return 0;
    }
    auto *handle = new Handle();
    handle->ctx = ctx;
    return reinterpret_cast<jlong>(handle);
}

// Devuelve 0 si ok, !=0 si error o cancelación (consultar con cancelled en Kotlin).
JNIEXPORT jint JNICALL
Java_com_listeningnotes_app_whisper_WhisperNative_transcribe(
        JNIEnv *env, jobject, jlong ptr, jfloatArray samples, jint nSamples, jstring language,
        jint threads, jobject listener) {
    Handle *handle = toHandle(ptr);
    if (handle == nullptr || handle->ctx == nullptr) return -100;

    // El array puede ser más grande que el audio real: usamos solo nSamples.
    const jsize n = std::min(nSamples, env->GetArrayLength(samples));
    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);
    const char *lang = env->GetStringUTFChars(language, nullptr);
    std::string langCopy(lang);
    env->ReleaseStringUTFChars(language, lang);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.language = langCopy.c_str();
    params.detect_language = false;
    params.translate = false;
    params.no_timestamps = false;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.suppress_nst = true;

    ProgressData progress;
    if (listener != nullptr) {
        jclass cls = env->GetObjectClass(listener);
        progress.method = env->GetMethodID(cls, "onProgress", "(I)V");
        progress.listener = env->NewGlobalRef(listener);
        params.progress_callback = progressCallback;
        params.progress_callback_user_data = &progress;
    }
    params.abort_callback = abortCallback;
    params.abort_callback_user_data = handle;

    const int rc = whisper_full(handle->ctx, params, pcm, static_cast<int>(n));

    if (progress.listener != nullptr) env->DeleteGlobalRef(progress.listener);
    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);  // solo lectura
    return rc;
}

JNIEXPORT void JNICALL
Java_com_listeningnotes_app_whisper_WhisperNative_cancel(JNIEnv *, jobject, jlong ptr) {
    if (Handle *h = toHandle(ptr)) h->cancel.store(true);
}

JNIEXPORT jboolean JNICALL
Java_com_listeningnotes_app_whisper_WhisperNative_wasCancelled(JNIEnv *, jobject, jlong ptr) {
    Handle *h = toHandle(ptr);
    return h != nullptr && h->cancel.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_listeningnotes_app_whisper_WhisperNative_getSegmentCount(JNIEnv *, jobject, jlong ptr) {
    return whisper_full_n_segments(toHandle(ptr)->ctx);
}

JNIEXPORT jstring JNICALL
Java_com_listeningnotes_app_whisper_WhisperNative_getSegmentText(JNIEnv *env, jobject, jlong ptr, jint i) {
    const char *text = whisper_full_get_segment_text(toHandle(ptr)->ctx, i);
    // NewStringUTF exige UTF-8 "modificado"; whisper puede cortar un carácter multibyte a mitad.
    // Lo pasamos como bytes y decodificamos en Kotlin para ser robustos.
    const size_t len = text != nullptr ? strlen(text) : 0;
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(len));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(len), reinterpret_cast<const jbyte *>(text));
    jclass strCls = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(strCls, "<init>", "([BLjava/lang/String;)V");
    jstring charset = env->NewStringUTF("UTF-8");
    jstring result = static_cast<jstring>(env->NewObject(strCls, ctor, bytes, charset));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(strCls);
    return result;
}

// whisper devuelve los tiempos en unidades de 10 ms; aquí ya se convierten a milisegundos.
JNIEXPORT jlong JNICALL
Java_com_listeningnotes_app_whisper_WhisperNative_getSegmentT0(JNIEnv *, jobject, jlong ptr, jint i) {
    return whisper_full_get_segment_t0(toHandle(ptr)->ctx, i) * 10;
}

JNIEXPORT jlong JNICALL
Java_com_listeningnotes_app_whisper_WhisperNative_getSegmentT1(JNIEnv *, jobject, jlong ptr, jint i) {
    return whisper_full_get_segment_t1(toHandle(ptr)->ctx, i) * 10;
}

JNIEXPORT void JNICALL
Java_com_listeningnotes_app_whisper_WhisperNative_free(JNIEnv *, jobject, jlong ptr) {
    Handle *h = toHandle(ptr);
    if (h == nullptr) return;
    if (h->ctx != nullptr) whisper_free(h->ctx);
    delete h;
}

JNIEXPORT jstring JNICALL
Java_com_listeningnotes_app_whisper_WhisperNative_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}

}  // extern "C"
