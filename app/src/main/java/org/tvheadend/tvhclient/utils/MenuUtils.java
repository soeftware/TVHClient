package org.tvheadend.tvhclient.utils;


import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;

import org.tvheadend.tvhclient.MainApplication;
import org.tvheadend.tvhclient.R;
import org.tvheadend.tvhclient.data.entity.ChannelTag;
import org.tvheadend.tvhclient.data.entity.Connection;
import org.tvheadend.tvhclient.data.entity.Program;
import org.tvheadend.tvhclient.data.entity.Recording;
import org.tvheadend.tvhclient.data.entity.SeriesRecording;
import org.tvheadend.tvhclient.data.entity.ServerProfile;
import org.tvheadend.tvhclient.data.entity.ServerStatus;
import org.tvheadend.tvhclient.data.entity.TimerRecording;
import org.tvheadend.tvhclient.data.repository.AppRepository;
import org.tvheadend.tvhclient.data.service.EpgSyncService;
import org.tvheadend.tvhclient.features.download.DownloadRecordingManager;
import org.tvheadend.tvhclient.features.notifications.ProgramNotificationReceiver;
import org.tvheadend.tvhclient.features.search.SearchActivity;
import org.tvheadend.tvhclient.features.shared.adapter.ChannelTagListAdapter;
import org.tvheadend.tvhclient.features.shared.adapter.GenreColorDialogAdapter;
import org.tvheadend.tvhclient.features.shared.callbacks.ChannelTagSelectionCallback;
import org.tvheadend.tvhclient.features.shared.callbacks.ChannelTimeSelectionCallback;
import org.tvheadend.tvhclient.features.shared.callbacks.RecordingRemovedCallback;
import org.tvheadend.tvhclient.features.startup.SplashActivity;
import org.tvheadend.tvhclient.features.streaming.external.PlayChannelActivity;
import org.tvheadend.tvhclient.features.streaming.external.PlayRecordingActivity;
import org.tvheadend.tvhclient.features.streaming.internal.HtspPlaybackActivity;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import timber.log.Timber;

import static android.content.Context.ALARM_SERVICE;

public class MenuUtils {

    private final boolean isUnlocked;
    @Inject
    protected AppRepository appRepository;
    @Inject
    protected SharedPreferences sharedPreferences;
    private final ServerStatus serverStatus;
    private WeakReference<Activity> activity;

    public MenuUtils(Activity activity) {
        MainApplication.getComponent().inject(this);

        this.activity = new WeakReference<>(activity);
        this.isUnlocked = MainApplication.getInstance().isUnlocked();
        this.serverStatus = appRepository.getServerStatusData().getActiveItem();
    }

    /**
     * Prepares a dialog that shows the available genre colors and the names. In
     * here the data for the adapter is created and the dialog prepared which
     * can be shown later.
     */
    public boolean handleMenuGenreColorSelection() {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        final String[] s = activity.getResources().getStringArray(R.array.pr_content_type0);

        // Fill the list for the adapter
        final List<GenreColorDialogAdapter.GenreColorDialogItem> items = new ArrayList<>();
        for (int i = 0; i < s.length; ++i) {
            GenreColorDialogAdapter.GenreColorDialogItem genreColor = new GenreColorDialogAdapter.GenreColorDialogItem();
            genreColor.color = UIUtils.getGenreColor(activity, ((i + 1) * 16), 0);
            genreColor.genre = s[i];
            items.add(genreColor);
        }
        new MaterialDialog.Builder(activity)
                .title(R.string.genre_color_list)
                .adapter(new GenreColorDialogAdapter(items), null)
                .show();
        return true;
    }

