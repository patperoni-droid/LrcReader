#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>

#if !defined(LRC_USE_SOUNDTOUCH)
#define LRC_USE_SOUNDTOUCH 0
#endif

#if LRC_USE_SOUNDTOUCH
#include <SoundTouch.h>
#endif

namespace {
    constexpr const char* kTag = "SOUNDTOUCH_JNI";

    struct Processor {
        int sampleRate = 0;
        int channels = 0;
        bool initialized = false;

#if LRC_USE_SOUNDTOUCH
        soundtouch::SoundTouch st;
#endif
    };

    static Processor* fromHandle(jlong handle) {
        return reinterpret_cast<Processor*>(handle);
    }

    static jbyteArray makeByteArray(JNIEnv* env, const std::vector<int16_t>& samples) {
        const jsize outBytes = static_cast<jsize>(samples.size() * sizeof(int16_t));
        jbyteArray out = env->NewByteArray(outBytes);
        if (out != nullptr && outBytes > 0) {
            env->SetByteArrayRegion(
                    out,
                    0,
                    outBytes,
                    reinterpret_cast<const jbyte*>(samples.data())
            );
        }
        return out;
    }

    static jbyteArray cloneInput(JNIEnv* env, jbyteArray input) {
        if (input == nullptr) return env->NewByteArray(0);

        const jsize n = env->GetArrayLength(input);
        if (n <= 0) return env->NewByteArray(0);

        jbyteArray out = env->NewByteArray(n);
        if (out == nullptr) return env->NewByteArray(0);

        std::vector<jbyte> tmp(static_cast<size_t>(n));
        env->GetByteArrayRegion(input, 0, n, tmp.data());
        env->SetByteArrayRegion(out, 0, n, tmp.data());
        return out;
    }

#if LRC_USE_SOUNDTOUCH
    static inline int16_t sampleToPcm16(soundtouch::SAMPLETYPE v) {
#ifdef SOUNDTOUCH_INTEGER_SAMPLES
        if (v > 32767) return 32767;
        if (v < -32768) return -32768;
        return static_cast<int16_t>(v);
#else
        float scaled = static_cast<float>(v) * 32767.0f;
        if (scaled > 32767.0f) scaled = 32767.0f;
        if (scaled < -32768.0f) scaled = -32768.0f;
        return static_cast<int16_t>(scaled);
#endif
    }

    static inline soundtouch::SAMPLETYPE pcm16ToSample(int16_t v) {
#ifdef SOUNDTOUCH_INTEGER_SAMPLES
        return static_cast<soundtouch::SAMPLETYPE>(v);
#else
        return static_cast<soundtouch::SAMPLETYPE>(static_cast<float>(v) / 32768.0f);
#endif
    }

    static std::vector<int16_t> drainOutputLimited(Processor* p, uint32_t maxFrames) {
        std::vector<int16_t> out;
        if (p == nullptr || p->channels <= 0) return out;

        soundtouch::FIFOSamplePipe& pipe = static_cast<soundtouch::FIFOSamplePipe&>(p->st);

        uint32_t drainedFramesTotal = 0;

        for (;;) {
            const uint32_t availableFrames = p->st.numSamples();
            if (availableFrames == 0) break;

            if (maxFrames > 0 && drainedFramesTotal >= maxFrames) break;

            const uint32_t framesToTake =
                    (maxFrames > 0)
                    ? std::min(availableFrames, maxFrames - drainedFramesTotal)
                    : availableFrames;

            const soundtouch::SAMPLETYPE* begin = pipe.ptrBegin();
            if (begin == nullptr) break;

            const size_t samplesToTake =
                    static_cast<size_t>(framesToTake) * static_cast<size_t>(p->channels);

            const size_t oldSize = out.size();
            out.resize(oldSize + samplesToTake);

            for (size_t i = 0; i < samplesToTake; ++i) {
                out[oldSize + i] = sampleToPcm16(begin[i]);
            }

            pipe.receiveSamples(framesToTake);
            drainedFramesTotal += framesToTake;
        }

        return out;
    }
#endif

} // namespace

// Kotlin: private external fun nativeIsAvailable(): Boolean
extern "C" JNIEXPORT jboolean JNICALL
Java_com_patrick_lrcreader_core_audio_SoundTouchBridge_nativeIsAvailable(
        JNIEnv*,
        jobject
) {
// En STUB, HQ doit être indisponible.
#if !LRC_USE_SOUNDTOUCH
    return JNI_FALSE;
#else
    return JNI_TRUE;
#endif
}

// Kotlin: private external fun nativeGetBuildFlavor(): String
extern "C" JNIEXPORT jstring JNICALL
Java_com_patrick_lrcreader_core_audio_SoundTouchBridge_nativeGetBuildFlavor(
        JNIEnv* env,
        jobject
) {
#if LRC_USE_SOUNDTOUCH
    return env->NewStringUTF("REAL");
#else
    return env->NewStringUTF("STUB");
#endif
}

