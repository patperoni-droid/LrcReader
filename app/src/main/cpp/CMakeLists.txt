cmake_minimum_required(VERSION 3.22.1)
project(soundtouch_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(soundtouch_jni SHARED
        soundtouch_bridge.cpp
)

# --- SoundTouch source tree expected here:
# app/src/main/cpp/soundtouch/
#   include/SoundTouch.h
#   source/SoundTouch/*.cpp
#   source/BPMDetect/*.cpp (optionnel)
set(SOUNDTOUCH_DIR "${CMAKE_CURRENT_SOURCE_DIR}/soundtouch")

set(ST_HEADER "${SOUNDTOUCH_DIR}/include/SoundTouch.h")
set(ST_SRC_DIR "${SOUNDTOUCH_DIR}/source/SoundTouch")
set(ST_BPM_DIR "${SOUNDTOUCH_DIR}/source/BPMDetect")

set(ST_SOURCES "")
if (EXISTS "${ST_SRC_DIR}")
    file(GLOB ST_SOURCES CONFIGURE_DEPENDS
            "${ST_SRC_DIR}/*.cpp"
            "${ST_BPM_DIR}/*.cpp"
    )

    # ✅ IMPORTANT: enlever les fichiers x86/MMX/SSE (non compilables sur ARM)
    list(FILTER ST_SOURCES EXCLUDE REGEX ".*cpu_detect_x86\\.cpp$")
    list(FILTER ST_SOURCES EXCLUDE REGEX ".*mmx_optimized\\.cpp$")
    list(FILTER ST_SOURCES EXCLUDE REGEX ".*sse_optimized\\.cpp$")
    list(FILTER ST_SOURCES EXCLUDE REGEX ".*sse2_optimized\\.cpp$")
    list(FILTER ST_SOURCES EXCLUDE REGEX ".*avx_optimized\\.cpp$")
endif()

message(STATUS "SoundTouch dir: ${SOUNDTOUCH_DIR}")
message(STATUS "SoundTouch header: ${ST_HEADER}")
message(STATUS "SoundTouch sources found: ${ST_SOURCES}")

if (EXISTS "${ST_HEADER}" AND ST_SOURCES)
    message(STATUS "SoundTouch: ENABLED (LRC_USE_SOUNDTOUCH=1)")
    target_compile_definitions(soundtouch_jni PRIVATE LRC_USE_SOUNDTOUCH=1)

    target_include_directories(soundtouch_jni PRIVATE
            "${SOUNDTOUCH_DIR}/include"
            "${ST_SRC_DIR}"
            "${ST_BPM_DIR}"
    )

    # + stub CPU detect (évite l'erreur detectCPUextensions manquant)
    target_sources(soundtouch_jni PRIVATE
            ${ST_SOURCES}
            "${SOUNDTOUCH_DIR}/cpu_detect_stub.cpp"
    )
else()
    message(WARNING "SoundTouch: DISABLED (LRC_USE_SOUNDTOUCH=0) - missing header or sources")
    target_compile_definitions(soundtouch_jni PRIVATE LRC_USE_SOUNDTOUCH=0)
endif()

find_library(log-lib log)
target_link_libraries(soundtouch_jni ${log-lib})