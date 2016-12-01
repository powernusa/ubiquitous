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
import android.content.SharedPreferences;
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
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 * *
 * Edited by Andy
 */
public class SunshineWatchFaceService extends CanvasWatchFaceService {
    public static String LOG_TAG = SunshineWatchFaceService.class.getSimpleName();
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

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
        private final WeakReference<Engine> mWeakReference;

        public EngineHandler(SunshineWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine
            implements GoogleApiClient.ConnectionCallbacks, DataApi.DataListener {
        static final String WEATHER_DATA_PATH = "/WEATHER_DATA_PATH";
        static final String WEATHER_ID = "WEATHER_ID";
        static final String WEATHER_HIGH = "WEATHER_HIGH";
        static final String WEATHER_LOW = "WEATHER_LOW";
        static final String WEATHER_IS_METRIC = "WEATHER_IS_METRIC";
        static final String PREFS = "PREFS";
        static final String KEY_WEATHER_DESC= "KEY_WEATHER_DESC";
        static final String KEY_WEATHER_ID = "KEY_WEATHER_ID";
        static final String KEY_WEATHER_IS_METRIC = "KEY_WEATHER_IS_METRIC";


        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;

        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();

            }
        };

        float mTimeYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private GoogleApiClient mGoogleApiClient;
        String time ="";
        Time mTime;
        Paint mTimePaint;

        String date="";
        Paint mDatePaint;
        private int mWeatherId;
        private String mWeatherDesc;
        private boolean mIsMetric;
        @Nullable Bitmap mBitmap;

        Paint mIconPaint;
        Paint mTemperaturePaint;
        Paint mLinePaint;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
            mWeatherDesc = preferences.getString(KEY_WEATHER_DESC, "");
            mWeatherId = preferences.getInt(KEY_WEATHER_ID, 0);
            mIsMetric = preferences.getBoolean(KEY_WEATHER_IS_METRIC,true);
            loadIconForWeatherId();

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFaceService.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFaceService.this.getResources();


            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mCalendar = Calendar.getInstance();


            mTime = new Time();
            mTimePaint = new Paint();
            mTimePaint.setColor(resources.getColor(R.color.digital_text));
            mTimePaint.setTypeface(NORMAL_TYPEFACE);
            mTimePaint.setAntiAlias(true);

            mDatePaint = new Paint();
            mDatePaint.setColor(resources.getColor(R.color.digital_text));
            mDatePaint.setTypeface(NORMAL_TYPEFACE);
            mDatePaint.setAntiAlias(true);

            mIconPaint = new Paint();

            mTemperaturePaint = new Paint();
            mTemperaturePaint.setColor(resources.getColor(R.color.digital_text));
            mTemperaturePaint.setTypeface(NORMAL_TYPEFACE);
            mTemperaturePaint.setAntiAlias(true);

