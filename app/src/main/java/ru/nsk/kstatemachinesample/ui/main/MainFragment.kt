package ru.nsk.kstatemachinesample.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.nsk.kstatemachinesample.R
import ru.nsk.kstatemachinesample.databinding.MainFragmentBinding
import ru.nsk.kstatemachinesample.ui.main.ControlEvent.*
import ru.nsk.kstatemachinesample.ui.main.HeroState.*
import ru.nsk.kstatemachinesample.utils.TouchListener
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
        binding.reloadAmmoButton.setOnClickListener { viewModel.reloadAmmo() }

        viewModel.modelData.observe(viewLifecycleOwner) { dataModel ->
            dataModel.activeStates.let {
                fun setDrawable(@DrawableRes id: Int, @DrawableRes idShooting: Int) =
                    setHeroDrawable(if (it.hasState<Shooting>()) idShooting else id)

                when {
                    it.hasState<Standing>() -> setDrawable(R.drawable.standing, R.drawable.standing_shooting)
                    it.hasState<AirAttacking>() -> setDrawable(R.drawable.airattacking, R.drawable.airattacking_shooting)
                    it.hasState<Ducking>() -> setDrawable(R.drawable.ducking, R.drawable.ducking_shooting)
                    it.hasState<Jumping>() -> setDrawable(R.drawable.jumping, R.drawable.jumping_shooting)
                }
            }

            dataModel.ammoLeft.let {
                binding.ammoTextView.text = getString(R.string.ammo, it.toInt())
            }
        }

        viewModel.modelEffect.observe(viewLifecycleOwner) {
            when(it) {
                ModelEffect.AmmoDecremented -> log("*")
                is ModelEffect.CurrentStateChanged -> log(getString(R.string.state, it.state::class.simpleName))
                is ModelEffect.ControlEventChanged -> log(getString(R.string.event, it.event::class.simpleName))
            }
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