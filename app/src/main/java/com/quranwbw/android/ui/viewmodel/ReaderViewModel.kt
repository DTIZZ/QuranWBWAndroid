package com.quranwbw.android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quranwbw.android.audio.QuranAudioPlayer
import com.quranwbw.android.audio.WordHighlightSync
import com.quranwbw.android.data.ArabicLetterParser
import com.quranwbw.android.data.QuranRepository
import com.quranwbw.android.dtadhan.data.DtAdhanRepository
import com.quranwbw.android.dtadhan.model.AppSettings
import com.quranwbw.android.dtadhan.model.GeoLocation
import com.quranwbw.android.dtadhan.model.LocationMode
import com.quranwbw.android.dtadhan.model.PrayerEvent
import com.quranwbw.android.dtadhan.stats.AyahReadRecord
import com.quranwbw.android.dtadhan.stats.ReadingSessionRecord
import com.quranwbw.android.dtadhan.weather.WeatherSnapshot
import com.quranwbw.android.model.ArabicLetterUnit
import com.quranwbw.android.model.Ayah
import com.quranwbw.android.model.QuranWord
import com.quranwbw.android.model.Surah
import com.quranwbw.android.speedread.FocusedReadMode
import com.quranwbw.android.speedread.SpeedReadTheme
import com.quranwbw.android.speedread.SpeedReadTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val isLoading: Boolean = true,
    val surahs: List<Surah> = emptyList(),
    val selectedSurahIndex: Int = 0,
    val selectedAyahNumber: Int? = null,
    val selectedWord: QuranWord? = null,
    val parsedLetters: List<ArabicLetterUnit> = emptyList(),
    val selectedLetter: ArabicLetterUnit? = null,
    val playingAyahNumber: Int? = null,
    val highlightedWordIndex: Int? = null,
    val isSpeedReadMode: Boolean = false,
    val speedReadWpm: Int = SpeedReadTokenizer.DEFAULT_SPEED_WPM,
    val speedReadTheme: SpeedReadTheme = SpeedReadTheme.WARM_SEPIA,
    val focusedReadMode: FocusedReadMode = FocusedReadMode.ARABIC_WBW,
    val settings: AppSettings = AppSettings(),
    val weather: WeatherSnapshot? = null,
    val ayahReads: List<AyahReadRecord> = emptyList(),
    val sessions: List<ReadingSessionRecord> = emptyList(),
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
) {
    val currentSurah: Surah? get() = surahs.getOrNull(selectedSurahIndex)
}

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = QuranRepository(application)
    private val dtAdhanRepo = DtAdhanRepository(application)
    private val audioPlayer = QuranAudioPlayer(application)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var syncJob: Job? = null

    init {
        viewModelScope.launch {
            val savedSettings = withContext(Dispatchers.IO) { dtAdhanRepo.loadSettings() }
            val savedWeather = withContext(Dispatchers.IO) { dtAdhanRepo.loadWeather() }
            val savedReads = withContext(Dispatchers.IO) { dtAdhanRepo.loadAyahReads() }
            val savedSessions = withContext(Dispatchers.IO) { dtAdhanRepo.loadSessions() }
            val surahs = withContext(Dispatchers.IO) { repository.loadSurahs() }
            val firstWord = surahs.firstOrNull()?.ayahs?.firstOrNull()?.words?.firstOrNull()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    surahs = surahs,
                    selectedAyahNumber = surahs.firstOrNull()?.ayahs?.firstOrNull()?.number,
                    selectedWord = firstWord,
                    parsedLetters = firstWord?.let { w -> ArabicLetterParser.parse(w.arabic) } ?: emptyList(),
                    settings = savedSettings,
                    weather = savedWeather,
                    ayahReads = savedReads,
                    sessions = savedSessions,
                    speedReadWpm = savedSettings.quran.speedReadWpm,
                    speedReadTheme = savedSettings.quran.speedReadTheme,
                    focusedReadMode = savedSettings.quran.focusedReadMode,
                )
            }

            savedSettings.location?.let { loc ->
                if (savedWeather == null || savedWeather.isStale(java.time.Instant.now())) {
                    runCatching { dtAdhanRepo.refreshWeather(loc) }.getOrNull()?.let { fresh ->
                        _uiState.update { it.copy(weather = fresh) }
                    }
                }
            }
        }
    }

    fun selectSurah(index: Int) {
        stopPlayback()
        val surah = _uiState.value.surahs.getOrNull(index) ?: return
        val firstAyah = surah.ayahs.firstOrNull()
        val firstWord = firstAyah?.words?.firstOrNull()
        _uiState.update {
            it.copy(
                selectedSurahIndex = index,
                selectedAyahNumber = firstAyah?.number,
                selectedWord = firstWord,
                parsedLetters = firstWord?.let { w -> ArabicLetterParser.parse(w.arabic) } ?: emptyList(),
                selectedLetter = null,
            )
        }
    }

    fun selectAndPlayWord(ayah: Ayah, word: QuranWord) {
        stopSyncJob()
        val letters = ArabicLetterParser.parse(word.arabic)
        _uiState.update {
            it.copy(
                selectedAyahNumber = ayah.number,
                selectedWord = word,
                parsedLetters = letters,
                selectedLetter = null,
                playingAyahNumber = null,
                highlightedWordIndex = null,
            )
        }
        audioPlayer.playUrl(word.audioUrl)

        // If the user taps the last word of an ayah, log that ayah as read
        if (ayah.words.lastOrNull()?.index == word.index) {
            recordAyahCompleted(ayah.number, (word.endMs - word.startMs).coerceAtLeast(3_000L))
        }
    }

    fun replaySelectedWord() {
        val word = _uiState.value.selectedWord ?: return
        audioPlayer.playUrl(word.audioUrl)
    }

    fun selectAndPlayLetter(letter: ArabicLetterUnit) {
        _uiState.update { it.copy(selectedLetter = letter) }
        audioPlayer.playLetter(letter)
    }

    fun togglePlayAyah(ayah: Ayah) {
        if (_uiState.value.playingAyahNumber == ayah.number) {
            stopPlayback()
            return
        }

        stopSyncJob()
        _uiState.update {
            it.copy(
                selectedAyahNumber = ayah.number,
                playingAyahNumber = ayah.number,
                highlightedWordIndex = 0,
            )
        }

        val estimatedDwell = ayah.words.maxOfOrNull { it.endMs }?.coerceAtLeast(4_000L) ?: 5_000L
        audioPlayer.playUrl(ayah.audioUrl) {
            recordAyahCompleted(ayah.number, estimatedDwell)
            stopPlayback()
        }

        syncJob = viewModelScope.launch {
            while (isActive) {
                if (audioPlayer.isPlaying()) {
                    val pos = audioPlayer.currentPositionMs()
                    val activeIdx = WordHighlightSync.findActiveWordIndex(pos, ayah.words)
                    _uiState.update { it.copy(highlightedWordIndex = activeIdx) }
                }
                delay(60L)
            }
        }
    }

    private fun stopSyncJob() {
        syncJob?.cancel()
        syncJob = null
    }

    fun stopPlayback() {
        stopSyncJob()
        audioPlayer.stop()
        _uiState.update {
            it.copy(
                playingAyahNumber = null,
                highlightedWordIndex = null,
            )
        }
    }

    fun toggleSpeedReadMode() {
        stopPlayback()
        _uiState.update { it.copy(isSpeedReadMode = !it.isSpeedReadMode) }
    }

    fun setSpeedReadWpm(wpm: Int) {
        val clamped = wpm.coerceIn(SpeedReadTokenizer.MIN_SPEED_WPM, SpeedReadTokenizer.MAX_SPEED_WPM)
        updateSettings { it.copy(quran = it.quran.copy(speedReadWpm = clamped)) }
    }

    fun setSpeedReadTheme(theme: SpeedReadTheme) {
        updateSettings { it.copy(quran = it.quran.copy(speedReadTheme = theme)) }
    }

    fun setFocusedReadMode(mode: FocusedReadMode) {
        updateSettings { it.copy(quran = it.quran.copy(focusedReadMode = mode)) }
    }

    fun recordAyahCompleted(ayahNumber: Int, dwellMs: Long) {
        val surah = _uiState.value.currentSurah ?: return
        val ayah = surah.ayahs.firstOrNull { it.number == ayahNumber } ?: return
        val wordsAr = ayah.words.size
        val wordsEn = ayah.words.sumOf { w ->
            w.translation.trim().split(Regex("""\s+""")).count { it.isNotEmpty() }
        }.coerceAtLeast(wordsAr)

        viewModelScope.launch(Dispatchers.IO) {
            val (reads, sessions) = dtAdhanRepo.recordAyahRead(
                surah = surah.number,
                ayah = ayah.number,
                wordsEn = wordsEn,
                wordsAr = wordsAr,
                dwellMs = dwellMs,
                settings = _uiState.value.settings,
            )
            _uiState.update {
                it.copy(
                    ayahReads = reads,
                    sessions = sessions,
                )
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_uiState.value.settings)
        _uiState.update {
            it.copy(
                settings = next,
                speedReadWpm = next.quran.speedReadWpm,
                speedReadTheme = next.quran.speedReadTheme,
                focusedReadMode = next.quran.focusedReadMode,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            dtAdhanRepo.saveSettings(next)
        }
    }

    fun togglePrayerAlert(event: PrayerEvent) {
        updateSettings { s ->
            val current = s.isAlertEnabled(event)
            s.copy(alertEnabled = s.alertEnabled + (event to !current))
        }
    }

    fun useGpsLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = null) }
            try {
                val loc = dtAdhanRepo.resolveGpsLocation()
                updateSettings { it.copy(locationMode = LocationMode.GPS, location = loc) }
                val weather = runCatching { dtAdhanRepo.refreshWeather(loc) }.getOrNull()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        weather = weather ?: it.weather,
                        statusMessage = "Location updated to ${loc.name}",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = e.message ?: "Could not resolve GPS location",
                    )
                }
            }
        }
    }

    fun usePostalCode(postalCode: String, countryCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = null) }
            try {
                val loc = dtAdhanRepo.resolvePostalCode(postalCode, countryCode)
                updateSettings {
                    it.copy(
                        locationMode = LocationMode.MANUAL,
                        manualPostalCode = postalCode.trim(),
                        manualCountryCode = countryCode,
                        location = loc,
                    )
                }
                val weather = runCatching { dtAdhanRepo.refreshWeather(loc) }.getOrNull()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        weather = weather ?: it.weather,
                        statusMessage = "Location set to ${loc.name}",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = e.message ?: "Could not look up postal code",
                    )
                }
            }
        }
    }

    fun selectPresetLocation(location: GeoLocation) {
        updateSettings { it.copy(locationMode = LocationMode.MANUAL, location = location) }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = "Location set to ${location.name}") }
            val weather = runCatching { dtAdhanRepo.refreshWeather(location) }.getOrNull()
            _uiState.update { it.copy(isBusy = false, weather = weather ?: it.weather) }
        }
    }

    fun refreshWeatherAndLocation() {
        val currentSettings = _uiState.value.settings
        if (currentSettings.locationMode == LocationMode.GPS && dtAdhanRepo.hasLocationPermission()) {
            useGpsLocation()
            return
        }
        val loc = currentSettings.location ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = null) }
            val weather = runCatching { dtAdhanRepo.refreshWeather(loc) }.getOrNull()
            _uiState.update {
                it.copy(
                    isBusy = false,
                    weather = weather ?: it.weather,
                    statusMessage = if (weather != null) null else "Offline: using cached prayer times & weather",
                )
            }
        }
    }

    fun clearReadingStats() {
        viewModelScope.launch(Dispatchers.IO) {
            dtAdhanRepo.clearAllReadingStats()
            _uiState.update {
                it.copy(
                    ayahReads = emptyList(),
                    sessions = emptyList(),
                    statusMessage = "Reading statistics reset",
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSyncJob()
        audioPlayer.release()
    }
}