    public boolean handleMenuTimeSelection(int currentSelection, int intervalInHours, int maxIntervalsToShow, ChannelTimeSelectionCallback callback) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }

        SimpleDateFormat startDateFormat = new SimpleDateFormat("dd.MM.yyyy - HH.00", Locale.US);
        SimpleDateFormat endDateFormat = new SimpleDateFormat("HH.00", Locale.US);

        String[] times = new String[maxIntervalsToShow];
        times[0] = activity.getString(R.string.current_time);

        // Set the time that shall be shown next in the dialog. This is the
        // current time plus the value of the intervalInHours in milliseconds
        long timeInMillis = Calendar.getInstance().getTimeInMillis() + 1000 * 60 * 60 * intervalInHours;

        // Add the date and time to the list. Remove Increase the time in
        // milliseconds for each iteration by the defined intervalInHours
        for (int i = 1; i < maxIntervalsToShow; i++) {
            String startTime = startDateFormat.format(timeInMillis);
            timeInMillis += 1000 * 60 * 60 * intervalInHours;
            String endTime = endDateFormat.format(timeInMillis);
            times[i] = startTime + " - " + endTime;
        }

        new MaterialDialog.Builder(activity)
                .title(R.string.select_time)
                .items(times)
                .itemsCallbackSingleChoice(currentSelection, (dialog, itemView, which, text) -> {
                    if (callback != null) {
                        callback.onTimeSelected(which);
                    }
                    return true;
                })
                .build()
                .show();
        return true;
    }

    public boolean handleMenuChannelTagsSelection(int channelTagId, ChannelTagSelectionCallback callback) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }

        // Create a default tag (All channels)
        ChannelTag tag = new ChannelTag();
        tag.setTagId(0);
        tag.setTagName(activity.getString(R.string.all_channels));

        // Add the default tag to the beginning of the list to indicate
        // that no tag is selected and all channels shall be shown
        List<ChannelTag> channelTagList = appRepository.getChannelTagData().getItems();
        channelTagList.add(0, tag);

        ChannelTagListAdapter channelTagListAdapter = new ChannelTagListAdapter(
                activity, channelTagList, channelTagId,
                appRepository.getChannelData().getItems().size());

        // Show the dialog that shows all available channel tags. When the
        // user has selected a tag, restart the loader to loadRecordingById the updated channel list
        final MaterialDialog dialog = new MaterialDialog.Builder(activity)
                .title(R.string.tags)
                .adapter(channelTagListAdapter, null)
                .build();

        // Set the callback to handle clicks. This needs to be done after the
        // dialog creation so that the inner method has access to the dialog variable
        channelTagListAdapter.setCallback(which -> {
            if (callback != null) {
                callback.onChannelTagIdSelected(which);
            }
            if (dialog != null) {
                dialog.dismiss();
            }
        });
        dialog.show();
        return true;
    }

    public boolean handleMenuDownloadSelection(int dvrId) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        new DownloadRecordingManager(activity, dvrId);
        return true;
    }

    public boolean handleMenuSearchImdbWebsite(String title) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        try {
            String url = URLEncoder.encode(title, "utf-8");
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("imdb:///find?s=tt&q=" + url));
            PackageManager packageManager = activity.getPackageManager();
            if (packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
                intent.setData(Uri.parse("http://www.imdb.org/find?s=tt&q=" + url));
            }
            activity.startActivity(intent);
            activity.finish();
        } catch (UnsupportedEncodingException e) {
            // NOP
        }
        return true;
    }

    public boolean handleMenuSearchFileAffinityWebsite(String title) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        try {
            String url = URLEncoder.encode(title, "utf-8");
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://www.filmaffinity.com/es/search.php?stext=" + url));
            activity.startActivity(intent);
            activity.finish();
        } catch (UnsupportedEncodingException e) {
            // NOP
        }
        return true;
    }

    public boolean handleMenuSearchEpgSelection(String title) {
        return handleMenuSearchEpgSelection(title, 0);
    }

    public boolean handleMenuSearchEpgSelection(String title, int channelId) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.setAction(Intent.ACTION_SEARCH);
        intent.putExtra(SearchManager.QUERY, title);
        intent.putExtra("channelId", channelId);
        activity.startActivity(intent);
        activity.finish();
        return true;
    }

    public boolean handleMenuRecordSelection(int eventId) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        Timber.d("handleMenuRecordSelection() called with: eventId = [" + eventId + "]");
        final Intent intent = new Intent(activity, EpgSyncService.class);
        intent.setAction("addDvrEntry");
        intent.putExtra("eventId", eventId);

        ServerProfile profile = appRepository.getServerProfileData().getItemById(serverStatus.getRecordingServerProfileId());
        if (MiscUtils.isServerProfileEnabled(profile, serverStatus)) {
            intent.putExtra("configName", profile.getName());
        }
        activity.startService(intent);
        return true;
    }

    public boolean handleMenuSeriesRecordSelection(String title) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        final Intent intent = new Intent(activity, EpgSyncService.class);
        intent.setAction("addAutorecEntry");
        intent.putExtra("title", title);

        ServerProfile profile = appRepository.getServerProfileData().getItemById(serverStatus.getRecordingServerProfileId());
        if (MiscUtils.isServerProfileEnabled(profile, serverStatus)) {
            intent.putExtra("configName", profile.getName());
        }
        activity.startService(intent);
        return true;
    }

    public boolean handleMenuStopRecordingSelection(int dvrId, String title) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        // Show a confirmation dialog before stopping the recording
        new MaterialDialog.Builder(activity)
                .title(R.string.record_stop)
                .content(activity.getString(R.string.stop_recording, title))
                .negativeText(R.string.cancel)
                .positiveText(R.string.stop)
                .onPositive((dialog, which) -> {
                    final Intent intent = new Intent(activity, EpgSyncService.class);
                    intent.setAction("stopDvrEntry");
                    intent.putExtra("id", dvrId);
                    activity.startService(intent);
                })
                .show();
        return true;
    }

    public boolean handleMenuRemoveRecordingSelection(int dvrId, String title, RecordingRemovedCallback callback) {
        Timber.d("handleMenuRemoveRecordingSelection() called with: dvrId = [" + dvrId + "], title = [" + title + "]");
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        Timber.d("handleMenuRemoveRecordingSelection: ");
        // Show a confirmation dialog before removing the recording
        new MaterialDialog.Builder(activity)
                .title(R.string.record_remove)
                .content(activity.getString(R.string.remove_recording, title))
                .negativeText(R.string.cancel)
                .positiveText(R.string.remove)
                .onPositive((dialog, which) -> {
                    final Intent intent = new Intent(activity, EpgSyncService.class);
                    intent.setAction("deleteDvrEntry");
                    intent.putExtra("id", dvrId);
                    activity.startService(intent);
                    if (callback != null) {
                        callback.onRecordingRemoved();
                    }
                })
                .show();
        return true;
    }

    public boolean handleMenuCancelRecordingSelection(int dvrId, String title, RecordingRemovedCallback callback) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        // Show a confirmation dialog before cancelling the recording
        new MaterialDialog.Builder(activity)
                .title(R.string.record_cancel)
                .content(activity.getString(R.string.cancel_recording, title))
                .negativeText(R.string.discard)
                .positiveText(R.string.cancel)
                .onPositive((dialog, which) -> {
                    final Intent intent = new Intent(activity, EpgSyncService.class);
                    intent.setAction("cancelDvrEntry");
                    intent.putExtra("id", dvrId);
                    activity.startService(intent);
                    if (callback != null) {
                        callback.onRecordingRemoved();
                    }
                })
                .show();
        return true;
    }

    public boolean handleMenuRemoveSeriesRecordingSelection(String id, String title, RecordingRemovedCallback callback) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        // Show a confirmation dialog before removing the recording
        new MaterialDialog.Builder(activity)
                .title(R.string.record_remove)
                .content(activity.getString(R.string.remove_series_recording, title))
                .negativeText(R.string.cancel)
                .positiveText(R.string.remove)
                .onPositive((dialog, which) -> {
                    final Intent intent = new Intent(activity, EpgSyncService.class);
                    intent.setAction("deleteAutorecEntry");
                    intent.putExtra("id", id);
                    activity.startService(intent);
                    if (callback != null) {
                        callback.onRecordingRemoved();
                    }
                })
                .show();
        return true;
    }

    public boolean handleMenuRemoveTimerRecordingSelection(String id, String title, RecordingRemovedCallback callback) {
        Timber.d("handleMenuRemoveTimerRecordingSelection() called with: id = [" + id + "], title = [" + title + "], callback = [" + callback + "]");
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        // Show a confirmation dialog before removing the recording
        new MaterialDialog.Builder(activity)
                .title(R.string.record_remove)
                .content(activity.getString(R.string.remove_timer_recording, title))
                .negativeText(R.string.cancel)
                .positiveText(R.string.remove)
                .onPositive((dialog, which) -> {
                    final Intent intent = new Intent(activity, EpgSyncService.class);
                    intent.setAction("deleteTimerecEntry");
                    intent.putExtra("id", id);
                    activity.startService(intent);
                    if (callback != null) {
                        callback.onRecordingRemoved();
                    }
                })
                .show();
        return true;
    }

    public boolean handleMenuRemoveAllRecordingsSelection(List<Recording> items) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        new MaterialDialog.Builder(activity)
                .title(R.string.record_remove_all)
                .content(R.string.confirm_remove_all)
                .positiveText(activity.getString(R.string.remove))
                .negativeText(activity.getString(R.string.cancel))
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        new Thread() {
                            public void run() {
                                for (Recording item : items) {
                                    final Intent intent = new Intent(activity, EpgSyncService.class);
                                    intent.putExtra("id", item.getId());
                                    if (item.isRecording() || item.isScheduled()) {
                                        intent.setAction("cancelDvrEntry");
                                    } else {
                                        intent.setAction("deleteDvrEntry");
                                    }
                                    activity.startService(intent);
                                    try {
                                        sleep(500);
                                    } catch (InterruptedException e) {
                                        // NOP
                                    }
                                }
                            }
                        }.start();
                    }
                }).show();
        return true;
    }

    public boolean handleMenuRemoveAllSeriesRecordingSelection(List<SeriesRecording> items) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        new MaterialDialog.Builder(activity)
                .title(R.string.record_remove_all)
                .content(R.string.remove_all_recordings)
                .positiveText(activity.getString(R.string.remove))
                .negativeText(activity.getString(R.string.cancel))
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        new Thread() {
                            public void run() {
                                for (SeriesRecording item : items) {
                                    final Intent intent = new Intent(activity, EpgSyncService.class);
                                    intent.setAction("deleteAutorecEntry");
                                    intent.putExtra("id", item.getId());
                                    activity.startService(intent);
                                    try {
                                        sleep(500);
                                    } catch (InterruptedException e) {
                                        // NOP
                                    }
                                }
                            }
                        }.start();
                    }
                }).show();
        return true;
    }

    public boolean handleMenuRemoveAllTimerRecordingSelection(List<TimerRecording> items) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        new MaterialDialog.Builder(activity)
                .title(R.string.record_remove_all)
                .content(R.string.remove_all_recordings)
                .positiveText(activity.getString(R.string.remove))
                .negativeText(activity.getString(R.string.cancel))
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        new Thread() {
                            public void run() {
                                for (TimerRecording item : items) {
                                    final Intent intent = new Intent(activity, EpgSyncService.class);
                                    intent.setAction("deleteTimerecEntry");
                                    intent.putExtra("id", item.getId());
                                    activity.startService(intent);
                                    try {
                                        sleep(500);
                                    } catch (InterruptedException e) {
                                        // NOP
                                    }
                                }
                            }
                        }.start();
                    }
                }).show();
        return true;
    }

    public boolean handleMenuCustomRecordSelection(final int eventId, final int channelId) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }

        final String[] dvrConfigList = appRepository.getServerProfileData().getRecordingProfileNames();

        // Get the selected recording profile to highlight the
        // correct item in the list of the selection dialog
        int dvrConfigNameValue = 0;

        ServerProfile serverProfile = appRepository.getServerProfileData().getItemById(serverStatus.getRecordingServerProfileId());
        if (serverProfile != null) {
            for (int i = 0; i < dvrConfigList.length; i++) {
                if (dvrConfigList[i].equals(serverProfile.getName())) {
                    dvrConfigNameValue = i;
                    break;
                }
            }
        }
        // Create the dialog to show the available profiles
        new MaterialDialog.Builder(activity)
                .title(R.string.select_dvr_config)
                .items(dvrConfigList)
                .itemsCallbackSingleChoice(dvrConfigNameValue, new MaterialDialog.ListCallbackSingleChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                        // Pass over the
                        Intent intent = new Intent(activity, EpgSyncService.class);
                        intent.setAction("addDvrEntry");
                        intent.putExtra("eventId", eventId);
                        intent.putExtra("channelId", channelId);
                        intent.putExtra("configName", dvrConfigList[which]);
                        activity.startService(intent);
                        return true;
                    }
                })
                .show();
        return true;
    }

    public void onPreparePopupMenu(Menu menu, Recording recording, boolean isNetworkAvailable) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return;
        }

        MenuItem recordOnceMenuItem = menu.findItem(R.id.menu_record_once);
        MenuItem recordOnceCustomProfileMenuItem = menu.findItem(R.id.menu_record_once_custom_profile);
        MenuItem recordSeriesMenuItem = menu.findItem(R.id.menu_record_series);
        MenuItem recordRemoveMenuItem = menu.findItem(R.id.menu_record_remove);
        MenuItem playMenuItem = menu.findItem(R.id.menu_play);
        MenuItem castMenuItem = menu.findItem(R.id.menu_cast);
        MenuItem addReminderMenuItem = menu.findItem(R.id.menu_add_notification);

        if (isNetworkAvailable) {
            if (recording == null || !recording.isRecording() && !recording.isScheduled()) {
                recordOnceMenuItem.setVisible(true);
                recordOnceCustomProfileMenuItem.setVisible(isUnlocked);
                recordSeriesMenuItem.setVisible(serverStatus.getHtspVersion() >= 13);

            } else if (recording.isRecording()) {
                playMenuItem.setVisible(true);
                CastSession castSession = CastContext.getSharedInstance(activity).getSessionManager().getCurrentCastSession();
                castMenuItem.setVisible(castSession != null);
                recordRemoveMenuItem.setTitle(R.string.stop);
                recordRemoveMenuItem.setVisible(true);

            } else if (recording.isScheduled()) {
                recordRemoveMenuItem.setTitle(R.string.cancel);
                recordRemoveMenuItem.setVisible(true);

            } else {
                recordRemoveMenuItem.setTitle(R.string.remove);
                recordRemoveMenuItem.setVisible(true);
            }
        }
        if (isUnlocked && sharedPreferences.getBoolean("notifications_enabled", true)) {
            addReminderMenuItem.setVisible(true);
        }
    }

    public void onPreparePopupSearchMenu(Menu menu, boolean isNetworkAvailable) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return;
        }

        MenuItem searchImdbMenuItem = menu.findItem(R.id.menu_search_imdb);
        if (searchImdbMenuItem != null) {
            searchImdbMenuItem.setVisible(
                    isNetworkAvailable && sharedPreferences.getBoolean("search_on_imdb_menu_enabled", true));
        }

        MenuItem searchFileAffinityMenuItem = menu.findItem(R.id.menu_search_fileaffinity);
        if (searchFileAffinityMenuItem != null) {
            searchFileAffinityMenuItem.setVisible(
                    isNetworkAvailable && sharedPreferences.getBoolean("search_on_fileaffinity_menu_enabled", true));
        }

        MenuItem searchEpgMenuItem = menu.findItem(R.id.menu_search_epg);
        if (searchEpgMenuItem != null) {
            searchEpgMenuItem.setVisible(
                    isNetworkAvailable && sharedPreferences.getBoolean("search_epg_menu_enabled", true));
        }
    }

    public void handleMenuReconnectSelection() {
        Activity activity = this.activity.get();
        if (activity == null) {
            return;
        }
        new MaterialDialog.Builder(activity)
                .title("Reconnect to server?")
                .content("The application will be restarted and a new initial sync will be done.")
                .negativeText(R.string.cancel)
                .positiveText("Reconnect")
                .onPositive((dialog, which) -> {
                    Timber.d("Reconnect requested, stopping service and clearing last update information");

                    activity.stopService(new Intent(activity, EpgSyncService.class));
                    // Update the connection with the information that a new sync is required.
                    Connection connection = appRepository.getConnectionData().getActiveItem();
                    connection.setSyncRequired(true);
                    connection.setLastUpdate(0);
                    appRepository.getConnectionData().updateItem(connection);
                    // Finally restart the application to show the startup fragment
                    Intent intent = new Intent(activity, SplashActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(intent);
                })
                .show();
    }

    public boolean handleMenuAddNotificationSelection(Program program) {
        Timber.d("handleMenuAddNotificationSelection: program " + program.getEventId() + ", " + program.getTitle());
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }

        Integer offset = Integer.valueOf(sharedPreferences.getString("notification_lead_time", "0"));

        // TODO time handling weird
        long currentTime = new Date().getTime();
        long notificationTime = (program.getStart() - (offset * 1000 * 60));
        if (notificationTime < currentTime) {
            notificationTime = currentTime;
        }

        Intent intent = new Intent(activity, ProgramNotificationReceiver.class);
        intent.putExtra("eventTitle", program.getTitle());
        intent.putExtra("eventId", program.getEventId());
        intent.putExtra("channelId", program.getChannelId());
        intent.putExtra("start", program.getStart());

        ServerProfile profile = appRepository.getServerProfileData().getItemById(serverStatus.getRecordingServerProfileId());
        if (MiscUtils.isServerProfileEnabled(profile, serverStatus)) {
            intent.putExtra("configName", profile.getName());
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(activity, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) activity.getSystemService(ALARM_SERVICE);
        if (am != null) {
            am.set(AlarmManager.RTC_WAKEUP, notificationTime, pendingIntent);
        }
        return true;
    }

    public boolean handleMenuPlayChannel(int channelId) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        if (sharedPreferences.getBoolean("internal_player_enabled", false)) {
            Intent intent = new Intent(activity, HtspPlaybackActivity.class);
            intent.putExtra("channelId", channelId);
            activity.startActivity(intent);
        } else {
            Intent intent = new Intent(activity, PlayChannelActivity.class);
            intent.putExtra("channelId", channelId);
            activity.startActivity(intent);
        }
        return true;
    }

    public boolean handleMenuPlayRecording(int dvrId) {
        Activity activity = this.activity.get();
        if (activity == null) {
            return false;
        }
        Intent intent = new Intent(activity, PlayRecordingActivity.class);
        intent.putExtra("dvrId", dvrId);
        activity.startActivity(intent);
        return true;
    }
}
