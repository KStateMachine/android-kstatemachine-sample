package ru.nsk.kstatemachinesample.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.nsk.kstatemachinesample.databinding.MainFragmentBinding
import ru.nsk.kstatemachinesample.ui.main.ControlEvent.*

class MainFragment : Fragment() {
    private val viewModel by viewModel<MainViewModel>()
    private lateinit var binding: MainFragmentBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = MainFragmentBinding.inflate(inflater, container, false)
        binding.logTextView.movementMethod = ScrollingMovementMethod()
        log("note: To perform an air attack press Duck while jumping")
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.duckButton.setOnTouchListener(
            TouchListener(onDown = { DuckPressEvent.process() }, onUp = { DuckReleaseEvent.process() })
        )
        binding.fireButton.setOnTouchListener(
            TouchListener(onDown = { FirePressEvent.process() }, onUp = { FireReleaseEvent.process() })
        )
        binding.jumpButton.setOnTouchListener(
            TouchListener(onDown = { JumpPressEvent.process() }, onUp = { /* empty */ })
        )

        viewModel.currentState.observe(viewLifecycleOwner) { log("State: " + it.name) }
    }

    private fun log(text: String) = binding.logTextView.append(text + System.lineSeparator())

    private fun ControlEvent.process() {
        // log("user event: ${this::class.simpleName}")
        viewModel.processEvent(this)
    }

    private class TouchListener(private val onDown: () -> Unit, private val onUp: () -> Unit) : View.OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> onDown()
                MotionEvent.ACTION_UP -> onUp()
                else -> return false
            }
            return true
        }
    }
}