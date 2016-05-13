/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import ninja.berbert.app.R;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchface extends CanvasWatchFaceService {
    final static private String TAG = "Sunshine Watchface";
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchface.Engine> mWeakReference;

        public EngineHandler(SunshineWatchface.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchface.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Bitmap mWeatherIcon;
        Paint mTextPaint;
        Paint mHourTextPaint;
        Paint mDateTextPaint;
        Paint mMaxTextPaint;
        Paint mMinTextPaint;

        String mMaxText = "";
        String mMinText = "";
        Asset mIconAsset;

        boolean mAmbient;
        int mSunshineLightBlueColor;
        //        Time mTime;
        Calendar mCalendar;
        SimpleDateFormat sdfDate = new SimpleDateFormat("EEE, MMM dd yyyy", Locale.getDefault());
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setDateTime();
            }
        };

        float mXOffset;
        float mYTimeOffset;
        float mYDateOffset;
        float mYWeatherOffset;
        float mYWeatherIconOffset;
        /*float mYTimeOffsetRound;
        float mYDateOffsetRound;
        float mYWeatherOffsetRound;
        float mYWeatherIconOffsetRound;*/

        private GoogleApiClient googleApiClient;

        boolean mIsRound;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchface.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setHotwordIndicatorGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL)
                    .build());
            Resources resources = SunshineWatchface.this.getResources();
            mYTimeOffset = resources.getDimension(R.dimen.digital_y_offset_time);
            mYDateOffset = resources.getDimension(R.dimen.y_offset_date);
            mYWeatherOffset = resources.getDimension(R.dimen.y_offset_weather);
            mYWeatherIconOffset = resources.getDimension(R.dimen.y_offset_weather_icon);
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.sunshine_blue));

            mSunshineLightBlueColor = resources.getColor(R.color.sunshine_light_blue);

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE);
            mHourTextPaint = new Paint();
            mHourTextPaint = createTextPaint(resources.getColor(R.color.digital_text), BOLD_TYPEFACE);
            mDateTextPaint = new Paint();
            mDateTextPaint = createTextPaint(mSunshineLightBlueColor, NORMAL_TYPEFACE);
            mMaxTextPaint = new Paint();
            mMaxTextPaint = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE);
            mMinTextPaint = new Paint();
            mMinTextPaint = createTextPaint(mSunshineLightBlueColor, NORMAL_TYPEFACE);

//            mTime = new Time();
            mCalendar = Calendar.getInstance();

            googleApiClient = new GoogleApiClient.Builder(SunshineWatchface.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }


        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            int width = bounds.width();
            int center = width / 2;
            mCalendar.setTimeInMillis(System.currentTimeMillis());
//            mDate.setTime(System.currentTimeMillis());
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
                mDateTextPaint.setColor(Color.WHITE);
                mMinTextPaint.setColor(Color.WHITE);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                mDateTextPaint.setColor(mSunshineLightBlueColor);
                int rectStart;
                int rectEnd;
                int rectTop;
                int rectBottom;
                if(mIsRound) {
                    rectStart = Math.round(center - getResources().getDimension(R.dimen.weather_icon_width)/2);
                    rectTop = Math.round(mYWeatherIconOffset);
                    rectEnd = Math.round(rectStart + getResources().getDimension(R.dimen.weather_icon_width));
                    rectBottom = Math.round(mYWeatherIconOffset + getResources().getDimension(R.dimen.weather_icon_width));
                } else {
                    rectStart = Math.round(getResources().getDimension(R.dimen.x_offset_weather_icon));
                    rectEnd = Math.round(getResources().getDimension(R.dimen.x_end_offset_weather_icon));
                    rectTop = Math.round(mYWeatherIconOffset);
                    rectBottom = Math.round(mYWeatherIconOffset+rectEnd-rectStart);
                }
                if (mWeatherIcon != null ) //canvas.drawBitmap(mWeatherIcon,0,mYWeatherOffset,null);
                    canvas.drawBitmap(mWeatherIcon, null,
                            new Rect(rectStart,
                                    rectTop,
                                    rectEnd,
                                    rectBottom), null);

            }

