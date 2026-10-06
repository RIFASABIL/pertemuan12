package com.industri.smartpos

import android.app.Application
import timber.log.Timber

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inisialisasi Timber DebugTree hanya pada mode DEBUG
        // Jangan mengaktifkan DebugTree pada mode Release
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
