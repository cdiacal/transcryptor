# Métodos nativos (JNI) y callbacks invocados desde C++ por nombre.
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.listeningnotes.app.whisper.WhisperNative { *; }
-keep interface com.listeningnotes.app.whisper.NativeProgressListener { *; }
