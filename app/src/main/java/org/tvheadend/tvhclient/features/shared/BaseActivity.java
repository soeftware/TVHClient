package org.tvheadend.tvhclient.features.shared;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;

import org.tvheadend.tvhclient.R;
import org.tvheadend.tvhclient.data.service.EpgSyncService;
import org.tvheadend.tvhclient.data.service.EpgSyncStatusCallback;
import org.tvheadend.tvhclient.data.service.EpgSyncTaskState;
import org.tvheadend.tvhclient.data.service.worker.EpgWorkerHandler;
import org.tvheadend.tvhclient.features.programs.ProgramListFragment;
import org.tvheadend.tvhclient.features.shared.callbacks.NetworkAvailabilityChangedInterface;
import org.tvheadend.tvhclient.features.shared.callbacks.NetworkStatusInterface;
import org.tvheadend.tvhclient.features.shared.callbacks.NetworkStatusReceiverCallback;
import org.tvheadend.tvhclient.features.shared.receivers.NetworkStatusReceiver;
import org.tvheadend.tvhclient.features.shared.receivers.ServiceStatusReceiver;
import org.tvheadend.tvhclient.features.shared.receivers.SnackbarMessageReceiver;

import timber.log.Timber;

public abstract class BaseActivity extends AppCompatActivity implements LifecycleObserver, NetworkStatusReceiverCallback, NetworkStatusInterface, EpgSyncStatusCallback {

    private NetworkStatusReceiver networkStatusReceiver;
    private boolean isNetworkAvailable;
    private ServiceStatusReceiver serviceStatusReceiver;
    private SnackbarMessageReceiver snackbarMessageReceiver;
    private int serverConnectionRetryCounter;
    private boolean appIsInForeground;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        isNetworkAvailable = false;
        networkStatusReceiver = new NetworkStatusReceiver(this);
        serviceStatusReceiver = new ServiceStatusReceiver(this);
        snackbarMessageReceiver = new SnackbarMessageReceiver(this);
        serverConnectionRetryCounter = 0;
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(snackbarMessageReceiver, new IntentFilter(SnackbarMessageReceiver.ACTION));
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceStatusReceiver, new IntentFilter(ServiceStatusReceiver.ACTION));
        registerReceiver(networkStatusReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(snackbarMessageReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStatusReceiver);
        unregisterReceiver(networkStatusReceiver);
    }

    @Override
    public void onNetworkStatusChanged(boolean isNetworkAvailable) {
        onNetworkAvailabilityChanged(isNetworkAvailable);
        if (!isNetworkAvailable && getCurrentFocus() != null) {
            Snackbar.make(getCurrentFocus(), "No network available.", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void onNetworkAvailabilityChanged(boolean isAvailable) {
        if (!appIsInForeground) {
            Timber.d("App is in the background, not doing anything");
            return;
        }

        if (isAvailable) {
            if (!isNetworkAvailable) {
                Timber.d("Network changed from offline to online, starting service");
                startService(new Intent(this, EpgSyncService.class));
            } else {
                Timber.d("Network still active, pinging server");
                Intent intent = new Intent(this, EpgSyncService.class);
                intent.setAction("getStatus");
                startService(intent);
            }
        } else {
            Timber.d("Network is not available anymore, stopping service");
            stopService(new Intent(this, EpgSyncService.class));
        }
        isNetworkAvailable = isAvailable;

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main);
        if (fragment instanceof NetworkAvailabilityChangedInterface) {
            ((NetworkAvailabilityChangedInterface) fragment).onNetworkAvailabilityChanged(isAvailable);
        }

        fragment = getSupportFragmentManager().findFragmentById(R.id.details);
        if (fragment instanceof NetworkAvailabilityChangedInterface) {
            ((NetworkAvailabilityChangedInterface) fragment).onNetworkAvailabilityChanged(isAvailable);
        }
        Timber.d("Network availability changed, invalidating menu");
        invalidateOptionsMenu();
    }

    @Override
    public boolean isNetworkAvailable() {
        return isNetworkAvailable;
    }

    @Override
    public void onEpgTaskStateChanged(EpgSyncTaskState state) {
        Timber.d("Epg task state changed to " + state.getState() + ", message is " + state.getMessage());
        switch (state.getState()) {
            case CLOSED:
                if (getCurrentFocus() != null) {
                    Snackbar.make(getCurrentFocus(), state.getMessage(), Snackbar.LENGTH_SHORT).show();
                }
                stopService(new Intent(this, EpgSyncService.class));
                if (serverConnectionRetryCounter < 3) {
                    serverConnectionRetryCounter++;
                    Timber.d("Starting connection attempt number " + serverConnectionRetryCounter + " to the server");
                    startService(new Intent(this, EpgSyncService.class));
                } else {
                    Timber.d("Already tried to connect " + serverConnectionRetryCounter + " times to the server");
                }
                break;

            case FAILED:
                if (getCurrentFocus() != null) {
                    Snackbar.make(getCurrentFocus(), state.getMessage(), Snackbar.LENGTH_SHORT).show();
                }
                onNetworkAvailabilityChanged(false);
                break;

            case CONNECTING:
                if (getCurrentFocus() != null) {
                    Snackbar.make(getCurrentFocus(), state.getMessage(), Snackbar.LENGTH_SHORT).show();
                }
                break;

            case CONNECTED:
                if (getCurrentFocus() != null) {
                    Snackbar.make(getCurrentFocus(), state.getMessage(), Snackbar.LENGTH_SHORT).show();
                }
                // Reset the start service retry count
                serverConnectionRetryCounter = 0;
                EpgWorkerHandler.startBackgroundWorkers(getApplicationContext());
                break;
        }
    }

    @Override
    public void onBackPressed() {
        boolean navHistoryEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("navigation_history_enabled", true);
        if (!navHistoryEnabled) {
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main);
            if (fragment instanceof ProgramListFragment) {
                super.onBackPressed();
            } else {
                finish();
            }
        } else {
            if (getSupportFragmentManager().getBackStackEntryCount() <= 1) {
                finish();
            } else {
                super.onBackPressed();
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onAppBackgrounded() {
        Timber.d("Application is in background");
        appIsInForeground = false;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onAppForegrounded() {
        Timber.d("Application is in foreground");
        appIsInForeground = true;
    }
}
