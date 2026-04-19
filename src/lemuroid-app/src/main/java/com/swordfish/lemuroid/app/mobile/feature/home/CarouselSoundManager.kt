package com.swordfish.lemuroid.app.mobile.feature.home

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.swordfish.lemuroid.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages UI sound effects for the carousel
 */
class CarouselSoundManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CarouselSoundManager"
    }
    
    private val soundPool: SoundPool
    private var swipeSound: Int = 0
    private var selectSound: Int = 0
    private var deleteSound: Int = 0
    private var errorSound: Int = 0
    private var exitSound: Int = 0
    
    private var soundsLoaded = false
    
    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()
        
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                soundsLoaded = true
                Log.d(TAG, "Sounds loaded successfully")
            }
        }
        
        loadSounds()
    }
    
    private fun loadSounds() {
        try {
            swipeSound = soundPool.load(context, R.raw.swipe, 1)
            selectSound = soundPool.load(context, R.raw.select, 1)
            deleteSound = soundPool.load(context, R.raw.borrado, 1)
            errorSound = soundPool.load(context, R.raw.error, 1)
            exitSound = soundPool.load(context, R.raw.exit, 1)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sounds: ${e.message}")
        }
    }
    
    fun playSwipe() {
        if (soundsLoaded && swipeSound != 0) {
            soundPool.play(swipeSound, 0.5f, 0.5f, 1, 0, 1f)
        }
    }
    
    fun playSelect() {
        if (soundsLoaded && selectSound != 0) {
            soundPool.play(selectSound, 0.7f, 0.7f, 1, 0, 1f)
        }
    }
    
    fun playDelete() {
        if (soundsLoaded && deleteSound != 0) {
            soundPool.play(deleteSound, 0.7f, 0.7f, 1, 0, 1f)
        }
    }
    
    fun playError() {
        if (soundsLoaded && errorSound != 0) {
            soundPool.play(errorSound, 0.7f, 0.7f, 1, 0, 1f)
        }
    }
    
    fun playExit() {
        if (soundsLoaded && exitSound != 0) {
            soundPool.play(exitSound, 0.7f, 0.7f, 1, 0, 1f)
        }
    }
    
    fun release() {
        soundPool.release()
    }
}

/**
 * Manages background music playback
 * - Auto-starts on creation
 * - Plays intro sound (open_apk) first
 * - Then plays random music tracks continuously
 */
class MusicPlayerManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MusicPlayerManager"
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private val musicTracks = listOf(
        R.raw.musica1,
        R.raw.musica2,
        R.raw.musica3,
        R.raw.musica4,
        R.raw.musica5,
        R.raw.musica6,
        R.raw.musica7
    )
    
    private var currentTrackIndex = 0
    private var shuffledTracks: List<Int> = musicTracks.shuffled()
    private var hasPlayedIntro = false
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentTrackName = MutableStateFlow("Intro")
    val currentTrackName: StateFlow<String> = _currentTrackName.asStateFlow()
    
    init {
        shuffledTracks = musicTracks.shuffled()
        // Check persistence: only auto-start if allowed
        val prefs = context.getSharedPreferences("music_state", Context.MODE_PRIVATE)
        val shouldPlay = prefs.getBoolean("music_enabled", true)
        if (shouldPlay) {
            startWithIntro()
        }
    }
    
    /**
     * Starts playback with the intro sound, then continues with random music
     */
    private fun startWithIntro() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, R.raw.open_apk).apply {
                setOnCompletionListener {
                    // After intro, start random music
                    hasPlayedIntro = true
                    playRandomMusic()
                }
                setVolume(0.5f, 0.5f) // Intro at higher volume
                start()
            }
            _isPlaying.value = true
            _currentTrackName.value = "Intro"
            Log.d(TAG, "Playing intro")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing intro: ${e.message}")
            // Fallback: start music directly
            hasPlayedIntro = true
            playRandomMusic()
        }
    }
    
    /**
     * Starts playing random music tracks (shuffled order for auto-play)
     */
    private fun playRandomMusic() {
        if (shuffledTracks.isEmpty()) return
        startTrack(shuffledTracks[currentTrackIndex], isAutoPlay = true)
    }
    
    fun play() {
        val prefs = context.getSharedPreferences("music_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("music_enabled", true).apply()

        if (mediaPlayer == null) {
            if (!hasPlayedIntro) {
                startWithIntro()
            } else {
                playRandomMusic()
            }
        } else {
            mediaPlayer?.start()
        }
        _isPlaying.value = true
    }
    
    /**
     * Resume playback with fade-in effect
     */
    fun fadeIn(durationMs: Long = 500) {
        val prefs = context.getSharedPreferences("music_state", Context.MODE_PRIVATE)
        // If user explicitly paused (saved state is false), DO NOT fade in automatically on resume
        if (!prefs.getBoolean("music_enabled", true)) {
            return
        }

        if (mediaPlayer == null) {
            if (!hasPlayedIntro) {
                startWithIntro()
            } else {
                playRandomMusic()
            }
        } else {
            mediaPlayer?.let { player ->
                player.setVolume(0f, 0f)
                player.start()
                // Animate volume from 0 to 0.3 (background music volume)
                val targetVolume = 0.3f
                val steps = 10
                val stepDuration = durationMs / steps
                val volumeStep = targetVolume / steps
                
                android.os.Handler(android.os.Looper.getMainLooper()).let { handler ->
                    var currentStep = 0
                    val runnable = object : Runnable {
                        override fun run() {
                            currentStep++
                            val volume = (currentStep * volumeStep).coerceAtMost(targetVolume)
                            player.setVolume(volume, volume)
                            if (currentStep < steps) {
                                handler.postDelayed(this, stepDuration)
                            }
                        }
                    }
                    handler.post(runnable)
                }
            }
        }
        _isPlaying.value = true
    }
    
    fun pause() {
        val prefs = context.getSharedPreferences("music_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("music_enabled", false).apply()
        
        mediaPlayer?.pause()
        _isPlaying.value = false
    }
    
    /**
     * Pause playback with fade-out effect
     */
    fun fadeOut(durationMs: Long = 500) {
        // Note: fadeOut is usually ephemeral (e.g. entering game), but if it's used for stopping, we might want to save state?
        // Usually fadeOut is temporary. But if the user pauses via UI, it calls pause(). This might be used when leaving app?
        // If used for backgrounding, we shouldn't necessarily save 'false' permanently if we want it to resume on return?
        // BUT the requirement is: "si el usuario pausa la música nunca volverá a activarse hasta que vuelva a darle a play".
        // The user pauses via togglePlayPause -> which calls pause().
        // fadeOut might be called by system life cycle.
        // Let's NOT save state in fadeOut to avoid side effects of temporary pausing.
        // Only explicit pause() saves state.
        
        mediaPlayer?.let { player ->
            val startVolume = 0.3f // Current background music volume
            val steps = 10
            val stepDuration = durationMs / steps
            val volumeStep = startVolume / steps
            
            android.os.Handler(android.os.Looper.getMainLooper()).let { handler ->
                var currentStep = 0
                val runnable = object : Runnable {
                    override fun run() {
                        currentStep++
                        val volume = (startVolume - currentStep * volumeStep).coerceAtLeast(0f)
                        player.setVolume(volume, volume)
                        if (currentStep < steps) {
                            handler.postDelayed(this, stepDuration)
                        } else {
                            player.pause()
                        }
                    }
                }
                handler.post(runnable)
            }
        }
        _isPlaying.value = false
    }
    
    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }
    
    /**
     * Next track - LINEAR navigation (not shuffled)
     */
    fun next() {
        hasPlayedIntro = true
        // Linear navigation through music tracks
        currentTrackIndex = (currentTrackIndex + 1) % musicTracks.size
        startTrack(musicTracks[currentTrackIndex], isAutoPlay = false)
    }
    
    /**
     * Previous track - LINEAR navigation (not shuffled)
     */
    fun previous() {
        hasPlayedIntro = true
        // Linear navigation through music tracks
        currentTrackIndex = if (currentTrackIndex > 0) currentTrackIndex - 1 else musicTracks.size - 1
        startTrack(musicTracks[currentTrackIndex], isAutoPlay = false)
    }
    
    /**
     * Start a track
     * @param isAutoPlay if true, adds 5 second delay before next track on completion
     */
    private fun startTrack(trackResId: Int, isAutoPlay: Boolean = true) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, trackResId).apply {
                setOnCompletionListener {
                    if (isAutoPlay) {
                        // Wait 5 seconds before next track (auto-play mode)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            // Continue with shuffled tracks for auto-play
                            currentTrackIndex++
                            if (currentTrackIndex >= shuffledTracks.size) {
                                shuffledTracks = musicTracks.shuffled()
                                currentTrackIndex = 0
                            }
                            if (_isPlaying.value) {
                                startTrack(shuffledTracks[currentTrackIndex], isAutoPlay = true)
                            }
                        }, 5000) // 5 second delay
                    }
                }
                setVolume(0.3f, 0.3f) // Background music volume
                start()
            }
            _isPlaying.value = true
            _currentTrackName.value = "Music ${musicTracks.indexOf(trackResId) + 1}"
            Log.d(TAG, "Playing: ${_currentTrackName.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing track: ${e.message}")
        }
    }
    
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
    }
}

