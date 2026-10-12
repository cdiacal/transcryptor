cmake_minimum_required(VERSION 3.22.1)
project(listening_notes_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Parámetros que pasa Gradle (ver app/build.gradle.kts y gradle.properties).
option(LN_CPU_VARIANTS "Compilar todas las variantes de CPU de ggml (selección en runtime)" OFF)
set(LN_ARM_ARCH "" CACHE STRING "Valor de -march para ggml en modo estático (vacío = armv8-a base)")

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)
if(NOT EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
        "Falta el submódulo whisper.cpp. Ejecuta: git submodule update --init --recursive")
endif()

# Optimización: siempre -O3 (incluso en debug, si no la transcripción es inutilizable).
set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} -O3")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")
set(CMAKE_C_FLAGS_DEBUG   "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG")

# --- Opciones de whisper.cpp / ggml ---
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)  # hilos propios de ggml, sin libomp
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)  # estamos cross-compilando

if(LN_CPU_VARIANTS)
    # ggml compila varias variantes (.so) del backend CPU (armv8.0, dotprod, fp16, i8mm, ...)
    # y carga la mejor para el dispositivo en tiempo de ejecución.
    set(BUILD_SHARED_LIBS     ON CACHE BOOL "" FORCE)
    set(GGML_BACKEND_DL       ON CACHE BOOL "" FORCE)
    set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
else()
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    if(LN_ARM_ARCH)
        # p. ej. armv8.2-a+fp16+dotprod
        set(GGML_CPU_ARM_ARCH "${LN_ARM_ARCH}" CACHE STRING "" FORCE)
    endif()
endif()

add_subdirectory(${WHISPER_DIR} whisper_build)

# --- Librería JNI ---
add_library(whisper_jni SHARED whisper_jni.cpp)
target_include_directories(whisper_jni PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include)
target_link_libraries(whisper_jni PRIVATE whisper ggml log android)
if(LN_CPU_VARIANTS)
    target_compile_definitions(whisper_jni PRIVATE LN_BACKEND_DL=1)
endif()
target_compile_options(whisper_jni PRIVATE -O3 -fvisibility=hidden -ffunction-sections -fdata-sections)
target_link_options(whisper_jni PRIVATE -Wl,--gc-sections)
