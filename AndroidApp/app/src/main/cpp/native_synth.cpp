#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <array>
#include <atomic>
#include <cmath>

#define LOG_TAG "EP133NATIVE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr int   MAX_VOICES   = 8;
static constexpr float A4_FREQ      = 440.0f;
static constexpr float A4_MIDI      = 69.0f;
static constexpr float TWO_PI       = 6.28318530718f;

// One polyphonic voice. Non-atomic fields are written only when active == false,
// protected by the release/acquire fence on the active flag.
struct Voice {
    std::atomic<bool> active{false};
    std::atomic<bool> releasing{false};

    int   midiNote{-1};
    float frequency{440.0f};
    float amplitude{0.0f};     // peak amplitude (velocity-scaled)
    float phase{0.0f};
    float phase2{0.0f};        // 2nd harmonic
    float phase3{0.0f};        // 3rd harmonic
    float envGain{0.0f};
    float attackRate{0.0f};    // envGain increase per sample
    float decayRate{0.0f};     // envGain decrease per sample (attack→sustain)
    float sustainLevel{0.0f};
    float releaseRate{0.0f};   // envGain decrease per sample on release
    bool  inAttack{true};
};

class NativeSynth : public oboe::AudioStreamDataCallback {
public:
    std::array<Voice, MAX_VOICES> voices{};
    oboe::AudioStream* stream{nullptr};
    int sampleRate{48000};

    bool start(int requestedSr) {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output);
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
        builder.setSharingMode(oboe::SharingMode::Exclusive);
        builder.setFormat(oboe::AudioFormat::Float);
        builder.setChannelCount(oboe::ChannelCount::Mono);
        builder.setSampleRate(requestedSr);
        builder.setDataCallback(this);

        oboe::Result result = builder.openStream(&stream);
        if (result != oboe::Result::OK) {
            LOGE("Exclusive open failed (%s), retrying shared", oboe::convertToText(result));
            builder.setSharingMode(oboe::SharingMode::Shared);
            result = builder.openStream(&stream);
        }
        if (result != oboe::Result::OK) {
            LOGE("Failed to open Oboe stream: %s", oboe::convertToText(result));
            return false;
        }

        sampleRate = stream->getSampleRate();
        LOGI("Oboe open: sr=%d burst=%d sharing=%s",
             sampleRate,
             stream->getFramesPerBurst(),
             stream->getSharingMode() == oboe::SharingMode::Exclusive ? "exclusive" : "shared");

