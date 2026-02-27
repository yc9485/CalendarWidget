package com.example.widgetcalendar

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

object SoundManager {
    private const val PREFS_NAME = "sound_prefs"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    
    private var soundPool: SoundPool? = null
    private var completionSoundId: Int = -1
    private var isInitialized = false
    
    fun initialize(context: Context) {
        if (isInitialized) return
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()
        
        // We'll use a system sound for now (notification sound)
        // In production, you'd add a custom sound file to res/raw/
        isInitialized = true
    }
    
    fun isSoundEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SOUND_ENABLED, true) // Default: enabled
    }
    
    fun setSoundEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SOUND_ENABLED, enabled)
            .apply()
    }
    
    fun playCompletionSound(context: Context) {
        if (!isSoundEnabled(context)) return
        
        // Play upward SMS ringtone: C6 E6 G6
        // Bright & happy melody
        try {
            // Note frequencies (Hz): C6=1047, E6=1319, G6=1568
            // Durations: 1/8 note = 150ms, 1/4 note = 300ms
            val melody = listOf(
                Pair(1047, 150),  // C6 (1/8 note)
                Pair(1319, 150),  // E6 (1/8 note)
                Pair(1568, 300)   // G6 (1/4 note)
            )
            
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var delay = 0L
            
            melody.forEach { (freq, duration) ->
                handler.postDelayed({
                    playTone(freq, duration)
                }, delay)
                delay += duration.toLong()
            }
            
        } catch (e: Exception) {
            // Silently fail if sound can't be played
        }
    }
    
    private fun playTone(frequency: Int, durationMs: Int) {
        try {
            val toneGen = android.media.ToneGenerator(
                android.media.AudioManager.STREAM_NOTIFICATION,
                85
            )
            // ToneGenerator doesn't support custom frequencies directly
            // We'll use AudioTrack for custom frequencies
            val sampleRate = 44100
            val numSamples = (durationMs * sampleRate / 1000)
            val samples = DoubleArray(numSamples)
            val buffer = ShortArray(numSamples)
            
            for (i in samples.indices) {
                samples[i] = Math.sin(2.0 * Math.PI * i.toDouble() / (sampleRate / frequency))
                buffer[i] = (samples[i] * Short.MAX_VALUE).toInt().toShort()
            }
            
            val audioTrack = android.media.AudioTrack(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                android.media.AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                buffer.size * 2,
                android.media.AudioTrack.MODE_STATIC,
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            
            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            
            // Release after playing
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                audioTrack.stop()
                audioTrack.release()
            }, durationMs.toLong() + 50)
            
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    fun release() {
        soundPool?.release()
        soundPool = null
        isInitialized = false
    }
}
