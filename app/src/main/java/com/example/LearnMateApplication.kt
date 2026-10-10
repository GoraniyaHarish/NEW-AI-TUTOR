package com.example

import android.app.Application

class LearnMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Direct Gemini API mode does not require Firebase initialization or App Check.
    }
}
