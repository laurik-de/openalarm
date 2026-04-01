package de.laurik.openalarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository.getInstance(application)

    val themeMode = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.themeMode.value)

    fun setThemeMode(mode: AppThemeMode) {
        repository.setThemeMode(mode)
    }

    val isPureBlack = repository.isPureBlack
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.isPureBlack.value)

    fun setPureBlack(enabled: Boolean) = repository.setPureBlack(enabled)

    val quickAdjustPresets = repository.quickAdjustPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.quickAdjustPresets.value)

    val timerPresets = repository.timerPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.timerPresets.value)

    val defaultSnooze = repository.defaultSnooze
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defaultSnooze.value)

    val defaultAutoStop = repository.defaultAutoStop
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defaultAutoStop.value)

    val timerRingtone = repository.timerRingtone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.timerRingtone.value)

    val timerVibration = repository.timerVibration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.timerVibration.value)

    val timerVolume = repository.timerVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.timerVolume.value)

    val timerTtsEnabled = repository.timerTtsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.timerTtsEnabled.value)

    val timerTtsText = repository.timerTtsText
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.timerTtsText.value)

    val defaultTimerAutoStop = repository.defaultTimerAutoStop
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defaultTimerAutoStop.value)

    fun setDefaultSnooze(m: Int) = repository.setDefaultSnooze(m)
    fun setDefaultAutoStop(m: Int) = repository.setDefaultAutoStop(m)

    fun setTimerRingtone(uri: String?) = repository.setTimerRingtone(uri)
    fun setTimerVibration(enabled: Boolean) = repository.setTimerVibration(enabled)
    fun setTimerVolume(v: Float) = repository.setTimerVolume(v)
    fun setTimerTts(enabled: Boolean, text: String) = repository.setTimerTts(enabled, text)

    fun setQuickAdjustPresets(list: List<Int>) = repository.setQuickAdjustPresets(list)
    fun setTimerPresets(list: List<Int>) = repository.setTimerPresets(list)
    fun setDefaultTimerAutoStop(m: Int) = repository.setDefaultTimerAutoStop(m)

    // NEW: Ringing Mode & Presets
    val defaultRingingMode = repository.defaultRingingMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defaultRingingMode.value)
    
    fun setDefaultRingingMode(mode: RingingScreenMode) = repository.setDefaultRingingMode(mode)

    val defaultSnoozePresets = repository.defaultSnoozePresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defaultSnoozePresets.value)
    
    fun setDefaultSnoozePresets(list: List<Int>) = repository.setDefaultSnoozePresets(list)

    val timerAdjustPresets = repository.timerAdjustPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.timerAdjustPresets.value)
    
    fun setTimerAdjustPresets(list: List<Int>) = repository.setTimerAdjustPresets(list)

    // ALARM DEFAULTS
    val defVibrationEnabled = repository.defVibrationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defVibrationEnabled.value)
    fun setDefVibration(e: Boolean) = repository.setDefVibration(e)

    val defRingtoneUri = repository.defRingtoneUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defRingtoneUri.value)
    fun setDefRingtone(u: String?) = repository.setDefRingtone(u)

    val defCustomVolume = repository.defCustomVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defCustomVolume.value)
    fun setDefVolume(v: Float?) = repository.setDefVolume(v)

    val defFadeInSeconds = repository.defFadeInSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defFadeInSeconds.value)
    fun setDefFadeIn(s: Int) = repository.setDefFadeIn(s)

    val defTtsMode = repository.defTtsMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defTtsMode.value)
    fun setDefTtsMode(m: TtsMode) = repository.setDefTtsMode(m)

    val defTtsText = repository.defTtsText
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defTtsText.value)
    fun setDefTtsText(t: String) = repository.setDefTtsText(t)

    val defIsSingleUse = repository.defIsSingleUse
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defIsSingleUse.value)
    fun setDefSingleUse(e: Boolean) = repository.setDefSingleUse(e)

    val defIsSelfDestroying = repository.defIsSelfDestroying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defIsSelfDestroying.value)
    fun setDefSelfDestroy(e: Boolean) = repository.setDefSelfDestroy(e)

    val defDaysOfWeek = repository.defDaysOfWeek
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defDaysOfWeek.value)
    fun setDefDaysOfWeek(days: List<Int>) = repository.setDefDaysOfWeek(days)

    val defIsSnoozeEnabled = repository.defIsSnoozeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defIsSnoozeEnabled.value)
    fun setDefSnoozeEnabled(e: Boolean) = repository.setDefSnoozeEnabled(e)

    val defMaxSnoozes = repository.defMaxSnoozes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defMaxSnoozes.value)
    fun setDefMaxSnoozes(m: Int?) = repository.setDefMaxSnoozes(m)

    val defDirectSnooze = repository.defDirectSnooze
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defDirectSnooze.value)
    fun setDefDirectSnooze(e: Boolean) = repository.setDefDirectSnooze(e)

    val defHurdleEnabled = repository.defHurdleEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defHurdleEnabled.value)
    fun setDefHurdleEnabled(e: Boolean) = repository.setDefHurdleEnabled(e)

    val defSelectedHurdles = repository.defSelectedHurdles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.defSelectedHurdles.value)
    fun setDefSelectedHurdles(h: List<HurdleType>) = repository.setDefSelectedHurdles(h)

    val notifyBeforeEnabled = repository.notifyBeforeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.notifyBeforeEnabled.value)
    fun setNotifyBeforeEnabled(e: Boolean) {
        repository.setNotifyBeforeEnabled(e)
        NotificationRenderer.refreshAll(getApplication())
        AlarmScheduler(getApplication()).scheduleNotificationUpdate()
    }

    val notifyBeforeMinutes = repository.notifyBeforeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.notifyBeforeMinutes.value)
    fun setNotifyBeforeMinutes(m: Int) {
        repository.setNotifyBeforeMinutes(m)
        NotificationRenderer.refreshAll(getApplication())
        AlarmScheduler(getApplication()).scheduleNotificationUpdate()
    }

    val fullScreenPermissionShown = repository.fullScreenPermissionShown
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.fullScreenPermissionShown.value)
    fun setFullScreenPermissionShown(s: Boolean) = repository.setFullScreenPermissionShown(s)

    val betaHurdlesEnabled = repository.betaHurdlesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.betaHurdlesEnabled.value)
    fun setBetaHurdlesEnabled(e: Boolean) = repository.setBetaHurdlesEnabled(e)

    fun createDefaultAlarm(hour: Int, minute: Int): AlarmItem {
        return AlarmItem(
            id = 0, hour = hour, minute = minute, groupId = "default",
            vibrationEnabled = defVibrationEnabled.value, ringtoneUri = defRingtoneUri.value,
            customVolume = defCustomVolume.value, fadeInSeconds = defFadeInSeconds.value,
            ttsMode = defTtsMode.value, ttsText = defTtsText.value.ifBlank { null },
            isSingleUse = defIsSingleUse.value,
            isSelfDestroying = defIsSelfDestroying.value, daysOfWeek = defDaysOfWeek.value,
            isSnoozeEnabled = defIsSnoozeEnabled.value, directSnooze = defDirectSnooze.value,
            maxSnoozes = defMaxSnoozes.value, snoozePresets = null, ringingScreenMode = defaultRingingMode.value,
            hurdleEnabled = defHurdleEnabled.value, selectedHurdles = defSelectedHurdles.value
        )
    }

    suspend fun exportBackup(outputStream: java.io.OutputStream): Boolean {
        return BackupManager.exportData(getApplication(), outputStream)
    }

    suspend fun importBackup(inputStream: java.io.InputStream): Boolean {
        return BackupManager.importData(getApplication(), inputStream)
    }
}
