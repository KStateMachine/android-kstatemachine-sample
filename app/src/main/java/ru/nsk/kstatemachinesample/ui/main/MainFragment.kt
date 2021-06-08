package ru.nsk.kstatemachinesample.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.nsk.kstatemachinesample.R
import ru.nsk.kstatemachinesample.databinding.MainFragmentBinding
import ru.nsk.kstatemachinesample.ui.main.ControlEvent.*
import ru.nsk.kstatemachinesample.ui.main.HeroState.*

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
        binding.duckButton.setOnTouchListener(
            TouchListener(onDown = { DuckPressEvent.send() }, onUp = { DuckReleaseEvent.send() })
        )
        binding.fireButton.setOnTouchListener(
            TouchListener(onDown = { FirePressEvent.send() }, onUp = { FireReleaseEvent.send() })
        )
        binding.jumpButton.setOnTouchListener(
            TouchListener(onDown = { JumpPressEvent.send() }, onUp = { /* empty */ })
        )

        viewModel.controlEvent.observe(viewLifecycleOwner) { log(getString(R.string.event, it::class.simpleName)) }
        viewModel.currentState.observe(viewLifecycleOwner) { log(getString(R.string.state, it.name)) }

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
            log("*")
        }
    }

    private fun setHeroDrawable(@DrawableRes id: Int) =
        binding.heroImageView.setImageDrawable(ContextCompat.getDrawable(requireContext(), id))

    private fun log(text: String) = binding.logTextView.append(text + System.lineSeparator())

    private fun ControlEvent.send() = viewModel.sendEvent(this)
}

private inline fun <reified S : HeroState> List<HeroState>.hasState() = filterIsInstance<S>().isNotEmpty()

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