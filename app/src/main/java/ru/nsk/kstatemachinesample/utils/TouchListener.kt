package ru.nsk.kstatemachinesample.utils

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

class TouchListener(lifecycle: Lifecycle, private val onDown: () -> Unit, private val onUp: () -> Unit) :
    View.OnTouchListener, DefaultLifecycleObserver {
    private var holding = false

    init {
        lifecycle.addObserver(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN ->  {
                holding = true
                onDown()
            }
            MotionEvent.ACTION_UP -> {
                holding = false
                onUp()
            }
            else -> return false
        }
        return true
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // send release event if a view is destroying while a user holds it
        if (holding) onUp()
    }
}