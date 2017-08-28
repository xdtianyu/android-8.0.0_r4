package com.android.networkrecommendation;

import android.app.Application;

import com.android.networkrecommendation.config.PreferenceFile;

/**
 * Initialize app-wide state.
 */
public class NetworkRecommendationApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        PreferenceFile.init(this);
    }
}