// Kotlin: private external fun nativeSelfTest(): Boolean
extern "C" JNIEXPORT jboolean JNICALL
Java_com_patrick_lrcreader_core_audio_SoundTouchBridge_nativeSelfTest(
        JNIEnv*,
        jobject
) {
#if !LRC_USE_SOUNDTOUCH
    __android_log_print(ANDROID_LOG_WARN, kTag, "nativeSelfTest=false (STUB build)");
    return JNI_FALSE;
#else
    Processor p;
    p.sampleRate = 44100;
    p.channels = 1;
    p.initialized = true;
    p.st.clear();
    p.st.setSampleRate(static_cast<uint32_t>(p.sampleRate));
    p.st.setChannels(static_cast<uint32_t>(p.channels));
    p.st.setTempo(1.20f);
    p.st.setPitchSemiTones(6.0f);

    const int frames = p.sampleRate / 2; // 500 ms
    std::vector<int16_t> in(static_cast<size_t>(frames) * static_cast<size_t>(p.channels));
    constexpr float kPi = 3.14159265358979323846f;
    constexpr float kTwoPi = 2.0f * kPi;
    constexpr float kFreqHz = 440.0f;
    constexpr float kAmp = 0.60f * 32767.0f;
    for (int i = 0; i < frames; ++i) {
        const float s = std::sin(kTwoPi * kFreqHz * (static_cast<float>(i) / static_cast<float>(p.sampleRate)));
        in[static_cast<size_t>(i)] = static_cast<int16_t>(std::max(-32768.0f, std::min(32767.0f, s * kAmp)));
    }

    std::vector<soundtouch::SAMPLETYPE> stIn(in.size());
    for (size_t i = 0; i < in.size(); ++i) {
        stIn[i] = pcm16ToSample(in[i]);
    }

    p.st.putSamples(stIn.data(), static_cast<uint32_t>(frames));
    p.st.flush();
    std::vector<int16_t> out = drainOutputLimited(&p, 0);

    if (out.empty()) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "nativeSelfTest=false (out empty)");
        return JNI_FALSE;
    }

    const size_t n = std::min(in.size(), out.size());
    double mad = 0.0;
    for (size_t i = 0; i < n; ++i) {
        mad += std::abs(static_cast<int>(out[i]) - static_cast<int>(in[i]));
    }
    const double madNorm = (n > 0) ? ((mad / static_cast<double>(n)) / 32768.0) : 0.0;
    const double lenDeltaRatio = (in.empty())
                                 ? 0.0
                                 : (std::abs(static_cast<double>(out.size()) - static_cast<double>(in.size()))
                                    / static_cast<double>(in.size()));

    const bool transformed = (lenDeltaRatio > 0.02) || (madNorm > 0.005);

    __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "nativeSelfTest in=%zu out=%zu lenDelta=%.5f madNorm=%.5f transformed=%d",
            in.size(),
            out.size(),
            lenDeltaRatio,
            madNorm,
            transformed ? 1 : 0
    );

    return transformed ? JNI_TRUE : JNI_FALSE;
#endif
}

// Kotlin: private external fun nativeCreate(): Long
extern "C" JNIEXPORT jlong JNICALL
Java_com_patrick_lrcreader_core_audio_SoundTouchBridge_nativeCreate(
        JNIEnv*,
        jobject
) {
    auto* p = new Processor();
    return reinterpret_cast<jlong>(p);
}

// Kotlin: private external fun nativeInit(handle: Long, sampleRate: Int, channels: Int): Boolean
extern "C" JNIEXPORT jboolean JNICALL
Java_com_patrick_lrcreader_core_audio_SoundTouchBridge_nativeInit(
        JNIEnv*,
        jobject,
        jlong handle,
        jint sampleRate,
        jint channels
) {
    auto* p = fromHandle(handle);
    if (!p) return JNI_FALSE;
    if (sampleRate <= 0 || channels <= 0) return JNI_FALSE;

    p->sampleRate = static_cast<int>(sampleRate);
    p->channels = static_cast<int>(channels);

#if !LRC_USE_SOUNDTOUCH
    __android_log_print(ANDROID_LOG_WARN, kTag,
                        "STUB MODE (LRC_USE_SOUNDTOUCH=0) => no pitch/tempo");
#else
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "REAL SoundTouch MODE (LRC_USE_SOUNDTOUCH=1)");
#endif

#if !LRC_USE_SOUNDTOUCH
    // STUB : on valide l'init pour tester le pipeline complet
    p->initialized = true;
    __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "STUB nativeInit OK sr=%d ch=%d bytesPerSample=2 (SoundTouch disabled)",
            sampleRate,
            channels
    );
    return JNI_TRUE;
