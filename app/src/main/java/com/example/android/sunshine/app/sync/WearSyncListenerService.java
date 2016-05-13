package com.example.android.sunshine.app.sync;

import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Felipe Berbert on 12/05/2016.
 */
public class WearSyncListenerService extends WearableListenerService {

    private final String LOG_TAG = WearSyncListenerService.class.getSimpleName();


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        Log.d(LOG_TAG, "Message received");
        if (messageEvent.getPath().equals("/request-weather")){
            SunshineSyncAdapter.syncImmediately(this);
        }
    }
}
