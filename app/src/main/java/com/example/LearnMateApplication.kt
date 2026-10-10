package com.example

import android.app.Application
import com.google.firebase.FirebaseApp

class LearnMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val firebaseApp = FirebaseApp.initializeApp(this) ?: return
        AppCheckProviderInstaller.install(firebaseApp)
    }
}
