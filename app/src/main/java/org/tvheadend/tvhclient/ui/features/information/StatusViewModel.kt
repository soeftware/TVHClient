package org.tvheadend.tvhclient.ui.features.information

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.data.service.HtspService
import org.tvheadend.tvhclient.domain.entity.Channel
import org.tvheadend.tvhclient.domain.entity.Input
import org.tvheadend.tvhclient.domain.entity.ServerStatus
import org.tvheadend.tvhclient.domain.entity.Subscription
import org.tvheadend.tvhclient.ui.base.BaseViewModel
import timber.log.Timber

class StatusViewModel(application: Application) : BaseViewModel(application), SharedPreferences.OnSharedPreferenceChangeListener {

    // TODO remove when variable in base model has been changed to live data
    val serverStatusLiveData: LiveData<ServerStatus> = appRepository.serverStatusData.liveDataActiveItem
    val channelCount: LiveData<Int> = appRepository.channelData.getLiveDataItemCount()
    val programCount: LiveData<Int> = appRepository.programData.getLiveDataItemCount()
    val timerRecordingCount: LiveData<Int> = appRepository.timerRecordingData.getLiveDataItemCount()
    val seriesRecordingCount: LiveData<Int> = appRepository.seriesRecordingData.getLiveDataItemCount()
    val completedRecordingCount: LiveData<Int> = appRepository.recordingData.getLiveDataCountByType("completed")
    val scheduledRecordingCount: LiveData<Int> = appRepository.recordingData.getLiveDataCountByType("scheduled")
    val failedRecordingCount: LiveData<Int> = appRepository.recordingData.getLiveDataCountByType("failed")
    val removedRecordingCount: LiveData<Int> = appRepository.recordingData.getLiveDataCountByType("removed")

    val showRunningRecordingCount = MediatorLiveData<Boolean>()
    val showLowStorageSpace = MediatorLiveData<Boolean>()
    var runningRecordingCount = 0
    var availableStorageSpace = 0

    val subscriptions: LiveData<List<Subscription>> = appRepository.subscriptionData.getLiveDataItems()
    val inputs: LiveData<List<Input>> = appRepository.inputData.getLiveDataItems()

    private lateinit var discSpaceUpdateTask: Runnable
    private val diskSpaceUpdateHandler = Handler()

    init {
        Timber.d("Initializing")
        // Listen to changes of the recording count. If the count changes to zero or the setting
        // to show notifications is disabled, set the value to false to remove any notification
        showRunningRecordingCount.addSource(appRepository.recordingData.getLiveDataCountByType("running")) { count ->
            Timber.d("Running recording count has changed, checking if notification shall be shown")
            runningRecordingCount = count
            val enabled = sharedPreferences.getBoolean("notify_running_recording_count_enabled", appContext.resources.getBoolean(R.bool.pref_default_notify_running_recording_count_enabled))
            showRunningRecordingCount.value = enabled && count > 0
        }

        // Listen to changes of the server status especially the free storage space.
        // If the free space is above the threshold or the setting to show
        // notifications is disabled, set the value to false to remove any notification
        showLowStorageSpace.addSource(serverStatusLiveData) { serverStatus ->
            if (serverStatus != null) {
                availableStorageSpace = (serverStatus.freeDiskSpace / 1000000000000).toInt()
                val enabled = sharedPreferences.getBoolean("notify_low_storage_space_enabled", appContext.resources.getBoolean(R.bool.pref_default_notify_low_storage_space_enabled))
                val threshold = Integer.valueOf(sharedPreferences.getString("low_storage_space_threshold", appContext.resources.getString(R.string.pref_default_low_storage_space_threshold))!!)
                Timber.d("Server status free space has changed to $availableStorageSpace, threshold is $threshold, checking if notification shall be shown")
                showLowStorageSpace.value = enabled && availableStorageSpace <= threshold
            }
        }

        discSpaceUpdateTask = Runnable {
            val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningAppProcessInfo = activityManager.runningAppProcesses?.get(0)

            if (runningAppProcessInfo != null
                    && runningAppProcessInfo.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {

                Timber.d("Application is in the foreground, starting service to get disk space ")
                val intent = Intent(appContext, HtspService::class.java)
                intent.action = "getDiskSpace"
                appContext.startService(intent)
            }
            Timber.d("Restarting disc space update handler in 60s")
            diskSpaceUpdateHandler.postDelayed(discSpaceUpdateTask, 60000)
        }

        onSharedPreferenceChanged(sharedPreferences, "notify_running_recording_count_enabled")
        onSharedPreferenceChanged(sharedPreferences, "notify_low_storage_space_enabled")

        Timber.d("Registering shared preference change listener")
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onCleared() {
        Timber.d("Unregistering shared preference change listener")
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        stopDiskSpaceUpdateHandler()
        super.onCleared()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Timber.d("Shared preference $key has changed")
        if (sharedPreferences == null) return
        when (key) {
            "notifications_enabled" -> {
                Timber.d("Setting has changed, checking if running recording count notification shall be shown")
                val enabled = sharedPreferences.getBoolean(key, appContext.resources.getBoolean(R.bool.pref_default_notify_running_recording_count_enabled))
                showRunningRecordingCount.value = enabled && runningRecordingCount > 0
            }
            "notify_low_storage_space_enabled" -> {
                val enabled = sharedPreferences.getBoolean(key, appContext.resources.getBoolean(R.bool.pref_default_notify_low_storage_space_enabled))
                val threshold = Integer.valueOf(sharedPreferences.getString("low_storage_space_threshold", appContext.resources.getString(R.string.pref_default_low_storage_space_threshold))!!)
                Timber.d("Server status free space has changed to $availableStorageSpace, threshold is $threshold, checking if notification shall be shown")
                showLowStorageSpace.value = enabled && availableStorageSpace <= threshold
            }
        }
    }

    fun getChannelById(id: Int): Channel? {
        return appRepository.channelData.getItemById(id)
    }

    fun startDiskSpaceUpdateHandler() {
        Timber.d("Starting disk space update handler")
        diskSpaceUpdateHandler.post(discSpaceUpdateTask)
    }

    fun stopDiskSpaceUpdateHandler() {
        Timber.d("Stopping disk space update handler")
        diskSpaceUpdateHandler.removeCallbacks(discSpaceUpdateTask)
    }
}