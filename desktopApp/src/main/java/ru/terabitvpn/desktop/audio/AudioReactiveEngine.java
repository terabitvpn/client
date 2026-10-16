package ru.terabitvpn.desktop.audio;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Low-latency system-output analyser. On Windows it uses WASAPI loopback, so
 * Spotify/browser/player audio is captured without relying on "Stereo Mix".
 * Rendering only reads the immutable snapshot and can never block on audio IO.
 */
public final class AudioReactiveEngine {
    private static final AudioReactiveEngine INSTANCE = new AudioReactiveEngine();
    private static final int FFT_SIZE = 2048;
    private static final int HOP_SIZE = 1024;
    private static final long NO_PACKET_DECAY_NS = 45_000_000L;
    private static final long TEMPO_RESET_SILENCE_NS = 3_500_000_000L;
    private static final long ENDPOINT_CHECK_NS = 2_000_000_000L;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.SILENT);
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean resetRequested = new AtomicBoolean();
    private final SampleSink sampleSink = this::acceptSamples;
    private final Runnable discontinuityHandler = () -> resetRequested.set(true);
    private volatile boolean captureAvailable;
    private volatile String captureStatus = "starting";
    private volatile Thread captureThread;
    private final float[] window = new float[FFT_SIZE];
    private final float[] hann = new float[FFT_SIZE];
    private final float[] fftReal = new float[FFT_SIZE];
    private final float[] fftImaginary = new float[FFT_SIZE];
    private final float[] previousSpectrum = new float[FFT_SIZE / 2];
    private final float[] onsetHistory = new float[512];
    private final float[] tempoScores = new float[258];
    private int windowFill;
    private int onsetCursor;
    private int onsetCount;
    private long lastAnalysisNs;
    private long lastSoundNs;
    private long analysedSamples;
    private long lastBpmUpdateSample;
    private float bass, melody, vocal, treble, energy, beat, aggression, calm, activityEnvelope;
    private float bpm, bpmConfidence;
    private float noiseFloor = 0.0025f;
    private float fluxAverage = 0.02f;
    private float previousBassRaw;
    private long lastOnsetSample = Long.MIN_VALUE / 4L;
    private double beatPhase;
    private boolean tempoAnchored;
    private boolean tempoResetForSilence;

    private AudioReactiveEngine() {
        for (int i = 0; i < FFT_SIZE; i++) {
            hann[i] = (float) (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (FFT_SIZE - 1)));
        }
    }

    public static Snapshot get() {
        INSTANCE.start();
        return INSTANCE.snapshot.get();
    }

    /** Pure lock-free read for the render thread after lifecycle startup. */
    public static Snapshot current() {
        return INSTANCE.snapshot.get();
    }

    public static void startEngine() {
        INSTANCE.start();
    }

    public static void shutdownEngine() {
        INSTANCE.stop();
    }

    /** Schedules a tempo/history reset without touching capture from another thread. */
    public static void notifyTrackChanged() {
        INSTANCE.resetRequested.set(true);
        INSTANCE.snapshot.set(Snapshot.SILENT);
    }

    public static boolean isCaptureAvailable() {
        INSTANCE.start();
        return INSTANCE.captureAvailable;
    }

    public static String getCaptureStatus() {
        INSTANCE.start();
        return INSTANCE.captureStatus;
    }

    private void start() {
        if (started.get() || !started.compareAndSet(false, true)) return;
        running.set(true);
        Thread thread = new Thread(this::captureForever, "Terabit Audio Reactor");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY);
        captureThread = thread;
        thread.start();
    }

    private void stop() {
        running.set(false);
        Thread thread = captureThread;
        if (thread != null) {
            thread.interrupt();
            if (thread != Thread.currentThread()) {
                try {
                    thread.join(1_000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        captureThread = null;
        captureAvailable = false;
        captureStatus = "stopped";
        started.set(false);
        snapshot.set(Snapshot.SILENT);
    }

    private void captureForever() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            captureStatus = "unsupported operating system";
            captureAvailable = false;
            return;
        }
        long retryMs = 1000L;
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try (WasapiLoopback capture = new WasapiLoopback()) {
                capture.open();
                resetAnalysisHistory();
                captureAvailable = true;
                captureStatus = "WASAPI loopback";
                retryMs = 1000L;
                long lastPacketNs = System.nanoTime();
                long lastEndpointCheckNs = lastPacketNs;
                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    int frames = capture.read(sampleSink, discontinuityHandler);
                    long now = System.nanoTime();
                    if (frames > 0) {
                        lastPacketNs = now;
                    } else {
                        if (now - lastPacketNs >= NO_PACKET_DECAY_NS) decayWithoutPackets(now);
                        Thread.sleep(2L);
                    }

                    if (now - lastEndpointCheckNs >= ENDPOINT_CHECK_NS) {
                        lastEndpointCheckNs = now;
                        if (!capture.isDefaultEndpointCurrent()) {
                            captureAvailable = false;
                            captureStatus = "switching output device";
                            break;
                        }
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Throwable failure) {
                captureAvailable = false;
                captureStatus = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
                waitWithDecay(retryMs);
                retryMs = Math.min(15_000L, retryMs * 2L);
            }
        }
        captureAvailable = false;
    }

    private void acceptSamples(float[] samples, int count, int sampleRate) {
        if (resetRequested.compareAndSet(true, false)) resetAnalysisHistory();
        for (int i = 0; i < count; i++) {
            window[windowFill++] = samples[i];
            if (windowFill != FFT_SIZE) continue;
            analyse(sampleRate);
            System.arraycopy(window, HOP_SIZE, window, 0, FFT_SIZE - HOP_SIZE);
            windowFill = FFT_SIZE - HOP_SIZE;
        }
    }

    private void analyse(int sampleRate) {
        analysedSamples += HOP_SIZE;
        double sumSq = 0.0;
        for (int i = 0; i < FFT_SIZE; i++) {
            float sample = window[i];
            sumSq += sample * sample;
            fftReal[i] = sample * hann[i];
            fftImaginary[i] = 0.0f;
        }
        fft(fftReal, fftImaginary);

        float bassRaw = 0f, lowMidRaw = 0f, vocalRaw = 0f, trebleRaw = 0f;
        int bassN = 0, lowMidN = 0, vocalN = 0, trebleN = 0;
        float flux = 0f;
        double tonalLogSum = 0.0;
        double tonalLinearSum = 0.0;
        int tonalBins = 0;
        for (int i = 1; i < FFT_SIZE / 2; i++) {
            float hz = i * sampleRate / (float) FFT_SIZE;
            float magnitude = (float) Math.hypot(fftReal[i], fftImaginary[i]) / FFT_SIZE;
            float logMagnitude = (float) Math.log1p(magnitude * 90.0f);
            float positive = Math.max(0f, logMagnitude - previousSpectrum[i]);
            if (hz >= 45f && hz <= 9_000f) flux += positive;
            previousSpectrum[i] = logMagnitude;
            if (hz >= 35f && hz < 180f) { bassRaw += logMagnitude; bassN++; }
            else if (hz < 650f) { lowMidRaw += logMagnitude; lowMidN++; }
            else if (hz < 3_400f) { vocalRaw += logMagnitude; vocalN++; }
            else if (hz < 10_000f) { trebleRaw += logMagnitude; trebleN++; }
            if (hz >= 180f && hz <= 6_500f) {
                tonalLogSum += Math.log(Math.max(1.0e-9f, magnitude));
                tonalLinearSum += magnitude;
                tonalBins++;
            }
        }

        float rms = (float) Math.sqrt(sumSq / FFT_SIZE);
        noiseFloor = rms < noiseFloor * 1.8f
                ? lerp(noiseFloor, Math.max(0.0004f, rms), 0.008f)
                : noiseFloor;
        boolean active = rms > Math.max(0.004f, noiseFloor * 2.15f);
        float gain = clamp((rms - noiseFloor) / Math.max(0.025f, noiseFloor * 12f));
        bassRaw = normalizeBand(bassRaw, bassN, gain, 6.5f);
        lowMidRaw = normalizeBand(lowMidRaw, lowMidN, gain, 7.0f);
        vocalRaw = normalizeBand(vocalRaw, vocalN, gain, 9.0f);
        trebleRaw = normalizeBand(trebleRaw, trebleN, gain, 12.0f);
        float spectralFlatness = tonalBins == 0 || tonalLinearSum <= 1.0e-9
                ? 1.0f
                : (float) (Math.exp(tonalLogSum / tonalBins) / (tonalLinearSum / tonalBins));
        float tonality = clamp(1.0f - spectralFlatness);
        float melodyRaw = clamp((lowMidRaw * 0.52f + vocalRaw * 0.34f + trebleRaw * 0.14f)
                * (0.64f + tonality * 0.46f));
        float bassRise = Math.max(0.0f, bassRaw - previousBassRaw);
        previousBassRaw = bassRaw;

        flux /= Math.max(1, FFT_SIZE / 2);
        fluxAverage = lerp(fluxAverage, flux, flux > fluxAverage ? 0.035f : 0.012f);
        float onset = clamp((flux - fluxAverage * 1.12f) / Math.max(0.0015f, fluxAverage * 1.8f));
        onset *= clamp(bassRaw * 0.75f + gain * 0.55f);
        onset = Math.max(onset, clamp((bassRise - 0.025f) * 4.8f) * clamp(gain * 1.5f));

        long now = System.nanoTime();
        boolean acceptedOnset = false;
        if (onset > 0.30f) {
            long refractorySamples = Math.max(1L, Math.round(sampleRate * 0.13));
            if (analysedSamples - lastOnsetSample < refractorySamples) {
                onset *= 0.16f;
            } else {
                lastOnsetSample = analysedSamples;
                acceptedOnset = true;
            }
        }
        float dt = HOP_SIZE / (float) sampleRate;
        lastAnalysisNs = now;
        bass = envelope(bass, active ? bassRaw : 0f, dt, 0.035f, 0.24f);
        // Tonal/harmonic content moves slowly; the voice-band proxy is more articulate.
        melody = envelope(melody, active ? melodyRaw : 0f, dt, 0.11f, 0.58f);
        vocal = envelope(vocal, active ? vocalRaw : 0f, dt, 0.065f, 0.34f);
        treble = envelope(treble, active ? trebleRaw : 0f, dt, 0.025f, 0.18f);
        energy = envelope(energy, active ? clamp(gain * 0.72f + bassRaw * 0.28f) : 0f, dt, 0.05f, 0.45f);
        beat = Math.max(beat * (float) Math.exp(-dt / 0.14f), onset);

        onsetHistory[onsetCursor] = onset;
        onsetCursor = (onsetCursor + 1) % onsetHistory.length;
        onsetCount = Math.min(onsetHistory.length, onsetCount + 1);
        if (active) lastSoundNs = lastAnalysisNs;
        if (analysedSamples - lastBpmUpdateSample >= Math.round(sampleRate * 0.85)
                && onsetCount > 180) {
            estimateBpm(sampleRate);
            lastBpmUpdateSample = analysedSamples;
        }

        if (bpmConfidence > 0.08f && bpm > 0.0f) beatPhase = (beatPhase + dt * bpm / 60.0) % 1.0;
        // The first trusted kick anchors the tempo grid. Later onsets act as a bounded
        // phase-locked-loop correction only near the predicted beat, so syncopation
        // still drives transient effects without making the BPM phase visibly jump.
        if (acceptedOnset && bpmConfidence > 0.12f && (bassRise > 0.035f || onset > 0.82f)) {
            if (!tempoAnchored) {
                beatPhase = 0.0;
                tempoAnchored = true;
            } else {
                double phaseError = beatPhase <= 0.5 ? -beatPhase : 1.0 - beatPhase;
                if (Math.abs(phaseError) <= 0.20) {
                    double correction = Math.max(-0.045, Math.min(0.045, phaseError * 0.32));
                    beatPhase = (beatPhase + correction + 1.0) % 1.0;
                }
            }
        }
        boolean signalPresent = active || lastAnalysisNs - lastSoundNs < 350_000_000L
                || bass + melody + vocal + treble + energy + beat > 0.025f;
        activityEnvelope = envelope(activityEnvelope, active ? 1.0f : 0.0f, dt, 0.035f, 0.65f);
        boolean audible = signalPresent || activityEnvelope > 0.01f;
        float tempoDrive = bpmConfidence * clamp((bpm - 105f) / 75f);
        float aggressionTarget = clamp(bass * 0.40f + beat * 0.28f + energy * 0.20f
                + treble * 0.12f + tempoDrive * 0.18f);
        aggression = envelope(aggression, audible ? aggressionTarget : 0.0f, dt, 0.75f, 2.6f);
        float calmTarget = audible
                ? clamp(1.0f - aggressionTarget * 0.82f - beat * 0.18f + melody * 0.10f + vocal * 0.06f)
                : 0.0f;
        calm = envelope(calm, calmTarget, dt, 1.4f, 3.2f);
        if (active) tempoResetForSilence = false;
        resetTempoAfterLongSilence(now);
        publishCurrent(audible);
    }

    private void estimateBpm(int sampleRate) {
        float frameRate = sampleRate / (float) HOP_SIZE;
        int minLag = Math.max(2, Math.round(frameRate * 60f / 210f));
        int maxLag = Math.min(onsetCount / 2, Math.round(frameRate * 60f / 58f));
        Arrays.fill(tempoScores, 0, Math.min(tempoScores.length, maxLag + 2), 0.0f);
        int supportMinLag = Math.max(2, minLag - 1);
        int supportMaxLag = Math.min(onsetCount / 2, maxLag + 1);
        for (int lag = supportMinLag; lag <= supportMaxLag; lag++) {
            float score = 0f;
            for (int n = lag; n < onsetCount; n++) score += history(n) * history(n - lag);
            tempoScores[lag] = score / Math.max(1, onsetCount - lag);
        }

        float best = 0f;
        int bestLag = 0;
        float average = 0f;
        int tested = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            // A one-bin tolerance prevents fractional beat periods (for example 23.44
            // analysis hops at 120 BPM/48 kHz) from losing to their perfectly aligned
            // half-time harmonic.
            float score = tempoScore(lag, minLag, maxLag);
            // Prefer the musically useful octave without forcing every slow song to double time.
            float candidate = frameRate * 60f / lag;
            score *= tempoPrior(candidate);
            average += score;
            tested++;
            if (score > best) { best = score; bestLag = lag; }
        }
        if (bestLag == 0) return;

        float bestSupport = tempoSupport(bestLag, minLag, maxLag);
        int harmonicLag = 0;
        float harmonicQuality = Float.NEGATIVE_INFINITY;
        for (int divisor = 2; divisor <= 3; divisor++) {
            float center = bestLag / (float) divisor;
            if (center < minLag - 0.5f) continue;
            int candidateLag = strongestTempoLag(Math.round(center), minLag, maxLag);
            if (candidateLag == 0) continue;
            float candidateSupport = tempoSupport(center, minLag, maxLag);
            float supportRatio = candidateSupport / Math.max(1.0e-6f, bestSupport);
            if (supportRatio < 0.86f) continue;
            float candidateBpm = frameRate * 60f / candidateLag;
            float quality = supportRatio * tempoPrior(candidateBpm);
            // Divisor two is evaluated first; a near-tie stays there instead of
            // needlessly tripling a slow song with several strong subdivisions.
            if (quality > harmonicQuality + 0.02f) {
                harmonicLag = candidateLag;
                harmonicQuality = quality;
            }
        }
        if (harmonicLag != 0) {
            bestLag = harmonicLag;
            best = tempoScore(bestLag, minLag, maxLag) * tempoPrior(frameRate * 60f / bestLag);
        }

        float refinedLag = bestLag;
        float weightedLag = 0.0f;
        float weightSum = 0.0f;
        for (int lag = Math.max(supportMinLag, bestLag - 1);
             lag <= Math.min(supportMaxLag, bestLag + 1); lag++) {
            float weight = tempoScores[lag];
            weightedLag += lag * weight;
            weightSum += weight;
        }
        if (weightSum > 1.0e-6f) {
            refinedLag = bestLag + clampSigned(weightedLag / weightSum - bestLag, -0.5f, 0.5f);
        }
        float estimated = clampSigned(frameRate * 60f / refinedLag, 58.0f, 210.0f);
        float confidence = clamp((best - average / Math.max(1, tested)) / Math.max(0.002f, best));
        if (confidence > 0.08f) {
            bpm = bpm <= 0.0f ? estimated : lerp(bpm, estimated, 0.18f + confidence * 0.28f);
        }
        bpmConfidence = lerp(bpmConfidence, confidence, 0.22f);
    }

    private float tempoScore(int lag, int minLag, int maxLag) {
        float score = tempoScores[lag];
        if (lag > Math.max(2, minLag - 1)) score += tempoScores[lag - 1] * 0.65f;
        if (lag < Math.min(onsetCount / 2, maxLag + 1)) score += tempoScores[lag + 1] * 0.65f;
        // Keep the denominator constant at the search edges. Renormalizing a
        // missing neighbor would incorrectly favor the min/max lag (for example
        // 200 BPM at 44.1 kHz drifting toward 215 BPM).
        return score / 2.30f;
    }

    private int strongestTempoLag(int center, int minLag, int maxLag) {
        int from = Math.max(minLag, center - 1);
        int to = Math.min(maxLag, center + 1);
        int strongest = 0;
        float best = 0.0f;
        for (int lag = from; lag <= to; lag++) {
            float score = tempoScore(lag, minLag, maxLag);
            if (score > best) { best = score; strongest = lag; }
        }
        return strongest;
    }

    private float tempoSupport(float center, int minLag, int maxLag) {
        int rounded = Math.round(center);
        float support = 0.0f;
        int from = Math.max(Math.max(2, minLag - 1), rounded - 1);
        int to = Math.min(Math.min(onsetCount / 2, maxLag + 1), rounded + 1);
        for (int lag = from; lag <= to; lag++) {
            support += tempoScores[lag];
        }
        return support;
    }

    private static float tempoPrior(float candidateBpm) {
        return 0.94f + 0.06f * (1f - Math.abs(candidateBpm - 112f) / 112f);
    }

    private float history(int chronologicalIndex) {
        int oldest = (onsetCursor - onsetCount + onsetHistory.length) % onsetHistory.length;
        return onsetHistory[(oldest + chronologicalIndex) % onsetHistory.length];
    }

    private void waitWithDecay(long delayMs) {
        long deadline = System.nanoTime() + delayMs * 1_000_000L;
        while (running.get() && !Thread.currentThread().isInterrupted() && System.nanoTime() < deadline) {
            decayWithoutPackets(System.nanoTime());
            try {
                Thread.sleep(20L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void decayWithoutPackets(long now) {
        if (resetRequested.compareAndSet(true, false)) resetAnalysisHistory();
        float dt = lastAnalysisNs == 0L ? 0.02f : Math.min(0.1f, (now - lastAnalysisNs) * 1.0e-9f);
        if (dt <= 0.0f) return;
        lastAnalysisNs = now;
        bass = envelope(bass, 0.0f, dt, 0.035f, 0.24f);
        melody = envelope(melody, 0.0f, dt, 0.11f, 0.58f);
        vocal = envelope(vocal, 0.0f, dt, 0.065f, 0.34f);
        treble = envelope(treble, 0.0f, dt, 0.025f, 0.18f);
        energy = envelope(energy, 0.0f, dt, 0.05f, 0.45f);
        beat *= (float) Math.exp(-dt / 0.14f);
        aggression = envelope(aggression, 0.0f, dt, 0.75f, 2.6f);
        calm = envelope(calm, 0.0f, dt, 1.4f, 3.2f);
        activityEnvelope = envelope(activityEnvelope, 0.0f, dt, 0.035f, 0.65f);
        bpmConfidence = envelope(bpmConfidence, 0.0f, dt, 0.2f, 2.4f);
        boolean audible = now - lastSoundNs < 350_000_000L
                || bass + melody + vocal + treble + energy + beat > 0.025f
                || activityEnvelope > 0.01f;
        resetTempoAfterLongSilence(now);
        publishCurrent(audible);
    }

    private void resetTempoAfterLongSilence(long now) {
        if (!tempoResetForSilence && lastSoundNs > 0L && now - lastSoundNs >= TEMPO_RESET_SILENCE_NS) {
            resetTempoHistory();
            tempoResetForSilence = true;
        }
    }

    private void resetAnalysisHistory() {
        windowFill = 0;
        Arrays.fill(window, 0.0f);
        Arrays.fill(previousSpectrum, 0.0f);
        noiseFloor = 0.0025f;
        fluxAverage = 0.02f;
        previousBassRaw = 0.0f;
        analysedSamples = 0L;
        lastOnsetSample = Long.MIN_VALUE / 4L;
        lastAnalysisNs = 0L;
        lastSoundNs = 0L;
        bass = melody = vocal = treble = energy = beat = 0.0f;
        aggression = calm = activityEnvelope = 0.0f;
        resetTempoHistory();
        tempoResetForSilence = true;
        snapshot.set(Snapshot.SILENT);
    }

    private void resetTempoHistory() {
        Arrays.fill(onsetHistory, 0.0f);
        onsetCursor = 0;
        onsetCount = 0;
        lastBpmUpdateSample = analysedSamples;
        lastOnsetSample = Long.MIN_VALUE / 4L;
        bpm = 0.0f;
        bpmConfidence = 0.0f;
        beatPhase = 0.0;
        tempoAnchored = false;
    }

    private void publishCurrent(boolean audible) {
        float bpmNormalized = bpm > 0.0f ? clamp((bpm - 60f) / 120f) : 1.0f / 3.0f;
        snapshot.set(new Snapshot(
                clamp(bass), clamp(melody), clamp(vocal), clamp(treble),
                clamp(energy), clamp(beat), bpm, bpmNormalized, clamp(bpmConfidence),
                (float) beatPhase, clamp(aggression), clamp(calm), clamp(activityEnvelope), audible
        ));
    }

    private static float normalizeBand(float sum, int count, float gain, float scale) {
        return clamp((sum / Math.max(1, count)) * scale * (0.32f + 0.68f * gain));
    }

    private static float envelope(float current, float target, float dt, float attack, float release) {
        float time = target > current ? attack : release;
        return lerp(current, target, 1f - (float) Math.exp(-dt / Math.max(0.001f, time)));
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * clamp(t); }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float clampSigned(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    private static void fft(float[] real, float[] imag) {
        int n = real.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                float tr = real[i]; real[i] = real[j]; real[j] = tr;
                float ti = imag[i]; imag[i] = imag[j]; imag[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2.0 * Math.PI / len;
            float wLenR = (float) Math.cos(angle), wLenI = (float) Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                float wr = 1f, wi = 0f;
                for (int j = 0; j < len / 2; j++) {
                    int a = i + j, b = a + len / 2;
                    float vr = real[b] * wr - imag[b] * wi;
                    float vi = real[b] * wi + imag[b] * wr;
                    real[b] = real[a] - vr; imag[b] = imag[a] - vi;
                    real[a] += vr; imag[a] += vi;
                    float nextWr = wr * wLenR - wi * wLenI;
                    wi = wr * wLenI + wi * wLenR; wr = nextWr;
                }
            }
        }
    }

    public record Snapshot(float bass, float melody, float vocalPresence, float treble,
                           float energy, float beat, float bpm, float bpmNormalized,
                           float bpmConfidence, float beatPhase, float aggression,
                           float calm, float activity, boolean active) {
        public static final Snapshot SILENT = new Snapshot(0, 0, 0, 0, 0, 0,
                0, 0.333f, 0, 0, 0, 0, 0, false);
    }

    @FunctionalInterface
    private interface SampleSink { void accept(float[] samples, int count, int sampleRate); }

    private static final class WasapiLoopback implements AutoCloseable {
        private static final Guid.CLSID CLSID_ENUMERATOR = new Guid.CLSID("{BCDE0395-E52F-467C-8E3D-C4579291692E}");
        private static final Guid.IID IID_ENUMERATOR = new Guid.IID("{A95664D2-9614-4F35-A746-DE8DB63617E6}");
        private static final Guid.IID IID_AUDIO_CLIENT = new Guid.IID("{1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}");
        private static final Guid.IID IID_CAPTURE_CLIENT = new Guid.IID("{C8ADBD64-E71E-48A0-A4DE-185C395CD317}");
        private static final int CLSCTX_ALL = 23;
        private static final int LOOPBACK = 0x00020000;
        private static final int DATA_DISCONTINUITY = 0x00000001;
        private static final int SILENT = 0x00000002;
        private Pointer enumerator, device, audioClient, captureClient, mixFormat;
        private int channels, sampleRate, blockAlign, bits, formatTag, subFormat;
        private String endpointId;
        private float[] monoBuffer = new float[2048];
        private final IntByReference packetRef = new IntByReference();
        private final IntByReference framesRef = new IntByReference();
        private final IntByReference flagsRef = new IntByReference();
        private final PointerByReference dataRef = new PointerByReference();
        private boolean comInitialized;

        void open() {
            check(Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED));
            comInitialized = true;
            PointerByReference out = new PointerByReference();
            check(Ole32.INSTANCE.CoCreateInstance(CLSID_ENUMERATOR, Pointer.NULL, CLSCTX_ALL, IID_ENUMERATOR, out));
            enumerator = out.getValue();
            out.setValue(null);
            check(call(enumerator, 4, WinNT.HRESULT.class, enumerator, 0, 1, out));
            device = out.getValue();
            endpointId = readDeviceId(device);
            out.setValue(null);
            check(call(device, 3, WinNT.HRESULT.class, device, IID_AUDIO_CLIENT, CLSCTX_ALL, Pointer.NULL, out));
            audioClient = out.getValue();
            PointerByReference formatOut = new PointerByReference();
            check(call(audioClient, 8, WinNT.HRESULT.class, audioClient, formatOut));
            mixFormat = formatOut.getValue();
            parseFormat();
            check(call(audioClient, 3, WinNT.HRESULT.class, audioClient, 0, LOOPBACK,
                    0L, 0L, mixFormat, Pointer.NULL));
            out.setValue(null);
            check(call(audioClient, 14, WinNT.HRESULT.class, audioClient, IID_CAPTURE_CLIENT, out));
            captureClient = out.getValue();
            check(call(audioClient, 10, WinNT.HRESULT.class, audioClient));
        }

        int read(SampleSink sink, Runnable discontinuityHandler) {
            packetRef.setValue(0);
            check(call(captureClient, 5, WinNT.HRESULT.class, captureClient, packetRef));
            int total = 0;
            while (packetRef.getValue() > 0) {
                dataRef.setValue(null);
                framesRef.setValue(0);
                flagsRef.setValue(0);
                check(call(captureClient, 3, WinNT.HRESULT.class, captureClient, dataRef, framesRef, flagsRef,
                        Pointer.NULL, Pointer.NULL));
                int frameCount = framesRef.getValue();
                ensureMonoCapacity(frameCount);
                try {
                    if ((flagsRef.getValue() & DATA_DISCONTINUITY) != 0) discontinuityHandler.run();
                    if ((flagsRef.getValue() & SILENT) != 0 || dataRef.getValue() == null) {
                        Arrays.fill(monoBuffer, 0, frameCount, 0.0f);
                    } else {
                        decode(dataRef.getValue(), monoBuffer, frameCount);
                    }
                } finally {
                    check(call(captureClient, 4, WinNT.HRESULT.class, captureClient, frameCount));
                }
                sink.accept(monoBuffer, frameCount, sampleRate);
                total += frameCount;
                check(call(captureClient, 5, WinNT.HRESULT.class, captureClient, packetRef));
            }
            return total;
        }

        boolean isDefaultEndpointCurrent() {
            Pointer currentDevice = null;
            try {
                PointerByReference current = new PointerByReference();
                check(call(enumerator, 4, WinNT.HRESULT.class, enumerator, 0, 1, current));
                currentDevice = current.getValue();
                return endpointId != null && endpointId.equals(readDeviceId(currentDevice));
            } finally {
                release(currentDevice);
            }
        }

        private void ensureMonoCapacity(int frames) {
            if (frames <= monoBuffer.length) return;
            int capacity = monoBuffer.length;
            while (capacity < frames) capacity <<= 1;
            monoBuffer = new float[capacity];
        }

        private static String readDeviceId(Pointer audioDevice) {
            if (audioDevice == null) return null;
            PointerByReference idRef = new PointerByReference();
            check(call(audioDevice, 5, WinNT.HRESULT.class, audioDevice, idRef));
            Pointer id = idRef.getValue();
            if (id == null) return null;
            try {
                return id.getWideString(0);
            } finally {
                Ole32.INSTANCE.CoTaskMemFree(id);
            }
        }

        private void parseFormat() {
            formatTag = mixFormat.getShort(0) & 0xffff;
            channels = mixFormat.getShort(2) & 0xffff;
            sampleRate = mixFormat.getInt(4);
            blockAlign = mixFormat.getShort(12) & 0xffff;
            bits = mixFormat.getShort(14) & 0xffff;
            subFormat = formatTag == 0xfffe ? mixFormat.getInt(24) : formatTag;
            if (channels < 1 || channels > 32 || sampleRate < 8_000 || sampleRate > 384_000 || blockAlign < channels) {
                throw new IllegalStateException("Unsupported WASAPI mix format");
            }
            boolean supportedFloat = subFormat == 3 && (bits == 32 || bits == 64);
            boolean supportedPcm = subFormat == 1 && (bits == 8 || bits == 16 || bits == 24 || bits == 32);
            if (!supportedFloat && !supportedPcm) {
                throw new IllegalStateException("Unsupported WASAPI sample encoding " + subFormat + "/" + bits);
            }
        }

        private void decode(Pointer data, float[] mono, int frames) {
            int bytesPerSample = Math.max(1, blockAlign / channels);
            for (int frame = 0; frame < frames; frame++) {
                float sum = 0f;
                long base = (long) frame * blockAlign;
                for (int channel = 0; channel < channels; channel++) {
                    long offset = base + (long) channel * bytesPerSample;
                    float value;
                    if (subFormat == 3 && bits == 32) value = data.getFloat(offset);
                    else if (subFormat == 3 && bits == 64) value = (float) data.getDouble(offset);
                    else if (bits == 8) value = ((data.getByte(offset) & 255) - 128) / 128f;
                    else if (bits == 16) value = data.getShort(offset) / 32768f;
                    else if (bits == 24) {
                        int raw = (data.getByte(offset) & 255) | ((data.getByte(offset + 1) & 255) << 8)
                                | (data.getByte(offset + 2) << 16);
                        value = raw / 8388608f;
                    } else if (bits == 32) value = data.getInt(offset) / 2147483648f;
                    else value = 0f;
                    sum += value;
                }
                mono[frame] = Math.max(-1f, Math.min(1f, sum / channels));
            }
        }

        @Override public void close() {
            if (audioClient != null) safeCall(audioClient, 11, WinNT.HRESULT.class, audioClient);
            release(captureClient); release(audioClient); release(device); release(enumerator);
            if (mixFormat != null) Ole32.INSTANCE.CoTaskMemFree(mixFormat);
            captureClient = audioClient = device = enumerator = mixFormat = null;
            endpointId = null;
            if (comInitialized) Ole32.INSTANCE.CoUninitialize();
            comInitialized = false;
        }

        private static Object call(Pointer object, int index, Class<?> returnType, Object... args) {
            Pointer vtable = object.getPointer(0);
            Pointer address = vtable.getPointer((long) index * Native.POINTER_SIZE);
            return Function.getFunction(address, Function.ALT_CONVENTION).invoke(returnType, args);
        }
        private static void safeCall(Pointer object, int index, Class<?> returnType, Object... args) {
            try { call(object, index, returnType, args); } catch (Throwable ignored) { }
        }
        private static void release(Pointer object) {
            if (object != null) safeCall(object, 2, Integer.class, object);
        }
        private static void check(Object result) {
            int value = result instanceof WinNT.HRESULT hr ? hr.intValue() : ((Number) result).intValue();
            if (value < 0) throw new IllegalStateException("WASAPI HRESULT 0x" + Integer.toHexString(value));
        }
    }
}
