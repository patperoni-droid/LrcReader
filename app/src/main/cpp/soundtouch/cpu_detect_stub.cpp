// app/src/main/cpp/soundtouch/cpu_detect_stub.cpp
// Stub CPU detect for Android/ARM builds.

#include <cstdint>

// SoundTouch calls this from inside its namespace on some versions.
namespace soundtouch {
    int detectCPUextensions() { return 0; }
}

// And some builds expect a global symbol too.
int detectCPUextensions() { return 0; }