//            mTime.setToNow();
            canvas.drawText(":", center - mHourTextPaint.measureText(":") / 2, mYTimeOffset, mHourTextPaint);

            String hourText = String.format(Locale.getDefault(), "%02d ", mCalendar.get(Calendar.HOUR_OF_DAY));
            canvas.drawText(hourText, center - mHourTextPaint.measureText(hourText), mYTimeOffset, mHourTextPaint);

            String minuteText = String.format(Locale.getDefault(), " %02d", mCalendar.get(Calendar.MINUTE));
            canvas.drawText(minuteText, center, mYTimeOffset, mTextPaint);

            // Date
            String dateText = sdfDate.format(mCalendar.getTime()).toUpperCase();
            canvas.drawText(dateText, center - mDateTextPaint.measureText(dateText) / 2, mYDateOffset, mDateTextPaint);

            // Weather
            if (mIsRound){
                float centerWeatherMaxPos = center - mMaxTextPaint.measureText(mMaxText);
                canvas.drawText(mMaxText, centerWeatherMaxPos, mYWeatherOffset , mMaxTextPaint);
                canvas.drawText(mMinText, center, mYWeatherOffset , mMinTextPaint);

            } else {
                float centerWeatherMaxPos = center - mMaxTextPaint.measureText(mMaxText) / 2;
                canvas.drawText(mMaxText, centerWeatherMaxPos, mYWeatherOffset , mMaxTextPaint);
                canvas.drawText(mMinText, centerWeatherMaxPos + mMaxTextPaint.measureText(mMaxText), mYWeatherOffset , mMinTextPaint);
            }

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            if (properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false))
                mHourTextPaint.setTypeface(NORMAL_TYPEFACE);
            else
                mHourTextPaint.setTypeface(BOLD_TYPEFACE);



        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mDateTextPaint.setAntiAlias(!inAmbientMode);
                    mHourTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (googleApiClient != null && googleApiClient.isConnected()) {
                googleApiClient.disconnect();
            }
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                setDateTime();
                googleApiClient.connect();
            } else {
                unregisterReceiver();
                if (googleApiClient != null && googleApiClient.isConnected()) {
                    googleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void setDateTime() {
            mCalendar.setTimeZone(TimeZone.getDefault());
            sdfDate = new SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault());
            sdfDate.setCalendar(mCalendar);
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchface.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchface.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchface.this.getResources();
            mIsRound = insets.isRound();
            mXOffset = resources.getDimension(mIsRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(mIsRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float dateTextSize = resources.getDimension(mIsRound
                    ? R.dimen.date_text_size_round : R.dimen.date_text_size);
            float weatherTextSize = resources.getDimension(mIsRound ? R.dimen.weather_text_size_round
                                    : R.dimen.weather_text_size);


            mYTimeOffset = resources.getDimension(mIsRound ? R.dimen.digital_y_offset_time_round : R.dimen.digital_y_offset_time);
            mYDateOffset = resources.getDimension(mIsRound ? R.dimen.y_offset_date_round : R.dimen.y_offset_date);
            mYWeatherOffset = resources.getDimension(mIsRound ? R.dimen.y_offset_weather_round : R.dimen.y_offset_weather);
            mYWeatherIconOffset = resources.getDimension(mIsRound ? R.dimen.y_offset_weather_icon_round : R.dimen.y_offset_weather_icon);



            mHourTextPaint.setTextSize(textSize);
            mTextPaint.setTextSize(textSize);
            mDateTextPaint.setTextSize(dateTextSize);
            mMaxTextPaint.setTextSize(weatherTextSize);
            mMinTextPaint.setTextSize(weatherTextSize);
        }


        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private static final String PATH = "/weather";

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(TAG, "connected GoogleAPI");
            Wearable.DataApi.addListener(googleApiClient, onDataChangedListener);
            Wearable.DataApi.getDataItems(googleApiClient).setResultCallback(onConnectedResultCallback);

            if(mMaxText.equals("") || mMinText.equals("")){ //Force the app to sync, when there is nothing to show
                new Thread() {
                    @Override
                    public void run() {
                        NodeApi.GetConnectedNodesResult connectedNodesResult = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                        for(Node node : connectedNodesResult.getNodes()) {
                            Log.d(TAG, "Node found: "+node.getDisplayName());
                            Wearable.MessageApi.sendMessage(googleApiClient, node.getId(),
                                    "/request-weather", new byte[0]).await();
                        }
                    }
                }.start();

            }
        }

        private final DataApi.DataListener onDataChangedListener = new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEventBuffer) {
                Log.d(TAG, "onDataChanged");
                for (DataEvent dataEvent : dataEventBuffer) {
                    if (Log.isLoggable(TAG, Log.DEBUG))
                        Log.d(TAG, "dataEvent " + dataEvent.getDataItem().getUri().getPath());
                    if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                        DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                        if (dataEvent.getDataItem().getUri().getPath().equals(PATH)) {
                            String max = dataMap.getString("max");
                            String min = dataMap.getString("min");
                            Asset weatherIcon = dataMap.getAsset("weatherIcon");

                            Log.d(TAG, "onDataChanged min" + min);
                            Log.d(TAG, "onDataChanged max" + max);

                            updateWeather(max, min, loadBitmapFromAsset(weatherIcon));
                        }
                    }
                }
            }
        };

        public Bitmap loadBitmapFromAsset(Asset asset) { //Snippet found at https://developer.android.com/intl/es/training/wearables/data-layer/assets.html
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            if (!googleApiClient.isConnected()) {
                return null;
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    googleApiClient, asset).await().getInputStream();

            if (assetInputStream == null) {
                Log.w(TAG, "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }

        private void updateWeather(String max, String min, Bitmap icon){
            mMaxText = max;
            mMinText = min;
            mWeatherIcon = icon;
        }
        private final ResultCallback<DataItemBuffer> onConnectedResultCallback = new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(DataItemBuffer dataItems) {
                for (DataItem item : dataItems) {
                    Log.d(TAG, "onConnectedResultCallback");
                }
                dataItems.release();
            }
        };

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "suspended GoogleAPI");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(TAG, "failed GoogleAPI");
        }
    }
}
