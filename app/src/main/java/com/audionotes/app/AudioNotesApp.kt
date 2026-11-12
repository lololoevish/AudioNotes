package com.audionotes.app

import android.app.Application
import com.audionotes.app.core.ServiceLocator

class AudioNotesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Прогреваем базу, чтобы первый экран открывался без задержки.
        ServiceLocator.repository(this)
    }
}
