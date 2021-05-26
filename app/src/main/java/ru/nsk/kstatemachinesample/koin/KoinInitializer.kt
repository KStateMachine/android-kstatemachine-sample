package ru.nsk.kstatemachinesample.koin

import android.content.Context
import androidx.startup.Initializer
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

class KoinInitializer : Initializer<KoinApplication> {
    override fun create(context: Context) = startKoin {
        androidContext(context)
        modules(koinModule)
    }

    override fun dependencies() = mutableListOf<Class<out Initializer<*>>>()
}