#else
    p->st.clear();
    p->st.setSampleRate(static_cast<uint32_t>(sampleRate));
    p->st.setChannels(static_cast<uint32_t>(channels));
    p->st.setTempo(1.0f);
    p->st.setPitchSemiTones(0.0f);
    p->initialized = true;

    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "SoundTouch nativeInit OK sr=%d ch=%d bytesPerSample=2",
        sampleRate,
        channels
    );
    return JNI_TRUE;
#endif
}

// Kotlin: private external fun nativeSetTempo(handle: Long, tempo: Float): Boolean
extern "C" JNIEXPORT jboolean JNICALL
Java_com_patrick_lrcreader_core_audio_SoundTouchBridge_nativeSetTempo(
        JNIEnv*,
        jobject,
        jlong handle,
        jfloat tempo
) {
    auto* p = fromHandle(handle);
    if (!p || !p->initialized) return JNI_FALSE;

#if !LRC_USE_SOUNDTOUCH
    (void)tempo;
    return JNI_TRUE;
#else
    p->st.setTempo(tempo);
    __android_log_print(ANDROID_LOG_INFO, kTag, "nativeSetTempo tempo=%f", tempo);
    return JNI_TRUE;
#endif
}

// Kotlin: private external fun nativeSetPitchSemi(handle: Long, semi: Float): Boolean
extern "C" JNIEXPORT jboolean JNICALL
Java_com_patrick_lrcreader_core_audio_SoundTouchBridge_nativeSetPitchSemi(
        JNIEnv*,
        jobject,
        jlong handle,
        jfloat semi
) {
    auto* p = fromHandle(handle);
    if (!p || !p->initialized) return JNI_FALSE;

#if !LRC_USE_SOUNDTOUCH
    (void)semi;
    return JNI_TRUE;
#else
    p->st.setPitchSemiTones(semi);
    __android_log_print(ANDROID_LOG_INFO, kTag, "nativeSetPitchSemi semi=%f", semi);
    return JNI_TRUE;
#endif
}

// Kotlin: private external fun nativeProcess(handle: Long, input: ByteArray): ByteArray?
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_patrick_lrcreader_core_audio_SoundTouchBridge_nativeProcess(
        JNIEnv* env,
        jobject,
        jlong handle,
        jbyteArray input
) {
    auto* p = fromHandle(handle);
    if (!p || !p->initialized || input == nullptr) return env->NewByteArray(0);

#if !LRC_USE_SOUNDTOUCH
    // STUB : passthrough
    return cloneInput(env, input);
#else
    const jsize inputBytes = env->GetArrayLength(input);
    if (inputBytes <= 0 || (inputBytes % 2) != 0) {
        return env->NewByteArray(0);
    }

    std::vector<int16_t> pcm(static_cast<size_t>(inputBytes) / 2u);
    env->GetByteArrayRegion(input, 0, inputBytes, reinterpret_cast<jbyte*>(pcm.data()));

    if (p->channels <= 0) {
        return env->NewByteArray(0);
    }

    const uint32_t frames =
        static_cast<uint32_t>(pcm.size() / static_cast<size_t>(p->channels));

    if (frames > 0) {
        std::vector<soundtouch::SAMPLETYPE> stIn(pcm.size());
        for (size_t i = 0; i < pcm.size(); ++i) {
            stIn[i] = pcm16ToSample(pcm[i]);
        }
        p->st.putSamples(stIn.data(), frames);
        __android_log_print(
                ANDROID_LOG_INFO,
            kTag,
            "nativeProcess inFrames=%u inSamples=%zu",
            frames,
            pcm.size()
        );
    }

    std::vector<int16_t> out = drainOutputLimited(p, 0); // 0 = drain tout
    return makeByteArray(env, out);
#endif
}

// Kotlin: private external fun nativeFlush(handle: Long): ByteArray?
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_patrick_lrcreader_core_audio_SoundTouchBridge_nativeFlush(
        JNIEnv* env,
        jobject,
        jlong handle
) {
    auto* p = fromHandle(handle);
    if (!p || !p->initialized) return env->NewByteArray(0);

#if !LRC_USE_SOUNDTOUCH
    return env->NewByteArray(0);
#else
    p->st.flush();
    std::vector<int16_t> out = drainOutputLimited(p, 0);
    return makeByteArray(env, out);
#endif
}

// Kotlin: private external fun nativeReset(handle: Long)
extern "C" JNIEXPORT void JNICALL
Java_com_patrick_lrcreader_core_audio_SoundTouchBridge_nativeReset(
        JNIEnv*,
        jobject,
        jlong handle
) {
    auto* p = fromHandle(handle);
    if (!p) return;

#if LRC_USE_SOUNDTOUCH
    p->st.clear();
#endif
    p->initialized = false;
}

// Kotlin: private external fun nativeRelease(handle: Long)
extern "C" JNIEXPORT void JNICALL
Java_com_patrick_lrcreader_core_audio_SoundTouchBridge_nativeRelease(
        JNIEnv*,
        jobject,
        jlong handle
) {
    auto* p = fromHandle(handle);
    if (!p) return;
    delete p;
}
