package com.evolet.tachyon

import android.app.Application
import android.content.Context

class TachyonApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as TachyonApp).container