        result = stream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Failed to start stream: %s", oboe::convertToText(result));
            stream->close();
            stream = nullptr;
            return false;
        }
        return true;
    }

    void noteOn(int midiNote, int velocity) {
        // Find a free voice (active == false). On overflow, steal a releasing voice.
        Voice* target = nullptr;
        for (auto& v : voices) {
            if (!v.active.load(std::memory_order_acquire)) { target = &v; break; }
        }
        if (!target) {
            for (auto& v : voices) {
                if (v.releasing.load(std::memory_order_relaxed)) { target = &v; break; }
            }
        }
        if (!target) return; // 8 sustained voices — drop note

        float freq   = A4_FREQ * powf(2.0f, (midiNote - A4_MIDI) / 12.0f);
        float amp    = (velocity / 127.0f) * 0.6f;
        float atk    = sampleRate * 0.01f;          // 10 ms attack
        float dec    = sampleRate * 2.0f;           // 2 s decay
        float sus    = amp * 0.5f;
        float rel    = sampleRate * 0.1f;           // 100 ms release

        target->midiNote     = midiNote;
        target->frequency    = freq;
        target->amplitude    = amp;
        target->phase        = 0.0f;
        target->phase2       = 0.0f;
        target->phase3       = 0.0f;
        target->envGain      = 0.0f;
        target->attackRate   = amp / atk;
        target->decayRate    = (amp - sus) / dec;
        target->sustainLevel = sus;
        target->releaseRate  = amp / rel;
        target->inAttack     = true;
        target->releasing.store(false, std::memory_order_relaxed);
        // Release fence: all above writes visible before active becomes true
        target->active.store(true, std::memory_order_release);
    }

    void noteOff(int midiNote) {
        for (auto& v : voices) {
            if (v.active.load(std::memory_order_relaxed)
                    && v.midiNote == midiNote
                    && !v.releasing.load(std::memory_order_relaxed)) {
                v.releasing.store(true, std::memory_order_relaxed);
                break;
            }
        }
    }

    void allNotesOff() {
        for (auto& v : voices) {
            v.releasing.store(true, std::memory_order_relaxed);
        }
    }

    void close() {
        if (stream) {
            stream->requestStop();
            stream->close();
            stream = nullptr;
        }
    }

    // Called on a real-time native thread managed by Oboe.
    // No allocations, no locks, no JNI calls in this method.
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* /*stream*/,
            void* audioData,
            int32_t numFrames) override {

        auto* out = static_cast<float*>(audioData);
        for (int i = 0; i < numFrames; ++i) out[i] = 0.0f;

        for (auto& v : voices) {
            // Acquire fence pairs with the release store in noteOn()
            if (!v.active.load(std::memory_order_acquire)) continue;

            float freq         = v.frequency;
            float phase        = v.phase;
            float phase2       = v.phase2;
            float phase3       = v.phase3;
            float envGain      = v.envGain;
            float amp          = v.amplitude;
            float attackRate   = v.attackRate;
            float decayRate    = v.decayRate;
            float sustainLevel = v.sustainLevel;
            float releaseRate  = v.releaseRate;
            bool  inAttack     = v.inAttack;
            bool  stillActive  = true;

            float freqIncr  = freq / sampleRate;
            float freqIncr2 = freqIncr * 2.0f;
            float freqIncr3 = freqIncr * 3.0f;

            for (int i = 0; i < numFrames; ++i) {
                if (v.releasing.load(std::memory_order_relaxed)) {
                    envGain -= releaseRate;
                    if (envGain <= 0.0f) {
                        envGain = 0.0f;
                        stillActive = false;
                        break;
                    }
                } else if (inAttack) {
                    envGain += attackRate;
                    if (envGain >= amp) { envGain = amp; inAttack = false; }
                } else if (envGain > sustainLevel) {
                    envGain -= decayRate;
                    if (envGain < sustainLevel) envGain = sustainLevel;
                }

                // Additive synthesis: fundamental (60%) + 2nd (25%) + 3rd (10%) harmonics
                out[i] += (sinf(phase  * TWO_PI) * 0.60f
                         + sinf(phase2 * TWO_PI) * 0.25f
                         + sinf(phase3 * TWO_PI) * 0.10f)
                         * envGain;

                phase  += freqIncr;  if (phase  >= 1.0f) phase  -= 1.0f;
                phase2 += freqIncr2; if (phase2 >= 1.0f) phase2 -= 1.0f;
                phase3 += freqIncr3; if (phase3 >= 1.0f) phase3 -= 1.0f;
            }

            v.phase    = phase;
            v.phase2   = phase2;
            v.phase3   = phase3;
            v.envGain  = envGain;
            v.inAttack = inAttack;
            if (!stillActive) v.active.store(false, std::memory_order_relaxed);
        }

        return oboe::DataCallbackResult::Continue;
    }
};

// ── JNI bridge ────────────────────────────────────────────────────────────────

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_ep133_sampletool_domain_midi_NativeSynth_nativeCreate(
        JNIEnv*, jobject, jint sampleRate) {
    auto* synth = new NativeSynth();
    if (!synth->start(static_cast<int>(sampleRate))) {
        delete synth;
        return 0L;
    }
    return reinterpret_cast<jlong>(synth);
}

JNIEXPORT void JNICALL
Java_com_ep133_sampletool_domain_midi_NativeSynth_nativeNoteOn(
        JNIEnv*, jobject, jlong ptr, jint note, jint velocity) {
    if (ptr) reinterpret_cast<NativeSynth*>(ptr)->noteOn(note, velocity);
}

JNIEXPORT void JNICALL
Java_com_ep133_sampletool_domain_midi_NativeSynth_nativeNoteOff(
        JNIEnv*, jobject, jlong ptr, jint note) {
    if (ptr) reinterpret_cast<NativeSynth*>(ptr)->noteOff(note);
}

JNIEXPORT void JNICALL
Java_com_ep133_sampletool_domain_midi_NativeSynth_nativeAllNotesOff(
        JNIEnv*, jobject, jlong ptr) {
    if (ptr) reinterpret_cast<NativeSynth*>(ptr)->allNotesOff();
}

JNIEXPORT void JNICALL
Java_com_ep133_sampletool_domain_midi_NativeSynth_nativeClose(
        JNIEnv*, jobject, jlong ptr) {
    if (ptr) {
        auto* synth = reinterpret_cast<NativeSynth*>(ptr);
        synth->close();
        delete synth;
    }
}

} // extern "C"