            mLinePaint = new Paint();
            mLinePaint.setColor(resources.getColor(R.color.digital_text));
            mLinePaint.setAntiAlias(true);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }



        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
            mGoogleApiClient.connect();
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mTimeYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);


            float digitalTextSize = getApplicationContext().getResources().getDimension(R.dimen.digital_text_size);
            mTimePaint.setTextSize(digitalTextSize);

            float dateTextSize = getApplicationContext().getResources().getDimension(R.dimen.date_text_size);
            mDatePaint.setTextSize(dateTextSize);

            float temperatureTextSize = resources.getDimension(R.dimen.temperature_text_size);
            mTemperaturePaint.setTextSize(temperatureTextSize);
            float lineSize = resources.getDimension(R.dimen.line_size);
            mLinePaint.setTextSize(lineSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            mTime.setToNow();
            time = String.format("%d:%02d",mTime.hour,mTime.minute);

            SimpleDateFormat formatter = new SimpleDateFormat("EEE, MMM d, yyyy");
            Date today = new Date();
            date = formatter.format(today);
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background

            if (isInAmbientMode()) {
                canvas.drawColor(Color.GRAY);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            float xPosTime = canvas.getWidth() / 2 - mTimePaint.measureText(time, 0, time.length()) / 2;
            canvas.drawText(time, xPosTime, mTimeYOffset, mTimePaint);


            if (!isInAmbientMode()) {

                float regular_padding = getApplicationContext().getResources().getDimension(R.dimen.regular_padding);
                float yPosDate = mTimeYOffset + mDatePaint.getTextSize() + regular_padding;
                float xPosDate = canvas.getWidth() / 2 - mDatePaint.measureText(date, 0, date.length()) / 2;

                canvas.drawText(date, xPosDate, yPosDate, mDatePaint);
                float line_padding = getApplicationContext().getResources().getDimension(R.dimen.line_padding);
                float yStartPosLine = yPosDate + line_padding;
                float xStartPosLine = canvas.getWidth() / 2 - mDatePaint.measureText(date, 0, date.length()) / 2;
                float xEndPosLine = canvas.getWidth()/2 + mDatePaint.measureText(date, 0, date.length()) / 2;
                float yEndPosLine = yStartPosLine;
                canvas.drawLine(xStartPosLine,yStartPosLine,xEndPosLine,yEndPosLine,mLinePaint);

                if (mWeatherId != 0 && mBitmap != null) {
                    // Weather Icon
                    float extraPadding25 = getApplicationContext().getResources().getDimension(R.dimen.extra_padding_25);
                    float yPosIcon = yPosDate + regular_padding ;
                    float xPosIcon = canvas.getWidth() / 2 - mBitmap.getWidth()- extraPadding25;
                    canvas.drawBitmap(mBitmap, xPosIcon, yPosIcon, mIconPaint);

                    // Temperature
                    float yPosWeather = yPosDate + (line_padding/2) + mTemperaturePaint.getTextSize() + mBitmap.getHeight() / 2;
                    float xPosWeather = canvas.getWidth() / 2  - extraPadding25;

                    canvas.drawText(mWeatherDesc != null ? mWeatherDesc : "", xPosWeather, yPosWeather, mTemperaturePaint);
                }

            }

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

        @Override
        public void onConnected(Bundle bundle) {
            Log.d(LOG_TAG, "onConnected");
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(LOG_TAG, "onConnectionSuspended");
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(LOG_TAG, "Weather data changed!");
            for (DataEvent event : dataEventBuffer) {
                DataItem item = event.getDataItem();
                if (WEATHER_DATA_PATH.equals(item.getUri().getPath())) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    double high = dataMap.getDouble(WEATHER_HIGH);
                    double low = dataMap.getDouble(WEATHER_LOW);
                    long id = dataMap.getLong(WEATHER_ID);
                    boolean isMetric = dataMap.getBoolean(WEATHER_IS_METRIC);

                    mWeatherId = (int) id;
                    mIsMetric = isMetric;
                    String unit;
                    if(mIsMetric){
                        unit = "C";
                        mWeatherDesc = getApplicationContext().getString(R.string.format_temperature,high,unit,low,unit);
                    }
                    else{
                        unit = "F";
                        mWeatherDesc = getApplicationContext().getString(R.string.format_temperature,high,unit,low,unit);
                    }
                    loadIconForWeatherId();

                    SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(KEY_WEATHER_DESC, mWeatherDesc);
                    editor.putInt(KEY_WEATHER_ID, mWeatherId);
                    editor.putBoolean(KEY_WEATHER_IS_METRIC,mIsMetric);
                    editor.apply();
                }
            }
        }
        private void loadIconForWeatherId() {

            int iconId = 0;
            if (mWeatherId >= 200 && mWeatherId <= 232) {
                iconId = R.drawable.ic_storm;
            } else if (mWeatherId >= 300 && mWeatherId <= 321) {
                iconId = R.drawable.ic_light_rain;
            } else if (mWeatherId >= 500 && mWeatherId <= 504) {
                iconId = R.drawable.ic_rain;
            } else if (mWeatherId == 511) {
                iconId = R.drawable.ic_snow;
            } else if (mWeatherId >= 520 && mWeatherId <= 531) {
                iconId = R.drawable.ic_rain;
            } else if (mWeatherId >= 600 && mWeatherId <= 622) {
                iconId = R.drawable.ic_snow;
            } else if (mWeatherId >= 701 && mWeatherId <= 761) {
                iconId = R.drawable.ic_fog;
            } else if (mWeatherId == 761 || mWeatherId == 781) {
                iconId = R.drawable.ic_storm;
            } else if (mWeatherId == 800) {
                iconId = R.drawable.ic_clear;
            } else if (mWeatherId == 801) {
                iconId = R.drawable.ic_light_clouds;
            } else if (mWeatherId >= 802 && mWeatherId <= 804) {
                iconId = R.drawable.ic_cloudy;
            }

            if (iconId != 0) {
                float scale = 1.2f;
                mBitmap = BitmapFactory.decodeResource(getResources(), iconId);
                float sizeY = (float) mBitmap.getHeight() * scale;
                float sizeX = (float) mBitmap.getWidth() * scale;
                mBitmap = Bitmap.createScaledBitmap(mBitmap, (int) sizeX, (int) sizeY, false);
            }
        }
    }
}
