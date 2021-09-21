package ru.nsk.kstatemachinesample.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.nsk.kstatemachinesample.R
import ru.nsk.kstatemachinesample.databinding.MainFragmentBinding
import ru.nsk.kstatemachinesample.ui.main.ControlEvent.*
import ru.nsk.kstatemachinesample.ui.main.HeroState.*
import java.lang.System.lineSeparator

class MainFragment : Fragment() {
    private val viewModel by viewModel<MainViewModel>()
    private lateinit var binding: MainFragmentBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = MainFragmentBinding.inflate(inflater, container, false)
        binding.logTextView.movementMethod = ScrollingMovementMethod()
        log("help: To perform an air attack press Duck while jumping")
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.logTextView.post { binding.logTextView.scrollToBottom() }

        binding.duckButton.setOnTouchListener(
            TouchListener(lifecycle, onDown = { DuckPressEvent.send() }, onUp = { DuckReleaseEvent.send() })
        )
        binding.fireButton.setOnTouchListener(
            TouchListener(lifecycle, onDown = { FirePressEvent.send() }, onUp = { FireReleaseEvent.send() })
        )
        binding.jumpButton.setOnTouchListener(
            TouchListener(lifecycle, onDown = { JumpPressEvent.send() }, onUp = { /* empty */ })
        )

        viewModel.controlEventChanged.observe(viewLifecycleOwner) {
            log(getString(R.string.event, it::class.simpleName))
        }
        viewModel.currentStateChanged.observe(viewLifecycleOwner) { log(getString(R.string.state, it.name)) }

        viewModel.activeStates.observe(viewLifecycleOwner) {
            fun setDrawable(@DrawableRes id: Int, @DrawableRes idShooting: Int) =
                setHeroDrawable(if (it.hasState<Shooting>()) idShooting else id)

            when {
                it.hasState<Standing>() -> setDrawable(R.drawable.standing, R.drawable.standing_shooting)
                it.hasState<AirAttacking>() -> setDrawable(R.drawable.airattacking, R.drawable.airattacking_shooting)
                it.hasState<Ducking>() -> setDrawable(R.drawable.ducking, R.drawable.ducking_shooting)
                it.hasState<Jumping>() -> setDrawable(R.drawable.jumping, R.drawable.jumping_shooting)
            }
        }

        viewModel.ammoLeft.observe(viewLifecycleOwner) {
            binding.ammoTextView.text = getString(R.string.ammo, it.toInt())
            log("*") // in real app we should not log LiveData notifications to avoid duplicates on config changes
        }
    }

    private fun setHeroDrawable(@DrawableRes id: Int) =
        binding.heroImageView.setImageDrawable(ContextCompat.getDrawable(requireContext(), id))

    private fun log(text: String) = with(binding.logTextView) {
        append(text + lineSeparator())
        scrollToBottom()
    }

    private fun ControlEvent.send() = viewModel.sendEvent(this)
}

private fun TextView.scrollToBottom() {
    layout?.let { layout ->
        val scrollDelta = (layout.getLineBottom(lineCount - 1) - scrollY - height)
        if (scrollDelta > 0) scrollBy(0, scrollDelta)
    }
}

private inline fun <reified S : HeroState> List<HeroState>.hasState() = filterIsInstance<S>().isNotEmpty()

private class TouchListener(lifecycle: Lifecycle, private val onDown: () -> Unit, private val onUp: () -> Unit) :
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