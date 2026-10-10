package com.example

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object AppCheckProviderInstaller {
    fun install(firebaseApp: FirebaseApp) {
        FirebaseAppCheck.getInstance(firebaseApp).installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
