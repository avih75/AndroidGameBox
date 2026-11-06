package com.gamebox.common.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

class GameAudioController {

    private enum class Effect { CARD_DRAW, CARD_COLLECT, CARD_PLAY }

    private data class Voice(
        val effect: Effect,
        var position: Int = 0,
        var noiseState: Int = 0x13579B
    )

    private val pendingEffects = ConcurrentLinkedQueue<Effect>()
    @Volatile private var active = false
    @Volatile private var musicEnabled = true
    @Volatile private var musicVolume = MUSIC_VOLUME
    @Volatile private var effectsEnabled = true
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var audioThread: Thread? = null

    fun startGame(music: Boolean, effects: Boolean, musicVolume: Double = MUSIC_VOLUME) {
        musicEnabled = music
        this.musicVolume = musicVolume.coerceIn(0.0, MUSIC_VOLUME)
        effectsEnabled = effects
        if (active) return
        if (!music && !effects) return

        audioThread?.join(THREAD_STOP_TIMEOUT_MS)
        if (audioThread?.isAlive == true) {
            Log.e(TAG, "Previous audio thread did not stop; audio remains disabled")
            return
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            Log.e(TAG, "Unable to determine audio buffer size: $minBufferSize")
            return
        }

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBufferSize, FRAMES_PER_BUFFER * BYTES_PER_SAMPLE * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Unable to create game audio track", error)
            return
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Unable to initialize game audio track", error)
            return
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "Audio track was not initialized")
            track.release()
            return
        }

        try {
            track.play()
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Unable to start game audio track", error)
            track.release()
            return
        }

        audioTrack = track
        active = true
        audioThread = Thread({ renderAudio(track) }, "GameBoxAudio").apply {
            isDaemon = true
            start()
        }
    }

    fun updateSettings(music: Boolean, effects: Boolean) {
        musicEnabled = music
        effectsEnabled = effects
        if (!effects) pendingEffects.clear()
    }

    fun playCardDraw() = enqueueEffect(Effect.CARD_DRAW)

    fun playCardsCollected() = enqueueEffect(Effect.CARD_COLLECT)

    fun playCardPlayed() = enqueueEffect(Effect.CARD_PLAY)

    fun stopGame() {
        active = false
        audioThread?.join(THREAD_STOP_TIMEOUT_MS)
        if (audioThread?.isAlive == true) {
            Log.e(TAG, "Game audio thread did not stop in time")
        }
    }

    private fun enqueueEffect(effect: Effect) {
        if (active && effectsEnabled) pendingEffects.add(effect)
    }

    private fun renderAudio(track: AudioTrack) {
        val buffer = ShortArray(FRAMES_PER_BUFFER)
        val voices = mutableListOf<Voice>()
        var samplePosition = 0L

        try {
            while (active) {
                for (frame in buffer.indices) {
                    while (true) {
                        val effect = pendingEffects.poll() ?: break
                        voices.add(Voice(effect))
                    }

                    var sample = if (musicEnabled) musicSample(samplePosition) else 0.0
                    val voiceIterator = voices.iterator()
                    while (voiceIterator.hasNext()) {
                        val voice = voiceIterator.next()
                        val voiceSample = effectSample(voice)
                        sample += voiceSample.first
                        if (voiceSample.second) voiceIterator.remove()
                    }

                    buffer[frame] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                    samplePosition++
                }

                var offset = 0
                while (active && offset < buffer.size) {
                    val written = track.write(
                        buffer,
                        offset,
                        buffer.size - offset,
                        AudioTrack.WRITE_BLOCKING
                    )
                    if (written <= 0) {
                        Log.e(TAG, "AudioTrack write failed: $written")
                        active = false
                    } else {
                        offset += written
                    }
                }
            }
        } finally {
            active = false
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.release()
            synchronized(this) {
                if (audioTrack === track) {
                    audioTrack = null
                    audioThread = null
                }
            }
            pendingEffects.clear()
        }
    }

    private fun musicSample(samplePosition: Long): Double {
        val seconds = samplePosition.toDouble() / SAMPLE_RATE
        val chordLength = CHORD_LENGTH_SECONDS
        val chordPosition = seconds % chordLength
        val chordIndex = (seconds / chordLength).toInt() % CHORDS.size
        val nextChordIndex = (chordIndex + 1) % CHORDS.size
        val transition = smoothStep(
            ((chordPosition - (chordLength - CHORD_FADE_SECONDS)) / CHORD_FADE_SECONDS)
                .coerceIn(0.0, 1.0)
        )
        val level = 0.72 + 0.28 * sin(2.0 * PI * seconds / BREATH_SECONDS)
        val current = chordSample(CHORDS[chordIndex], seconds)
        val next = chordSample(CHORDS[nextChordIndex], seconds)
        val accompaniment = current * (1.0 - transition) + next * transition
        return (accompaniment * ACCOMPANIMENT_VOLUME + melodySample(seconds)) *
            level * musicVolume
    }

    private fun melodySample(seconds: Double): Double {
        val notePosition = seconds % MELODY_LOOP_SECONDS
        val noteIndex = (notePosition / MELODY_NOTE_SECONDS).toInt()
        val noteTime = notePosition % MELODY_NOTE_SECONDS
        val frequency = MELODY_NOTES[noteIndex]
        val attack = (noteTime / MELODY_ATTACK_SECONDS).coerceIn(0.0, 1.0)
        val release = ((MELODY_NOTE_SECONDS - noteTime) / MELODY_RELEASE_SECONDS)
            .coerceIn(0.0, 1.0)
        val envelope = smoothStep(attack) * smoothStep(release)
        val phase = 2.0 * PI * frequency * noteTime
        return (sin(phase) + sin(phase * 2.0) * MELODY_HARMONIC_VOLUME) *
            envelope * MELODY_VOLUME
    }

    private fun chordSample(chord: DoubleArray, seconds: Double): Double {
        var sample = 0.0
        for (index in chord.indices) {
            val harmonicVolume = if (index == 0) 0.46 else 0.27
            sample += sin(2.0 * PI * chord[index] * seconds) * harmonicVolume
        }
        return sample
    }

    private fun effectSample(voice: Voice): Pair<Double, Boolean> {
        val length = when (voice.effect) {
            Effect.CARD_DRAW -> DRAW_LENGTH
            Effect.CARD_COLLECT -> COLLECT_LENGTH
            Effect.CARD_PLAY -> PLAY_LENGTH
        }
        if (voice.position >= length) return 0.0 to true

        val progress = voice.position.toDouble() / length
        val envelope = (1.0 - progress) * (1.0 - progress)
        val value = when (voice.effect) {
            Effect.CARD_DRAW -> {
                val frequency = DRAW_START_HZ + (DRAW_END_HZ - DRAW_START_HZ) * progress
                val tone = sin(2.0 * PI * frequency * voice.position / SAMPLE_RATE)
                val noise = nextNoise(voice) * DRAW_NOISE_LEVEL
                (tone * DRAW_TONE_LEVEL + noise) * envelope
            }
            Effect.CARD_COLLECT -> {
                val frequency = COLLECT_START_HZ + (COLLECT_END_HZ - COLLECT_START_HZ) * progress
                val tone = sin(2.0 * PI * frequency * voice.position / SAMPLE_RATE)
                (tone * COLLECT_TONE_LEVEL + nextNoise(voice) * COLLECT_NOISE_LEVEL) * envelope
            }
            Effect.CARD_PLAY -> {
                val frequency = PLAY_START_HZ + (PLAY_END_HZ - PLAY_START_HZ) * progress
                val tone = sin(2.0 * PI * frequency * voice.position / SAMPLE_RATE)
                (tone * PLAY_TONE_LEVEL + nextNoise(voice) * PLAY_NOISE_LEVEL) * envelope
            }
        }
        voice.position++
        return value to false
    }

    private fun nextNoise(voice: Voice): Double {
        var value = voice.noiseState
        value = value xor (value shl 13)
        value = value xor (value ushr 17)
        value = value xor (value shl 5)
        voice.noiseState = value
        return (value.toDouble() / Int.MAX_VALUE).coerceIn(-1.0, 1.0)
    }

    private fun smoothStep(value: Double): Double = value * value * (3.0 - 2.0 * value)

    companion object {
        private const val TAG = "GameBoxAudio"
        private const val SAMPLE_RATE = 22050
        private const val FRAMES_PER_BUFFER = 512
        private const val BYTES_PER_SAMPLE = 2
        private const val THREAD_STOP_TIMEOUT_MS = 500L
        private const val CHORD_LENGTH_SECONDS = 8.0
        private const val CHORD_FADE_SECONDS = 2.0
        private const val BREATH_SECONDS = 6.0
        private const val MUSIC_VOLUME = 0.10
        private const val ACCOMPANIMENT_VOLUME = 0.42
        private const val MELODY_VOLUME = 0.38
        private const val MELODY_HARMONIC_VOLUME = 0.12
        private const val MELODY_NOTE_SECONDS = 1.0
        private const val MELODY_LOOP_SECONDS = 8.0
        private const val MELODY_ATTACK_SECONDS = 0.06
        private const val MELODY_RELEASE_SECONDS = 0.18
        private const val DRAW_LENGTH = SAMPLE_RATE / 5
        private const val COLLECT_LENGTH = SAMPLE_RATE / 4
        private const val PLAY_LENGTH = SAMPLE_RATE / 7
        private const val DRAW_START_HZ = 620.0
        private const val DRAW_END_HZ = 1120.0
        private const val DRAW_TONE_LEVEL = 0.42
        private const val DRAW_NOISE_LEVEL = 0.14
        private const val COLLECT_START_HZ = 440.0
        private const val COLLECT_END_HZ = 290.0
        private const val COLLECT_TONE_LEVEL = 0.36
        private const val COLLECT_NOISE_LEVEL = 0.10
        private const val PLAY_START_HZ = 760.0
        private const val PLAY_END_HZ = 480.0
        private const val PLAY_TONE_LEVEL = 0.30
        private const val PLAY_NOISE_LEVEL = 0.06
        private val CHORDS = arrayOf(
            doubleArrayOf(130.81, 164.81, 196.00),
            doubleArrayOf(110.00, 130.81, 164.81),
            doubleArrayOf(87.31, 110.00, 130.81),
            doubleArrayOf(98.00, 123.47, 146.83)
        )
        private val MELODY_NOTES = doubleArrayOf(
            261.63, 329.63, 392.00, 329.63,
            293.66, 349.23, 392.00, 261.63
        )
    }
}
