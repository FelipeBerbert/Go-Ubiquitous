package ninja.berbert.sunshinewatchface;

import android.util.Log;

import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Felipe Berbert on 06/05/2016.
 */
public class SunshineWatchfaceReceiverService extends WearableListenerService {
    private static final String PATH = "/weather";
    private final String LOG_TAG = SunshineWatchfaceReceiverService.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (Log.isLoggable(LOG_TAG, Log.DEBUG))
            Log.d(LOG_TAG, "onMessageReceived");
    }


    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        if (Log.isLoggable(LOG_TAG, Log.DEBUG))
            Log.d(LOG_TAG, "onDataChanged");
        for (DataEvent dataEvent : dataEvents){
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                Log.d(LOG_TAG, "dataEvent "+dataEvent.getDataItem().getUri().getPath());
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED){
                DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                if(dataEvent.getDataItem().getUri().getPath().equals(PATH)){
                    int max = dataMap.getInt("max");
                    int min = dataMap.getInt("min");
                    Asset weatherIcon = dataMap.getAsset("weatherIcon");

                    if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                        Log.d(LOG_TAG, "onDataChanged min" + min);
                        Log.d(LOG_TAG, "onDataChanged max" + max);
                    }
                }
            }
        }
    }
}
