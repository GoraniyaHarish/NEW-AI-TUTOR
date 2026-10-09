package com.example

import android.app.Application
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class LearnMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val firebaseApp = FirebaseApp.initializeApp(this) ?: return
        val providerFactory = if (BuildConfig.DEBUG) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        FirebaseAppCheck.getInstance(firebaseApp).installAppCheckProviderFactory(
            providerFactory
        )
    }
}
