package ru.nsk.kstatemachinesample.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ru.nsk.kstatemachinesample.R

/**
 * This is a single activity app
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
    }
}